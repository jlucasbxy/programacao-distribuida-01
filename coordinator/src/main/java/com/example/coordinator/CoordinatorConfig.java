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
                if (i + 1 < args.length) {
                    seedsFile = args[++i];
                } else {
                    System.err.println("--seeds-file requires a path argument.");
                }
            } else if ("--seeds-count".equals(args[i])) {
                if (i + 1 < args.length) {
                    try {
                        int parsed = Integer.parseInt(args[++i]);
                        if (parsed < 1) {
                            System.err.println("--seeds-count must be >= 1. Ignoring.");
                        } else {
                            seedsCount = parsed;
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid --seeds-count value. Ignoring.");
                    }
                } else {
                    System.err.println("--seeds-count requires a numeric argument.");
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

        return new CoordinatorConfig(port, seedsFile, seedsCount);
    }
}
