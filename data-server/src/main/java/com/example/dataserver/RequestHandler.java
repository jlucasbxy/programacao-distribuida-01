package com.example.dataserver;

import java.util.Map;

final class RequestHandler {
    private RequestHandler() {
    }

    static String formatResponse(Map<String, String> mock, String requestLine) {
        if (requestLine == null || requestLine.isBlank()) {
            return "ERROR: EMPTY_REQUEST\n";
        }
        String url = resolveRequestedUrl(requestLine);
        if (url == null) {
            return "ERROR: INVALID_REQUEST\n";
        }
        String preformattedResponse = mock.get(url);
        if (preformattedResponse == null) {
            return "ERROR: URL_NOT_FOUND\n";
        }
        return preformattedResponse;
    }

    private static String resolveRequestedUrl(String requestLine) {
        String trimmed = requestLine.trim();
        if (trimmed.regionMatches(true, 0, "GET", 0, 3)) {
            if (trimmed.length() == 3) {
                return null;
            }
            if (!Character.isWhitespace(trimmed.charAt(3))) {
                return null;
            }
            String payload = trimmed.substring(3).trim();
            if (payload.isBlank()) {
                return null;
            }
            return payload;
        }
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed;
    }
}
