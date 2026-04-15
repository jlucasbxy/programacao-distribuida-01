package com.example.common.dataserver;

public final class DataServerRequestFormatter {
    private DataServerRequestFormatter() {
    }

    public static String formatGetRequest(String url) {
        if (url == null) {
            throw new IllegalArgumentException("url must not be null");
        }

        String normalized = url.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        return "GET " + normalized;
    }
}
