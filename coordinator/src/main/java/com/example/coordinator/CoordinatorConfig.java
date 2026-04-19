package com.example.coordinator;

record CoordinatorConfig(int port, String seedsFile, int seedsCount) {
    private static final int DEFAULT_PORT = 7070;
    private static final String DEFAULT_SEEDS_FILE = "seeds.txt";
    private static final int UNLIMITED_SEEDS = -1;

    static CoordinatorConfig fromArgs(String[] args) {
        int port = DEFAULT_PORT;
        String seedsFile = DEFAULT_SEEDS_FILE;
        int seedsCount = UNLIMITED_SEEDS;

        for (int i = 0; i < args.length; i++) {
            if ("--seeds-file".equals(args[i])) {
                seedsFile = parseSeedsFile(args, ++i, seedsFile);
            } else if ("--seeds-count".equals(args[i])) {
                seedsCount = parseSeedsCount(args, ++i, seedsCount);
            } else if (i == 0) {
                port = parsePort(args[i]);
            }
        }

        return new CoordinatorConfig(port, seedsFile, seedsCount);
    }

    private static String parseSeedsFile(String[] args, int valueIndex, String fallback) {
        if (valueIndex >= args.length) {
            System.err.println("--seeds-file requires a path argument.");
            return fallback;
        }
        return args[valueIndex];
    }

    private static int parseSeedsCount(String[] args, int valueIndex, int fallback) {
        if (valueIndex >= args.length) {
            System.err.println("--seeds-count requires a numeric argument.");
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(args[valueIndex]);
            if (parsed < 1) {
                System.err.println("--seeds-count must be >= 1. Ignoring.");
                return fallback;
            }
            return parsed;
        } catch (NumberFormatException e) {
            System.err.println("Invalid --seeds-count value. Ignoring.");
            return fallback;
        }
    }

    private static int parsePort(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > 65535) {
                System.err.println("Invalid port " + parsed + ". Falling back to " + DEFAULT_PORT + ".");
                return DEFAULT_PORT;
            }
            return parsed;
        } catch (NumberFormatException e) {
            System.err.println("Invalid port format. Falling back to " + DEFAULT_PORT + ".");
            return DEFAULT_PORT;
        }
    }
}
