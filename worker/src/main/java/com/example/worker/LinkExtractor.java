package com.example.worker;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class LinkExtractor {
    private static final Pattern ABSOLUTE_URI_PATTERN = Pattern.compile("(?i)^[a-z][a-z0-9+\\-.]*://.*");
    private static final String HTTP = "http";
    private static final String HTTPS = "https";

    private LinkExtractor() {}

    static List<String> extract(String pageContent, String sourceUrl) {
        if (pageContent == null || pageContent.isBlank()) {
            return List.of();
        }

        URI baseUri = toBaseUri(sourceUrl);
        Document document = baseUri == null
                ? Jsoup.parse(pageContent)
                : Jsoup.parse(pageContent, baseUri.toString());
        Set<String> extractedHosts = new LinkedHashSet<>();
        for (Element anchor : document.select("a[href]")) {
            String href = anchor.attr("href");
            String normalized = normalizeHref(href, baseUri);
            if (normalized != null) {
                extractedHosts.add(normalized);
            }
        }

        return List.copyOf(extractedHosts);
    }

    private static String normalizeHref(String rawHref, URI baseUri) {
        if (rawHref == null) {
            return null;
        }

        String href = rawHref.trim();
        if (href.isEmpty() || href.startsWith("#")) {
            return null;
        }

        int fragmentIndex = href.indexOf('#');
        if (fragmentIndex >= 0) {
            href = href.substring(0, fragmentIndex).trim();
            if (href.isEmpty()) {
                return null;
            }
        }

        String hrefLower = href.toLowerCase();
        if (hrefLower.startsWith("javascript:")
                || hrefLower.startsWith("mailto:")
                || hrefLower.startsWith("tel:")
                || hrefLower.startsWith("data:")) {
            return null;
        }

        if (href.startsWith("//")) {
            URI schemeRelative = parseUri(HTTPS + ":" + href);
            return normalizeHost(schemeRelative == null ? null : schemeRelative.getHost());
        }

        if (ABSOLUTE_URI_PATTERN.matcher(href).matches()) {
            URI absolute = parseUri(href);
            if (absolute == null) {
                return null;
            }
            String scheme = absolute.getScheme();
            if (scheme == null) {
                return null;
            }
            String normalizedScheme = scheme.toLowerCase();
            if (!HTTP.equals(normalizedScheme) && !HTTPS.equals(normalizedScheme)) {
                return null;
            }
            return normalizeHost(absolute.getHost());
        }

        URI noSchemeAsHost = parseUri(HTTPS + "://" + href);
        if (noSchemeAsHost != null && looksLikeHostReference(href)) {
            return normalizeHost(noSchemeAsHost.getHost());
        }

        if (baseUri == null) {
            return null;
        }

        URI relative = parseUri(href);
        if (relative == null) {
            return null;
        }

        URI resolved = baseUri.resolve(relative);
        return normalizeHost(resolved.getHost());
    }

    private static URI toBaseUri(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return null;
        }

        String trimmed = sourceUrl.trim();
        String candidate = ABSOLUTE_URI_PATTERN.matcher(trimmed).matches() ? trimmed : HTTPS + "://" + trimmed;
        URI uri = parseUri(candidate);
        if (uri == null || uri.getHost() == null) {
            return null;
        }

        return uri;
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
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring("www.".length());
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
