package com.example.coordinator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class CrawlState {
    private final BlockingQueue<String> frontierQueue;
    private final Set<String> visitedUrls;
    private final ConcurrentHashMap<String, WorkerState> workers;
    private int tasksInFlight;
    private final AtomicBoolean completionReached;
    private final AtomicBoolean hadAnyWorker;

    private volatile Runnable onCompletion;

    public CrawlState(ConcurrentHashMap<String, WorkerState> workers) {
        this.frontierQueue = new LinkedBlockingQueue<>();
        this.visitedUrls = ConcurrentHashMap.newKeySet();
        this.workers = workers;
        this.tasksInFlight = 0;
        this.completionReached = new AtomicBoolean(false);
        this.hadAnyWorker = new AtomicBoolean(false);
    }

    public void setOnCompletion(Runnable onCompletion) {
        this.onCompletion = onCompletion;
    }

    public int enqueueAllIfNew(List<String> urls) {
        int added = 0;
        for (String url : urls) {
            if (enqueueIfNew(url)) added++;
        }
        return added;
    }

    private boolean enqueueIfNew(String url) {
        if (url == null) return false;

        String normalized = normalizeUrl(url);
        if (normalized.isEmpty()) return false;

        if (!visitedUrls.add(normalized)) return false;

        frontierQueue.offer(normalized);
        return true;
    }

    public synchronized String pollAndAssign(WorkerState worker) {
        String nextUrl = frontierQueue.poll();
        if (nextUrl != null) {
            worker.assignTask(nextUrl);
            tasksInFlight++;
        }
        return nextUrl;
    }

    public int frontierSize() {
        return frontierQueue.size();
    }

    public synchronized void completeTask(WorkerState worker) {
        String completedTask = worker.completeTask();
        if (completedTask != null) {
            tasksInFlight--;
        }
    }

    public synchronized boolean retryTask(WorkerState worker) {
        String task = worker.releaseTaskWithoutCompleting();
        if (task == null) return false;
        tasksInFlight--;
        frontierQueue.offer(task);
        return true;
    }

    public int addFoundLinks(String message) {
        List<String> links = parseFoundLinks(message);
        int added = 0;
        for (String link : links) {
            if (enqueueIfNew(link)) added++;
        }
        return added;
    }

    private static List<String> parseFoundLinks(String foundMessage) {
        String payload = foundMessage.substring("FOUND:".length()).trim();
        int fromIndex = payload.toUpperCase().lastIndexOf(" FROM ");
        String linksPart = fromIndex >= 0 ? payload.substring(0, fromIndex).trim() : payload;
        if (linksPart.isBlank()) return List.of();

        String[] parts = linksPart.split(",");
        List<String> links = new ArrayList<>(parts.length);
        for (String part : parts) {
            String candidate = normalizeUrl(part);
            if (!candidate.isBlank()) links.add(candidate);
        }
        return links;
    }

    private static String normalizeUrl(String url) {
        String normalized = url.trim();
        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }

    public void markWorkerRegistered() {
        hadAnyWorker.set(true);
    }

    public boolean isCompletionReached() {
        return completionReached.get();
    }

    public synchronized void evaluateCompletion() {
        if (!hadAnyWorker.get()) return;
        if (!frontierQueue.isEmpty()) return;
        if (tasksInFlight > 0) return;

        boolean allIdle = workers.values().stream().allMatch(WorkerState::isIdle);
        if (allIdle && completionReached.compareAndSet(false, true)) {
            System.out.println("Coordinator completed crawl: frontier empty, workers idle, no tasks in flight.");
            if (onCompletion != null) onCompletion.run();
        }
    }
}
