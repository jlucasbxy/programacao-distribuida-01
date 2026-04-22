package com.example.common.net;

import com.example.common.logging.AppLogger;

public final class PortParser {
    private PortParser() {
    }

    public static int parseOrDefault(String value, int fallbackPort, AppLogger logger) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > 65535) {
                logger.error("Invalid port " + parsed + ". Falling back to " + fallbackPort + ".");
                return fallbackPort;
            }
            return parsed;
        } catch (NumberFormatException e) {
            logger.error("Invalid port format. Falling back to " + fallbackPort + ".");
            return fallbackPort;
        }
    }
}
