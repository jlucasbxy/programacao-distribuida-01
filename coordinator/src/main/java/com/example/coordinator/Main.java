package com.example.coordinator;

import com.example.common.logging.AppLogger;
import com.example.common.logging.Loggers;

import java.io.IOException;
import java.util.Arrays;

public class Main {
    private static final AppLogger LOGGER = Loggers.consoleWithPrefix("coordinator-main", "[coordinator] ");

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            LOGGER.error("Usage: Main --coordinator [port] [--seeds-file <path>] [--seeds-count <n>]");
            System.exit(1);
        }

        String mode = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        switch (mode) {
            case "--coordinator" -> new Coordinator(CoordinatorConfig.fromArgs(rest)).start();
            default -> {
                LOGGER.error("Unknown mode: " + mode);
                LOGGER.error("Use --coordinator.");
                System.exit(1);
            }
        }
    }
}
