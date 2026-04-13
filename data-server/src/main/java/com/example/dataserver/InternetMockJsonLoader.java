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

    static Map<String, InternetPageData> load(String dataFilePath) throws IOException {
        if (dataFilePath == null || dataFilePath.isBlank()) {
            throw new IOException("Missing internet mock json path. Pass it as the second argument.");
        }

        List<InternetPage> loadedPages = OBJECT_MAPPER.readValue(Path.of(dataFilePath).toFile(), new TypeReference<List<InternetPage>>() {
        });

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
