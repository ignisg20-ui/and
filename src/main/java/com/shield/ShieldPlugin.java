package com.shield;

import com.shield.anticheat.AntiCheatEngine;
import com.shield.anticrash.AntiCrashModule;
import com.shield.antibot.AntiBotModule;
import com.shield.antiddos.AntiDDoSModule;
import com.shield.antiexploit.AntiExploitModule;
import com.shield.antiop.AntiOpModule;
import com.shield.antixray.AntiXrayModule;
import com.shield.command.ShieldCommand;
import com.shield.config.ShieldConfig;
import com.shield.listener.ConsoleSandboxListener;
import com.shield.log.SecurityLogger;
import com.shield.packet.PacketInterceptor;
import com.shield.quarantine.QuarantineManager;
import com.shield.webhook.AlertDispatcher;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Entry point for the Shield security suite.
 *
 * <p>The plugin is organised into independently-configurable modules. Each
 * module owns its own state and listeners; the main class is responsible only
 * for wiring them together, propagating configuration reloads and providing a
 * single shared access point for the rest of the codebase.</p>
 */
public final class ShieldPlugin extends JavaPlugin {

    private static ShieldPlugin instance;

    private ShieldConfig config;
    private SecurityLogger securityLogger;
    private AlertDispatcher alertDispatcher;
    private QuarantineManager quarantineManager;

    private AntiDDoSModule antiDDoS;
    private AntiBotModule antiBot;
    private AntiExploitModule antiExploit;
    private AntiCheatEngine antiCheat;
    private AntiXrayModule antiXray;
    private AntiCrashModule antiCrash;
    private AntiOpModule antiOp;
    private PacketInterceptor packetInterceptor;
    private ConsoleSandboxListener consoleSandbox;

    public static @NotNull ShieldPlugin get() {
        return Objects.requireNonNull(instance, "Shield plugin accessed before enable");
    }

    @Override
    public void onLoad() {
        instance = this;
        saveDefaultConfig();
        this.config = new ShieldConfig(this);
        this.config.load();
    }

    @Override
    public void onEnable() {
        this.securityLogger = new SecurityLogger(this);
        this.alertDispatcher = new AlertDispatcher(this);
        this.quarantineManager = new QuarantineManager(this);

        this.antiDDoS = new AntiDDoSModule(this);
        this.antiBot = new AntiBotModule(this);
        this.antiExploit = new AntiExploitModule(this);
        this.antiCheat = new AntiCheatEngine(this);
        this.antiXray = new AntiXrayModule(this);
        this.antiCrash = new AntiCrashModule(this);
        this.antiOp = new AntiOpModule(this);
        this.packetInterceptor = new PacketInterceptor(this);
        this.consoleSandbox = new ConsoleSandboxListener(this);

        // Wire modules into the Bukkit event bus.
        Bukkit.getPluginManager().registerEvents(quarantineManager, this);
        Bukkit.getPluginManager().registerEvents(consoleSandbox, this);
        antiDDoS.start();
        antiBot.start();
        antiExploit.start();
        antiCheat.start();
        antiXray.start();
        antiCrash.start();
        antiOp.start();
        packetInterceptor.start();

        PluginCommand command = getCommand("shield");
        if (command != null) {
            ShieldCommand handler = new ShieldCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        securityLogger.info("Shield enabled (v" + getDescription().getVersion() + ").");
        alertDispatcher.dispatch(com.shield.log.Severity.INFO,
                "Shield online", "All modules initialised.");
    }

    @Override
    public void onDisable() {
        if (packetInterceptor != null) packetInterceptor.stop();
        if (antiOp != null) antiOp.stop();
        if (antiCrash != null) antiCrash.stop();
        if (antiXray != null) antiXray.stop();
        if (antiCheat != null) antiCheat.stop();
        if (antiExploit != null) antiExploit.stop();
        if (antiBot != null) antiBot.stop();
        if (antiDDoS != null) antiDDoS.stop();

        if (securityLogger != null) {
            securityLogger.info("Shield disabled.");
            securityLogger.close();
        }
    }

    /** Reload configuration and propagate it to every active module. */
    public void reloadShield() {
        reloadConfig();
        config.load();
        securityLogger.reload();
        alertDispatcher.reload();
        antiDDoS.reload();
        antiBot.reload();
        antiExploit.reload();
        antiCheat.reload();
        antiXray.reload();
        antiCrash.reload();
        antiOp.reload();
        packetInterceptor.reload();
        quarantineManager.reload();
        if (consoleSandbox != null) consoleSandbox.reload();
        securityLogger.info("Configuration reloaded.");
    }

    public @NotNull ShieldConfig shieldConfig() {
        return config;
    }

    public @NotNull SecurityLogger securityLogger() {
        return securityLogger;
    }

    public @NotNull AlertDispatcher alerts() {
        return alertDispatcher;
    }

    public @NotNull QuarantineManager quarantine() {
        return quarantineManager;
    }

    public @NotNull AntiDDoSModule antiDDoS() {
        return antiDDoS;
    }

    public @NotNull AntiBotModule antiBot() {
        return antiBot;
    }

    public @NotNull AntiExploitModule antiExploit() {
        return antiExploit;
    }

    public @NotNull AntiCheatEngine antiCheat() {
        return antiCheat;
    }

    public @NotNull AntiXrayModule antiXray() {
        return antiXray;
    }

    public @NotNull AntiCrashModule antiCrash() {
        return antiCrash;
    }

    public @NotNull AntiOpModule antiOp() {
        return antiOp;
    }

    public @NotNull PacketInterceptor packets() {
        return packetInterceptor;
    }
}
