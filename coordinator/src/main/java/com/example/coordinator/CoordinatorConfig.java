package com.example.coordinator;

import com.example.common.logging.AppLogger;
import com.example.common.net.PortParser;

record CoordinatorConfig(int port, int seedsCount) {
    private static final int DEFAULT_PORT = 7070;
    private static final int UNLIMITED_SEEDS = -1;

    static CoordinatorConfig fromArgs(String[] args, AppLogger logger) {
        int port = DEFAULT_PORT;
        int seedsCount = UNLIMITED_SEEDS;

        for (int i = 0; i < args.length; i++) {
            if ("--seeds-count".equals(args[i])) {
                seedsCount = parseSeedsCount(args, ++i, seedsCount, logger);
            } else if (i == 0) {
                port = PortParser.parseOrDefault(args[i], DEFAULT_PORT, logger);
            }
        }

        return new CoordinatorConfig(port, seedsCount);
    }

    private static int parseSeedsCount(String[] args, int valueIndex, int fallback, AppLogger logger) {
        if (valueIndex >= args.length) {
            logger.error("--seeds-count requires a numeric argument.");
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(args[valueIndex]);
            if (parsed < 1) {
                logger.error("--seeds-count must be >= 1. Ignoring.");
                return fallback;
            }
            return parsed;
        } catch (NumberFormatException e) {
            logger.error("Invalid --seeds-count value. Ignoring.");
            return fallback;
        }
    }

}
