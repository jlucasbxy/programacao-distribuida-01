package com.example.coordinator;

import java.util.ArrayList;
import java.util.List;

enum MessageType {
    REQUEST, FOUND, DONE, IDLE, HEARTBEAT, QUIT, UNKNOWN;

    private static final String MSG_REQUEST  = "REQUEST";
    private static final String MSG_FOUND    = "FOUND:";
    private static final String MSG_DONE     = "DONE";
    private static final String MSG_IDLE     = "IDLE";
    private static final String MSG_HEARTBEAT = "PING";
    private static final String MSG_QUIT     = "QUIT";

    static MessageType parse(String line) {
        String message = line == null ? "" : line.trim();
        if (message.equals(MSG_REQUEST))         return REQUEST;
        if (message.startsWith(MSG_FOUND))       return FOUND;
        if (message.startsWith(MSG_DONE))        return DONE;
        if (message.equals(MSG_IDLE))            return IDLE;
        if (message.equals(MSG_HEARTBEAT))       return HEARTBEAT;
        if (message.equals(MSG_QUIT))            return QUIT;
        return UNKNOWN;
    }

    static List<String> parseFoundLinks(String foundMessage) {
        String payload = foundMessage.substring(MSG_FOUND.length()).trim();
        int fromIndex = payload.toUpperCase().lastIndexOf(" FROM ");
        String linksPart = fromIndex >= 0 ? payload.substring(0, fromIndex).trim() : payload;
        return parseCsvLinks(linksPart);
    }

    static String normalizeUrl(String url) {
        String normalized = url.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static List<String> parseCsvLinks(String csvValue) {
        if (csvValue == null || csvValue.isBlank()) {
            return List.of();
        }

        String[] parts = csvValue.split(",");
        List<String> links = new ArrayList<>(parts.length);
        for (String part : parts) {
            String candidate = normalizeUrl(part);
            if (!candidate.isBlank()) {
                links.add(candidate);
            }
        }
        return links;
    }
}
