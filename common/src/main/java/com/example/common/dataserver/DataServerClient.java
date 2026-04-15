package com.example.common.dataserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DataServerClient {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 9090;
    private static final int DEFAULT_TIMEOUT_MS = 5_000;

    private final String host;
    private final int port;
    private final int timeoutMs;

    public DataServerClient() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    public DataServerClient(String host, int port) {
        this(host, port, DEFAULT_TIMEOUT_MS);
    }

    public DataServerClient(String host, int port, int timeoutMs) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        if (timeoutMs < 1) {
            throw new IllegalArgumentException("timeoutMs must be greater than zero");
        }

        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    public DataServerResponse getPage(String url) throws IOException {
        return sendRaw(DataServerRequestFormatter.formatGetRequest(url));
    }

    public DataServerResponse sendRaw(String requestLine) throws IOException {
        if (requestLine == null) {
            throw new IllegalArgumentException("requestLine must not be null");
        }

        List<String> responseLines = new ArrayList<>();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            try (PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                writer.println(requestLine);

                String line;
                while ((line = reader.readLine()) != null) {
                    responseLines.add(line);
                }
            }
        }

        return DataServerResponseParser.parse(responseLines);
    }
}
