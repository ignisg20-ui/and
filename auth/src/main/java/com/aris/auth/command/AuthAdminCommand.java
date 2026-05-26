package com.aris.auth.command;

import com.aris.auth.AuthPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /auth <reload|status|session>} - administrator command.
 */
public final class AuthAdminCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("reload", "status", "session");

    private final AuthPlugin plugin;

    public AuthAdminCommand(@NotNull AuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("arisauth.admin")) {
            sender.sendMessage("§cInsufficient permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§6/auth §f<" + String.join("|", SUBS) + ">");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage("§aArisAuth configuration reloaded.");
            }
            case "status" -> {
                sender.sendMessage("§6==== ArisAuth Status ====");
                sender.sendMessage("§7Online players:  §f" + Bukkit.getOnlinePlayers().size());
                sender.sendMessage("§7Active states:   §f" + plugin.auth().activeStates());
                sender.sendMessage("§7Cached sessions: §f" + plugin.sessions().size());
                sender.sendMessage("§7Storage backend: §f" + plugin.config().storageType);
                sender.sendMessage("§7Session window:  §f" + plugin.config().sessionMs / 1000L + "s");
                sender.sendMessage("§7Login timeout:   §f" + plugin.config().loginTimeoutSeconds + "s");
            }
            case "session" -> {
                if (args.length < 2) {
                    sender.sendMessage("§e/auth session <player>");
                    return true;
                }
                Player p = Bukkit.getPlayerExact(args[1]);
                if (p == null) {
                    sender.sendMessage("§cPlayer not online.");
                    return true;
                }
                var s = plugin.sessions().get(p.getUniqueId());
                if (s == null) {
                    sender.sendMessage("§7No active session for " + p.getName());
                } else {
                    long age = (System.currentTimeMillis() - s.lastSeenMs) / 1000L;
                    sender.sendMessage("§7" + p.getName() + " session ip=" + s.ip + " age=" + age + "s");
                }
            }
            default -> sender.sendMessage("§eUnknown sub-command.");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(SUBS.size());
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase())) out.add(s);
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("session")) {
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
            return out;
        }
        return List.of();
    }
}
