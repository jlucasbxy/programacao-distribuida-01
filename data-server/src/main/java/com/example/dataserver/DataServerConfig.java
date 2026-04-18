package com.example.dataserver;

record DataServerConfig(int port, String dataFilePath) {
    private static final int DEFAULT_PORT = 9090;
    private static final String DEFAULT_DATA_FILE_PATH = "internet-mock.json";

    static DataServerConfig fromArgs(String[] args) {
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
        String dataFilePath = args.length > 1 ? args[1] : DEFAULT_DATA_FILE_PATH;
        return new DataServerConfig(port, dataFilePath);
    }
}
