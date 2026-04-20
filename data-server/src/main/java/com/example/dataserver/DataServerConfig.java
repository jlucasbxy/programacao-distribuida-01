package com.example.dataserver;

import com.example.common.logging.AppLogger;
import com.example.common.logging.Loggers;

record DataServerConfig(int port, String dataFilePath) {
    private static final int DEFAULT_PORT = 9090;
    private static final String DEFAULT_DATA_FILE_PATH = "internet-mock.json";
    private static final AppLogger LOGGER = Loggers.consoleWithPrefix("[data-server] ");

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
        String dataFilePath = args.length > 1 ? args[1] : DEFAULT_DATA_FILE_PATH;
        return new DataServerConfig(port, dataFilePath);
    }
}
