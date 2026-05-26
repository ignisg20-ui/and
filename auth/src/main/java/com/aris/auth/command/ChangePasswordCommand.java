package com.aris.auth.command;

import com.aris.auth.AuthPlugin;
import com.aris.auth.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * {@code /changepassword <old> <new>} - changes a registered account's
 * password. Only available to already-authenticated players.
 */
public final class ChangePasswordCommand implements CommandExecutor {

    private final AuthPlugin plugin;

    public ChangePasswordCommand(@NotNull AuthPlugin plugin) {
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
            player.sendMessage(prefix() + "§e/changepassword <старый> <новый>");
            return true;
        }
        if (!plugin.auth().isAuthenticated(player)) {
            player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-must-login")));
            return true;
        }
        String oldPass = args[0];
        String newPass = args[1];
        if (newPass.length() < plugin.config().passwordMinLength) {
            player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-register-too-short")
                    .replace("%min%", Integer.toString(plugin.config().passwordMinLength))));
            return true;
        }
        if (newPass.length() > plugin.config().passwordMaxLength) {
            player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-register-too-long")
                    .replace("%max%", Integer.toString(plugin.config().passwordMaxLength))));
            return true;
        }
        plugin.userStore().find(player.getUniqueId()).whenComplete((user, error) -> {
            if (error != null || user == null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to read user", error);
                return;
            }
            CompletableFuture.supplyAsync(() -> plugin.hasher().verify(oldPass, user.passwordHash()))
                    .thenAccept(ok -> {
                        if (!ok) {
                            Bukkit.getScheduler().runTask(plugin, () ->
                                    player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-changepass-wrong-old"))));
                            return;
                        }
                        String newHash = plugin.hasher().hash(newPass);
                        plugin.userStore().save(user.withHash(newHash)).whenComplete((unused, err2) ->
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-changepass-success")));
                                    plugin.hud().playSuccess(player);
                                }));
                    });
        });
        return true;
    }

    private @NotNull String prefix() { return plugin.auth().prefix(); }
}
