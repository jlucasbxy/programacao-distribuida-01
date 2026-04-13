package com.example.dataserver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

final class InternetMockJsonLoader {
    private static final String DEFAULT_RESOURCE = "/internet-mock.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private InternetMockJsonLoader() {
    }

    static Map<String, List<String>> load(String dataFilePath) throws IOException {
        List<InternetPage> loadedPages;

        if (dataFilePath != null && !dataFilePath.isBlank()) {
            loadedPages = OBJECT_MAPPER.readValue(Path.of(dataFilePath).toFile(), new TypeReference<List<InternetPage>>() {
            });
        } else {
            InputStream input = Main.class.getResourceAsStream(DEFAULT_RESOURCE);
            if (input == null) {
                throw new IOException("Resource not found: " + DEFAULT_RESOURCE);
            }
            loadedPages = OBJECT_MAPPER.readValue(input, new TypeReference<List<InternetPage>>() {
            });
        }

        Map<String, List<String>> internetMock = new HashMap<>();
        for (InternetPage pageData : loadedPages) {
            if (pageData == null || pageData.url() == null || pageData.url().isBlank()) {
                continue;
            }

            List<String> links = pageData.links();
            if (links == null) {
                internetMock.put(pageData.url(), List.of());
                continue;
            }

            internetMock.put(pageData.url(), List.copyOf(links));
        }

        return Map.copyOf(internetMock);
    }

    private record InternetPage(String url, List<String> links) {
    }
}
