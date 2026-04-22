package com.example.dataserver;

import com.example.common.logging.AppLogger;
import com.example.common.logging.Loggers;
import com.example.common.net.PortParser;

record DataServerConfig(int port) {
    private static final int DEFAULT_PORT = 9090;
    private static final AppLogger LOGGER = Loggers.consoleWithPrefix("data-server-config", "[data-server] ");

    static DataServerConfig fromArgs(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            port = PortParser.parseOrDefault(args[0], DEFAULT_PORT, LOGGER);
        }
        return new DataServerConfig(port);
    }
}
