package com.example.worker;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class LinkExtractor {
    private static final String HTTPS_PREFIX = "https://";
    private static final String WWW_PREFIX = "www.";

    private LinkExtractor() {}

    static List<String> extract(String pageContent, String sourceUrl) {
        if (pageContent == null || pageContent.isBlank()) {
            return List.of();
        }

        Document document = Jsoup.parse(pageContent, toBaseUri(sourceUrl));
        Set<String> extractedHosts = new LinkedHashSet<>();
        for (Element anchor : document.select("a[href]")) {
            String href = anchor.attr("abs:href").trim();
            if (href.isEmpty()) {
                href = anchor.attr("href").trim();
            }

            String host = extractHost(href);
            if (host != null) {
                extractedHosts.add(host);
            }
        }

        return List.copyOf(extractedHosts);
    }

    private static String extractHost(String href) {
        if (href == null || href.isBlank()) {
            return null;
        }

        URI uri = parseUri(href);
        if (uri == null || uri.getHost() == null) {
            String maybeHost = href.trim();
            if (!looksLikeHostReference(maybeHost)) {
                return null;
            }
            uri = parseUri(HTTPS_PREFIX + maybeHost);
        }

        if (uri == null) {
            return null;
        }

        return normalizeHost(uri.getHost());
    }

    private static String toBaseUri(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return "";
        }

        String trimmed = sourceUrl.trim();
        return trimmed.contains("://") ? trimmed : HTTPS_PREFIX + trimmed;
    }

    private static URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }

        String normalized = host.trim().toLowerCase();
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.startsWith(WWW_PREFIX)) {
            normalized = normalized.substring(WWW_PREFIX.length());
        }
        if (normalized.isBlank()) {
            return null;
        }
        return normalized;
    }

    private static boolean looksLikeHostReference(String href) {
        String withoutPath = href;
        int slashIndex = href.indexOf('/');
        if (slashIndex >= 0) {
            withoutPath = href.substring(0, slashIndex);
        }
        if (withoutPath.isBlank()) {
            return false;
        }
        return withoutPath.contains(".") && !withoutPath.startsWith(".") && !withoutPath.endsWith(".");
    }
}
