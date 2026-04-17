package com.example.coordinator;

import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CrawlState {
    private final BlockingQueue<String> frontierQueue;
    private final Set<String> visitedUrls;
    private final ConcurrentHashMap<String, WorkerState> workers;
    private final AtomicInteger tasksInFlight;
    private final AtomicBoolean completionReached;
    private final AtomicBoolean hadAnyWorker;

    private volatile Runnable onCompletion;

    public CrawlState(ConcurrentHashMap<String, WorkerState> workers) {
        this.frontierQueue = new LinkedBlockingQueue<>();
        this.visitedUrls = ConcurrentHashMap.newKeySet();
        this.workers = workers;
        this.tasksInFlight = new AtomicInteger(0);
        this.completionReached = new AtomicBoolean(false);
        this.hadAnyWorker = new AtomicBoolean(false);
    }

    public void setOnCompletion(Runnable onCompletion) {
        this.onCompletion = onCompletion;
    }

    public boolean enqueueIfNew(String url) {
        if (url == null) return false;

        String normalized = CoordinatorMessageSupport.normalizeUrl(url);
        if (normalized.isEmpty()) return false;

        if (!visitedUrls.add(normalized)) return false;

        frontierQueue.offer(normalized);
        return true;
    }

    public String pollTask() {
        return frontierQueue.poll();
    }

    public int frontierSize() {
        return frontierQueue.size();
    }

    public void incrementTasksInFlight() {
        tasksInFlight.incrementAndGet();
    }

    public void completeTask(WorkerState worker) {
        String completedTask = worker.completeTask();
        if (completedTask != null) {
            tasksInFlight.decrementAndGet();
        }
    }

    public void retryTask(WorkerState worker) {
        String task = worker.releaseTaskWithoutCompleting();
        if (task != null) {
            tasksInFlight.decrementAndGet();
            frontierQueue.offer(task);
        }
    }

    public int addFoundLinks(WorkerState worker, String message) {
        completeTask(worker);
        worker.markIdle();

        List<String> links = CoordinatorMessageSupport.parseFoundLinks(message);
        int added = 0;
        for (String link : links) {
            if (enqueueIfNew(link)) added++;
        }
        return added;
    }

    public void markWorkerRegistered() {
        hadAnyWorker.set(true);
    }

    public boolean isCompletionReached() {
        return completionReached.get();
    }

    public void evaluateCompletion() {
        if (!hadAnyWorker.get()) return;
        if (!frontierQueue.isEmpty()) return;
        if (tasksInFlight.get() > 0) return;

        boolean allIdle = workers.values().stream().allMatch(WorkerState::isIdle);
        if (allIdle && completionReached.compareAndSet(false, true)) {
            System.out.println("Coordinator completed crawl: frontier empty, workers idle, no tasks in flight.");
            if (onCompletion != null) onCompletion.run();
        }
    }
}
