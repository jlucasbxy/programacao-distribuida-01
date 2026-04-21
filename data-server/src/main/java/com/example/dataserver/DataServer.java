package com.example.dataserver;

import com.example.common.logging.AppLogger;
import com.example.common.logging.Loggers;

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

public class DataServer {
    private final DataServerConfig config;
    private final Map<String, String> internetMock;
    private final AppLogger logger;

    public DataServer(DataServerConfig config) {
        this.config = config;
        this.logger = Loggers.consoleWithPrefix("data-server", "[data-server] ");
        this.internetMock = InternetMockJsonLoader.load(config.dataFilePath());
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(config.port())) {
            start(serverSocket);
        } catch (IOException e) {
            logger.error("Server failed to start: " + e.getMessage());
        }
    }

    public void start(ServerSocket serverSocket) {
        ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        AtomicBoolean running = new AtomicBoolean(true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            workers.shutdown();
        }));

        logger.info("Data server listening on port " + serverSocket.getLocalPort() + " with " + internetMock.size() + " pages loaded.");

        try {
            while (running.get()) {
                try {
                    Socket workerSocket = serverSocket.accept();
                    workers.submit(() -> handleWorkerRequest(workerSocket));
                } catch (IOException e) {
                    if (running.get()) {
                        logger.error("Error accepting connection: " + e.getMessage());
                    }
                }
            }
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
            String response = RequestHandler.formatResponse(internetMock, requestLine);
            writer.print(response);
            writer.flush();
        } catch (IOException e) {
            logger.error("Worker request failed: " + e.getMessage());
        }
    }
}
