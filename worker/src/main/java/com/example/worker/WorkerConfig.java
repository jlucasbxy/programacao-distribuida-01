package com.example.worker;

import java.util.UUID;

public record WorkerConfig(
        String coordinatorHost,
        int coordinatorPort,
        String dataServerHost,
        int dataServerPort,
        int capacity,
        String workerId
) {
    private static final String DEFAULT_COORDINATOR_HOST = "localhost";
    private static final int DEFAULT_COORDINATOR_PORT = 7070;
    private static final String DEFAULT_DATA_SERVER_HOST = "localhost";
    private static final int DEFAULT_DATA_SERVER_PORT = 9090;
    private static final int DEFAULT_CAPACITY = 1;

    public static WorkerConfig fromArgs(String[] args) {
        String coordinatorHost = DEFAULT_COORDINATOR_HOST;
        int coordinatorPort = DEFAULT_COORDINATOR_PORT;
        String dataServerHost = DEFAULT_DATA_SERVER_HOST;
        int dataServerPort = DEFAULT_DATA_SERVER_PORT;
        int capacity = DEFAULT_CAPACITY;
        String workerId = UUID.randomUUID().toString().substring(0, 8);

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--coordinator-host" -> coordinatorHost = args[++i];
                case "--coordinator-port" -> coordinatorPort = Integer.parseInt(args[++i]);
                case "--data-server-host" -> dataServerHost = args[++i];
                case "--data-server-port" -> dataServerPort = Integer.parseInt(args[++i]);
                case "--capacity"         -> capacity = Integer.parseInt(args[++i]);
                case "--worker-id"        -> workerId = args[++i];
            }
        }

        return new WorkerConfig(coordinatorHost, coordinatorPort, dataServerHost, dataServerPort, capacity, workerId);
    }
}
