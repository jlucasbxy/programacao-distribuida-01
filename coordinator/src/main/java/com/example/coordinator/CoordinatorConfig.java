package com.example.coordinator;

import java.util.List;

record CoordinatorConfig(int port, String seedsFile) {
    private static final int DEFAULT_PORT = 7070;
    private static final String DEFAULT_SEEDS_FILE = "seeds.txt";

    static CoordinatorConfig fromArgs(String[] args) {
        int port = DEFAULT_PORT;
        String seedsFile = DEFAULT_SEEDS_FILE;

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

        return new CoordinatorConfig(port, seedsFile);
    }
}
