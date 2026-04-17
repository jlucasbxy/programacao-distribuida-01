package com.example.coordinator;

import java.util.ArrayList;
import java.util.List;

final class CoordinatorMessageSupport {
    static final String REGISTER_PREFIX = "REGISTER";
    static final String REQUEST = "REQUEST";
    static final String FOUND_PREFIX = "FOUND:";
    static final String DONE_PREFIX = "DONE";
    static final String IDLE = "IDLE";
    static final String QUIT = "QUIT";
    static final String HEARTBEAT = "PING";

    record RegisterRequest(String workerId, int capacity) {}

    private CoordinatorMessageSupport() {
    }

    static RegisterRequest parseRegister(String line) {
        String normalized = line == null ? "" : line.trim();
        if (!normalized.startsWith(REGISTER_PREFIX)) {
            return null;
        }

        String[] parts = normalized.split("\\s+");
        String workerId = parts.length >= 2 && !parts[1].isBlank()
                ? parts[1]
                : "worker-" + java.util.UUID.randomUUID();

        int capacity = 1;
        if (parts.length >= 3) {
            try {
                capacity = Math.max(1, Integer.parseInt(parts[2]));
            } catch (NumberFormatException ignored) {
                capacity = 1;
            }
        }

        return new RegisterRequest(workerId, capacity);
    }

    static List<String> parseFoundLinks(String foundMessage) {
        String payload = foundMessage.substring(FOUND_PREFIX.length()).trim();
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
