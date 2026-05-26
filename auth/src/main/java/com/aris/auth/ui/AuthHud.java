package com.aris.auth.ui;

import com.aris.auth.AuthPlugin;
import com.aris.auth.config.AuthConfig;
import com.aris.auth.session.AuthState;
import com.aris.auth.util.Texts;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns every UI affordance the player sees while authenticating:
 * <ul>
 *   <li>Title screen on join (with fade-in / stay / fade-out timings)</li>
 *   <li>BossBar that counts down from {@code login-timeout-seconds}</li>
 *   <li>ActionBar reminder that loops every two seconds</li>
 *   <li>Portal particle ring around the player</li>
 *   <li>Sound feedback on prompt, success, failure and per-keystroke</li>
 * </ul>
 */
public final class AuthHud {

    private final AuthPlugin plugin;
    private final ConcurrentHashMap<UUID, BukkitTask> particleTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BukkitTask> actionbarTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BukkitTask> timeoutTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BukkitTask> bossbarTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BukkitTask> titleTasks = new ConcurrentHashMap<>();

    public AuthHud(@NotNull AuthPlugin plugin) {
        this.plugin = plugin;
    }

    public void showTitleFor(@NotNull Player player, @NotNull AuthState state) {
        AuthConfig c = plugin.config();
        if (!c.titleEnabled) return;
        String main = Texts.colorise(c.message(state.registered() ? "login-title-main" : "register-title-main"));
        String sub = Texts.colorise(c.message(state.registered() ? "login-title-sub" : "register-title-sub"));
        // Initial title uses the configured fade-in for a smooth intro.
        player.sendTitle(main, sub, c.titleFadeIn, 100, 10);
        // Repeating refresh keeps the title pinned for the entire auth window.
        // Mojang's vanilla titles fade out after `stay`; re-sending with a long
        // stay value and fade-in=0 makes the prompt look permanent.
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state.authenticated() || !player.isOnline()) return;
            player.sendTitle(main, sub, 0, 100, 10);
        }, 80L, 80L);
        titleTasks.put(player.getUniqueId(), task);
    }

    public void applyBlindness(@NotNull Player player) {
        AuthConfig c = plugin.config();
        if (!c.blindnessEnabled) return;
        // Integer.MAX_VALUE / 20 = ~3.4 years. Ambient + no particles + no icon
        // so the HUD stays minimal.
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS,
                Integer.MAX_VALUE,
                c.blindnessAmplifier,
                false, false, false));
    }

    public void clearBlindness(@NotNull Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    public void attachBossBar(@NotNull Player player, @NotNull AuthState state) {
        AuthConfig c = plugin.config();
        if (!c.bossbarEnabled) return;
        BossBar bar = Bukkit.createBossBar(barText(c, state.remainingSeconds().get()), c.bossbarColor, c.bossbarStyle, BarFlag.PLAY_BOSS_MUSIC);
        bar.setProgress(1.0);
        bar.addPlayer(player);
        state.setBossbar(bar);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state.authenticated() || !player.isOnline()) return;
            int seconds = state.remainingSeconds().get();
            double progress = Math.max(0.0, Math.min(1.0,
                    (double) seconds / (double) plugin.config().loginTimeoutSeconds));
            bar.setProgress(progress);
            bar.setTitle(barText(c, seconds));
        }, 20L, 20L);
        bossbarTasks.put(player.getUniqueId(), task);
    }

    public void startActionBarLoop(@NotNull Player player, @NotNull AuthState state) {
        AuthConfig c = plugin.config();
        if (!c.actionbarEnabled) return;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state.authenticated() || !player.isOnline()) return;
            String key = state.registered() ? "actionbar-reminder-login" : "actionbar-reminder-register";
            String msg = Texts.colorise(c.message(key));
            try {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
            } catch (Throwable ignored) {
                player.sendMessage(msg);
            }
        }, 10L, c.actionbarIntervalTicks);
        actionbarTasks.put(player.getUniqueId(), task);
    }

    public void startWaitingParticles(@NotNull Player player, @NotNull AuthState state) {
        AuthConfig c = plugin.config();
        if (!c.particlesEnabled || !c.particlePortalCircle || c.waitingParticle == null) return;
        Particle particle = c.waitingParticle;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            double angle = 0;
            @Override
            public void run() {
                if (state.authenticated() || !player.isOnline()) return;
                Location origin = player.getLocation();
                double radius = 1.2;
                for (int i = 0; i < 4; i++) {
                    double theta = angle + i * Math.PI / 2;
                    double x = origin.getX() + radius * Math.cos(theta);
                    double z = origin.getZ() + radius * Math.sin(theta);
                    player.getWorld().spawnParticle(particle, x, origin.getY() + 1.0, z, 1, 0, 0, 0, 0);
                }
                angle += Math.PI / 16;
            }
        }, 5L, 4L);
        particleTasks.put(player.getUniqueId(), task);
    }

    public void scheduleTimeout(@NotNull Player player, @NotNull AuthState state) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state.authenticated() || !player.isOnline()) return;
            int left = state.remainingSeconds().decrementAndGet();
            if (left <= 0) {
                String reason = Texts.colorise(plugin.config().message("chat-kick-timeout"));
                player.kickPlayer(reason);
            }
        }, 20L, 20L);
        timeoutTasks.put(player.getUniqueId(), task);
    }

    public void playPromptSound(@NotNull Player player, boolean registered) {
        AuthConfig c = plugin.config();
        if (!c.soundsEnabled) return;
        AuthConfig.SoundEntry sound = registered ? c.loginPromptSound : c.registerPromptSound;
        if (sound != null) player.playSound(player.getLocation(), sound.sound(), sound.volume(), sound.pitch());
    }

    public void playSuccess(@NotNull Player player) {
        AuthConfig c = plugin.config();
        if (c.soundsEnabled && c.successSound != null) {
            player.playSound(player.getLocation(), c.successSound.sound(), c.successSound.volume(), c.successSound.pitch());
        }
        if (c.particlesEnabled && c.particleWelcomeBurst && c.successParticle != null) {
            player.getWorld().spawnParticle(c.successParticle, player.getLocation().add(0, 1.0, 0),
                    c.successParticleCount, 0.6, 0.8, 0.6, 0.05);
        }
        if (c.titleEnabled) {
            String main = Texts.colorise(c.message("welcome-title-main"));
            String sub = Texts.colorise(c.message("welcome-title-sub"));
            player.sendTitle(main, sub, c.titleFadeIn, c.titleStay, c.titleFadeOut);
        }
    }

    public void playFailureSound(@NotNull Player player) {
        AuthConfig c = plugin.config();
        if (!c.soundsEnabled || c.failureSound == null) return;
        player.playSound(player.getLocation(), c.failureSound.sound(), c.failureSound.volume(), c.failureSound.pitch());
    }

    public void playTickSound(@NotNull Player player) {
        AuthConfig c = plugin.config();
        if (!c.soundsEnabled || c.tickSound == null) return;
        player.playSound(player.getLocation(), c.tickSound.sound(), c.tickSound.volume(), c.tickSound.pitch());
    }

    public void teardown(@NotNull Player player, @NotNull AuthState state) {
        if (state.cleanedUp()) return;
        state.markCleanedUp();
        UUID id = player.getUniqueId();
        cancel(particleTasks.remove(id));
        cancel(actionbarTasks.remove(id));
        cancel(timeoutTasks.remove(id));
        cancel(bossbarTasks.remove(id));
        cancel(titleTasks.remove(id));
        if (state.bossbar() instanceof BossBar bar) {
            bar.removeAll();
        }
        // Clear any leftover title and blindness instantly when the player
        // transitions out of the auth state (whether authenticated or quit).
        if (player.isOnline()) {
            player.resetTitle();
            clearBlindness(player);
        }
    }

    private static void cancel(BukkitTask task) {
        if (task != null && !task.isCancelled()) task.cancel();
    }

    private static @NotNull String barText(@NotNull AuthConfig c, int secondsLeft) {
        return Texts.colorise(c.message("bossbar-text").replace("%seconds%", Integer.toString(secondsLeft)));
    }
}
