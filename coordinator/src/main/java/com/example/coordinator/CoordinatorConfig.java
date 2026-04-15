package com.example.coordinator;

import java.util.ArrayList;
import java.util.List;

record CoordinatorConfig(int port, int threadPoolSize, List<String> seeds) {
    private static final int DEFAULT_PORT = 7070;
    private static final List<String> DEFAULT_SEEDS = List.of("google.com");

    static CoordinatorConfig fromArgs(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                int parsed = Integer.parseInt(args[0]);
                if (parsed < 1 || parsed > 65535) {
                    System.err.println("Invalid port " + parsed + ". Falling back to " + DEFAULT_PORT + ".");
                } else {
                    port = parsed;
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid port format. Falling back to " + DEFAULT_PORT + ".");
            }
        }

        List<String> seeds = parseSeeds(args);
        int threadPoolSize = Math.max(4, Runtime.getRuntime().availableProcessors());
        return new CoordinatorConfig(port, threadPoolSize, seeds);
    }

    private static List<String> parseSeeds(String[] args) {
        if (args.length <= 1) {
            return DEFAULT_SEEDS;
        }

        List<String> parsedSeeds = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String raw = args[i];
            if (raw == null || raw.isBlank()) {
                continue;
            }

            String[] items = raw.split(",");
            for (String item : items) {
                String seed = item.trim();
                if (!seed.isEmpty()) {
                    parsedSeeds.add(seed);
                }
            }
        }

        if (parsedSeeds.isEmpty()) {
            return DEFAULT_SEEDS;
        }
        return List.copyOf(parsedSeeds);
    }
}
