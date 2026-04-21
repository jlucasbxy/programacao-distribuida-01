package com.example.common.dataserver;

import java.util.ArrayList;
import java.util.List;

public final class DataServerResponseParser {
    private static final String ERROR_PREFIX = "ERROR: ";
    private static final String NAME_PREFIX = "NAME: ";
    private static final String LINKS_PREFIX = "LINKS: ";
    private static final String CONTENT_PREFIX = "CONTENT: ";

    private DataServerResponseParser() {
    }

    public static DataServerResponse parse(List<String> responseLines) {
        return parse(responseLines, null);
    }

    public static DataServerResponse parse(List<String> responseLines, String requestedUrl) {
        if (responseLines == null || responseLines.isEmpty()) {
            return DataServerResponse.error("EMPTY_RESPONSE");
        }

        String firstLine = responseLines.get(0);
        if (firstLine.startsWith(ERROR_PREFIX)) {
            return DataServerResponse.error(firstLine.substring(ERROR_PREFIX.length()).trim());
        }

        String name = null;
        List<String> links = List.of();
        String content = null;

        for (String line : responseLines) {
            if (line.startsWith(NAME_PREFIX)) {
                name = line.substring(NAME_PREFIX.length()).trim();
                continue;
            }
            if (line.startsWith(LINKS_PREFIX)) {
                links = parseLinks(line.substring(LINKS_PREFIX.length()));
                continue;
            }
            if (line.startsWith(CONTENT_PREFIX)) {
                content = line.substring(CONTENT_PREFIX.length()).trim();
            }
        }

        if (content == null) {
            return DataServerResponse.error("INVALID_RESPONSE");
        }

        String fallbackName = normalizeRequestedUrl(requestedUrl);
        if (name == null || name.isBlank()) {
            name = fallbackName;
        }

        return DataServerResponse.success(name, links, content);
    }

    private static String normalizeRequestedUrl(String requestedUrl) {
        if (requestedUrl == null) {
            return null;
        }

        String normalized = requestedUrl.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<String> parseLinks(String linksValue) {
        String normalized = linksValue == null ? "" : linksValue.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }

        String[] parts = normalized.split(",");
        List<String> links = new ArrayList<>(parts.length);
        for (String part : parts) {
            String link = part.trim();
            if (!link.isEmpty()) {
                links.add(link);
            }
        }
        return links;
    }
}
