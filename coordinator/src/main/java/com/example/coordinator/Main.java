package com.example.coordinator;

import com.example.common.logging.AppLogger;
import com.example.common.logging.Loggers;

import java.io.IOException;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws IOException {
        AppLogger logger = Loggers.consoleWithPrefix("coordinator-main", "[coordinator] ");
        if (args.length == 0) {
            logger.error("Usage: Main --coordinator [port] [--seeds-count <n>]");
            System.exit(1);
        }

        String mode = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        switch (mode) {
            case "--coordinator" -> new Coordinator(CoordinatorConfig.fromArgs(rest, logger), logger).start();
            default -> {
                logger.error("Unknown mode: " + mode);
                logger.error("Use --coordinator.");
                System.exit(1);
            }
        }
    }
}
