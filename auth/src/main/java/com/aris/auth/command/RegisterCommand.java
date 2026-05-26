package com.aris.auth.command;

import com.aris.auth.AuthPlugin;
import com.aris.auth.model.AuthUser;
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
 * {@code /register <password> <repeat>} - creates a new account and (depending
 * on configuration) immediately authenticates the player.
 */
public final class RegisterCommand implements CommandExecutor {

    private final AuthPlugin plugin;

    public RegisterCommand(@NotNull AuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-must-register")));
            return true;
        }
        if (plugin.auth().isAuthenticated(player)) {
            player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-already-authed")));
            return true;
        }
        plugin.userStore().find(player.getUniqueId()).whenComplete((existing, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to read user", error);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (existing != null) {
                    player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-already-registered")));
                    return;
                }
                String pass = args[0];
                String repeat = args[1];
                if (!pass.equals(repeat)) {
                    player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-register-mismatch")));
                    plugin.hud().playFailureSound(player);
                    return;
                }
                if (pass.length() < plugin.config().passwordMinLength) {
                    player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-register-too-short")
                            .replace("%min%", Integer.toString(plugin.config().passwordMinLength))));
                    return;
                }
                if (pass.length() > plugin.config().passwordMaxLength) {
                    player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-register-too-long")
                            .replace("%max%", Integer.toString(plugin.config().passwordMaxLength))));
                    return;
                }
                // Hashing is CPU-bound so we hash on the IO executor.
                java.util.concurrent.CompletableFuture.supplyAsync(() -> plugin.hasher().hash(pass))
                        .thenAccept(hash -> {
                            AuthUser user = new AuthUser(player.getUniqueId(), player.getName(), hash,
                                    AuthManager.ipOf(player), System.currentTimeMillis(), System.currentTimeMillis());
                            plugin.userStore().save(user).whenComplete((unused, err2) -> {
                                if (err2 != null) {
                                    plugin.getLogger().log(Level.SEVERE, "Failed to save user", err2);
                                    return;
                                }
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-register-success")));
                                    if (plugin.config().autoLoginOnRegister) {
                                        plugin.auth().authenticate(player, true);
                                    }
                                });
                            });
                        });
            });
        });
        return true;
    }

    private @NotNull String prefix() { return plugin.auth().prefix(); }
}
