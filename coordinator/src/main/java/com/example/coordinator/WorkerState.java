package com.example.coordinator;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class WorkerState {
    private final String id;
    private final int capacity;
    private final Set<String> currentTasks = new HashSet<>();
    private volatile PrintWriter writer;

    WorkerState(String id, int capacity) {
        this.id = id;
        this.capacity = capacity;
    }

    String id() {
        return id;
    }

    int capacity() {
        return capacity;
    }

    void attachWriter(PrintWriter writer) {
        this.writer = writer;
    }

    void sendLine(String line) {
        PrintWriter w = this.writer;
        if (w != null) {
            w.println(line);
        }
    }

    synchronized boolean hasCapacity() {
        return currentTasks.size() < capacity;
    }

    synchronized void assignTask(String task) {
        currentTasks.add(task);
    }

    synchronized boolean completeTask(String task) {
        return currentTasks.remove(task);
    }

    synchronized List<String> releaseAllTasks() {
        List<String> released = new ArrayList<>(currentTasks);
        currentTasks.clear();
        return released;
    }

    synchronized boolean isIdle() {
        return currentTasks.isEmpty();
    }
}
