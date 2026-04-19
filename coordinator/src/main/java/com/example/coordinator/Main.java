package com.example.coordinator;

import java.io.IOException;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: Main --coordinator [port] [--seeds-file <path>] [--seeds-count <n>]");
            System.exit(1);
        }

        String mode = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        switch (mode) {
            case "--coordinator" -> new Coordinator(CoordinatorConfig.fromArgs(rest)).start();
            default -> {
                System.err.println("Unknown mode: " + mode);
                System.err.println("Use --coordinator.");
                System.exit(1);
            }
        }
    }
}
