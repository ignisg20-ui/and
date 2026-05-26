package com.shield.util;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Lock-free rolling counter that records timestamps of recent events and
 * answers count queries over a sliding window.
 *
 * <p>Used pervasively by rate-limiters and anti-cheat checks. Memory usage is
 * O(number of events in window); call {@link #count(long)} (which prunes) or
 * the explicit {@link #prune(long)} regularly.</p>
 */
public final class RollingCounter {

    private final ConcurrentLinkedDeque<Long> events = new ConcurrentLinkedDeque<>();

    public void record() {
        events.addLast(System.currentTimeMillis());
    }

    public void record(long nowMs) {
        events.addLast(nowMs);
    }

    public int count(long windowMs) {
        prune(windowMs);
        return events.size();
    }

    public void prune(long windowMs) {
        long cutoff = System.currentTimeMillis() - windowMs;
        Long head;
        while ((head = events.peekFirst()) != null && head < cutoff) {
            events.pollFirst();
        }
    }

    public void clear() {
        events.clear();
    }
}
