package com.example.common.sitecontent;

public record SiteContent(String url, String content) {
    public SiteContent {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (content == null) {
            content = "";
        }
    }
}
