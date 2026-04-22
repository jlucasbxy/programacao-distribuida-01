package com.example.worker;

import com.example.common.concurrent.ExecutorShutdown;
import com.example.common.dataserver.DataServerClient;
import com.example.common.dataserver.DataServerResponse;
import com.example.common.logging.AppLogger;
import com.example.common.logging.Loggers;
import com.example.common.protocol.Protocol;

import java.io.BufferedReader;
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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Worker {
    private static final int PING_INTERVAL_MS       = 5_000;
    private static final int COORDINATOR_TIMEOUT_MS = 15_000;
    private static final int SHUTDOWN_TIMEOUT_SEC   = 30;

    private final WorkerConfig config;
    private final AppLogger logger;
    private final Object writerLock = new Object();

    public Worker(WorkerConfig config) {
        this.config = config;
        this.logger = Loggers.consoleWithPrefix("worker-" + config.workerId(), "[" + config.workerId() + "] ");
    }

    public void start() {
        ExecutorService taskPool = Executors.newVirtualThreadPerTaskExecutor();

        try (Socket socket = new Socket(config.coordinatorHost(), config.coordinatorPort());
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            socket.setSoTimeout(COORDINATOR_TIMEOUT_MS);

            if (!register(reader, writer)) {
                return;
            }

            Future<?> pingTask = startPingTask(taskPool, socket, writer);

            try {
                runReaderLoop(reader, writer, taskPool, pingTask);
            } finally {
                pingTask.cancel(true);
            }

            logger.info("Crawl complete. Disconnecting.");
        } catch (SocketTimeoutException e) {
            logger.error("Coordinator timed out (no heartbeat).");
        } catch (IOException e) {
            logger.error("Error: " + e.getMessage());
        } finally {
            shutdownPool(taskPool);
        }
    }

    private boolean register(BufferedReader reader, PrintWriter writer) throws IOException {
        sendLine(writer, Protocol.REGISTER + " " + config.workerId() + " " + config.capacity());
        String registered = reader.readLine();
        if (registered == null || !registered.startsWith(Protocol.REGISTERED)) {
            logger.error("Registration failed: " + registered);
            return false;
        }
        logger.info(Protocol.REGISTERED + " (capacity=" + config.capacity() + ")");
        return true;
    }

    private void runReaderLoop(
            BufferedReader reader,
            PrintWriter writer,
            ExecutorService taskPool,
            Future<?> pingTask
    ) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(Protocol.TASK + " ")) {
                String url = line.substring(Protocol.TASK.length() + 1).trim();
                taskPool.submit(() -> processTask(url, writer));
            } else if (Protocol.STOP.equals(line)) {
                pingTask.cancel(true);
                drainTasks(taskPool);
                sendLine(writer, Protocol.QUIT);
                return;
            }
            // PING, ACK *, REGISTERED (already consumed), and unknown lines are ignored
        }
    }

    private void drainTasks(ExecutorService taskPool) {
        taskPool.shutdown();
        try {
            if (!taskPool.awaitTermination(SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                logger.error("In-flight tasks did not finish within " + SHUTDOWN_TIMEOUT_SEC + "s.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void shutdownPool(ExecutorService taskPool) {
        ExecutorShutdown.shutdownGracefully(taskPool, 5, TimeUnit.SECONDS);
    }

    private Future<?> startPingTask(ExecutorService taskPool, Socket socket, PrintWriter writer) {
        return taskPool.submit(() -> {
            try {
                while (!socket.isClosed()) {
                    Thread.sleep(PING_INTERVAL_MS);
                    if (!socket.isClosed()) sendLine(writer, Protocol.PING);
                }
            } catch (InterruptedException ignored) {}
        });
    }

    private void processTask(String url, PrintWriter writer) {
        DataServerClient client = new DataServerClient(config.dataServerHost(), config.dataServerPort());
        DataServerResponse page = client.getPage(url);

        if (page.isError()) {
            logger.error("Error fetching " + url + ": " + page.error());
            sendLine(writer, Protocol.DONE + " " + url);
            return;
        }

        String content = page.content();

        List<String> links = LinkExtractor.extract(content, url)
                    .stream()
                    .filter(link -> link != null && !link.isBlank())
                    .filter(link -> !link.equals(url))
                    .toList();

        String category = config.categories().entrySet().stream()
                .filter(e -> e.getValue().test(content))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("geral");


        logger.info("crawled=" + url
                + " category=" + category
                + " foundLinks=" + links.size());

        synchronized (writerLock) {
            if (!links.isEmpty()) {
                writer.println(Protocol.FOUND_PREFIX + " " + String.join(", ", links) + " FROM " + url);
            }
            writer.println(Protocol.DONE + " " + url);
        }
    }

    private void sendLine(PrintWriter writer, String line) {
        synchronized (writerLock) {
            writer.println(line);
        }
    }

}
