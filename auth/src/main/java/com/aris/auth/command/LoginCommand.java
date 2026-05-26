package com.aris.auth.command;

import com.aris.auth.AuthPlugin;
import com.aris.auth.session.AuthManager;
import com.aris.auth.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * {@code /login <password>} - verifies the password and clears the auth
 * freeze. Wrong passwords are rate-limited per-IP via
 * {@link com.aris.auth.security.Bruteforce}.
 */
public final class LoginCommand implements CommandExecutor {

    private final AuthPlugin plugin;

    public LoginCommand(@NotNull AuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (plugin.auth().isAuthenticated(player)) {
            player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-already-authed")));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-must-login")));
            return true;
        }
        String pass = args[0];
        plugin.userStore().find(player.getUniqueId()).whenComplete((user, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to read user", error);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (user == null) {
                    player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-must-register")));
                    plugin.hud().playFailureSound(player);
                    return;
                }
                String ip = AuthManager.ipOf(player);
                if (plugin.bruteforce().isLocked(ip)) {
                    long minutes = (plugin.bruteforce().lockedRemainingMs(ip) + 59_999L) / 60_000L;
                    String reason = Texts.colorise(plugin.config().message("chat-kick-locked")
                            .replace("%minutes%", Long.toString(minutes)));
                    player.kickPlayer(reason);
                    return;
                }
                // Verify on a worker because bcrypt is CPU-heavy.
                java.util.concurrent.CompletableFuture.supplyAsync(() -> plugin.hasher().verify(pass, user.passwordHash()))
                        .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!ok) {
                                plugin.bruteforce().recordFailure(ip, player.getName());
                                int left = plugin.bruteforce().remainingAttempts(ip);
                                if (left <= 0) {
                                    long minutes = plugin.config().bruteForceLockoutMs / 60_000L;
                                    String reason = Texts.colorise(plugin.config().message("chat-kick-locked")
                                            .replace("%minutes%", Long.toString(minutes)));
                                    player.kickPlayer(reason);
                                    return;
                                }
                                player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-login-wrong")
                                        .replace("%left%", Integer.toString(left))));
                                plugin.hud().playFailureSound(player);
                                return;
                            }
                            plugin.bruteforce().recordSuccess(ip);
                            plugin.userStore().save(user.withLogin(ip, System.currentTimeMillis()));
                            player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-login-success")));
                            plugin.auth().authenticate(player, true);
                        }));
            });
        });
        return true;
    }

    private @NotNull String prefix() { return plugin.auth().prefix(); }
}
