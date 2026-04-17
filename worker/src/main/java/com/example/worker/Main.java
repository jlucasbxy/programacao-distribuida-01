package com.example.worker;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        WorkerConfig config = WorkerConfig.fromArgs(args);
        System.out.println("Starting worker " + config.workerId()
                + " (capacity=" + config.capacity() + ")"
                + " -> coordinator=" + config.coordinatorHost() + ":" + config.coordinatorPort()
                + " data-server=" + config.dataServerHost() + ":" + config.dataServerPort());
        new Worker(config).start();
    }
}
