package com.example.worker;

import com.example.common.logging.AppLogger;
import com.example.common.logging.Loggers;

public class Main {
    public static void main(String[] args) {
        WorkerConfig config = WorkerConfig.fromArgs(args);
        AppLogger logger = Loggers.withMode("worker-main", "", config.logOutput());
        logger.info("Starting worker " + config.workerId()
                + " (capacity=" + config.capacity() + ")"
                + " -> coordinator=" + config.coordinatorHost() + ":" + config.coordinatorPort()
                + " data-server=" + config.dataServerHost() + ":" + config.dataServerPort());
        new Worker(config).start();
    }
}
