package com.shield.anticheat.checks;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

/**
 * Fly / hover detection. We tolerate brief airborne phases (jump, knockback,
 * elytra burst), so the check only flags continuous suspension beyond a
 * configurable tick budget.
 */
public final class FlyCheck implements Check {

    @Override
    public @NotNull String name() {
        return "fly";
    }

    @Override
    public void onMove(@NotNull CheckContext ctx, @NotNull PlayerMoveEvent event) {
        if (!ctx.cfgBool("anti-cheat.fly.enabled", true)) return;
        Player p = ctx.player();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (p.getAllowFlight() || p.isFlying() || p.isGliding() || p.isInsideVehicle()) return;
        if (p.hasPotionEffect(PotionEffectType.LEVITATION) || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return;

        if (p.isOnGround() || isClimbable(p.getLocation().getBlock().getType())) {
            ctx.data().airTicks().set(0);
            return;
        }
        int suspended = ctx.data().airTicks().get();
        int max = ctx.cfgInt("anti-cheat.fly.max-suspended-ticks", 80);
        double sensitivity = ctx.cfgDouble("anti-cheat.fly.sensitivity", 1.0);
        max = (int) Math.max(20, max * sensitivity);
        if (suspended > max) {
            ctx.flag(name(), "airborne for " + suspended + " ticks");
            ctx.data().airTicks().set(0);
        }
    }

    private boolean isClimbable(Material type) {
        return type == Material.LADDER || type == Material.VINE || type == Material.SCAFFOLDING
                || type == Material.TWISTING_VINES || type == Material.WEEPING_VINES
                || type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT;
    }
}
