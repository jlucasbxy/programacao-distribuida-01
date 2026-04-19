package com.example.coordinator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
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
    private final List<WorkerState> rotation = new ArrayList<>();
    private int rrIndex = 0;

    private volatile ServerSocket serverSocket;
    private volatile Thread dispatcherThread;

    public Coordinator(CoordinatorConfig config) {
        this.config = config;
        this.workers = new ConcurrentHashMap<>();
        this.crawlState = new CrawlState(workers);
        this.running = new AtomicBoolean(true);

        crawlState.setOnCompletion(this::signalDispatcher);
        crawlState.setOnUrlEnqueued(this::signalDispatcher);

        for (String seed : SeedsLoader.load(config.seedsFile())) {
            crawlState.enqueueIfNew(seed);
        }
    }

    public void start() throws IOException {
        ExecutorService workerConnections = Executors.newVirtualThreadPerTaskExecutor();

        dispatcherThread = Thread.ofVirtual().name("dispatcher").start(this::dispatchLoop);

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
            signalDispatcher();

            String line;
            while ((line = reader.readLine()) != null) {
                String response = handleMessage(worker, line);
                writer.println(response);
                signalDispatcher();
                if ("BYE".equals(response)) {
                    break;
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                System.err.println("Worker connection error: " + e.getMessage());
            }
        } finally {
            if (worker != null) {
                unregisterWorker(worker);
                crawlState.retryTask(worker);
                crawlState.evaluateCompletion();
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
        synchronized (dispatchLock) {
            rotation.add(worker);
        }
        crawlState.markWorkerRegistered();
        return worker;
    }

    private void unregisterWorker(WorkerState worker) {
        workers.remove(worker.id());
        synchronized (dispatchLock) {
            int i = rotation.indexOf(worker);
            if (i >= 0) {
                rotation.remove(i);
                if (rotation.isEmpty()) {
                    rrIndex = 0;
                } else {
                    if (rrIndex > i) rrIndex--;
                    if (rrIndex >= rotation.size()) rrIndex = 0;
                }
            }
            dispatchLock.notifyAll();
        }
    }

    private String handleMessage(WorkerState worker, String line) {
        if (line == null || line.isBlank()) {
            return "ERROR EMPTY_MESSAGE";
        }

        return switch (MessageType.parse(line)) {
            case FOUND -> {
                int added = crawlState.addFoundLinks(line.trim());
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
                yield "ACK IDLE";
            }
            case HEARTBEAT -> "PONG";
            case QUIT -> "BYE";
            case UNKNOWN -> "ERROR UNKNOWN_COMMAND";
        };
    }

    private void dispatchLoop() {
        try {
            while (running.get()) {
                WorkerState target = null;
                String url = null;
                synchronized (dispatchLock) {
                    while (running.get() && !crawlState.isCompletionReached()) {
                        if (crawlState.frontierSize() > 0) {
                            target = pickNextIdleWorker();
                            if (target != null) break;
                        }
                        dispatchLock.wait();
                    }
                    if (!running.get() || crawlState.isCompletionReached()) break;
                    url = crawlState.pollAndAssign(target);
                }
                if (url != null && target != null) {
                    target.sendLine("TASK " + url);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            broadcastStop();
            requestShutdown();
        }
    }

    private WorkerState pickNextIdleWorker() {
        int n = rotation.size();
        if (n == 0) return null;
        for (int i = 0; i < n; i++) {
            int idx = (rrIndex + i) % n;
            WorkerState w = rotation.get(idx);
            if (w.isIdle() && !w.hasAssignedTask()) {
                rrIndex = (idx + 1) % n;
                return w;
            }
        }
        return null;
    }

    private void signalDispatcher() {
        synchronized (dispatchLock) {
            dispatchLock.notifyAll();
        }
    }

    private void broadcastStop() {
        List<WorkerState> snapshot;
        synchronized (dispatchLock) {
            snapshot = new ArrayList<>(rotation);
        }
        for (WorkerState w : snapshot) {
            try {
                w.sendLine("STOP");
            } catch (Exception ignored) {
            }
        }
    }

    private void requestShutdown() {
        if (!running.compareAndSet(true, false)) return;
        signalDispatcher();
        ServerSocket socket = this.serverSocket;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
