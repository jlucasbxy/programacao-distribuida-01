package com.example.dataserver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

final class InternetMockJsonLoader {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private InternetMockJsonLoader() {
    }

    static Map<String, InternetPageData> load(String dataFilePath) {
        if (dataFilePath == null || dataFilePath.isBlank()) {
            System.err.println("Missing internet mock json path. Pass it as the second argument. Starting with empty data.");
            return Map.of();
        }

        List<InternetPage> loadedPages;
        try {
            loadedPages = OBJECT_MAPPER.readValue(Path.of(dataFilePath).toFile(), new TypeReference<List<InternetPage>>() {
            });
        } catch (IOException e) {
            System.err.println("Failed to load internet mock data: " + e.getMessage() + ". Starting with empty data.");
            return Map.of();
        }

        Map<String, InternetPageData> internetMock = new HashMap<>();
        for (InternetPage pageData : loadedPages) {
            if (pageData == null || pageData.url() == null || pageData.url().isBlank()) {
                continue;
            }

            List<String> links = pageData.links() == null ? List.of() : List.copyOf(pageData.links());
            String name = pageData.name() == null || pageData.name().isBlank() ? pageData.url() : pageData.name();
            String content = pageData.content() == null ? "" : pageData.content();

            internetMock.put(pageData.url(), new InternetPageData(name, content, links));
        }

        return Map.copyOf(internetMock);
    }

    record InternetPageData(String name, String content, List<String> links) {
    }

    private record InternetPage(String url, String name, String content, List<String> links) {
    }
}
