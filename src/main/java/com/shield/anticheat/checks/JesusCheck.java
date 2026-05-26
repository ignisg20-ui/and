package com.shield.anticheat.checks;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

/**
 * Detects "Jesus" / waterwalk: standing on the surface of liquid blocks
 * without being supported by anything solid.
 */
public final class JesusCheck implements Check {

    @Override
    public @NotNull String name() {
        return "jesus";
    }

    @Override
    public void onMove(@NotNull CheckContext ctx, @NotNull PlayerMoveEvent event) {
        if (!ctx.cfgBool("anti-cheat.jesus.enabled", true)) return;
        Player p = ctx.player();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (p.isFlying() || p.isGliding() || p.isInsideVehicle()) return;
        if (p.hasPotionEffect(PotionEffectType.LEVITATION) || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return;
        if (p.getInventory().getBoots() != null && p.getInventory().getBoots().getEnchantments().containsKey(org.bukkit.enchantments.Enchantment.FROST_WALKER)) return;
        Block below = event.getTo().clone().subtract(0, 0.01, 0).getBlock();
        if (below.isLiquid() && (below.getType() == Material.WATER || below.getType() == Material.LAVA)) {
            // Need to also verify we're actually still — surfing waves is normal during current.
            double vy = p.getVelocity().getY();
            if (p.isOnGround() && Math.abs(vy) < 0.01) {
                ctx.flag(name(), "standing on " + below.getType());
            }
        }
    }
}
