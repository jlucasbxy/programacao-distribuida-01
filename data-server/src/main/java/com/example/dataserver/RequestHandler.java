package com.example.dataserver;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RequestHandler {
    private static final Pattern GET_PATTERN = Pattern.compile("^GET\\s+/?([^\\s]+)");

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
        Matcher matcher = GET_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }
}
