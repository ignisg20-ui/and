package com.aris.auth.listener;

import com.aris.auth.AuthPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Makes the player effectively unkillable while they have not authenticated.
 *
 * <p>{@link Player#setInvulnerable(boolean)} already neutralises most damage
 * sources but we also handle a few edge cases (knockback, mob targeting,
 * splash potions) so the player feels completely frozen.</p>
 */
public final class DamageImmunityListener implements Listener {

    private final AuthPlugin plugin;

    public DamageImmunityListener(@NotNull AuthPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean unauthed(@NotNull Player p) {
        return !plugin.auth().isAuthenticated(p);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && unauthed(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDamageBy(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player p && unauthed(p)) {
            event.setCancelled(true);
        }
        // Also block unauthed players from hitting things.
        if (event.getDamager() instanceof Player attacker && unauthed(attacker)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player p && unauthed(p)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        for (var affected : event.getAffectedEntities()) {
            if (affected instanceof Player p && unauthed(p)) {
                event.setIntensity(p, 0.0);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player p && unauthed(p)) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onKnockback(PlayerVelocityEvent event) {
        if (unauthed(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
