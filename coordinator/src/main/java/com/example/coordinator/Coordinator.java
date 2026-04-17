package com.example.coordinator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Coordinator {
    private final CoordinatorConfig config;
    private final ConcurrentHashMap<String, WorkerState> workers;
    private final CrawlState crawlState;
    private final AtomicBoolean running;

    private volatile ServerSocket serverSocket;

    public Coordinator(CoordinatorConfig config) {
        this.config = config;
        this.workers = new ConcurrentHashMap<>();
        this.crawlState = new CrawlState(workers);
        this.running = new AtomicBoolean(true);

        crawlState.setOnCompletion(this::requestShutdown);

        for (String seed : config.seeds()) {
            crawlState.enqueueIfNew(seed);
        }
    }

    public void start() throws IOException {
        ExecutorService workerConnections = Executors.newFixedThreadPool(config.threadPoolSize());

        try (ServerSocket server = new ServerSocket(config.port())) {
            this.serverSocket = server;
            Runtime.getRuntime().addShutdownHook(new Thread(this::requestShutdown));

            System.out.println("Coordinator listening on port " + config.port()
                    + " with " + crawlState.frontierSize() + " initial seed(s).");

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

            worker = registerWorker(registrationLine);
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
                crawlState.retryTask(worker);
                crawlState.evaluateCompletion();
            }
        }
    }

    private WorkerState registerWorker(String registrationLine) {
        RegisterRequest req = RegisterRequest.parse(registrationLine);
        if (req == null) {
            return null;
        }

        WorkerState worker = new WorkerState(req.workerId(), req.capacity());
        workers.put(worker.id(), worker);
        crawlState.markWorkerRegistered();
        return worker;
    }

    private String handleMessage(WorkerState worker, String line) {
        if (line == null || line.isBlank()) {
            return "ERROR EMPTY_MESSAGE";
        }

        if (crawlState.isCompletionReached()) {
            return "STOP";
        }

        return switch (MessageType.parse(line)) {
            case REQUEST -> handleTaskRequest(worker);
            case FOUND -> {
                int added = crawlState.addFoundLinks(worker, line.trim());
                crawlState.evaluateCompletion();
                yield "ACK FOUND " + added;
            }
            case DONE -> {
                crawlState.completeTask(worker);
                worker.markIdle();
                crawlState.evaluateCompletion();
                yield "ACK DONE";
            }
            case IDLE -> {
                worker.markIdle();
                crawlState.evaluateCompletion();
                yield crawlState.isCompletionReached() ? "STOP" : "ACK IDLE";
            }
            case HEARTBEAT -> "PONG";
            case QUIT -> "BYE";
            case UNKNOWN -> "ERROR UNKNOWN_COMMAND";
        };
    }

    private String handleTaskRequest(WorkerState worker) {
        if (crawlState.isCompletionReached()) {
            return "STOP";
        }

        if (worker.hasAssignedTask()) {
            return "ERROR WORK_IN_PROGRESS";
        }

        String nextUrl = crawlState.pollTask();
        if (nextUrl != null) {
            worker.assignTask(nextUrl);
            crawlState.incrementTasksInFlight();
            return "TASK " + nextUrl;
        }

        worker.markIdle();
        crawlState.evaluateCompletion();
        if (crawlState.isCompletionReached()) {
            return "STOP";
        }

        return "WAIT";
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
