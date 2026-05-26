package com.aris.auth.listener;

import com.aris.auth.AuthPlugin;
import com.aris.auth.security.Bruteforce;
import com.aris.auth.util.Texts;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Hooks the lifecycle: join -> begin auth session, quit -> close state.
 *
 * <p>Brute-force lockouts are enforced in {@link AsyncPlayerPreLoginEvent} so
 * the player never reaches the world while their IP is locked.</p>
 */
public final class ConnectionListener implements Listener {

    private final AuthPlugin plugin;

    public ConnectionListener(@NotNull AuthPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String ip = event.getAddress().getHostAddress();
        Bruteforce bf = plugin.bruteforce();
        if (bf.isLocked(ip)) {
            long minutes = (bf.lockedRemainingMs(ip) + 59_999L) / 60_000L;
            String reason = Texts.colorise(plugin.config().message("chat-kick-locked")
                    .replace("%minutes%", Long.toString(minutes)));
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, reason);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.auth().onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.auth().onQuit(event.getPlayer());
    }
}
