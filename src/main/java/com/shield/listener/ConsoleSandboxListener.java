package com.shield.listener;

import com.shield.ShieldPlugin;
import com.shield.log.Severity;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Filters console commands. When {@code anti-console.enabled} is on we:
 * <ul>
 *   <li>require an explicit "shield-confirm" flag before any dangerous command runs,</li>
 *   <li>strip leading slashes / namespace prefixes for safer matching,</li>
 *   <li>validate that the sender holds the right permission when the
 *       sandbox is enabled (relevant for command-blocks that proxy as console).</li>
 * </ul>
 */
public final class ConsoleSandboxListener implements Listener {

    private final ShieldPlugin plugin;
    private final Set<String> dangerousCommands = new HashSet<>();
    private volatile boolean enabled;
    private volatile boolean sandboxEnabled;
    private volatile boolean permissionValidation;

    public ConsoleSandboxListener(@NotNull ShieldPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        enabled = plugin.shieldConfig().bool("anti-console.enabled", true);
        sandboxEnabled = plugin.shieldConfig().bool("anti-console.sandbox-enabled", true);
        permissionValidation = plugin.shieldConfig().bool("anti-console.permission-validation", true);
        dangerousCommands.clear();
        for (String s : plugin.shieldConfig().stringList("anti-console.dangerous-commands")) {
            dangerousCommands.add(s.toLowerCase(Locale.ROOT));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (!enabled) return;
        String raw = event.getCommand().trim();
        String head = raw.startsWith("/") ? raw.substring(1) : raw;
        int space = head.indexOf(' ');
        String label = (space < 0 ? head : head.substring(0, space)).toLowerCase(Locale.ROOT);
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);

        if (sandboxEnabled && dangerousCommands.contains(label)) {
            if (!raw.contains("--shield-confirm")) {
                plugin.alerts().dispatch(Severity.HIGH, "Console sandbox",
                        event.getSender().getName() + " tried " + label + " without --shield-confirm");
                event.setCancelled(true);
                event.getSender().sendMessage("§c[Shield] §fDangerous command. Re-run with --shield-confirm.");
                return;
            }
            event.setCommand(raw.replace("--shield-confirm", "").trim());
            plugin.securityLogger().write(Severity.HIGH, "CONSOLE-EXEC",
                    event.getSender().getName() + " :: " + event.getCommand());
        }

        if (permissionValidation && !(event.getSender() instanceof ConsoleCommandSender)) {
            if (!event.getSender().hasPermission("shield.admin")) {
                plugin.alerts().dispatch(Severity.HIGH, "Console proxy",
                        event.getSender().getName() + " attempted privileged command via proxy: " + raw);
                event.setCancelled(true);
            }
        }
    }
}
