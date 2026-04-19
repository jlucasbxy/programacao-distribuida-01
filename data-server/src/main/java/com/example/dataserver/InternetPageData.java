package com.example.dataserver;

import java.util.List;

record InternetPageData(String name, String content, List<String> links) {
}
