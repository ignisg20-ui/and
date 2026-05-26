package com.shield.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Simple key -&gt; "expires-at" map used for short-term throttling decisions.
 */
public final class CooldownMap<K> {

    private final ConcurrentHashMap<K, Long> entries = new ConcurrentHashMap<>();

    public boolean tryAcquire(K key, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long existing = entries.get(key);
        if (existing != null && existing > now) return false;
        entries.put(key, now + cooldownMs);
        return true;
    }

    public void set(K key, long durationMs) {
        entries.put(key, System.currentTimeMillis() + durationMs);
    }

    public boolean isActive(K key) {
        Long until = entries.get(key);
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            entries.remove(key, until);
            return false;
        }
        return true;
    }

    public void clear(K key) {
        entries.remove(key);
    }

    public void sweep(Consumer<K> onExpired) {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(e -> {
            if (e.getValue() <= now) {
                if (onExpired != null) onExpired.accept(e.getKey());
                return true;
            }
            return false;
        });
    }

    public int size() {
        return entries.size();
    }
}
