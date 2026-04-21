package com.example.common.sitecontent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class SiteContentCsvLoader {
    private static final int DEFAULT_LINKS_PER_SITE = 1_000;
    private static final List<List<String>> CATEGORY_KEYWORDS = List.of(
            List.of("futebol", "basquete", "esporte", "placar"),
            List.of("noticia", "manchete", "jornal"),
            List.of("clima", "temperatura", "chuva", "tempo"),
            List.of("tech", "software", "computador")
    );
    private static final List<String> CONTEXT_SNIPPETS = List.of(
            "atualizacao diaria",
            "resumo rapido",
            "boletim local",
            "destaque da hora",
            "painel de leitura"
    );

    private SiteContentCsvLoader() {
    }

    public static List<SiteContent> load(String filePath) {
        return load(filePath, DEFAULT_LINKS_PER_SITE);
    }

    public static List<SiteContent> load(String filePath, int linksPerSite) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be blank");
        }
        if (linksPerSite < 1) {
            throw new IllegalArgumentException("linksPerSite must be greater than zero");
        }

        List<String> domains = parseDomains(Path.of(filePath));
        if (domains.isEmpty()) {
            return List.of();
        }

        List<SiteContent> siteContents = new ArrayList<>();
        for (int i = 0; i < domains.size(); i += linksPerSite) {
            int end = Math.min(i + linksPerSite, domains.size());
            List<String> links = domains.subList(i, end).stream().map(SiteContentCsvLoader::normalizeToUrl).toList();
            siteContents.add(createSiteContentFromLinks(links));
        }

        return List.copyOf(siteContents);
    }

    private static List<String> parseDomains(Path filePath) {
        List<String> domains = new ArrayList<>();

        try (var reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String[] parts = line.split(",", 2);
                if (parts.length < 2) {
                    continue;
                }

                try {
                    Integer.parseInt(parts[0].trim());
                } catch (NumberFormatException ignored) {
                    continue;
                }

                String domain = parts[1].trim();
                if (domain.isEmpty()) {
                    continue;
                }

                domains.add(domain);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read CSV file: " + filePath.toAbsolutePath(), e);
        }

        return domains;
    }

    private static SiteContent createSiteContentFromLinks(List<String> links) {
        String url = links.get(0);
        if (links.size() == 1) {
            return new SiteContent(url, "");
        }

        StringBuilder items = new StringBuilder();
        for (int i = 0; i < links.size(); i++) {
            String link = normalizeToUrl(links.get(i));
            items.append("<li><a href=\"").append(link).append("\">").append(i + 1).append("</a></li>");
        }

        String content = "<html><body>" + buildCategorySnippet() + "<ul>" + items + "</ul></body></html>";
        return new SiteContent(url, content);
    }

    private static String normalizeToUrl(String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return "https://" + value;
    }

    private static String buildCategorySnippet() {
        List<String> keywords = randomItem(CATEGORY_KEYWORDS);
        String keyword = randomItem(keywords);
        String context = randomItem(CONTEXT_SNIPPETS);
        int token = ThreadLocalRandom.current().nextInt(1_000_000);
        String tokenText = String.format("%06d", token);

        return "<section><p>" + context + " " + keyword + " token-" + tokenText + "</p></section>";
    }

    private static <T> T randomItem(List<T> items) {
        int index = ThreadLocalRandom.current().nextInt(items.size());
        return items.get(index);
    }
}
