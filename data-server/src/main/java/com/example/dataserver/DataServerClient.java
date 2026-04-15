package com.example.dataserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class DataServerClient {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 9090;

    private final String host;
    private final int port;

    public DataServerClient(String[] args) {
        this.host = args.length > 0 ? args[0] : DEFAULT_HOST;
        this.port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
    }

    public void start() throws IOException {
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);

        System.out.println("Data Server Client — connected to " + host + ":" + port);
        System.out.println("Type a URL to fetch (e.g. example.com) or 'exit' to quit.");

        while (true) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;
            if (input.equalsIgnoreCase("exit")) break;

            try (Socket socket = new Socket(host, port);
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                writer.println("GET " + input);

                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException e) {
                System.err.println("Connection error: " + e.getMessage());
            }

            System.out.println();
        }

        System.out.println("Bye.");
    }
}
