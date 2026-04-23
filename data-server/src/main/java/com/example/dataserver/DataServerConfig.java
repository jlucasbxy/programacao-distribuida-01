package com.example.dataserver;

import com.example.common.logging.AppLogger;
import com.example.common.net.PortParser;

record DataServerConfig(int port) {
    private static final int DEFAULT_PORT = 9090;

    static DataServerConfig fromArgs(String[] args, AppLogger logger) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            port = PortParser.parseOrDefault(args[0], DEFAULT_PORT, logger);
        }
        return new DataServerConfig(port);
    }
}
