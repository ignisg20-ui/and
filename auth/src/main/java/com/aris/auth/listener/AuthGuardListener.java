package com.aris.auth.listener;

import com.aris.auth.AuthPlugin;
import com.aris.auth.session.AuthState;
import com.aris.auth.util.Texts;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Locks down every action a player can take while unauthenticated.
 *
 * <p>The single design rule: <em>fail closed</em>. Listeners are registered at
 * {@code MONITOR} for read-only checks where applicable and at
 * {@code LOWEST} priority for cancellations so plugins downstream see an
 * already-cancelled event.</p>
 */
public final class AuthGuardListener implements Listener {

    /**
     * Allowed commands while unauthenticated. The leading slash is stripped
     * before matching; aliases registered in plugin.yml are honoured too.
     */
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "login", "l",
            "register", "reg",
            "changepassword", "changepass", "cp");

    private final AuthPlugin plugin;

    public AuthGuardListener(@NotNull AuthPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean blocked(@NotNull Player player) {
        return !plugin.auth().isAuthenticated(player);
    }

    private void deny(@NotNull Player player) {
        player.sendMessage(plugin.auth().prefix() + Texts.colorise(plugin.config().message("chat-blocked-action")));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (!blocked(event.getPlayer())) return;
        Player p = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        // Allow rotation but pin the position. Compare with epsilon to
        // tolerate sub-block float drift Mojang sometimes emits.
        if (Math.abs(from.getX() - to.getX()) < 1e-4
                && Math.abs(from.getY() - to.getY()) < 1e-4
                && Math.abs(from.getZ() - to.getZ()) < 1e-4) {
            return;
        }
        AuthState state = plugin.auth().state(p.getUniqueId());
        Location anchor = state == null ? from : state.spawn();
        if (anchor == null) anchor = from;
        // Preserve head rotation for smooth feel.
        anchor = anchor.clone();
        anchor.setYaw(to.getYaw());
        anchor.setPitch(to.getPitch());
        event.setTo(anchor);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!blocked(event.getPlayer())) return;
        // Allow our own freeze-teleports (cause == UNKNOWN/PLUGIN initialise spawn).
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p && blocked(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p && blocked(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (blocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (blocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.auth().prefix() + Texts.colorise(plugin.config().message("chat-must-login")));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!blocked(event.getPlayer())) return;
        String msg = event.getMessage();
        if (msg.startsWith("/")) msg = msg.substring(1);
        int space = msg.indexOf(' ');
        String label = (space < 0 ? msg : msg.substring(0, space)).toLowerCase();
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);
        if (!ALLOWED_COMMANDS.contains(label)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.auth().prefix() + Texts.colorise(plugin.config().message("chat-blocked-action")));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (blocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && blocked(p)) {
            event.setCancelled(true);
        }
    }
}
