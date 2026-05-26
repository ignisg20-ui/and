package com.shield.antiddos;

import com.shield.ShieldPlugin;
import com.shield.log.Severity;
import com.shield.util.CooldownMap;
import com.shield.util.IpUtil;
import com.shield.util.RollingCounter;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * First-line defence against connection floods, login spam, and reconnect
 * abuse. The module piggy-backs on the regular {@link AsyncPlayerPreLoginEvent}
 * for per-IP gating; raw socket throttling beneath this layer is handled by
 * the channel handlers installed by {@link com.shield.packet.PacketInterceptor}.
 */
public final class AntiDDoSModule implements Listener {

    private final ShieldPlugin plugin;
    private final ConcurrentHashMap<String, RollingCounter> perIpConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RollingCounter> perIpHandshakes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RollingCounter> perIpLogins = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastDisconnect = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> reconnectScore = new ConcurrentHashMap<>();
    private final CooldownMap<String> blockedIps = new CooldownMap<>();
    private final RollingCounter globalConnections = new RollingCounter();
    private final RollingCounter globalHandshakes = new RollingCounter();
    private final RollingCounter whitelistedJoins = new RollingCounter();
    private final Set<String> userBannedIps = ConcurrentHashMap.newKeySet();

    private volatile boolean enabled;
    private volatile int maxNewConnectionsPerSecond;
    private volatile int perIpMaxConnections;
    private volatile long perIpWindowMs;
    private volatile int handshakeMaxPerSecond;
    private volatile int handshakeMaxPerIp;
    private volatile long handshakeWindowMs;
    private volatile int loginMaxPerIp;
    private volatile long loginWindowMs;
    private volatile long reconnectMinIntervalMs;
    private volatile int reconnectThreshold;
    private volatile int whitelistBurstMax;
    private volatile boolean autoBanEnabled;
    private volatile int autoBanThreshold;
    private volatile long autoBanDurationMs;

