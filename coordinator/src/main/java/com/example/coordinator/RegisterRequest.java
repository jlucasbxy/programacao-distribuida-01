package com.example.coordinator;

import com.example.common.protocol.Protocol;

import java.util.UUID;

record RegisterRequest(String workerId, int capacity) {

    static RegisterRequest parse(String line) {
        String normalized = line == null ? "" : line.trim();
        if (!normalized.startsWith(Protocol.REGISTER)) {
            return null;
        }

        String[] parts = normalized.split("\\s+");
        String workerId = parts.length >= 2 && !parts[1].isBlank()
                ? parts[1]
                : "worker-" + UUID.randomUUID();

        int capacity = 1;
        if (parts.length >= 3) {
            try {
                capacity = Math.max(1, Integer.parseInt(parts[2]));
            } catch (NumberFormatException ignored) {
                capacity = 1;
            }
        }

        return new RegisterRequest(workerId, capacity);
    }
}
