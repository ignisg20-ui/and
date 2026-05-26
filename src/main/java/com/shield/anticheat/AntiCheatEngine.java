package com.shield.anticheat;

import com.shield.ShieldPlugin;
import com.shield.anticheat.checks.AimAssistCheck;
import com.shield.anticheat.checks.AutoClickerCheck;
import com.shield.anticheat.checks.Check;
import com.shield.anticheat.checks.CheckContext;
import com.shield.anticheat.checks.FlyCheck;
import com.shield.anticheat.checks.JesusCheck;
import com.shield.anticheat.checks.KillAuraCheck;
import com.shield.anticheat.checks.NoFallCheck;
import com.shield.anticheat.checks.ReachCheck;
import com.shield.anticheat.checks.ScaffoldCheck;
import com.shield.anticheat.checks.SpeedCheck;
import com.shield.anticheat.checks.TimerCheck;
import com.shield.anticheat.checks.VelocityCheck;
import com.shield.log.Severity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates every individual {@link Check} and owns per-player state.
 *
 * <p>Checks run synchronously from Bukkit event handlers so they always see a
 * coherent server snapshot. Score accumulation and per-tier responses are
 * handled centrally to make the response policy easy to tune from config and
 * to keep individual checks tiny.</p>
 */
public final class AntiCheatEngine implements Listener {

    private final ShieldPlugin plugin;
    private final ConcurrentHashMap<UUID, PlayerData> data = new ConcurrentHashMap<>();
    private final List<Check> checks;
    private final TimerCheck timerCheck;

    private volatile boolean enabled;
    private volatile int warmupSeconds;
    private volatile int decaySeconds;
    private volatile long warnAt;
    private volatile long alertAt;
    private volatile long kickAt;
    private volatile long banAt;

