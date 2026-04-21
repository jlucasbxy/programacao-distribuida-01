package com.example.dataserver;

import com.example.common.logging.AppLogger;
import com.example.common.logging.Loggers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

final class InternetMockJsonLoader {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AppLogger LOGGER = Loggers.consoleWithPrefix("[data-server] ");

    private InternetMockJsonLoader() {
    }

    static Map<String, String> load(String dataFilePath) {
        if (dataFilePath == null || dataFilePath.isBlank()) {
            LOGGER.error("Missing internet mock json path. Pass it as the second argument. Starting with empty data.");
            return Map.of();
        }

        List<InternetPage> loadedPages;
        try {
            loadedPages = OBJECT_MAPPER.readValue(Path.of(dataFilePath).toFile(), new TypeReference<List<InternetPage>>() {
            });
        } catch (IOException e) {
            LOGGER.error("Failed to load internet mock data: " + e.getMessage() + ". Starting with empty data.");
            return Map.of();
        }

        Map<String, String> internetMock = new HashMap<>();
        for (InternetPage pageData : loadedPages) {
            if (pageData == null || pageData.url() == null || pageData.url().isBlank()) {
                continue;
            }

            String content = pageData.content() == null ? "" : pageData.content();

            internetMock.put(pageData.url(), formatResponse(content));
        }

        return Map.copyOf(internetMock);
    }

    private static String formatResponse(String content) {
        return content.replace("\r", "").replace("\n", "") + "\n";
    }

    private record InternetPage(String url, String content) {
    }
}
