package com.example.common.net;

public final class UrlNormalizer {
    private UrlNormalizer() {
    }

    public static String trimAndValidateNotBlank(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        return normalized;
    }
}
