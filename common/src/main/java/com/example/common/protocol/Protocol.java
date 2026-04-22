package com.example.common.protocol;

public final class Protocol {
    public static final String REGISTER = "REGISTER";
    public static final String REGISTERED = "REGISTERED";
    public static final String PING = "PING";
    public static final String DONE = "DONE";
    public static final String FOUND = "FOUND";
    public static final String FOUND_PREFIX = FOUND + ":";
    public static final String STOP = "STOP";
    public static final String QUIT = "QUIT";
    public static final String TASK = "TASK";

    private Protocol() {
    }
}
