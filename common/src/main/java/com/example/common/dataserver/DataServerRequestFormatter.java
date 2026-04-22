package com.example.common.dataserver;

import com.example.common.net.UrlNormalizer;

public final class DataServerRequestFormatter {
    private DataServerRequestFormatter() {
    }

    public static String formatGetRequest(String url) {
        String normalized = UrlNormalizer.trimAndValidateNotBlank(url);

        return "GET " + normalized;
    }
}
