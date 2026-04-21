package com.example.common.dataserver;

public record DataServerResponse(String content, String error) {
    public static DataServerResponse success(String content) {
        return new DataServerResponse(content, null);
    }

    public static DataServerResponse error(String error) {
        String normalizedError = (error == null || error.isBlank()) ? "UNKNOWN_ERROR" : error;
        return new DataServerResponse(null, normalizedError);
    }

    public boolean isError() {
        return error != null && !error.isBlank();
    }
}
