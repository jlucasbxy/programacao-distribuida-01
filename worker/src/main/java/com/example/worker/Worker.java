package com.example.worker;

import com.example.common.dataserver.DataServerClient;
import com.example.common.dataserver.DataServerResponse;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Worker {
    private static final int PING_INTERVAL_MS       = 5_000;
    private static final int COORDINATOR_TIMEOUT_MS = 15_000;
    private static final int SHUTDOWN_TIMEOUT_SEC   = 30;

    private final WorkerConfig config;
    private final Object writerLock = new Object();
    private static final Object stdoutLock = new Object();
    private static final FileOutputStream stdout = new FileOutputStream(FileDescriptor.out);

    public Worker(WorkerConfig config) {
        this.config = config;
    }

    public void start() {
        String logPrefix = "[" + config.workerId() + "] ";
        ExecutorService taskPool = Executors.newVirtualThreadPerTaskExecutor();

        try (Socket socket = new Socket(config.coordinatorHost(), config.coordinatorPort());
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            socket.setSoTimeout(COORDINATOR_TIMEOUT_MS);

            sendLine(writer, "REGISTER " + config.workerId() + " " + config.capacity());
            String registered = reader.readLine();
            if (registered == null || !registered.startsWith("REGISTERED")) {
                System.err.println(logPrefix + "Registration failed: " + registered);
                return;
            }
            logInfo(logPrefix + "REGISTERED (capacity=" + config.capacity() + ")");

            Thread pingThread = startPingThread(socket, writer);

            try {
                runReaderLoop(logPrefix, reader, writer, taskPool);
            } finally {
                pingThread.interrupt();
            }

            logInfo(logPrefix + "Crawl complete. Disconnecting.");
        } catch (SocketTimeoutException e) {
            System.err.println(logPrefix + "Coordinator timed out (no heartbeat).");
        } catch (IOException e) {
            System.err.println(logPrefix + "Error: " + e.getMessage());
        } finally {
            shutdownPool(logPrefix, taskPool);
        }
    }

    private void runReaderLoop(String logPrefix, BufferedReader reader, PrintWriter writer, ExecutorService taskPool) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("TASK ")) {
                String url = line.substring(5).trim();
                taskPool.submit(() -> processTask(logPrefix, url, writer));
            } else if ("STOP".equals(line)) {
                drainTasks(logPrefix, taskPool);
                sendLine(writer, "QUIT");
                return;
            }
            // PING, ACK *, REGISTERED (already consumed), and unknown lines are ignored
        }
    }

    private void drainTasks(String logPrefix, ExecutorService taskPool) {
        taskPool.shutdown();
        try {
            if (!taskPool.awaitTermination(SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                System.err.println(logPrefix + "In-flight tasks did not finish within " + SHUTDOWN_TIMEOUT_SEC + "s.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void shutdownPool(String logPrefix, ExecutorService taskPool) {
        if (taskPool.isShutdown()) return;
        taskPool.shutdown();
        try {
            if (!taskPool.awaitTermination(5, TimeUnit.SECONDS)) {
                taskPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            taskPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private Thread startPingThread(Socket socket, PrintWriter writer) {
        return Thread.ofVirtual().name("ping-" + config.workerId()).start(() -> {
            try {
                while (!socket.isClosed()) {
                    Thread.sleep(PING_INTERVAL_MS);
                    if (!socket.isClosed()) sendLine(writer, "PING");
                }
            } catch (InterruptedException ignored) {}
        });
    }

    private void processTask(String logPrefix, String url, PrintWriter writer) {
        try {
            DataServerClient client = new DataServerClient(config.dataServerHost(), config.dataServerPort());
            DataServerResponse page = client.getPage(url);

            if (page.isError()) {
                System.err.println(logPrefix + "Error fetching " + url + ": " + page.error());
                sendLine(writer, "DONE " + url);
                return;
            }

            List<String> links = page.links().stream()
                    .filter(link -> link != null && !link.isBlank())
                    .filter(link -> !link.equals(url))
                    .toList();

            String category = config.categories().entrySet().stream()
                    .filter(e -> e.getValue().test(page.content()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse("geral");

            logInfo(logPrefix + "crawled=" + url
                    + " category=" + category
                    + " links=" + links.size());

            synchronized (writerLock) {
                if (!links.isEmpty()) {
                    writer.println("FOUND: " + String.join(", ", links) + " FROM " + url);
                }
                writer.println("DONE " + url);
            }
        } catch (IOException e) {
            System.err.println(logPrefix + "Task error for " + url + ": " + e.getMessage());
            sendLine(writer, "DONE " + url);
        }
    }

    private void sendLine(PrintWriter writer, String line) {
        synchronized (writerLock) {
            writer.println(line);
        }
    }

    private void logInfo(String message) {
        byte[] line = (message + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        synchronized (stdoutLock) {
            try {
                stdout.write(line);
                stdout.flush();
            } catch (IOException e) {
                System.out.println(message);
            }
        }
    }
}
