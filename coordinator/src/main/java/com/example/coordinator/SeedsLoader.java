package com.example.coordinator;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

class SeedsLoader {
    static List<String> load(String path) {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(path))) {
            List<String> seeds = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String seed = line.strip();
                if (!seed.isEmpty() && !seed.startsWith("#")) {
                    seeds.add(seed);
                }
            }
            return List.copyOf(seeds);
        } catch (IOException e) {
            System.err.println("Could not read seeds file: " + path + " — " + e.getMessage());
            return List.of();
        }
    }
}
