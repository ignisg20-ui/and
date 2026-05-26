package com.aris.auth.security;

import com.aris.auth.AuthPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Per-IP brute-force throttle. Each failed password increments a counter; once
 * the counter exceeds {@code maxAttempts} the IP is locked out until the
 * configured timeout elapses.
 */
public final class Bruteforce {

    private final AuthPlugin plugin;
    private final ConcurrentHashMap<String, Entry> state = new ConcurrentHashMap<>();

    public Bruteforce(@NotNull AuthPlugin plugin) {
        this.plugin = plugin;
    }

    public void recordFailure(@Nullable String ip, @NotNull String playerName) {
        if (!plugin.config().bruteForceEnabled || ip == null) return;
        long now = System.currentTimeMillis();
        state.compute(ip, (k, existing) -> {
            if (existing == null || now - existing.firstAttemptMs > plugin.config().bruteForceWindowMs) {
                return new Entry(1, now, 0L);
            }
            existing.attempts++;
            if (existing.attempts >= plugin.config().bruteForceMaxAttempts) {
                existing.lockedUntilMs = now + plugin.config().bruteForceLockoutMs;
                plugin.getLogger().log(Level.WARNING, "Bruteforce lockout for IP " + ip
                        + " triggered by " + playerName);
            }
            return existing;
        });
    }

    public void recordSuccess(@Nullable String ip) {
        if (ip == null) return;
        state.remove(ip);
    }

    public boolean isLocked(@Nullable String ip) {
        if (!plugin.config().bruteForceEnabled || ip == null) return false;
        Entry e = state.get(ip);
        if (e == null) return false;
        if (e.lockedUntilMs == 0L) return false;
        if (System.currentTimeMillis() > e.lockedUntilMs) {
            state.remove(ip);
            return false;
        }
        return true;
    }

    public int remainingAttempts(@Nullable String ip) {
        if (!plugin.config().bruteForceEnabled || ip == null) return plugin.config().bruteForceMaxAttempts;
        Entry e = state.get(ip);
        if (e == null) return plugin.config().bruteForceMaxAttempts;
        return Math.max(0, plugin.config().bruteForceMaxAttempts - e.attempts);
    }

    public long lockedRemainingMs(@Nullable String ip) {
        if (ip == null) return 0L;
        Entry e = state.get(ip);
        if (e == null || e.lockedUntilMs == 0L) return 0L;
        return Math.max(0L, e.lockedUntilMs - System.currentTimeMillis());
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        state.entrySet().removeIf(en -> {
            Entry e = en.getValue();
            if (e.lockedUntilMs != 0L) {
                return now > e.lockedUntilMs;
            }
            return now - e.firstAttemptMs > plugin.config().bruteForceWindowMs;
        });
    }

    private static final class Entry {
        int attempts;
        long firstAttemptMs;
        long lockedUntilMs;
        Entry(int attempts, long firstAttemptMs, long lockedUntilMs) {
            this.attempts = attempts;
            this.firstAttemptMs = firstAttemptMs;
            this.lockedUntilMs = lockedUntilMs;
        }
    }
}
