package com.shield.anticheat;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-player anti-cheat scratchpad. Each anti-cheat check stores its own
 * counters / samples on this object, indexed by check name to keep the
 * structure compact and lock-free.
 */
public final class PlayerData {

    private final UUID uuid;
    private final long joinedAtMs;

    private volatile Location lastLocation;
    private volatile long lastMoveMs;
    private volatile long lastDamageMs;
    private volatile long lastVelocityMs;
    private volatile double lastVelocityX;
    private volatile double lastVelocityY;
    private volatile double lastVelocityZ;
    private final AtomicInteger groundTicks = new AtomicInteger();
    private final AtomicInteger airTicks = new AtomicInteger();
    private final AtomicLong totalScore = new AtomicLong();
    private final ConcurrentHashMap<String, AtomicLong> perCheckScore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> lastUpdateMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Double>> sampleBuffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Long>> timeBuffers = new ConcurrentHashMap<>();

    public PlayerData(@NotNull Player player) {
        this.uuid = player.getUniqueId();
        this.joinedAtMs = System.currentTimeMillis();
        this.lastLocation = player.getLocation();
        this.lastMoveMs = joinedAtMs;
    }

    public UUID uuid() {
        return uuid;
    }

    public long joinedAtMs() {
        return joinedAtMs;
    }

    public Location lastLocation() {
        return lastLocation;
    }

    public void setLastLocation(Location loc) {
        this.lastLocation = loc;
    }

    public long lastMoveMs() {
        return lastMoveMs;
    }

    public void setLastMoveMs(long lastMoveMs) {
        this.lastMoveMs = lastMoveMs;
    }

    public long lastDamageMs() {
        return lastDamageMs;
    }

    public void setLastDamageMs(long lastDamageMs) {
        this.lastDamageMs = lastDamageMs;
    }

    public long lastVelocityMs() {
        return lastVelocityMs;
    }

    public double lastVelocityX() {
        return lastVelocityX;
    }

    public double lastVelocityY() {
        return lastVelocityY;
    }

    public double lastVelocityZ() {
        return lastVelocityZ;
    }

    public void recordVelocity(long ms, double vx, double vy, double vz) {
        this.lastVelocityMs = ms;
        this.lastVelocityX = vx;
        this.lastVelocityY = vy;
        this.lastVelocityZ = vz;
    }

    public AtomicInteger groundTicks() {
        return groundTicks;
    }

    public AtomicInteger airTicks() {
        return airTicks;
    }

    public long totalScore() {
        return totalScore.get();
    }

    public long addScore(@NotNull String check, long amount) {
        perCheckScore.computeIfAbsent(check, k -> new AtomicLong()).addAndGet(amount);
        return totalScore.addAndGet(amount);
    }

    public long perCheckScore(@NotNull String check) {
        AtomicLong v = perCheckScore.get(check);
        return v == null ? 0 : v.get();
    }

    public void decay(long amount) {
        long current = totalScore.get();
        if (current <= 0) return;
        long newValue = Math.max(0, current - amount);
        totalScore.set(newValue);
        perCheckScore.values().forEach(v -> v.getAndUpdate(c -> Math.max(0, c - amount)));
    }

    public void markUpdate(@NotNull String check, long ms) {
        lastUpdateMs.computeIfAbsent(check, k -> new AtomicLong()).set(ms);
    }

    public long lastUpdate(@NotNull String check) {
        AtomicLong v = lastUpdateMs.get(check);
        return v == null ? 0L : v.get();
    }

    public @NotNull Deque<Double> samples(@NotNull String check, int max) {
        return sampleBuffers.computeIfAbsent(check, k -> new ArrayDeque<>(max + 1));
    }

    public @NotNull Deque<Long> times(@NotNull String check, int max) {
        return timeBuffers.computeIfAbsent(check, k -> new ArrayDeque<>(max + 1));
    }
}
