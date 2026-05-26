package com.shield.anticheat.checks;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

/**
 * Detects "NoFall" - clients that cancel the fall-damage packet. We watch the
 * player's airborne tracker; when the fall distance is clearly damaging but
 * the resulting fall-damage event is missing in the same tick we flag.
 */
public final class NoFallCheck implements Check {

    @Override
    public @NotNull String name() {
        return "nofall";
    }

    @Override
    public void onTakeDamage(@NotNull CheckContext ctx, @NotNull EntityDamageEvent event) {
        if (!ctx.cfgBool("anti-cheat.no-fall.enabled", true)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        Player p = ctx.player();
        // When fall damage *does* fire we treat the data point as a healthy reference; no flag.
        if (p.hasPotionEffect(PotionEffectType.SLOW_FALLING) || p.hasPotionEffect(PotionEffectType.LEVITATION)) return;
        ctx.data().samples("nofall-fall", 4).clear();
    }

    @Override
    public void onMove(@NotNull CheckContext ctx, @NotNull org.bukkit.event.player.PlayerMoveEvent event) {
        if (!ctx.cfgBool("anti-cheat.no-fall.enabled", true)) return;
        Player p = ctx.player();
        if (p.getFallDistance() > 3.5 && p.isOnGround()
                && !p.isFlying() && !p.isGliding() && !p.isInsideVehicle()
                && p.getVehicle() == null && p.getType() == EntityType.PLAYER
                && p.getLocation().getBlock().getType().isAir()) {
            // Player landed without normal fall damage being expected from this position.
            ctx.flag(name(), "fallDistance=" + p.getFallDistance() + " but no fall damage");
        }
    }
}
