package com.example.coordinator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
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

    private final Object dispatchLock = new Object();
    private final ArrayDeque<WorkerState> idleWorkers = new ArrayDeque<>();

    private volatile ServerSocket serverSocket;

    public Coordinator(CoordinatorConfig config) {
        this.config = config;
        this.workers = new ConcurrentHashMap<>();
        this.crawlState = new CrawlState(workers);
        this.running = new AtomicBoolean(true);

        crawlState.setOnCompletion(this::onCrawlComplete);

        for (String seed : SeedsLoader.load(config.seedsFile())) {
            crawlState.enqueueIfNew(seed);
        }
    }

    public void start() throws IOException {
        ExecutorService workerConnections = Executors.newVirtualThreadPerTaskExecutor();

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

            worker = registerWorker(registrationLine, writer);
            if (worker == null) {
                writer.println("ERROR INVALID_REGISTER");
                return;
            }

            writer.println("REGISTERED " + worker.id() + " " + worker.capacity());
            System.out.println("Worker connected: " + worker.id() + " (capacity=" + worker.capacity() + ")");
            markWorkerIdle(worker);

            String line;
            while ((line = reader.readLine()) != null) {
                HandleResult result = handleMessage(worker, line);
                writer.println(result.response());
                if (result.dispatchAfter()) {
                    markWorkerIdle(worker);
                }
                if ("BYE".equals(result.response())) {
                    break;
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                System.err.println("Worker connection error: " + e.getMessage());
            }
        } finally {
            if (worker != null) {
                removeWorker(worker);
            }
        }
    }

    private WorkerState registerWorker(String registrationLine, PrintWriter writer) {
        RegisterRequest req = RegisterRequest.parse(registrationLine);
        if (req == null) {
            return null;
        }

        WorkerState worker = new WorkerState(req.workerId(), req.capacity());
        worker.attachWriter(writer);
        workers.put(worker.id(), worker);
        crawlState.markWorkerRegistered();
        return worker;
    }

    private void removeWorker(WorkerState worker) {
        workers.remove(worker.id());
        synchronized (dispatchLock) {
            idleWorkers.remove(worker);
        }
        boolean requeued = crawlState.retryTask(worker);
        if (requeued) tryDispatch();
        crawlState.evaluateCompletion();
    }

    private HandleResult handleMessage(WorkerState worker, String line) {
        if (line == null || line.isBlank()) {
            return new HandleResult("ERROR EMPTY_MESSAGE", false);
        }

        return switch (MessageType.parse(line)) {
            case FOUND -> {
                int added = crawlState.addFoundLinks(line.trim());
                if (added > 0) tryDispatch();
                yield new HandleResult("ACK FOUND " + added, false);
            }
            case DONE -> {
                crawlState.completeTask(worker);
                crawlState.evaluateCompletion();
                yield new HandleResult("ACK DONE", true);
            }
            case IDLE -> {
                worker.markIdle();
                crawlState.evaluateCompletion();
                yield new HandleResult("ACK IDLE", true);
            }
            case HEARTBEAT -> new HandleResult("PONG", false);
            case QUIT -> new HandleResult("BYE", false);
            case UNKNOWN -> new HandleResult("ERROR UNKNOWN_COMMAND", false);
        };
    }

    private record HandleResult(String response, boolean dispatchAfter) {}

    private void markWorkerIdle(WorkerState worker) {
        String url;
        synchronized (dispatchLock) {
            url = crawlState.pollAndAssign(worker);
            if (url == null) {
                idleWorkers.addLast(worker);
                return;
            }
        }
        worker.sendLine("TASK " + url);
    }

    private void tryDispatch() {
        List<Assignment> toSend = null;
        synchronized (dispatchLock) {
            while (!idleWorkers.isEmpty()) {
                WorkerState w = idleWorkers.peekFirst();
                String url = crawlState.pollAndAssign(w);
                if (url == null) break;
                idleWorkers.pollFirst();
                if (toSend == null) toSend = new ArrayList<>();
                toSend.add(new Assignment(w, url));
            }
        }
        if (toSend != null) {
            for (Assignment a : toSend) {
                a.worker.sendLine("TASK " + a.url);
            }
        }
    }

    private record Assignment(WorkerState worker, String url) {}

    private void onCrawlComplete() {
        Thread.ofVirtual().name("stop-broadcaster").start(() -> {
            broadcastStop();
            requestShutdown();
        });
    }

    private void broadcastStop() {
        for (WorkerState w : workers.values()) {
            try {
                w.sendLine("STOP");
            } catch (Exception ignored) {
            }
        }
    }

    private void requestShutdown() {
        if (!running.compareAndSet(true, false)) return;
        ServerSocket socket = this.serverSocket;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
