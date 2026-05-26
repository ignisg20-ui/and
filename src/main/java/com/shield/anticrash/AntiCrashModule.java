package com.shield.anticrash;

import com.shield.ShieldPlugin;
import com.shield.log.Severity;
import com.shield.util.RollingCounter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Soft caps designed to make the server *resilient* rather than restrictive.
 * Limits never disconnect players; instead they cancel the offending action
 * and emit an alert so the admin can investigate.
 */
public final class AntiCrashModule implements Listener {

    private final ShieldPlugin plugin;
    private final ConcurrentHashMap<UUID, RollingCounter> chunkLoadsPerPlayer = new ConcurrentHashMap<>();
    private final RollingCounter entitySpawns = new RollingCounter();
    private final ConcurrentHashMap<Long, Integer> redstoneUpdatesThisTick = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile int packetSpamMax;
    private volatile boolean chunkLoadEnabled;
    private volatile int maxChunkLoadsPerSecond;
    private volatile boolean entitySpawnEnabled;
    private volatile int maxEntitySpawnPerSecond;
    private volatile boolean redstoneEnabled;
    private volatile int maxRedstonePerChunkPerTick;
    private volatile boolean hopperEnabled;
    private volatile int maxHoppersPerChunk;

    public AntiCrashModule(@NotNull ShieldPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> redstoneUpdatesThisTick.clear(), 1L, 1L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sweep, 100L, 100L);
    }

    public void stop() {
        chunkLoadsPerPlayer.clear();
        entitySpawns.clear();
        redstoneUpdatesThisTick.clear();
    }

    public void reload() {
        enabled = plugin.shieldConfig().bool("anti-crash.enabled", true);
        packetSpamMax = plugin.shieldConfig().integer("anti-crash.packet-spam.max-per-second", 800);
        chunkLoadEnabled = plugin.shieldConfig().bool("anti-crash.chunk-load.enabled", true);
        maxChunkLoadsPerSecond = plugin.shieldConfig().integer("anti-crash.chunk-load.max-per-player-per-second", 30);
        entitySpawnEnabled = plugin.shieldConfig().bool("anti-crash.entity-spawn.enabled", true);
        maxEntitySpawnPerSecond = plugin.shieldConfig().integer("anti-crash.entity-spawn.max-per-second", 60);
        redstoneEnabled = plugin.shieldConfig().bool("anti-crash.redstone.enabled", true);
        maxRedstonePerChunkPerTick = plugin.shieldConfig().integer("anti-crash.redstone.max-updates-per-chunk-per-tick", 600);
        hopperEnabled = plugin.shieldConfig().bool("anti-crash.hopper-abuse.enabled", true);
        maxHoppersPerChunk = plugin.shieldConfig().integer("anti-crash.hopper-abuse.max-hoppers-per-chunk", 24);
    }

    public int packetSpamMax() {
        return packetSpamMax;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!enabled || !chunkLoadEnabled) return;
        // We only track *forced* chunk loads (those driven by player actions like /tp spam).
        // ChunkLoadEvent does not carry the source player so we approximate by penalising
        // all online players in the same world over the same window.
        for (org.bukkit.entity.Player p : event.getWorld().getPlayers()) {
            RollingCounter rc = chunkLoadsPerPlayer.computeIfAbsent(p.getUniqueId(), uuid -> new RollingCounter());
            rc.record();
            if (rc.count(1000) > maxChunkLoadsPerSecond) {
                plugin.alerts().dispatch(Severity.MEDIUM, "Chunk-load spam",
                        p.getName() + " caused " + rc.count(1000) + " chunk loads in last second.");
                rc.clear();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!enabled || !entitySpawnEnabled) return;
        entitySpawns.record();
        if (entitySpawns.count(1000) > maxEntitySpawnPerSecond) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (!enabled || !redstoneEnabled) return;
        long key = chunkKey(event.getBlock().getChunk().getX(), event.getBlock().getChunk().getZ());
        int n = redstoneUpdatesThisTick.merge(key, 1, Integer::sum);
        if (n == maxRedstonePerChunkPerTick + 1) {
            plugin.alerts().dispatch(Severity.HIGH, "Redstone lag",
                    "Chunk " + event.getBlock().getChunk().getX() + "/" + event.getBlock().getChunk().getZ()
                            + " physics > " + maxRedstonePerChunkPerTick);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopperPlace(BlockPlaceEvent event) {
        if (!enabled || !hopperEnabled) return;
        if (event.getBlock().getType() != Material.HOPPER) return;
        int count = 0;
        for (org.bukkit.block.BlockState state : event.getBlock().getChunk().getTileEntities()) {
            if (state.getType() == Material.HOPPER && ++count >= maxHoppersPerChunk) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§c[Shield] §fДостигнут лимит хопперов в этом чанке.");
                plugin.alerts().dispatch(Severity.LOW, "Hopper limit",
                        event.getPlayer().getName() + " hit hopper cap in chunk "
                                + event.getBlock().getChunk().getX() + "/" + event.getBlock().getChunk().getZ());
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        chunkLoadsPerPlayer.remove(event.getPlayer().getUniqueId());
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private void sweep() {
        chunkLoadsPerPlayer.values().forEach(rc -> rc.prune(2000));
        entitySpawns.prune(2000);
    }
}
