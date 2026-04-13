package com.example.dataserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final int DEFAULT_PORT = 9090;
    private static final String DEFAULT_RESOURCE = "/internet-mock.csv";
    private static final Pattern GET_PATTERN = Pattern.compile("^GET\\s+/?([^\\s]+)");

    public static void main(String[] args) {
        int port = resolvePort(args);
        String dataFilePath = args.length > 1 ? args[1] : null;

        Map<String, List<String>> internetMock;
        try {
            internetMock = loadInternetMock(dataFilePath);
        } catch (IOException e) {
            System.err.println("Failed to load internet mock data: " + e.getMessage());
            return;
        }

        ExecutorService workers = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
        AtomicBoolean running = new AtomicBoolean(true);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                try {
                    serverSocket.close();
                } catch (IOException ignored) {
                }
                workers.shutdown();
            }));

            System.out.println("Data server listening on port " + port + " with " + internetMock.size() + " pages loaded.");

            while (running.get()) {
                try {
                    Socket workerSocket = serverSocket.accept();
                    workers.submit(() -> handleWorkerRequest(workerSocket, internetMock));
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

    private static int resolvePort(String[] args) {
        if (args.length == 0) {
            return DEFAULT_PORT;
        }

        try {
            int port = Integer.parseInt(args[0]);
            if (port < 1 || port > 65535) {
                System.err.println("Invalid port " + port + ". Falling back to " + DEFAULT_PORT + ".");
                return DEFAULT_PORT;
            }
            return port;
        } catch (NumberFormatException e) {
            System.err.println("Invalid port format. Falling back to " + DEFAULT_PORT + ".");
            return DEFAULT_PORT;
        }
    }

    private static Map<String, List<String>> loadInternetMock(String dataFilePath) throws IOException {
        Map<String, List<String>> internetMock = new HashMap<>();
        List<String> lines;

        if (dataFilePath != null && !dataFilePath.isBlank()) {
            lines = Files.readAllLines(Path.of(dataFilePath), StandardCharsets.UTF_8);
        } else {
            InputStream input = Main.class.getResourceAsStream(DEFAULT_RESOURCE);
            if (input == null) {
                throw new IOException("Resource not found: " + DEFAULT_RESOURCE);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                lines = reader.lines().toList();
            }
        }

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split(";", -1);
            if (parts.length < 3) {
                continue;
            }

            String pageId = parts[0].trim();
            if (pageId.isEmpty()) {
                continue;
            }

            List<String> links = new ArrayList<>();
            String linksRaw = parts[2].trim();
            if (!linksRaw.isEmpty()) {
                Arrays.stream(linksRaw.split(","))
                    .map(String::trim)
                    .filter(link -> !link.isEmpty())
                    .forEach(links::add);
            }

            internetMock.put(pageId, List.copyOf(links));
        }

        return Map.copyOf(internetMock);
    }

    private static void handleWorkerRequest(Socket workerSocket, Map<String, List<String>> internetMock) {
        try (Socket socket = workerSocket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                writer.println("ERROR: EMPTY_REQUEST");
                return;
            }

            Matcher matcher = GET_PATTERN.matcher(requestLine.trim());
            if (!matcher.find()) {
                writer.println("ERROR: INVALID_REQUEST");
                return;
            }

            String url = matcher.group(1);
            List<String> links = internetMock.get(url);
            if (links == null) {
                writer.println("ERROR: URL_NOT_FOUND");
                return;
            }

            writer.println("LINKS: " + String.join(", ", links));
        } catch (IOException e) {
            System.err.println("Worker request failed: " + e.getMessage());
        }
    }
}
