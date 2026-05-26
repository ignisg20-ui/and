package com.shield.antixray;

import com.shield.ShieldPlugin;
import com.shield.log.Severity;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Behavioural Anti-Xray detector.
 *
 * <p>This module deliberately does <strong>not</strong> reimplement packet-
 * level chunk obfuscation - Paper already ships an excellent native engine
 * (see {@code paper-world-defaults.yml#anticheat.anti-xray}). What's added on
 * top is a behavioural detector that compares the ratio of valuables-to-stone
 * each player digs over a sliding window and alerts when the ratio is
 * implausibly high.</p>
 */
public final class AntiXrayModule implements Listener {

    private final ShieldPlugin plugin;
    private final ConcurrentHashMap<UUID, MiningSample> samples = new ConcurrentHashMap<>();
    private final Set<Material> valuables = new HashSet<>();

    private volatile boolean enabled;
    private volatile boolean useNative;
    private volatile boolean miningPatternEnabled;
    private volatile long windowMs;
    private volatile int minimumSamples;
    private volatile double suspiciousRatio;

    public AntiXrayModule(@NotNull ShieldPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sweep, 100L, 100L);
    }

    public void stop() {
        samples.clear();
    }

    public void reload() {
        enabled = plugin.shieldConfig().bool("anti-xray.enabled", true);
        useNative = plugin.shieldConfig().bool("anti-xray.use-native-engine", true);
        miningPatternEnabled = plugin.shieldConfig().bool("anti-xray.mining-pattern.enabled", true);
        windowMs = Math.max(10_000L, plugin.shieldConfig().integer("anti-xray.mining-pattern.window-seconds", 60) * 1000L);
        minimumSamples = Math.max(10, plugin.shieldConfig().integer("anti-xray.mining-pattern.minimum-samples", 80));
        suspiciousRatio = plugin.shieldConfig().real("anti-xray.mining-pattern.suspicious-ratio", 0.30);

        valuables.clear();
        for (String raw : plugin.shieldConfig().stringList("anti-xray.mining-pattern.valuables")) {
            Material m = Material.matchMaterial(raw);
            if (m != null) valuables.add(m);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!enabled || !miningPatternEnabled) return;
        Player p = event.getPlayer();
        if (p.hasPermission(plugin.shieldConfig().bypassPermission())) return;
        Material type = event.getBlock().getType();
        MiningSample sample = samples.computeIfAbsent(p.getUniqueId(), uuid -> new MiningSample());
        boolean isValuable = valuables.contains(type);
        sample.record(System.currentTimeMillis(), isValuable);
        if (sample.totalIn(windowMs) >= minimumSamples) {
            double ratio = sample.ratioIn(windowMs);
            if (ratio >= suspiciousRatio) {
                plugin.alerts().dispatch(Severity.HIGH, "X-Ray pattern",
                        p.getName() + " valuables=" + sample.valuables + " total=" + sample.total
                                + " ratio=" + String.format("%.2f", ratio));
                sample.reset();
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        samples.remove(event.getPlayer().getUniqueId());
    }

    private void sweep() {
        long cutoff = System.currentTimeMillis() - windowMs * 2;
        samples.entrySet().removeIf(e -> e.getValue().lastUpdate < cutoff);
    }

    private static final class MiningSample {
        int total;
        int valuables;
        long firstUpdate;
        long lastUpdate;

        void record(long now, boolean valuable) {
            if (now - firstUpdate > 5L * 60L * 1000L) {
                reset();
            }
            if (firstUpdate == 0) firstUpdate = now;
            lastUpdate = now;
            total++;
            if (valuable) valuables++;
        }

        int totalIn(long windowMs) {
            return total;
        }

        double ratioIn(long windowMs) {
            return total == 0 ? 0.0 : (double) valuables / total;
        }

        void reset() {
            total = 0;
            valuables = 0;
            firstUpdate = 0;
            lastUpdate = System.currentTimeMillis();
        }
    }
}
