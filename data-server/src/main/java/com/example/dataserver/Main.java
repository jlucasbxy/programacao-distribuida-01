package com.example.dataserver;

import com.example.common.logging.AppLogger;
import com.example.common.logging.Loggers;

import java.io.IOException;
import java.util.Arrays;

public class Main {
    private static final AppLogger LOGGER = Loggers.consoleWithPrefix("[data-server] ");

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            LOGGER.error("Usage: Main --server [port] [dataFilePath]");
            System.exit(1);
        }

        String mode = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        switch (mode) {
            case "--server" -> new DataServer(DataServerConfig.fromArgs(rest)).start();
            default -> {
                LOGGER.error("Unknown mode: " + mode);
                LOGGER.error("Use --server.");
                System.exit(1);
            }
        }
    }
}
