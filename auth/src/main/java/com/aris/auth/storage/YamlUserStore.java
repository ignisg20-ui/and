package com.aris.auth.storage;

import com.aris.auth.AuthPlugin;
import com.aris.auth.model.AuthUser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * YAML-backed user store. Slower than SQLite but useful for tiny servers or
 * portable backups.
 */
public final class YamlUserStore implements UserStore {

    private final AuthPlugin plugin;
    private final File file;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ArisAuth-YAML");
        t.setDaemon(true);
        return t;
    });

    private volatile YamlConfiguration cfg;

    public YamlUserStore(@NotNull AuthPlugin plugin, @NotNull File file) {
        this.plugin = plugin;
        this.file = file;
    }

    @Override
    public void init() throws IOException {
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            throw new IOException("Cannot create directory " + file.getParentFile());
        }
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("Cannot create " + file);
        }
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public void close() {
        io.shutdown();
        try {
            io.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public @NotNull CompletableFuture<@Nullable AuthUser> find(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            ConfigurationSection section = cfg.getConfigurationSection("users." + uuid);
            if (section == null) return null;
            return new AuthUser(
                    uuid,
                    section.getString("name", uuid.toString()),
                    section.getString("password", ""),
                    section.getString("last-ip"),
                    section.getLong("last-login-ms"),
                    section.getLong("created-ms", System.currentTimeMillis()));
        }, io);
    }

    @Override
    public @NotNull CompletableFuture<Void> save(@NotNull AuthUser user) {
        return CompletableFuture.runAsync(() -> {
            String base = "users." + user.uuid();
            cfg.set(base + ".name", user.name());
            cfg.set(base + ".password", user.passwordHash());
            cfg.set(base + ".last-ip", user.lastIp());
            cfg.set(base + ".last-login-ms", user.lastLoginMs());
            cfg.set(base + ".created-ms", user.createdMs());
            try {
                cfg.save(file);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save YAML store", ex);
            }
        }, io);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> delete(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!cfg.contains("users." + uuid)) return false;
            cfg.set("users." + uuid, null);
            try {
                cfg.save(file);
                return true;
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save YAML store", ex);
                return false;
            }
        }, io);
    }
}
