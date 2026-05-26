package com.aris.auth.config;

import com.aris.auth.AuthPlugin;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Thread-safe wrapper around the YAML configuration file. All values are
 * cached at {@link #reload()} time so individual getters don't pay a parsing
 * cost on every call.
 */
public final class AuthConfig {

    private final AuthPlugin plugin;

    // ---- general ------------------------------------------------------------
    public volatile long sessionMs;
    public volatile boolean autoLoginOnRegister;
    public volatile int loginTimeoutSeconds;

    // ---- password -----------------------------------------------------------
    public volatile int bcryptRounds;
    public volatile int passwordMinLength;
    public volatile int passwordMaxLength;

    // ---- brute force --------------------------------------------------------
    public volatile boolean bruteForceEnabled;
    public volatile int bruteForceMaxAttempts;
    public volatile long bruteForceLockoutMs;
    public volatile long bruteForceWindowMs;

    // ---- storage ------------------------------------------------------------
    public volatile String storageType;
    public volatile String sqliteFile;
    public volatile String yamlFile;

    // ---- UI -----------------------------------------------------------------
    public volatile boolean bossbarEnabled;
    public volatile BarColor bossbarColor;
    public volatile BarStyle bossbarStyle;
    public volatile boolean actionbarEnabled;
    public volatile int actionbarIntervalTicks;
    public volatile boolean titleEnabled;
    public volatile int titleFadeIn;
    public volatile int titleStay;
    public volatile int titleFadeOut;
    public volatile boolean particlesEnabled;
    public volatile boolean particlePortalCircle;
    public volatile boolean particleWelcomeBurst;
    public volatile boolean soundsEnabled;
    public volatile boolean blindnessEnabled;
    public volatile int blindnessAmplifier;

    // ---- particles ----------------------------------------------------------
    public volatile @Nullable Particle waitingParticle;
    public volatile int waitingParticleCount;
    public volatile @Nullable Particle successParticle;
    public volatile int successParticleCount;

    // ---- sounds -------------------------------------------------------------
    public volatile @Nullable SoundEntry loginPromptSound;
    public volatile @Nullable SoundEntry registerPromptSound;
    public volatile @Nullable SoundEntry successSound;
    public volatile @Nullable SoundEntry failureSound;
    public volatile @Nullable SoundEntry tickSound;

    public AuthConfig(@NotNull AuthPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        var c = plugin.getConfig();

        sessionMs = Math.max(0L, c.getLong("general.session-seconds", 300)) * 1000L;
        autoLoginOnRegister = c.getBoolean("general.auto-login-on-register", true);
        loginTimeoutSeconds = Math.max(5, c.getInt("general.login-timeout-seconds", 60));

        bcryptRounds = clamp(c.getInt("password.bcrypt-rounds", 10), 4, 14);
        passwordMinLength = Math.max(1, c.getInt("password.min-length", 5));
        passwordMaxLength = Math.max(passwordMinLength, c.getInt("password.max-length", 64));

        bruteForceEnabled = c.getBoolean("brute-force.enabled", true);
        bruteForceMaxAttempts = Math.max(1, c.getInt("brute-force.max-attempts", 5));
        bruteForceLockoutMs = Math.max(1L, c.getLong("brute-force.lockout-seconds", 600)) * 1000L;
        bruteForceWindowMs = Math.max(1L, c.getLong("brute-force.attempt-window-seconds", 600)) * 1000L;

        storageType = c.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT);
        sqliteFile = c.getString("storage.sqlite.file", "users.db");
        yamlFile = c.getString("storage.yaml.file", "users.yml");

        bossbarEnabled = c.getBoolean("ui.bossbar.enabled", true);
        bossbarColor = parseEnum(BarColor.class, c.getString("ui.bossbar.color", "YELLOW"), BarColor.YELLOW);
        bossbarStyle = parseEnum(BarStyle.class, c.getString("ui.bossbar.style", "SEGMENTED_10"), BarStyle.SEGMENTED_10);
        actionbarEnabled = c.getBoolean("ui.actionbar.enabled", true);
        actionbarIntervalTicks = Math.max(10, c.getInt("ui.actionbar.interval-ticks", 40));
        titleEnabled = c.getBoolean("ui.title.enabled", true);
        titleFadeIn = c.getInt("ui.title.fade-in", 10);
        titleStay = c.getInt("ui.title.stay", 50);
        titleFadeOut = c.getInt("ui.title.fade-out", 10);

        particlesEnabled = c.getBoolean("ui.effects.particles.enabled", true);
        particlePortalCircle = c.getBoolean("ui.effects.particles.portal-circle", true);
        particleWelcomeBurst = c.getBoolean("ui.effects.particles.welcome-burst", true);
        soundsEnabled = c.getBoolean("ui.effects.sounds.enabled", true);
        blindnessEnabled = c.getBoolean("ui.effects.blindness.enabled", true);
        blindnessAmplifier = Math.max(0, c.getInt("ui.effects.blindness.amplifier", 0));

        waitingParticle = parseParticle(c.getString("particles.waiting.name", "PORTAL"));
        waitingParticleCount = Math.max(1, c.getInt("particles.waiting.count", 30));
        successParticle = parseParticle(c.getString("particles.success.name", "TOTEM_OF_UNDYING"));
        successParticleCount = Math.max(1, c.getInt("particles.success.count", 80));

        loginPromptSound = readSound(c.getConfigurationSection("sounds.login-prompt"));
        registerPromptSound = readSound(c.getConfigurationSection("sounds.register-prompt"));
        successSound = readSound(c.getConfigurationSection("sounds.success"));
        failureSound = readSound(c.getConfigurationSection("sounds.failure"));
        tickSound = readSound(c.getConfigurationSection("sounds.tick"));
    }

    public @NotNull String message(@NotNull String key) {
        return plugin.getConfig().getString("messages." + key, "");
    }

    private static @Nullable SoundEntry readSound(@Nullable ConfigurationSection section) {
        if (section == null) return null;
        String name = section.getString("name", "").trim();
        if (name.isEmpty()) return null;
        Sound sound;
        try {
            sound = Sound.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return new SoundEntry(sound,
                (float) section.getDouble("volume", 1.0),
                (float) section.getDouble("pitch", 1.0));
    }

    private static @Nullable Particle parseParticle(@Nullable String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static <T extends Enum<T>> T parseEnum(@NotNull Class<T> type, @Nullable String name, T fallback) {
        if (name == null) return fallback;
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public record SoundEntry(@NotNull Sound sound, float volume, float pitch) { }
}
