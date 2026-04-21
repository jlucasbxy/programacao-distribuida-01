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
    private static final String LOGS_DIRECTORY_NAME = "logs";
    private static final Path LOGS_DIRECTORY = Paths.get(LOGS_DIRECTORY_NAME).toAbsolutePath().normalize();
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
        Path requestedPath = Paths.get(normalizedFileName).normalize();
        Path resolvedPath;

        if (requestedPath.isAbsolute()) {
            resolvedPath = LOGS_DIRECTORY.resolve(resolveSafeFileName(requestedPath, fallbackFileName)).normalize();
        } else if (requestedPath.startsWith(LOGS_DIRECTORY_NAME)) {
            resolvedPath = requestedPath.toAbsolutePath().normalize();
        } else {
            resolvedPath = LOGS_DIRECTORY.resolve(requestedPath).normalize();
        }

        if (!resolvedPath.startsWith(LOGS_DIRECTORY)) {
            resolvedPath = LOGS_DIRECTORY.resolve(resolveSafeFileName(requestedPath, fallbackFileName)).normalize();
        }

        return resolvedPath;
    }

    private static String resolveSafeFileName(Path requestedPath, String fallbackFileName) {
        Path fileName = requestedPath.getFileName();
        if (fileName == null || fileName.toString().isBlank()) {
            return fallbackFileName;
        }
        return fileName.toString();
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
