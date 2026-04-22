package com.example.dataserver;

import com.example.common.logging.AppLogger;
import com.example.common.logging.Loggers;
import com.example.common.sitecontent.SiteContent;
import com.example.common.sitecontent.SiteContentLoader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class InternetMockJsonLoader {
    private static final AppLogger LOGGER = Loggers.consoleWithPrefix("data-server-loader", "[data-server] ");

    private InternetMockJsonLoader() {
    }

    static Map<String, String> load() {
        List<SiteContent> loadedPages;
        try {
            loadedPages = SiteContentLoader.load();
        } catch (RuntimeException e) {
            LOGGER.error("Failed to generate internet mock data from CSV: " + e.getMessage() + ". Starting with empty data.");
            return Map.of();
        }

        Map<String, String> internetMock = new HashMap<>();
        for (SiteContent pageData : loadedPages) {
            internetMock.put(pageData.url(), pageData.content());
        }

        return Map.copyOf(internetMock);
    }

}
