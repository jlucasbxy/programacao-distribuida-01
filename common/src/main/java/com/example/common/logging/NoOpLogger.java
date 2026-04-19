package com.example.common.logging;

public final class NoOpLogger implements AppLogger {
    @Override
    public void info(String message) {}

    @Override
    public void error(String message) {}
}
