package com.aris.auth.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Immutable view of a stored account row.
 */
public final class AuthUser {

    private final UUID uuid;
    private final String name;
    private final String passwordHash;
    private final @Nullable String lastIp;
    private final long lastLoginMs;
    private final long createdMs;

    public AuthUser(@NotNull UUID uuid, @NotNull String name, @NotNull String passwordHash,
                    @Nullable String lastIp, long lastLoginMs, long createdMs) {
        this.uuid = uuid;
        this.name = name;
        this.passwordHash = passwordHash;
        this.lastIp = lastIp;
        this.lastLoginMs = lastLoginMs;
        this.createdMs = createdMs;
    }

    public @NotNull UUID uuid() { return uuid; }
    public @NotNull String name() { return name; }
    public @NotNull String passwordHash() { return passwordHash; }
    public @Nullable String lastIp() { return lastIp; }
    public long lastLoginMs() { return lastLoginMs; }
    public long createdMs() { return createdMs; }

    public @NotNull AuthUser withHash(@NotNull String newHash) {
        return new AuthUser(uuid, name, newHash, lastIp, lastLoginMs, createdMs);
    }

    public @NotNull AuthUser withLogin(@Nullable String ip, long whenMs) {
        return new AuthUser(uuid, name, passwordHash, ip, whenMs, createdMs);
    }
}
