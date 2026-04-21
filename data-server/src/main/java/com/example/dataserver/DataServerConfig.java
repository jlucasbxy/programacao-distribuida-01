package com.example.dataserver;

import com.example.common.logging.AppLogger;
import com.example.common.logging.Loggers;

record DataServerConfig(int port) {
    private static final int DEFAULT_PORT = 9090;
    private static final AppLogger LOGGER = Loggers.consoleWithPrefix("data-server-config", "[data-server] ");

    static DataServerConfig fromArgs(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                int parsed = Integer.parseInt(args[0]);
                if (parsed < 1 || parsed > 65535) {
                    LOGGER.error("Invalid port " + parsed + ". Falling back to " + DEFAULT_PORT + ".");
                } else {
                    port = parsed;
                }
            } catch (NumberFormatException e) {
                LOGGER.error("Invalid port format. Falling back to " + DEFAULT_PORT + ".");
            }
        }
        return new DataServerConfig(port);
    }
}
