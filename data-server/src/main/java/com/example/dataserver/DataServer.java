package com.example.dataserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DataServer {
    private static final Pattern GET_PATTERN = Pattern.compile("^GET\\s+/?([^\\s]+)");

    private final ServerConfig config;
    private final Map<String, InternetMockJsonLoader.InternetPageData> internetMock;

    public DataServer(ServerConfig config) {
        this.config = config;
        this.internetMock = InternetMockJsonLoader.load(config.dataFilePath());
    }

    public void start() {
        ExecutorService workers = Executors.newFixedThreadPool(config.threadPoolSize());
        AtomicBoolean running = new AtomicBoolean(true);

        try (ServerSocket serverSocket = new ServerSocket(config.port())) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                try {
                    serverSocket.close();
                } catch (IOException ignored) {
                }
                workers.shutdown();
            }));

            System.out.println("Data server listening on port " + config.port() + " with " + internetMock.size() + " pages loaded.");

            while (running.get()) {
                try {
                    Socket workerSocket = serverSocket.accept();
                    workers.submit(() -> handleWorkerRequest(workerSocket));
                } catch (IOException e) {
                    if (running.get()) {
                        System.err.println("Error accepting connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Server failed to start: " + e.getMessage());
        } finally {
            workers.shutdown();
            try {
                if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                    workers.shutdownNow();
                }
            } catch (InterruptedException e) {
                workers.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void handleWorkerRequest(Socket workerSocket) {
        try (Socket socket = workerSocket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                writer.println("ERROR: EMPTY_REQUEST");
                return;
            }

            String url = resolveRequestedUrl(requestLine);
            if (url == null) {
                writer.println("ERROR: INVALID_REQUEST");
                return;
            }

            InternetMockJsonLoader.InternetPageData page = internetMock.get(url);
            if (page == null) {
                writer.println("ERROR: URL_NOT_FOUND");
                return;
            }

            writer.println("NAME: " + page.name());
            writer.println("LINKS: " + String.join(", ", page.links()));
            writer.println("CONTENT: " + page.content().replace("\r", " ").replace("\n", " "));
        } catch (IOException e) {
            System.err.println("Worker request failed: " + e.getMessage());
        }
    }

    private static String resolveRequestedUrl(String requestLine) {
        String trimmedRequest = requestLine.trim();
        Matcher matcher = GET_PATTERN.matcher(trimmedRequest);
        if (matcher.find()) {
            return matcher.group(1);
        }

        if (trimmedRequest.isBlank()) {
            return null;
        }

        return trimmedRequest.startsWith("/") ? trimmedRequest.substring(1) : trimmedRequest;
    }
}
