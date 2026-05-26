package com.shield.quarantine;

import com.shield.ShieldPlugin;
import com.shield.log.Severity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages players who have been temporarily isolated by Shield. Quarantined
 * players cannot move outside a small radius around their entry point and are
 * (by default) prevented from chatting, executing commands, breaking or
 * placing blocks.
 *
 * <p>Quarantine is non-destructive: when the timer expires (or an admin calls
 * {@link #release(UUID)}) the player is fully released without any state
 * mutation. This is intentionally light-weight and serves as an intermediate
 * state between "free" and "kicked".</p>
 */
public final class QuarantineManager implements Listener {

    private final ShieldPlugin plugin;
    private final ConcurrentHashMap<UUID, Entry> entries = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile int radius;
    private volatile boolean denyChat;
    private volatile boolean denyCommands;
    private volatile boolean denyBlockChanges;
    private volatile int defaultDurationSeconds;

    public QuarantineManager(@NotNull ShieldPlugin plugin) {
        this.plugin = plugin;
        reload();
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sweep, 20L, 20L);
    }

    public void reload() {
        enabled = plugin.shieldConfig().bool("anti-bot.quarantine.enabled", true);
        radius = Math.max(1, plugin.shieldConfig().integer("anti-bot.quarantine.radius-blocks", 5));
        denyChat = plugin.shieldConfig().bool("anti-bot.quarantine.deny-chat", true);
        denyCommands = plugin.shieldConfig().bool("anti-bot.quarantine.deny-commands", true);
        denyBlockChanges = plugin.shieldConfig().bool("anti-bot.quarantine.deny-block-changes", true);
        defaultDurationSeconds = Math.max(5, plugin.shieldConfig().integer("anti-bot.quarantine.default-duration-seconds", 120));
    }

    public boolean isQuarantined(@Nullable UUID uuid) {
        if (uuid == null) return false;
        Entry e = entries.get(uuid);
        if (e == null) return false;
        if (e.expiresAt < System.currentTimeMillis()) {
            entries.remove(uuid, e);
            return false;
        }
        return true;
    }

    public void quarantine(@NotNull Player player, @NotNull String reason) {
        quarantine(player, reason, defaultDurationSeconds);
    }

    public void quarantine(@NotNull Player player, @NotNull String reason, int durationSeconds) {
        if (!enabled) return;
        long expires = System.currentTimeMillis() + durationSeconds * 1000L;
        entries.put(player.getUniqueId(), new Entry(player.getLocation().clone(), expires, reason));
        player.sendMessage("§c[Shield] §fВы помещены в карантин: §7" + reason);
        plugin.alerts().dispatch(Severity.MEDIUM, "Quarantine",
                player.getName() + " quarantined for " + durationSeconds + "s :: " + reason);
    }

    public void release(@NotNull UUID uuid) {
        Entry removed = entries.remove(uuid);
        if (removed == null) return;
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) p.sendMessage("§a[Shield] §fКарантин снят.");
    }

    public int size() {
        return entries.size();
    }

    public void clearAll() {
        entries.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Entry e = entries.get(event.getPlayer().getUniqueId());
        if (e == null) return;
        Location to = event.getTo();
        if (to == null) return;
        Location origin = e.origin;
        if (!origin.getWorld().equals(to.getWorld()) || origin.distanceSquared(to) > (double) radius * radius) {
            event.setTo(origin.clone());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!denyChat) return;
        if (entries.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[Shield] §fЧат заблокирован во время карантина.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!denyCommands) return;
        if (entries.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[Shield] §fКоманды заблокированы во время карантина.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (denyBlockChanges && entries.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (denyBlockChanges && entries.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        entries.remove(event.getPlayer().getUniqueId());
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(e -> {
            if (e.getValue().expiresAt < now) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p != null) p.sendMessage("§a[Shield] §fКарантин снят автоматически.");
                });
                return true;
            }
            return false;
        });
    }

    private record Entry(Location origin, long expiresAt, String reason) {}
}
