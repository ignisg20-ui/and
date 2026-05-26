package com.aris.auth.session;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-online-player runtime state used while the player is in the
 * authentication phase. Each {@link AuthState} is created on join, mutated by
 * listeners (move/damage/etc.) and disposed on quit or after a successful
 * authentication.
 */
public final class AuthState {

    private final UUID uuid;
    private final boolean registered;
    private final long joinedAtMs;
    private final AtomicReference<Location> spawnLocation = new AtomicReference<>();
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private final AtomicInteger remainingSeconds;
    private final @Nullable String ip;
    private volatile Object bossbar;
    private volatile boolean cleanedUp;

    public AuthState(@NotNull UUID uuid, boolean registered, int timeoutSeconds, @Nullable String ip) {
        this.uuid = uuid;
        this.registered = registered;
        this.joinedAtMs = System.currentTimeMillis();
        this.remainingSeconds = new AtomicInteger(timeoutSeconds);
        this.ip = ip;
    }

    public @NotNull UUID uuid() { return uuid; }
    public boolean registered() { return registered; }
    public long joinedAtMs() { return joinedAtMs; }
    public @Nullable String ip() { return ip; }

    public void setSpawn(@NotNull Location loc) { spawnLocation.set(loc.clone()); }
    public @Nullable Location spawn() { return spawnLocation.get(); }

    public boolean authenticated() { return authenticated.get(); }
    public boolean authenticate() { return authenticated.compareAndSet(false, true); }

    public AtomicInteger remainingSeconds() { return remainingSeconds; }

    public void setBossbar(@Nullable Object bar) { this.bossbar = bar; }
    public @Nullable Object bossbar() { return bossbar; }

    public boolean cleanedUp() { return cleanedUp; }
    public void markCleanedUp() { this.cleanedUp = true; }
}
