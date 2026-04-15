package com.example.common.dataserver;

import java.util.List;

public record DataServerResponse(String name, List<String> links, String content, String error) {
    public DataServerResponse {
        links = links == null ? List.of() : List.copyOf(links);
    }

    public static DataServerResponse success(String name, List<String> links, String content) {
        return new DataServerResponse(name, links, content, null);
    }

    public static DataServerResponse error(String error) {
        return new DataServerResponse(null, List.of(), null, error);
    }

    public boolean isError() {
        return error != null && !error.isBlank();
    }
}