    public AntiCheatEngine(@NotNull ShieldPlugin plugin) {
        this.plugin = plugin;
        this.timerCheck = new TimerCheck();
        this.checks = List.of(
                new KillAuraCheck(),
                new ReachCheck(),
                new SpeedCheck(),
                new FlyCheck(),
                new NoFallCheck(),
                new ScaffoldCheck(),
                new AutoClickerCheck(),
                new AimAssistCheck(),
                timerCheck,
                new VelocityCheck(),
                new JesusCheck()
        );
        reload();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::decay, 20L, 20L);
    }

    public void stop() {
        data.clear();
    }

    public void reload() {
        enabled = plugin.shieldConfig().bool("anti-cheat.enabled", true);
        warmupSeconds = plugin.shieldConfig().integer("anti-cheat.warmup-seconds", 5);
        decaySeconds = Math.max(5, plugin.shieldConfig().integer("anti-cheat.score-decay-seconds", 90));
        warnAt = plugin.shieldConfig().integer("anti-cheat.actions.warn-at", 5);
        alertAt = plugin.shieldConfig().integer("anti-cheat.actions.alert-at", 10);
        kickAt = plugin.shieldConfig().integer("anti-cheat.actions.kick-at", 25);
        banAt = plugin.shieldConfig().integer("anti-cheat.actions.ban-at", 60);
    }

    private @NotNull PlayerData dataFor(@NotNull Player player) {
        return data.computeIfAbsent(player.getUniqueId(), uuid -> new PlayerData(player));
    }

    private boolean active(@NotNull Player player) {
        if (!enabled) return false;
        if (player.hasPermission(plugin.shieldConfig().bypassPermission())) return false;
        PlayerData pd = dataFor(player);
        return System.currentTimeMillis() - pd.joinedAtMs() >= warmupSeconds * 1000L;
    }

    // --- Events ------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        data.put(event.getPlayer().getUniqueId(), new PlayerData(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        data.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (!active(event.getPlayer())) return;
        Player p = event.getPlayer();
        PlayerData pd = dataFor(p);
        if (p.isOnGround()) {
            pd.groundTicks().incrementAndGet();
            pd.airTicks().set(0);
        } else {
            pd.airTicks().incrementAndGet();
            pd.groundTicks().set(0);
        }
        dispatch(p, pd, ctx -> {
            for (Check c : checks) c.onMove(ctx, event);
        });
        pd.setLastLocation(event.getTo());
        pd.setLastMoveMs(System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) return;
        if (!active(p)) return;
        PlayerData pd = dataFor(p);
        dispatch(p, pd, ctx -> {
            for (Check c : checks) c.onAttack(ctx, event);
        });
        pd.setLastDamageMs(System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!active(p)) return;
        PlayerData pd = dataFor(p);
        dispatch(p, pd, ctx -> {
            for (Check c : checks) c.onTakeDamage(ctx, event);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!active(event.getPlayer())) return;
        Player p = event.getPlayer();
        PlayerData pd = dataFor(p);
        dispatch(p, pd, ctx -> {
            for (Check c : checks) c.onInteract(ctx, event);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!active(event.getPlayer())) return;
        Player p = event.getPlayer();
        PlayerData pd = dataFor(p);
        dispatch(p, pd, ctx -> {
            for (Check c : checks) c.onBlockPlace(ctx, event);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        if (!active(event.getPlayer())) return;
        Player p = event.getPlayer();
        PlayerData pd = dataFor(p);
        long now = System.currentTimeMillis();
        pd.recordVelocity(now,
                event.getVelocity().getX(),
                event.getVelocity().getY(),
                event.getVelocity().getZ());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!active(p)) continue;
            PlayerData pd = dataFor(p);
            CheckContext ctx = new CheckContext(plugin, this, p, pd, now);
            timerCheck.tick(ctx);
        }
    }

    private void decay() {
        long perSecondDecay = 1; // 1 point/sec — combined with decaySeconds for the smoothing factor.
        // Convert "decay over N seconds" into a flat per-second deduction so the score visibly trends down.
        long amount = Math.max(1L, perSecondDecay * (60L / decaySeconds));
        for (PlayerData pd : data.values()) pd.decay(amount);
    }

    private void dispatch(@NotNull Player player, @NotNull PlayerData pd, @NotNull java.util.function.Consumer<CheckContext> body) {
        long now = System.currentTimeMillis();
        body.accept(new CheckContext(plugin, this, player, pd, now));
        respond(player, pd);
    }

    public void flag(@NotNull Player player, @NotNull String check, @NotNull String detail) {
        PlayerData pd = dataFor(player);
        int weight = plugin.shieldConfig().integer("anti-cheat.weights." + check.toLowerCase(), 2);
        long total = pd.addScore(check, weight);
        plugin.securityLogger().write(Severity.LOW, "AC-" + check.toUpperCase(),
                player.getName() + " :: " + detail + " (score=" + total + ", +" + weight + ")");
        respond(player, pd);
    }

    private void respond(@NotNull Player player, @NotNull PlayerData pd) {
        long total = pd.totalScore();
        if (total >= banAt) {
            plugin.alerts().dispatch(Severity.CRITICAL, "AntiCheat ban",
                    player.getName() + " reached score " + total);
            if (!plugin.shieldConfig().observeOnly()) {
                Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer("§c[Shield] §fПодозрение в читах. Доступ временно ограничен."));
                plugin.antiDDoS().banIp(player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown",
                        plugin.shieldConfig().integer("anti-ddos.auto-ban.duration-seconds", 600) * 1000L,
                        "anti-cheat auto-ban");
            }
            pd.decay(total);
        } else if (total >= kickAt) {
            plugin.alerts().dispatch(Severity.HIGH, "AntiCheat kick",
                    player.getName() + " reached score " + total);
            if (!plugin.shieldConfig().observeOnly()) {
                Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer("§c[Shield] §fПодозрительное поведение."));
            }
            pd.decay(kickAt);
        } else if (total >= alertAt) {
            plugin.alerts().dispatch(Severity.MEDIUM, "AntiCheat alert",
                    player.getName() + " reached score " + total);
        } else if (total >= warnAt && plugin.shieldConfig().bool("anti-cheat.warn-message", true)) {
            // Soft, in-game only.
            plugin.securityLogger().write(Severity.LOW, "AC-WARN",
                    player.getName() + " soft-warn at score " + total);
        }
    }
}
