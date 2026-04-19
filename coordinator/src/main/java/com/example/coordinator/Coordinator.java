package com.example.coordinator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
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

    private static final int WORKER_TIMEOUT_MS  = 15_000;
    private static final int PING_INTERVAL_MS   = 5_000;

    private volatile ServerSocket serverSocket;
    private volatile ExecutorService workerConnections;

    public Coordinator(CoordinatorConfig config) {
        this.config = config;
        this.workers = new ConcurrentHashMap<>();
        this.crawlState = new CrawlState(workers);
        this.running = new AtomicBoolean(true);

        crawlState.setOnCompletion(this::onCrawlComplete);

        crawlState.enqueueAllIfNew(SeedsLoader.load(config.seedsFile(), config.seedsCount()));
    }

    public void start() throws IOException {
        this.workerConnections = Executors.newVirtualThreadPerTaskExecutor();
        Thread pingBroadcaster = startPingBroadcaster();

        try (ServerSocket server = new ServerSocket(config.port())) {
            this.serverSocket = server;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                pingBroadcaster.interrupt();
                requestShutdown();
            }));

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
            pingBroadcaster.interrupt();
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

    private Thread startPingBroadcaster() {
        return Thread.ofVirtual().name("ping-broadcaster").start(() -> {
            try {
                while (running.get()) {
                    Thread.sleep(PING_INTERVAL_MS);
                    broadcastParallel("PING");
                }
            } catch (InterruptedException ignored) {}
        });
    }

    private void broadcastParallel(String message) {
        ExecutorService executor = this.workerConnections;
        if (executor == null || workers.isEmpty()) return;
        CompletableFuture<?>[] sends = workers.values().stream()
                .map(w -> CompletableFuture.runAsync(() -> {
                    try {
                        w.sendLine(message);
                    } catch (Exception ignored) {}
                }, executor))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(sends).join();
    }

    private void handleWorkerConnection(Socket socket) {
        WorkerState worker = null;

        try (Socket workerSocket = socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(workerSocket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(workerSocket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            workerSocket.setSoTimeout(WORKER_TIMEOUT_MS);
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
            tryDispatch();

            String line;
            while ((line = reader.readLine()) != null) {
                if (!handleMessage(worker, writer, line)) break;
            }
        } catch (SocketTimeoutException e) {
            System.err.println("Worker timed out (no heartbeat): " + (worker != null ? worker.id() : "unknown"));
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
        System.out.println("Worker connected: " + req.workerId() + " (capacity=" + req.capacity() + ")");
        return worker;
    }

    private void removeWorker(WorkerState worker) {
        workers.remove(worker.id());
        int requeued = crawlState.retryTasks(worker);
        if (requeued > 0) tryDispatch();
        crawlState.evaluateCompletion();
    }

    private boolean handleMessage(WorkerState worker, PrintWriter writer, String line) {
        if (line == null || line.isBlank()) {
            writer.println("ERROR EMPTY_MESSAGE");
            return true;
        }

        switch (MessageType.parse(line)) {
            case FOUND -> {
                int added = crawlState.addFoundLinks(line.trim());
                writer.println("ACK FOUND " + added);
                if (added > 0) tryDispatch();
            }
            case DONE -> {
                String url = parseDoneUrl(line);
                if (url != null) {
                    crawlState.completeTask(worker, url);
                }
                writer.println("ACK DONE");
                crawlState.evaluateCompletion();
                tryDispatch();
            }
            case QUIT -> {
                writer.println("BYE");
                return false;
            }
            case PING -> { /* liveness heartbeat, no response needed */ }
            case UNKNOWN -> writer.println("ERROR UNKNOWN_COMMAND");
        }
        return true;
    }

    private static String parseDoneUrl(String line) {
        String trimmed = line.trim();
        if (trimmed.length() <= "DONE".length()) return null;
        String payload = trimmed.substring("DONE".length()).trim();
        return payload.isBlank() ? null : payload;
    }

    private void tryDispatch() {
        for (WorkerState w : workers.values()) {
            String url;
            while ((url = crawlState.pollAndAssign(w)) != null) {
                w.sendLine("TASK " + url);
            }
        }
    }

    private void onCrawlComplete() {
        Thread.ofVirtual().name("stop-broadcaster").start(() -> {
            broadcastStop();
            requestShutdown();
        });
    }

    private void broadcastStop() {
        broadcastParallel("STOP");
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
