package com.example.coordinator;

import com.example.common.protocol.Protocol;

enum MessageType {
    FOUND, DONE, QUIT, PING, UNKNOWN;

    static MessageType parse(String line) {
        String message = line == null ? "" : line.trim();
        if (message.startsWith(Protocol.FOUND_PREFIX)) return FOUND;
        if (message.startsWith(Protocol.DONE))         return DONE;
        if (message.equals(Protocol.QUIT))             return QUIT;
        if (message.equals(Protocol.PING))             return PING;
        return UNKNOWN;
    }
}
