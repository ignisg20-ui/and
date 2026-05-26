package com.shield.anticheat.checks;

import com.shield.ShieldPlugin;
import com.shield.anticheat.AntiCheatEngine;
import com.shield.anticheat.PlayerData;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Plain context object passed to every individual check on each event so
 * checks can stay free of state and field plumbing.
 */
public final class CheckContext {

    private final ShieldPlugin plugin;
    private final AntiCheatEngine engine;
    private final Player player;
    private final PlayerData data;
    private final long nowMs;

    public CheckContext(@NotNull ShieldPlugin plugin, @NotNull AntiCheatEngine engine,
                        @NotNull Player player, @NotNull PlayerData data, long nowMs) {
        this.plugin = plugin;
        this.engine = engine;
        this.player = player;
        this.data = data;
        this.nowMs = nowMs;
    }

    public @NotNull ShieldPlugin plugin() {
        return plugin;
    }

    public @NotNull AntiCheatEngine engine() {
        return engine;
    }

    public @NotNull Player player() {
        return player;
    }

    public @NotNull PlayerData data() {
        return data;
    }

    public long nowMs() {
        return nowMs;
    }

    public boolean cfgBool(@NotNull String path, boolean def) {
        return plugin.shieldConfig().bool(path, def);
    }

    public int cfgInt(@NotNull String path, int def) {
        return plugin.shieldConfig().integer(path, def);
    }

    public double cfgDouble(@NotNull String path, double def) {
        return plugin.shieldConfig().real(path, def);
    }

    public void flag(@NotNull String check, @NotNull String detail) {
        engine.flag(player, check, detail);
    }
}
