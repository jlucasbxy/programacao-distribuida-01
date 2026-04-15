package com.example.coordinator;

final class WorkerState {
    private final String id;
    private final int capacity;
    private volatile boolean idle;
    private String currentTask;

    WorkerState(String id, int capacity) {
        this.id = id;
        this.capacity = capacity;
        this.idle = true;
    }

    String id() {
        return id;
    }

    int capacity() {
        return capacity;
    }

    synchronized void assignTask(String task) {
        this.currentTask = task;
        this.idle = false;
    }

    synchronized boolean hasAssignedTask() {
        return this.currentTask != null;
    }

    synchronized String completeTask() {
        String done = this.currentTask;
        this.currentTask = null;
        this.idle = true;
        return done;
    }

    synchronized String releaseTaskWithoutCompleting() {
        String task = this.currentTask;
        this.currentTask = null;
        this.idle = true;
        return task;
    }

    synchronized void markIdle() {
        this.idle = true;
    }

    boolean isIdle() {
        return idle;
    }
}
