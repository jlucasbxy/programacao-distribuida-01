package com.example.dataserver;

import java.io.IOException;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: Main --server [port] [dataFilePath]");
            System.err.println("       Main --client [host] [port]");
            System.exit(1);
        }

        String mode = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        switch (mode) {
            case "--server" -> new DataServer(ServerConfig.fromArgs(rest)).start();
            case "--client" -> new DataServerClient(rest).start();
            default -> {
                System.err.println("Unknown mode: " + mode);
                System.err.println("Use --server or --client.");
                System.exit(1);
            }
        }
    }
}
