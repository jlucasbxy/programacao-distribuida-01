package com.example.worker;

import com.example.common.logging.Loggers;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public record WorkerConfig(
        String coordinatorHost,
        int coordinatorPort,
        String dataServerHost,
        int dataServerPort,
        int capacity,
        String workerId,
        Loggers.Output logOutput
) {
    private static final String DEFAULT_COORDINATOR_HOST = "localhost";
    private static final int DEFAULT_COORDINATOR_PORT = 7070;
    private static final String DEFAULT_DATA_SERVER_HOST = "localhost";
    private static final int DEFAULT_DATA_SERVER_PORT = 9090;
    private static final int DEFAULT_CAPACITY = 1;

    private static final Map<String, Predicate<String>> CATEGORIES;

    static {
        CATEGORIES = new LinkedHashMap<>();
        CATEGORIES.put("esporte",    c -> c != null && (c.contains("futebol") || c.contains("basquete") || c.contains("esporte") || c.contains("placar")));
        CATEGORIES.put("noticias",   c -> c != null && (c.contains("notícia") || c.contains("noticia") || c.contains("manchete") || c.contains("jornal")));
        CATEGORIES.put("clima",      c -> c != null && (c.contains("clima") || c.contains("temperatura") || c.contains("chuva") || c.contains("tempo")));
        CATEGORIES.put("tecnologia", c -> c != null && (c.contains("tech") || c.contains("software") || c.contains("programação") || c.contains("computador")));
    }

    public static WorkerConfig fromArgs(String[] args) {
        String coordinatorHost = DEFAULT_COORDINATOR_HOST;
        int coordinatorPort = DEFAULT_COORDINATOR_PORT;
        String dataServerHost = DEFAULT_DATA_SERVER_HOST;
        int dataServerPort = DEFAULT_DATA_SERVER_PORT;
        int capacity = DEFAULT_CAPACITY;
        String workerId = UUID.randomUUID().toString().substring(0, 8);
        Loggers.Output logOutput = Loggers.Output.STDOUT;

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--coordinator-host" -> coordinatorHost = args[++i];
                case "--coordinator-port" -> coordinatorPort = Integer.parseInt(args[++i]);
                case "--data-server-host" -> dataServerHost = args[++i];
                case "--data-server-port" -> dataServerPort = Integer.parseInt(args[++i]);
                case "--capacity"         -> capacity = Integer.parseInt(args[++i]);
                case "--worker-id"        -> workerId = args[++i];
                case "--log-mode" -> logOutput = parseLogOutput(args[++i]);
            }
        }

        return new WorkerConfig(coordinatorHost, coordinatorPort, dataServerHost, dataServerPort, capacity, workerId, logOutput);
    }

    private static Loggers.Output parseLogOutput(String value) {
        if (value == null || value.isBlank()) {
            return Loggers.Output.STDOUT;
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "logger" -> Loggers.Output.LOGGER;
            case "disabled", "disable", "off", "none" -> Loggers.Output.DISABLED;
            case "stdout", "console" -> Loggers.Output.STDOUT;
            default -> Loggers.Output.STDOUT;
        };
    }

    public Map<String, Predicate<String>> categories() {
        return CATEGORIES;
    }
}
