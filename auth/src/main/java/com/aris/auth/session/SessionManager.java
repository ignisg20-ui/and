package com.aris.auth.session;

import com.aris.auth.AuthPlugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds short-lived "trust" records for players who recently logged in. When
 * the same player reconnects from the same IP within the configured window
 * we restore them without asking for the password again.
 */
public final class SessionManager {

    private final AuthPlugin plugin;
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();

    public SessionManager(@NotNull AuthPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(@NotNull Player player) {
        sessions.put(player.getUniqueId(), new Session(
                player.getUniqueId(),
                player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress(),
                System.currentTimeMillis()));
    }

    public void touch(@NotNull UUID uuid) {
        Session s = sessions.get(uuid);
        if (s != null) s.lastSeenMs = System.currentTimeMillis();
    }

    public boolean canResume(@NotNull Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null) return false;
        long age = System.currentTimeMillis() - s.lastSeenMs;
        if (age > plugin.config().sessionMs) {
            sessions.remove(player.getUniqueId());
            return false;
        }
        String ip = player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
        // Same player + same IP within window -> session resumes.
        return s.ip != null && s.ip.equals(ip);
    }

    public void close(@NotNull UUID uuid) {
        sessions.remove(uuid);
    }

    public @Nullable Session get(@NotNull UUID uuid) {
        return sessions.get(uuid);
    }

    public int size() {
        return sessions.size();
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> now - e.getValue().lastSeenMs > plugin.config().sessionMs);
    }

    public static final class Session {
        public final UUID uuid;
        public final @Nullable String ip;
        public volatile long lastSeenMs;

        public Session(UUID uuid, @Nullable String ip, long lastSeenMs) {
            this.uuid = uuid;
            this.ip = ip;
            this.lastSeenMs = lastSeenMs;
        }
    }
}
