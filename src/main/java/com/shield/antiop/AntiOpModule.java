package com.shield.antiop;

import com.shield.ShieldPlugin;
import com.shield.log.Severity;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Audits OP grants, deop revocations, and command execution by operators. The
 * module performs a snapshot diff at every plugin tick: any OP that wasn't in
 * the previous snapshot or in the {@code authorized-uuids} list is rolled
 * back automatically when {@code auto-rollback} is enabled.
 */
public final class AntiOpModule implements Listener {

    private final ShieldPlugin plugin;
    private final ConcurrentHashMap<UUID, String> knownOps = new ConcurrentHashMap<>();
    private final Set<UUID> authorisedUuids = ConcurrentHashMap.newKeySet();

    private volatile boolean enabled;
    private volatile boolean autoRollback;
    private volatile boolean auditAllCommands;

    public AntiOpModule(@NotNull ShieldPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (OfflinePlayer op : Bukkit.getOperators()) {
            knownOps.put(op.getUniqueId(), op.getName() == null ? op.getUniqueId().toString() : op.getName());
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::scanOpDiff, 100L, 100L);
    }

    public void stop() {
        knownOps.clear();
    }

    public void reload() {
        enabled = plugin.shieldConfig().bool("anti-op.enabled", true);
        autoRollback = plugin.shieldConfig().bool("anti-op.auto-rollback", true);
        auditAllCommands = plugin.shieldConfig().bool("anti-op.audit-all-commands-run-by-ops", true);

        authorisedUuids.clear();
        for (String raw : plugin.shieldConfig().stringList("anti-op.authorized-uuids")) {
            try {
                authorisedUuids.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Bad UUID in anti-op.authorized-uuids: " + raw);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled || !auditAllCommands) return;
        Player p = event.getPlayer();
        if (!p.isOp()) return;
        plugin.securityLogger().write(Severity.LOW, "OP-AUDIT",
                p.getName() + " ran " + event.getMessage());
        if (looksLikeOpChange(event.getMessage())) {
            plugin.alerts().dispatch(Severity.CRITICAL, "OP change",
                    p.getName() + " executed " + event.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        if (!enabled || !auditAllCommands) return;
        plugin.securityLogger().write(Severity.LOW, "OP-AUDIT-CONSOLE",
                event.getSender().getName() + " ran " + event.getCommand());
        if (looksLikeOpChange(event.getCommand())) {
            plugin.alerts().dispatch(Severity.HIGH, "OP change (console)",
                    event.getSender().getName() + " executed " + event.getCommand());
        }
    }

    private void scanOpDiff() {
        if (!enabled) return;
        Set<OfflinePlayer> current = Bukkit.getOperators();
        Set<UUID> currentIds = new HashSet<>();
        for (OfflinePlayer op : current) {
            currentIds.add(op.getUniqueId());
            String previousName = knownOps.put(op.getUniqueId(), op.getName() == null ? op.getUniqueId().toString() : op.getName());
            if (previousName == null) {
                String name = op.getName() == null ? op.getUniqueId().toString() : op.getName();
                plugin.alerts().dispatch(Severity.CRITICAL, "New OP detected",
                        name + " was granted operator privileges.");
                if (autoRollback && !authorisedUuids.contains(op.getUniqueId())) {
                    op.setOp(false);
                    plugin.alerts().dispatch(Severity.HIGH, "OP rolled back",
                            name + " was un-OPed by Shield (not in authorized-uuids).");
                }
            }
        }
        knownOps.keySet().removeIf(uuid -> {
            if (!currentIds.contains(uuid)) {
                plugin.alerts().dispatch(Severity.HIGH, "OP revoked",
                        knownOps.get(uuid) + " is no longer operator.");
                return true;
            }
            return false;
        });
    }

    private static boolean looksLikeOpChange(@NotNull String message) {
        String lower = message.toLowerCase(Locale.ROOT).strip();
        if (lower.startsWith("/")) lower = lower.substring(1);
        return lower.startsWith("op ") || lower.equals("op")
                || lower.startsWith("deop ") || lower.equals("deop")
                || lower.startsWith("minecraft:op ") || lower.startsWith("minecraft:deop ");
    }
}
