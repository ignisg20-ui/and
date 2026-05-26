package com.aris.auth;

import com.aris.auth.command.AuthAdminCommand;
import com.aris.auth.command.ChangePasswordCommand;
import com.aris.auth.command.LoginCommand;
import com.aris.auth.command.RegisterCommand;
import com.aris.auth.config.AuthConfig;
import com.aris.auth.listener.AuthGuardListener;
import com.aris.auth.listener.ConnectionListener;
import com.aris.auth.listener.DamageImmunityListener;
import com.aris.auth.security.Bruteforce;
import com.aris.auth.security.PasswordHasher;
import com.aris.auth.session.AuthManager;
import com.aris.auth.session.SessionManager;
import com.aris.auth.storage.SqliteUserStore;
import com.aris.auth.storage.UserStore;
import com.aris.auth.storage.YamlUserStore;
import com.aris.auth.ui.AuthHud;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Plugin entry point. Wires together every component and registers commands.
 */
public final class AuthPlugin extends JavaPlugin {

    private static AuthPlugin instance;

    private AuthConfig config;
    private UserStore userStore;
    private SessionManager sessions;
    private AuthManager auth;
    private AuthHud hud;
    private Bruteforce bruteforce;
    private PasswordHasher hasher;

    public static @NotNull AuthPlugin get() {
        return Objects.requireNonNull(instance, "ArisAuth accessed before enable.");
    }

    @Override
    public void onEnable() {
        instance = this;
        try {
            this.config = new AuthConfig(this);
            this.hasher = new PasswordHasher(config.bcryptRounds);
            this.bruteforce = new Bruteforce(this);
            this.sessions = new SessionManager(this);
            this.hud = new AuthHud(this);
            this.auth = new AuthManager(this);
            this.userStore = createUserStore();
            this.userStore.init();
        } catch (IOException | RuntimeException ex) {
            getLogger().log(Level.SEVERE, "Could not initialise ArisAuth - disabling.", ex);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        Bukkit.getPluginManager().registerEvents(new ConnectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AuthGuardListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DamageImmunityListener(this), this);

        bindCommand("register", new RegisterCommand(this));
        bindCommand("login", new LoginCommand(this));
        bindCommand("changepassword", new ChangePasswordCommand(this));
        bindCommand("auth", new AuthAdminCommand(this));

        // Periodic janitor for both bruteforce state and stale sessions.
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            bruteforce.purgeExpired();
            sessions.purgeExpired();
        }, 20L * 30L, 20L * 30L);

        getLogger().info("ArisAuth enabled. Storage=" + config.storageType
                + " sessionWindow=" + (config.sessionMs / 1000L) + "s");
    }

    @Override
    public void onDisable() {
        if (auth != null) auth.shutdown();
        if (userStore != null) userStore.close();
        instance = null;
    }

    public void reloadAll() {
        config.reload();
        hasher = new PasswordHasher(config.bcryptRounds);
    }

    private @NotNull UserStore createUserStore() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Cannot create plugin data folder: " + dataFolder);
        }
        return switch (config.storageType) {
            case "yaml" -> new YamlUserStore(this, new File(dataFolder, config.yamlFile));
            case "sqlite" -> new SqliteUserStore(this, new File(dataFolder, config.sqliteFile));
            default -> new SqliteUserStore(this, new File(dataFolder, config.sqliteFile));
        };
    }

    private void bindCommand(@NotNull String name, @NotNull Object handler) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command not defined in plugin.yml: " + name);
            return;
        }
        if (handler instanceof org.bukkit.command.TabExecutor te) {
            cmd.setExecutor(te);
            cmd.setTabCompleter(te);
        } else if (handler instanceof org.bukkit.command.CommandExecutor ce) {
            cmd.setExecutor(ce);
        }
    }

    public @NotNull AuthConfig config() { return config; }
    public @NotNull UserStore userStore() { return userStore; }
    public @NotNull SessionManager sessions() { return sessions; }
    public @NotNull AuthManager auth() { return auth; }
    public @NotNull AuthHud hud() { return hud; }
    public @NotNull Bruteforce bruteforce() { return bruteforce; }
    public @NotNull PasswordHasher hasher() { return hasher; }
}
