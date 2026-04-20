package com.example.dataserver;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RequestHandler {
    private static final Pattern GET_PATTERN = Pattern.compile("^GET\\s+/?([^\\s]+)");

    private RequestHandler() {
    }

    static String formatResponse(Map<String, InternetPageData> mock, String requestLine) {
        if (requestLine == null || requestLine.isBlank()) {
            return "ERROR: EMPTY_REQUEST\n";
        }
        String url = resolveRequestedUrl(requestLine);
        if (url == null) {
            return "ERROR: INVALID_REQUEST\n";
        }
        InternetPageData page = mock.get(url);
        if (page == null) {
            return "ERROR: URL_NOT_FOUND\n";
        }
        return "NAME: " + page.name() + "\n"
                + "LINKS: " + String.join(", ", page.links()) + "\n"
                + "CONTENT: " + page.content().replace("\r", " ").replace("\n", " ") + "\n";
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
