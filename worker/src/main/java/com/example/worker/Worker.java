package com.example.worker;

import com.example.common.dataserver.DataServerClient;
import com.example.common.dataserver.DataServerResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class Worker {
    private static final int PING_INTERVAL_MS      = 5_000;
    private static final int COORDINATOR_TIMEOUT_MS = 15_000;

    private final WorkerConfig config;

    public Worker(WorkerConfig config) {
        this.config = config;
    }

    public void start() {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<?>[] threads = new CompletableFuture[config.capacity()];
            for (int i = 0; i < config.capacity(); i++) {
                final String threadId = config.workerId() + "-" + i;
                threads[i] = CompletableFuture.runAsync(() -> runWorkerThread(threadId), pool);
            }
            CompletableFuture.allOf(threads).join();
        }
    }

    private void runWorkerThread(String threadId) {
        try (Socket socket = new Socket(config.coordinatorHost(), config.coordinatorPort());
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            socket.setSoTimeout(COORDINATOR_TIMEOUT_MS);

            writer.println("REGISTER " + threadId + " 1");
            String registered = reader.readLine();
            if (registered == null || !registered.startsWith("REGISTERED")) {
                System.err.println("[" + threadId + "] Registration failed: " + registered);
                return;
            }
            System.out.println("[" + threadId + "] " + registered);

            Thread pingThread = startPingThread(threadId, socket, writer);

            boolean running = true;
            while (running) {
                String response = reader.readLine();
                if (response == null) break;

                if (response.startsWith("TASK ")) {
                    String url = response.substring(5).trim();
                    processTask(threadId, url, writer, reader);
                } else if ("STOP".equals(response)) {
                    writer.println("QUIT");
                    readSkipPing(reader);
                    running = false;
                } else if (!"PING".equals(response)) {
                    System.err.println("[" + threadId + "] Unexpected response: " + response);
                }
            }

            pingThread.interrupt();
            System.out.println("[" + threadId + "] Crawl complete. Disconnecting.");
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("[" + threadId + "] Coordinator timed out (no heartbeat).");
        } catch (IOException e) {
            System.err.println("[" + threadId + "] Error: " + e.getMessage());
        }
    }

    private Thread startPingThread(String threadId, Socket socket, PrintWriter writer) {
        return Thread.ofVirtual().name("ping-" + threadId).start(() -> {
            try {
                while (!socket.isClosed()) {
                    Thread.sleep(PING_INTERVAL_MS);
                    if (!socket.isClosed()) writer.println("PING");
                }
            } catch (InterruptedException ignored) {}
        });
    }

    private String readSkipPing(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null && "PING".equals(line)) {}
        return line;
    }

    private void processTask(String threadId, String url, PrintWriter writer, BufferedReader reader) throws IOException {
        DataServerClient client = new DataServerClient(config.dataServerHost(), config.dataServerPort());
        DataServerResponse page = client.getPage(url);

        if (page.isError()) {
            System.err.println("[" + threadId + "] Error fetching " + url + ": " + page.error());
            writer.println("DONE");
            readSkipPing(reader);
            return;
        }

        // Integrity filter: remove self-references and blank links
        List<String> links = page.links().stream()
                .filter(link -> link != null && !link.isBlank())
                .filter(link -> !link.equals(url))
                .collect(Collectors.toList());

        // Content categorization via functional Predicate map
        String category = config.categories().entrySet().stream()
                .filter(e -> e.getValue().test(page.content()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("geral");

        System.out.println("[" + threadId + "] crawled=" + url
                + " category=" + category
                + " links=" + links.size());

        if (!links.isEmpty()) {
            String foundMsg = "FOUND: " + String.join(", ", links) + " FROM " + url;
            writer.println(foundMsg);
            readSkipPing(reader);
        }

        writer.println("DONE");
        readSkipPing(reader);
    }
}
