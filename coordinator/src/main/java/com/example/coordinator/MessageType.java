package com.example.coordinator;

enum MessageType {
    FOUND, DONE, IDLE, HEARTBEAT, QUIT, UNKNOWN;

    private static final String MSG_FOUND    = "FOUND:";
    private static final String MSG_DONE     = "DONE";
    private static final String MSG_IDLE     = "IDLE";
    private static final String MSG_HEARTBEAT = "PING";
    private static final String MSG_QUIT     = "QUIT";

    static MessageType parse(String line) {
        String message = line == null ? "" : line.trim();
        if (message.startsWith(MSG_FOUND))       return FOUND;
        if (message.startsWith(MSG_DONE))        return DONE;
        if (message.equals(MSG_IDLE))            return IDLE;
        if (message.equals(MSG_HEARTBEAT))       return HEARTBEAT;
        if (message.equals(MSG_QUIT))            return QUIT;
        return UNKNOWN;
    }
}
