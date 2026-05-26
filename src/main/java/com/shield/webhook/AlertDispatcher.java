package com.shield.webhook;

import com.shield.ShieldPlugin;
import com.shield.log.Severity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Central fan-out for security alerts.
 *
 * <p>Outgoing webhook calls run on a small dedicated executor so they never
 * block the main thread. A short de-duplication window suppresses identical
 * alerts that arrive in bursts.</p>
 */
public final class AlertDispatcher {

    private final ShieldPlugin plugin;
    private final ScheduledExecutorService executor;
    private final ConcurrentHashMap<String, Long> recent = new ConcurrentHashMap<>();

    private volatile DiscordWebhook discord;
    private volatile TelegramWebhook telegram;
    private volatile Severity minSeverity = Severity.MEDIUM;

    public AlertDispatcher(@NotNull ShieldPlugin plugin) {
        this.plugin = plugin;
        this.executor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "Shield-Alerts");
            t.setDaemon(true);
            return t;
        });
        reload();
        executor.scheduleAtFixedRate(this::sweep, 30, 30, TimeUnit.SECONDS);
    }

    public void reload() {
        discord = new DiscordWebhook(
                plugin.shieldConfig().bool("webhook.discord.enabled", false),
                plugin.shieldConfig().string("webhook.discord.url", ""),
                plugin.shieldConfig().string("webhook.discord.username", "Shield"),
                plugin.shieldConfig().string("webhook.discord.avatar-url", "")
        );
        telegram = new TelegramWebhook(
                plugin.shieldConfig().bool("webhook.telegram.enabled", false),
                plugin.shieldConfig().string("webhook.telegram.bot-token", ""),
                plugin.shieldConfig().string("webhook.telegram.chat-id", "")
        );
        minSeverity = Severity.parse(plugin.shieldConfig().string("webhook.minimum-severity", "MEDIUM"), Severity.MEDIUM);
    }

    public void dispatch(@NotNull Severity severity, @NotNull String title, @NotNull String message) {
        plugin.securityLogger().write(severity, "ALERT", title + " :: " + message);
        if (!severity.isAtLeast(minSeverity)) return;

        long now = System.currentTimeMillis();
        long window = plugin.shieldConfig().alertDedupWindowMs();
        String key = severity.name() + '|' + title + '|' + message;
        Long last = recent.put(key, now);
        if (last != null && now - last < window) return;

        notifyInGame(severity, title, message);
        executor.execute(() -> {
            try {
                if (discord.isEnabled()) discord.send(severity, title, message);
                if (telegram.isEnabled()) telegram.send(severity, title, message);
            } catch (Throwable t) {
                plugin.getLogger().warning("Webhook delivery failed: " + t.getMessage());
            }
        });
    }

    private void notifyInGame(@NotNull Severity severity, @NotNull String title, @NotNull String message) {
        String prefix = switch (severity) {
            case CRITICAL -> "§4[Shield-CRIT] ";
            case HIGH -> "§c[Shield] ";
            case MEDIUM -> "§6[Shield] ";
            case LOW -> "§e[Shield] ";
            case INFO -> "§7[Shield] ";
        };
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("shield.notify")) {
                p.sendMessage(prefix + "§f" + title + " §8— §7" + message);
            }
        }
    }

    private void sweep() {
        long cutoff = System.currentTimeMillis() - plugin.shieldConfig().alertDedupWindowMs() * 4L;
        recent.entrySet().removeIf(e -> e.getValue() < cutoff);
    }
}
