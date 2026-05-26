package com.shield.config;

import com.shield.ShieldPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Thin, type-safe wrapper around the plugin's {@link FileConfiguration}.
 *
 * <p>All getters return defensive copies for collection types and never null.
 * Modules are expected to call {@link #load()} from a single thread; reads
 * are otherwise safe to perform concurrently.</p>
 */
public final class ShieldConfig {

    private final ShieldPlugin plugin;
    private volatile FileConfiguration root;

    public ShieldConfig(@NotNull ShieldPlugin plugin) {
        this.plugin = plugin;
        this.root = plugin.getConfig();
    }

    public void load() {
        plugin.reloadConfig();
        this.root = plugin.getConfig();
    }

    public @NotNull FileConfiguration root() {
        return root;
    }

    public @NotNull ConfigurationSection section(@NotNull String path) {
        ConfigurationSection s = root.getConfigurationSection(path);
        if (s != null) return s;
        return root.createSection(path);
    }

    public boolean bool(@NotNull String path, boolean def) {
        return root.getBoolean(path, def);
    }

    public int integer(@NotNull String path, int def) {
        return root.getInt(path, def);
    }

    public long longValue(@NotNull String path, long def) {
        return root.getLong(path, def);
    }

    public double real(@NotNull String path, double def) {
        return root.getDouble(path, def);
    }

    public @NotNull String string(@NotNull String path, @NotNull String def) {
        String v = root.getString(path, def);
        return v == null ? def : v;
    }

    public @NotNull List<String> stringList(@NotNull String path) {
        List<String> list = root.getStringList(path);
        return list == null ? Collections.emptyList() : list;
    }

    public boolean observeOnly() {
        return bool("general.observe-only", false);
    }

    public @NotNull String bypassPermission() {
        return string("general.bypass-permission", "shield.bypass");
    }

    public int housekeepingIntervalTicks() {
        return Math.max(20, integer("general.housekeeping-interval-ticks", 100));
    }

    public long alertDedupWindowMs() {
        return Math.max(0L, longValue("general.alert-dedup-window-ms", 5000));
    }
}
