package com.example.worker;

import com.example.common.dataserver.DataServerClient;
import com.example.common.dataserver.DataServerResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Worker {
    private static final Map<String, Predicate<String>> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("esporte",   c -> c != null && (c.contains("futebol") || c.contains("basquete") || c.contains("esporte") || c.contains("placar")));
        CATEGORIES.put("noticias",  c -> c != null && (c.contains("notícia") || c.contains("noticia") || c.contains("manchete") || c.contains("jornal")));
        CATEGORIES.put("clima",     c -> c != null && (c.contains("clima") || c.contains("temperatura") || c.contains("chuva") || c.contains("tempo")));
        CATEGORIES.put("tecnologia",c -> c != null && (c.contains("tech") || c.contains("software") || c.contains("programação") || c.contains("computador")));
    }

    private final WorkerConfig config;

    public Worker(WorkerConfig config) {
        this.config = config;
    }

    public void start() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(config.capacity());

        for (int i = 0; i < config.capacity(); i++) {
            final String threadId = config.workerId() + "-" + i;
            pool.submit(() -> runWorkerThread(threadId));
        }

        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.HOURS);
    }

    private void runWorkerThread(String threadId) {
        try (Socket socket = new Socket(config.coordinatorHost(), config.coordinatorPort());
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            writer.println("REGISTER " + threadId + " 1");
            String registered = reader.readLine();
            if (registered == null || !registered.startsWith("REGISTERED")) {
                System.err.println("[" + threadId + "] Registration failed: " + registered);
                return;
            }
            System.out.println("[" + threadId + "] " + registered);

            boolean running = true;
            while (running) {
                writer.println("REQUEST");
                String response = reader.readLine();
                if (response == null) break;

                if (response.startsWith("TASK ")) {
                    String url = response.substring(5).trim();
                    processTask(threadId, url, writer, reader);
                } else if ("WAIT".equals(response)) {
                    Thread.sleep(200);
                } else if ("STOP".equals(response)) {
                    writer.println("QUIT");
                    reader.readLine();
                    running = false;
                } else {
                    System.err.println("[" + threadId + "] Unexpected response: " + response);
                }
            }

            System.out.println("[" + threadId + "] Crawl complete. Disconnecting.");
        } catch (IOException | InterruptedException e) {
            System.err.println("[" + threadId + "] Error: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private void processTask(String threadId, String url, PrintWriter writer, BufferedReader reader) throws IOException {
        DataServerClient client = new DataServerClient(config.dataServerHost(), config.dataServerPort());
        DataServerResponse page = client.getPage(url);

        if (page.isError()) {
            System.err.println("[" + threadId + "] Error fetching " + url + ": " + page.error());
            writer.println("DONE");
            reader.readLine();
            return;
        }

        // Integrity filter: remove self-references and blank links
        List<String> links = page.links().stream()
                .filter(link -> link != null && !link.isBlank())
                .filter(link -> !link.equals(url))
                .collect(Collectors.toList());

        // Content categorization via functional Predicate map
        String category = CATEGORIES.entrySet().stream()
                .filter(e -> e.getValue().test(page.content()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("geral");

        System.out.println("[" + threadId + "] crawled=" + url
                + " category=" + category
                + " links=" + links.size());

        String foundMsg = "FOUND: " + String.join(", ", links) + " FROM " + url;
        writer.println(foundMsg);
        reader.readLine();

        writer.println("DONE");
        reader.readLine();
    }
}
