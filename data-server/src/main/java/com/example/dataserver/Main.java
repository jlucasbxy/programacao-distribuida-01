package com.example.dataserver;

public class Main {
    public static void main(String[] args) {
        ServerConfig config = ServerConfig.fromArgs(args);
        new DataServer(config).start();
    }
}
