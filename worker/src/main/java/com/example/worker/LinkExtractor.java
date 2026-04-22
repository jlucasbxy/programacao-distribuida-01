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

    private LinkExtractor() {}

    static List<String> extract(String pageContent, String sourceUrl) {
        if (pageContent == null || pageContent.isBlank()) {
            return List.of();
        }

        Document document = Jsoup.parse(pageContent, toBaseUri(sourceUrl));
        Set<String> extractedLinks = new LinkedHashSet<>();
        for (Element anchor : document.select("a[href]")) {
            String href = anchor.attr("abs:href").trim();
            if (href.isEmpty()) {
                href = anchor.attr("href").trim();
            }

            String link = extractLink(href);
            if (link != null) {
                extractedLinks.add(link);
            }
        }

        return List.copyOf(extractedLinks);
    }

    private static String extractLink(String href) {
        if (href == null || href.isBlank()) {
            return null;
        }

        URI uri = parseUri(href.trim());

        if (uri == null) {
            return null;
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            return null;
        }

        if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            return null;
        }

        return uri.toString();
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

}
