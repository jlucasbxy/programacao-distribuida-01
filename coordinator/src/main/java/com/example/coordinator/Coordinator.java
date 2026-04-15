package com.example.coordinator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Coordinator {
    private final CoordinatorConfig config;
    private final BlockingQueue<String> frontierQueue;
    private final Set<String> visitedUrls;
    private final ConcurrentHashMap<String, WorkerState> workers;
    private final AtomicInteger tasksInFlight;
    private final AtomicBoolean running;
    private final AtomicBoolean completionReached;
    private final AtomicBoolean hadAnyWorker;

    private volatile ServerSocket serverSocket;

    public Coordinator(CoordinatorConfig config) {
        this.config = config;
        this.frontierQueue = new LinkedBlockingQueue<>();
        this.visitedUrls = ConcurrentHashMap.newKeySet();
        this.workers = new ConcurrentHashMap<>();
        this.tasksInFlight = new AtomicInteger(0);
        this.running = new AtomicBoolean(true);
        this.completionReached = new AtomicBoolean(false);
        this.hadAnyWorker = new AtomicBoolean(false);

        for (String seed : config.seeds()) {
            enqueueIfNew(seed);
        }
    }

    public void start() throws IOException {
        ExecutorService workerConnections = Executors.newFixedThreadPool(config.threadPoolSize());

        try (ServerSocket server = new ServerSocket(config.port())) {
            this.serverSocket = server;
            Runtime.getRuntime().addShutdownHook(new Thread(this::requestShutdown));

            System.out.println("Coordinator listening on port " + config.port()
                    + " with " + frontierQueue.size() + " initial seed(s).");

            while (running.get()) {
                try {
                    Socket socket = server.accept();
                    workerConnections.submit(() -> handleWorkerConnection(socket));
                } catch (SocketException e) {
                    if (running.get()) {
                        System.err.println("Error accepting connection: " + e.getMessage());
                    }
                }
            }
        } finally {
            requestShutdown();
            workerConnections.shutdown();
            try {
                if (!workerConnections.awaitTermination(5, TimeUnit.SECONDS)) {
                    workerConnections.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerConnections.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void handleWorkerConnection(Socket socket) {
        WorkerState worker = null;

        try (Socket workerSocket = socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(workerSocket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(workerSocket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            String registrationLine = reader.readLine();
            if (registrationLine == null) {
                return;
            }

            worker = registerWorker(registrationLine, workerSocket);
            if (worker == null) {
                writer.println("ERROR INVALID_REGISTER");
                return;
            }

            writer.println("REGISTERED " + worker.id() + " " + worker.capacity());
            System.out.println("Worker connected: " + worker.id() + " (capacity=" + worker.capacity() + ")");

            String line;
            while ((line = reader.readLine()) != null) {
                String response = handleMessage(worker, line);
                writer.println(response);
                if ("STOP".equals(response)) {
                    break;
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                System.err.println("Worker connection error: " + e.getMessage());
            }
        } finally {
            if (worker != null) {
                workers.remove(worker.id());
                String taskToRetry = worker.releaseTaskWithoutCompleting();
                if (taskToRetry != null) {
                    tasksInFlight.decrementAndGet();
                    frontierQueue.offer(taskToRetry);
                }
                evaluateCompletion();
            }
        }
    }

    private WorkerState registerWorker(String registrationLine, Socket socket) {
        String normalized = registrationLine == null ? "" : registrationLine.trim();
        if (!normalized.startsWith(CoordinatorMessageSupport.REGISTER_PREFIX)) {
            return null;
        }

        String[] parts = normalized.split("\\s+");
        String workerId;
        int capacity = 1;

        if (parts.length >= 2 && !parts[1].isBlank()) {
            workerId = parts[1];
        } else {
            workerId = "worker-" + UUID.randomUUID();
        }

        if (parts.length >= 3) {
            try {
                capacity = Math.max(1, Integer.parseInt(parts[2]));
            } catch (NumberFormatException ignored) {
                capacity = 1;
            }
        }

        WorkerState worker = new WorkerState(workerId, capacity);
        workers.put(worker.id(), worker);
        hadAnyWorker.set(true);
        return worker;
    }

    private String handleMessage(WorkerState worker, String line) {
        String message = line == null ? "" : line.trim();
        if (message.isBlank()) {
            return "ERROR EMPTY_MESSAGE";
        }

        if (completionReached.get()) {
            return "STOP";
        }

        if (message.equals(CoordinatorMessageSupport.REQUEST)) {
            return handleTaskRequest(worker);
        }

        if (message.startsWith(CoordinatorMessageSupport.FOUND_PREFIX)) {
            int added = handleFound(worker, message);
            evaluateCompletion();
            return "ACK FOUND " + added;
        }

        if (message.startsWith(CoordinatorMessageSupport.DONE_PREFIX)) {
            completeTask(worker);
            worker.markIdle();
            evaluateCompletion();
            return "ACK DONE";
        }

        if (message.equals(CoordinatorMessageSupport.IDLE)) {
            worker.markIdle();
            evaluateCompletion();
            return completionReached.get() ? "STOP" : "ACK IDLE";
        }

        if (message.equals(CoordinatorMessageSupport.HEARTBEAT)) {
            return "PONG";
        }

        if (message.equals(CoordinatorMessageSupport.QUIT)) {
            return "BYE";
        }

        return "ERROR UNKNOWN_COMMAND";
    }

    private String handleTaskRequest(WorkerState worker) {
        if (completionReached.get()) {
            return "STOP";
        }

        if (worker.hasAssignedTask()) {
            return "ERROR WORK_IN_PROGRESS";
        }

        String nextUrl = frontierQueue.poll();
        if (nextUrl != null) {
            worker.assignTask(nextUrl);
            tasksInFlight.incrementAndGet();
            return "TASK " + nextUrl;
        }

        worker.markIdle();
        evaluateCompletion();
        if (completionReached.get()) {
            return "STOP";
        }

        return "WAIT";
    }

    private int handleFound(WorkerState worker, String message) {
        completeTask(worker);
        worker.markIdle();

        List<String> links = CoordinatorMessageSupport.parseFoundLinks(message);
        int added = 0;
        for (String link : links) {
            if (enqueueIfNew(link)) {
                added++;
            }
        }
        return added;
    }

    private void completeTask(WorkerState worker) {
        String completedTask = worker.completeTask();
        if (completedTask != null) {
            tasksInFlight.decrementAndGet();
        }
    }

    private boolean enqueueIfNew(String url) {
        if (url == null) {
            return false;
        }

        String normalized = CoordinatorMessageSupport.normalizeUrl(url);
        if (normalized.isEmpty()) {
            return false;
        }

        if (!visitedUrls.add(normalized)) {
            return false;
        }

        frontierQueue.offer(normalized);
        return true;
    }

    private void evaluateCompletion() {
        if (!hadAnyWorker.get()) {
            return;
        }
        if (!frontierQueue.isEmpty()) {
            return;
        }
        if (tasksInFlight.get() > 0) {
            return;
        }

        boolean allIdle = workers.values().stream().allMatch(WorkerState::isIdle);
        if (allIdle) {
            completionReached.set(true);
            requestShutdown();
            System.out.println("Coordinator completed crawl: frontier empty, workers idle, no tasks in flight.");
        }
    }

    private void requestShutdown() {
        running.set(false);
        ServerSocket socket = this.serverSocket;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
