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

    private CoordinatorMessageSupport() {
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
