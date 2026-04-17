package com.example.coordinator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

record CoordinatorConfig(int port, int threadPoolSize, List<String> seeds) {
    private static final int DEFAULT_PORT = 7070;
    private static final String DEFAULT_SEEDS_RESOURCE = "/seeds.txt";

    static CoordinatorConfig fromArgs(String[] args) {
        int port = DEFAULT_PORT;
        String seedsFile = null;

        for (int i = 0; i < args.length; i++) {
            if ("--seeds-file".equals(args[i])) {
                if (i + 1 < args.length) {
                    seedsFile = args[++i];
                } else {
                    System.err.println("--seeds-file requires a path argument.");
                }
            } else if (i == 0) {
                try {
                    int parsed = Integer.parseInt(args[i]);
                    if (parsed < 1 || parsed > 65535) {
                        System.err.println("Invalid port " + parsed + ". Falling back to " + DEFAULT_PORT + ".");
                    } else {
                        port = parsed;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port format. Falling back to " + DEFAULT_PORT + ".");
                }
            }
        }

        List<String> seeds = seedsFile != null ? loadSeedsFromFile(seedsFile) : loadDefaultSeeds();

        int threadPoolSize = Math.max(4, Runtime.getRuntime().availableProcessors());
        return new CoordinatorConfig(port, threadPoolSize, seeds);
    }

    private static List<String> loadSeedsFromFile(String path) {
        try {
            List<String> seeds = parseLines(Files.newBufferedReader(Paths.get(path)));
            if (seeds.isEmpty()) {
                System.err.println("Seeds file is empty: " + path + ". Falling back to defaults.");
                return loadDefaultSeeds();
            }
            return seeds;
        } catch (IOException e) {
            System.err.println("Could not read seeds file: " + path + ". Falling back to defaults.");
            return loadDefaultSeeds();
        }
    }

    private static List<String> loadDefaultSeeds() {
        try (InputStream is = CoordinatorConfig.class.getResourceAsStream(DEFAULT_SEEDS_RESOURCE)) {
            if (is == null) return List.of("google.com");
            List<String> seeds = parseLines(new BufferedReader(new InputStreamReader(is)));
            return seeds.isEmpty() ? List.of("google.com") : seeds;
        } catch (IOException e) {
            return List.of("google.com");
        }
    }

    private static List<String> parseLines(BufferedReader reader) throws IOException {
        List<String> seeds = new ArrayList<>();
        try (reader) {
            String line;
            while ((line = reader.readLine()) != null) {
                String seed = line.strip();
                if (!seed.isEmpty() && !seed.startsWith("#")) {
                    seeds.add(seed);
                }
            }
        }
        return List.copyOf(seeds);
    }
}
