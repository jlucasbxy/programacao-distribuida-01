package com.example.dataserver;

record ServerConfig(int port, String dataFilePath, int threadPoolSize) {
    private static final int DEFAULT_PORT = 9090;

    static ServerConfig fromArgs(String[] args) {
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
        String dataFilePath = args.length > 1 ? args[1] : null;
        int threadPoolSize = Math.max(4, Runtime.getRuntime().availableProcessors());
        return new ServerConfig(port, dataFilePath, threadPoolSize);
    }
}
