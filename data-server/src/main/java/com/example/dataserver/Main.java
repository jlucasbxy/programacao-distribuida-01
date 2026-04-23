package com.example.dataserver;

import com.example.common.logging.AppLogger;
import com.example.common.logging.Loggers;

import java.io.IOException;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws IOException {
        AppLogger logger = Loggers.consoleWithPrefix("data-server-main", "[data-server] ");
        if (args.length == 0) {
            logger.error("Usage: Main --server [port]");
            System.exit(1);
        }

        String mode = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        switch (mode) {
            case "--server" -> new DataServer(DataServerConfig.fromArgs(rest, logger), logger).start();
            default -> {
                logger.error("Unknown mode: " + mode);
                logger.error("Use --server.");
                System.exit(1);
            }
        }
    }
}