    public AntiDDoSModule(@NotNull ShieldPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sweep, 100L, 100L);
    }

    public void stop() {
        perIpConnections.clear();
        perIpHandshakes.clear();
        perIpLogins.clear();
        lastDisconnect.clear();
        reconnectScore.clear();
        globalConnections.clear();
        globalHandshakes.clear();
        whitelistedJoins.clear();
    }

    public void reload() {
        enabled = plugin.shieldConfig().bool("anti-ddos.enabled", true);
        maxNewConnectionsPerSecond = plugin.shieldConfig().integer("anti-ddos.max-new-connections-per-second", 25);
        perIpMaxConnections = plugin.shieldConfig().integer("anti-ddos.per-ip.max-connections", 6);
        perIpWindowMs = plugin.shieldConfig().integer("anti-ddos.per-ip.window-seconds", 10) * 1000L;
        handshakeMaxPerSecond = plugin.shieldConfig().integer("anti-ddos.handshake.max-per-second", 40);
        handshakeMaxPerIp = plugin.shieldConfig().integer("anti-ddos.handshake.max-per-ip-window", 5);
        handshakeWindowMs = plugin.shieldConfig().integer("anti-ddos.handshake.window-seconds", 5) * 1000L;
        loginMaxPerIp = plugin.shieldConfig().integer("anti-ddos.login.max-per-ip-window", 4);
        loginWindowMs = plugin.shieldConfig().integer("anti-ddos.login.window-seconds", 30) * 1000L;
        reconnectMinIntervalMs = plugin.shieldConfig().integer("anti-ddos.reconnect-spam.min-interval-ms", 1500);
        reconnectThreshold = plugin.shieldConfig().integer("anti-ddos.reconnect-spam.score-threshold", 5);
        whitelistBurstMax = plugin.shieldConfig().integer("anti-ddos.whitelist-burst.max-per-second", 10);
        autoBanEnabled = plugin.shieldConfig().bool("anti-ddos.auto-ban.enabled", true);
        autoBanThreshold = plugin.shieldConfig().integer("anti-ddos.auto-ban.threshold", 12);
        autoBanDurationMs = plugin.shieldConfig().integer("anti-ddos.auto-ban.duration-seconds", 600) * 1000L;

        userBannedIps.clear();
        userBannedIps.addAll(plugin.shieldConfig().stringList("ban.ips"));
    }

    public boolean isBlocked(@NotNull String ip) {
        return userBannedIps.contains(ip) || blockedIps.isActive(ip);
    }

    public void banIp(@NotNull String ip, long durationMs, @NotNull String reason) {
        blockedIps.set(ip, durationMs);
        plugin.alerts().dispatch(Severity.HIGH, "IP ban", ip + " banned for " + (durationMs / 1000) + "s :: " + reason);
    }

    public void unbanIp(@NotNull String ip) {
        blockedIps.clear(ip);
        userBannedIps.remove(ip);
    }

    public @NotNull Set<String> blockedIpsSnapshot() {
        Set<String> snap = new HashSet<>(userBannedIps);
        return snap;
    }

    /**
     * Called from the netty channel handler when a brand-new connection is opened.
     * Returns {@code true} when the connection should be closed immediately.
     */
    public boolean shouldDropChannel(@NotNull String ip) {
        if (!enabled) return false;
        if (isBlocked(ip)) return true;

        globalConnections.record();
        if (globalConnections.count(1000) > maxNewConnectionsPerSecond) {
            penalise(ip, "global connection rate");
            return true;
        }
        RollingCounter rc = perIpConnections.computeIfAbsent(ip, k -> new RollingCounter());
        rc.record();
        if (rc.count(perIpWindowMs) > perIpMaxConnections) {
            penalise(ip, "per-IP connection burst");
            return true;
        }

        long now = System.currentTimeMillis();
        Long previous = lastDisconnect.put(ip, now);
        if (previous != null && now - previous < reconnectMinIntervalMs) {
            int score = reconnectScore.merge(ip, 1, Integer::sum);
            if (score > reconnectThreshold) {
                penalise(ip, "reconnect spam");
                return true;
            }
        } else {
            reconnectScore.merge(ip, -1, (a, b) -> Math.max(0, a + b));
        }
        return false;
    }

    public boolean shouldDropHandshake(@NotNull String ip) {
        if (!enabled) return false;
        if (isBlocked(ip)) return true;
        globalHandshakes.record();
        if (globalHandshakes.count(1000) > handshakeMaxPerSecond) {
            penalise(ip, "global handshake rate");
            return true;
        }
        RollingCounter rc = perIpHandshakes.computeIfAbsent(ip, k -> new RollingCounter());
        rc.record();
        if (rc.count(handshakeWindowMs) > handshakeMaxPerIp) {
            penalise(ip, "per-IP handshake spam");
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!enabled) return;
        InetAddress addr = event.getAddress();
        String ip = IpUtil.stringify(addr);
        if (isBlocked(ip)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "§c[Shield] §fВаш IP временно заблокирован.");
            return;
        }
        RollingCounter rc = perIpLogins.computeIfAbsent(ip, k -> new RollingCounter());
        rc.record();
        if (rc.count(loginWindowMs) > loginMaxPerIp) {
            penalise(ip, "login rate-limit");
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "§c[Shield] §fСлишком много попыток входа. Попробуйте позже.");
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onLogin(PlayerLoginEvent event) {
        if (!enabled) return;
        String ip = IpUtil.stringify(event.getAddress());
        if (Bukkit.hasWhitelist()) {
            whitelistedJoins.record();
            if (whitelistedJoins.count(1000) > whitelistBurstMax) {
                event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                        "§c[Shield] §fВход временно ограничен (burst control).");
                penalise(ip, "whitelist burst");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Disconnect timestamps are used by reconnect-spam detection only; we
        // intentionally do not delete here so subsequent fast reconnects are
        // still scored.
    }

    private void penalise(@NotNull String ip, @NotNull String reason) {
        int score = reconnectScore.merge(ip, 3, Integer::sum);
        plugin.securityLogger().write(Severity.HIGH, "DDOS", ip + " :: " + reason + " (score=" + score + ")");
        if (autoBanEnabled && score >= autoBanThreshold) {
            banIp(ip, autoBanDurationMs, "automatic - " + reason);
            reconnectScore.put(ip, 0);
        }
    }

    private void sweep() {
        perIpConnections.values().forEach(rc -> rc.prune(perIpWindowMs));
        perIpHandshakes.values().forEach(rc -> rc.prune(handshakeWindowMs));
        perIpLogins.values().forEach(rc -> rc.prune(loginWindowMs));
        globalConnections.prune(2000);
        globalHandshakes.prune(2000);
        whitelistedJoins.prune(2000);
        blockedIps.sweep(null);

        long cutoff = System.currentTimeMillis() - 60_000L;
        lastDisconnect.entrySet().removeIf(e -> e.getValue() < cutoff);
        reconnectScore.entrySet().removeIf(e -> e.getValue() <= 0);
    }
}
