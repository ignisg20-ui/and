package com.shield.command;

import com.shield.ShieldPlugin;
import com.shield.log.Severity;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implements the {@code /shield} command suite. Each sub-command is small
 * and self-contained so it's easy to wire additional verbs in.
 */
public final class ShieldCommand implements TabExecutor {

    private static final List<String> SUBS = List.of(
            "status", "banip", "unbanip", "whitelist", "scan", "quarantine", "release", "debug", "reload");

    private final ShieldPlugin plugin;

    public ShieldCommand(@NotNull ShieldPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("shield.admin")) {
            sender.sendMessage("§cYou do not have permission to use /shield.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§6/shield §f<" + String.join("|", SUBS) + ">");
            return true;
        }
        String sub = args[0].toLowerCase();
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        return switch (sub) {
            case "status" -> status(sender);
            case "banip" -> banIp(sender, rest);
            case "unbanip" -> unbanIp(sender, rest);
            case "whitelist" -> whitelist(sender, rest);
            case "scan" -> scan(sender);
            case "quarantine" -> quarantine(sender, rest);
            case "release" -> release(sender, rest);
            case "debug" -> debug(sender);
            case "reload" -> reload(sender);
            default -> {
                sender.sendMessage("§eUnknown sub-command. Try one of: §f" + String.join(", ", SUBS));
                yield true;
            }
        };
    }

    private boolean status(@NotNull CommandSender sender) {
        sender.sendMessage("§6==== Shield Status ====");
        sender.sendMessage("§7Online players: §f" + Bukkit.getOnlinePlayers().size());
        sender.sendMessage("§7Blocked IPs:    §f" + plugin.antiDDoS().blockedIpsSnapshot().size());
        sender.sendMessage("§7Quarantined:    §f" + plugin.quarantine().size());
        sender.sendMessage("§7Observe-only:   §f" + plugin.shieldConfig().observeOnly());
        sender.sendMessage("§7Anti-DDoS:      §f" + state("anti-ddos.enabled"));
        sender.sendMessage("§7Anti-Bot:       §f" + state("anti-bot.enabled"));
        sender.sendMessage("§7Anti-Exploit:   §f" + state("anti-exploit.enabled"));
        sender.sendMessage("§7Anti-Cheat:     §f" + state("anti-cheat.enabled"));
        sender.sendMessage("§7Anti-Xray:      §f" + state("anti-xray.enabled"));
        sender.sendMessage("§7Anti-Crash:     §f" + state("anti-crash.enabled"));
        sender.sendMessage("§7Anti-OP:        §f" + state("anti-op.enabled"));
        sender.sendMessage("§7Anti-Console:   §f" + state("anti-console.enabled"));
        return true;
    }

    private boolean banIp(@NotNull CommandSender sender, @NotNull String[] rest) {
        if (rest.length < 1) {
            sender.sendMessage("§eUsage: /shield banip <ip> [seconds] [reason...]");
            return true;
        }
        String ip = rest[0];
        long seconds = rest.length >= 2 ? safeLong(rest[1], 600L) : 600L;
        String reason = rest.length >= 3 ? String.join(" ", Arrays.copyOfRange(rest, 2, rest.length)) : "manual";
        plugin.antiDDoS().banIp(ip, seconds * 1000L, reason);
        plugin.alerts().dispatch(Severity.HIGH, "Manual IP ban",
                sender.getName() + " banned " + ip + " for " + seconds + "s :: " + reason);
        sender.sendMessage("§aBanned " + ip + " for " + seconds + "s.");
        return true;
    }

    private boolean unbanIp(@NotNull CommandSender sender, @NotNull String[] rest) {
        if (rest.length < 1) {
            sender.sendMessage("§eUsage: /shield unbanip <ip>");
            return true;
        }
        plugin.antiDDoS().unbanIp(rest[0]);
        sender.sendMessage("§aUnbanned " + rest[0] + ".");
        return true;
    }

    private boolean whitelist(@NotNull CommandSender sender, @NotNull String[] rest) {
        if (rest.length < 1) {
            sender.sendMessage("§eUsage: /shield whitelist <on|off|add|remove|list> [value]");
            return true;
        }
        String op = rest[0].toLowerCase();
        switch (op) {
            case "on" -> Bukkit.setWhitelist(true);
            case "off" -> Bukkit.setWhitelist(false);
            case "add" -> {
                if (rest.length < 2) {
                    sender.sendMessage("§eUsage: /shield whitelist add <name>");
                    return true;
                }
                Bukkit.getOfflinePlayer(rest[1]).setWhitelisted(true);
            }
            case "remove" -> {
                if (rest.length < 2) {
                    sender.sendMessage("§eUsage: /shield whitelist remove <name>");
                    return true;
                }
                Bukkit.getOfflinePlayer(rest[1]).setWhitelisted(false);
            }
            case "list" -> sender.sendMessage("§7Whitelisted: §f"
                    + Bukkit.getWhitelistedPlayers().stream()
                    .map(op2 -> op2.getName() == null ? op2.getUniqueId().toString() : op2.getName())
                    .collect(Collectors.joining(", ")));
            default -> sender.sendMessage("§eUnknown whitelist sub-command.");
        }
        return true;
    }

    private boolean scan(@NotNull CommandSender sender) {
        int suspect = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            // Tab-completes / inventory open are common sanity checks - here we just
            // surface the live counts so the admin can decide what to do.
            sender.sendMessage("§7- §f" + p.getName()
                    + " §8(§7" + p.getAddress() + "§8)"
                    + " ping=" + p.getPing()
                    + " op=" + p.isOp()
                    + " gm=" + p.getGameMode());
            if (p.getPing() < 5) suspect++;
        }
        sender.sendMessage("§6Scan complete. §7Sub-5ms ping players: §f" + suspect);
        return true;
    }

    private boolean quarantine(@NotNull CommandSender sender, @NotNull String[] rest) {
        if (rest.length < 1) {
            sender.sendMessage("§eUsage: /shield quarantine <player> [seconds] [reason...]");
            return true;
        }
        Player p = Bukkit.getPlayerExact(rest[0]);
        if (p == null) {
            sender.sendMessage("§cPlayer not online.");
            return true;
        }
        int seconds = rest.length >= 2 ? (int) safeLong(rest[1], 120L) : 120;
        String reason = rest.length >= 3 ? String.join(" ", Arrays.copyOfRange(rest, 2, rest.length)) : "admin command";
        plugin.quarantine().quarantine(p, reason, seconds);
        sender.sendMessage("§aQuarantined " + p.getName() + " for " + seconds + "s.");
        return true;
    }

    private boolean release(@NotNull CommandSender sender, @NotNull String[] rest) {
        if (rest.length < 1) {
            sender.sendMessage("§eUsage: /shield release <player>");
            return true;
        }
        Player p = Bukkit.getPlayerExact(rest[0]);
        UUID uuid = p == null ? null : p.getUniqueId();
        if (uuid == null) {
            try {
                uuid = UUID.fromString(rest[0]);
            } catch (IllegalArgumentException ex) {
                sender.sendMessage("§cUnknown player.");
                return true;
            }
        }
        plugin.quarantine().release(uuid);
        sender.sendMessage("§aReleased " + rest[0] + " from quarantine.");
        return true;
    }

    private boolean debug(@NotNull CommandSender sender) {
        sender.sendMessage("§6==== Shield Debug ====");
        sender.sendMessage("§7Plugin:        §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§7Bukkit:        §f" + Bukkit.getBukkitVersion());
        sender.sendMessage("§7Server impl:   §f" + Bukkit.getName() + " " + Bukkit.getVersion());
        sender.sendMessage("§7TPS:           §f" + Arrays.toString(Bukkit.getTPS()));
        sender.sendMessage("§7Plugins:       §f" + Bukkit.getPluginManager().getPlugins().length);
        sender.sendMessage("§7Worlds:        §f" + Bukkit.getWorlds().size());
        sender.sendMessage("§7Free mem MB:   §f" + Runtime.getRuntime().freeMemory() / 1024 / 1024);
        sender.sendMessage("§7Max mem MB:    §f" + Runtime.getRuntime().maxMemory() / 1024 / 1024);
        return true;
    }

    private boolean reload(@NotNull CommandSender sender) {
        plugin.reloadShield();
        sender.sendMessage("§aShield configuration reloaded.");
        return true;
    }

    private @NotNull String state(@NotNull String path) {
        return plugin.shieldConfig().bool(path, true) ? "§aenabled" : "§cdisabled";
    }

    private long safeLong(@NotNull String raw, long fallback) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return prefixFilter(SUBS, args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("quarantine")) {
            return prefixFilter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("whitelist")) {
            return prefixFilter(List.of("on", "off", "add", "remove", "list"), args[1]);
        }
        return List.of();
    }

    private @NotNull List<String> prefixFilter(@NotNull List<String> options, @NotNull String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>(options.size());
        for (String s : options) if (s.toLowerCase().startsWith(lower)) out.add(s);
        return out;
    }
}
