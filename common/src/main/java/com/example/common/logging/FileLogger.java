package com.example.common.logging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public final class FileLogger implements AppLogger {
    private static final String DEFAULT_INFO_FILE_NAME = "application.log";
    private static final String DEFAULT_ERROR_FILE_NAME = "application-error.log";
    private static final Object OUTPUT_LOCK = new Object();

    private final Path infoFilePath;
    private final Path errorFilePath;

    public FileLogger() {
        this(DEFAULT_INFO_FILE_NAME, DEFAULT_ERROR_FILE_NAME);
    }

    public FileLogger(String infoFileName) {
        this(infoFileName, buildErrorFileName(infoFileName));
    }

    public FileLogger(String infoFileName, String errorFileName) {
        this.infoFilePath = resolvePath(infoFileName, DEFAULT_INFO_FILE_NAME);
        this.errorFilePath = resolvePath(errorFileName, DEFAULT_ERROR_FILE_NAME);
    }

    @Override
    public void info(String message) {
        appendLine(infoFilePath, message);
    }

    @Override
    public void error(String message) {
        appendLine(errorFilePath, message);
    }

    private static Path resolvePath(String fileName, String fallbackFileName) {
        String normalizedFileName = fileName == null || fileName.isBlank() ? fallbackFileName : fileName;
        return Paths.get(normalizedFileName).toAbsolutePath().normalize();
    }

    private static String buildErrorFileName(String infoFileName) {
        if (infoFileName == null || infoFileName.isBlank()) {
            return DEFAULT_ERROR_FILE_NAME;
        }

        int extensionStart = infoFileName.lastIndexOf('.');
        if (extensionStart <= 0 || extensionStart == infoFileName.length() - 1) {
            return infoFileName + "-error.log";
        }

        String fileNameWithoutExtension = infoFileName.substring(0, extensionStart);
        String extension = infoFileName.substring(extensionStart);
        return fileNameWithoutExtension + "-error" + extension;
    }

    private static void appendLine(Path filePath, String message) {
        String line = String.valueOf(message) + System.lineSeparator();
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);

        synchronized (OUTPUT_LOCK) {
            try {
                Path parent = filePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                Files.write(filePath, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write log line to " + filePath, e);
            }
        }
    }
}
