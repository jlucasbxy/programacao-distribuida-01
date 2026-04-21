package com.example.common.logging;

public final class FileAndConsoleLogger implements AppLogger {
    private final AppLogger consoleLogger;
    private final AppLogger fileLogger;

    public FileAndConsoleLogger() {
        this("", new FileLogger());
    }

    public FileAndConsoleLogger(String consolePrefix) {
        this(consolePrefix, new FileLogger());
    }

    public FileAndConsoleLogger(String consolePrefix, String infoFileName) {
        this(consolePrefix, new FileLogger(infoFileName));
    }

    public FileAndConsoleLogger(String consolePrefix, String infoFileName, String errorFileName) {
        this(consolePrefix, new FileLogger(infoFileName, errorFileName));
    }

    private FileAndConsoleLogger(String consolePrefix, AppLogger fileLogger) {
        this.consoleLogger = new ConsoleLogger(consolePrefix);
        this.fileLogger = fileLogger;
    }

    @Override
    public void info(String message) {
        RuntimeException firstFailure = null;

        try {
            consoleLogger.info(message);
        } catch (RuntimeException e) {
            firstFailure = e;
        }

        try {
            fileLogger.info(message);
        } catch (RuntimeException e) {
            if (firstFailure != null) {
                e.addSuppressed(firstFailure);
            }
            throw e;
        }

        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    @Override
    public void error(String message) {
        RuntimeException firstFailure = null;

        try {
            consoleLogger.error(message);
        } catch (RuntimeException e) {
            firstFailure = e;
        }

        try {
            fileLogger.error(message);
        } catch (RuntimeException e) {
            if (firstFailure != null) {
                e.addSuppressed(firstFailure);
            }
            throw e;
        }

        if (firstFailure != null) {
            throw firstFailure;
        }
    }
}
