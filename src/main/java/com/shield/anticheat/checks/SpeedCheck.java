package com.shield.anticheat.checks;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

/**
 * Horizontal-speed sanity check. Combines elapsed time, current effects, and a
 * configurable per-server multiplier so values can be tuned without code.
 */
public final class SpeedCheck implements Check {

    @Override
    public @NotNull String name() {
        return "speed";
    }

    @Override
    public void onMove(@NotNull CheckContext ctx, @NotNull PlayerMoveEvent event) {
        if (!ctx.cfgBool("anti-cheat.speed.enabled", true)) return;
        Player p = ctx.player();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (p.isInsideVehicle() || p.isGliding() || p.isFlying()) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        long deltaMs = Math.max(1, ctx.nowMs() - ctx.data().lastMoveMs());
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double bps = Math.sqrt(dx * dx + dz * dz) * 1000.0 / deltaMs;

        double sensitivity = ctx.cfgDouble("anti-cheat.speed.sensitivity", 1.0);
        double max = ctx.cfgDouble("anti-cheat.speed.max-horizontal-bps", 8.4) * sensitivity;
        if (p.hasPotionEffect(PotionEffectType.SPEED)) {
            int amp = p.getPotionEffect(PotionEffectType.SPEED).getAmplifier();
            max *= 1.0 + 0.20 * (amp + 1);
        }
        if (p.isSprinting()) max *= 1.1;
        if (p.hasPotionEffect(PotionEffectType.JUMP_BOOST)) max *= 1.1;
        if (p.isInWater() || p.isInLava()) max *= 0.7;

        if (bps > max) {
            ctx.flag(name(), String.format("%.2f bps > %.2f", bps, max));
        }
    }
}
