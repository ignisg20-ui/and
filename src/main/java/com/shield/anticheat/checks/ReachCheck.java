package com.shield.anticheat.checks;

import org.bukkit.GameMode;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Hit-distance ("reach") check. Uses the bounding-box closest-point distance
 * instead of the eye-to-eye distance so big mobs aren't false-flagged.
 */
public final class ReachCheck implements Check {

    @Override
    public @NotNull String name() {
        return "reach";
    }

    @Override
    public void onAttack(@NotNull CheckContext ctx, @NotNull EntityDamageByEntityEvent event) {
        if (!ctx.cfgBool("anti-cheat.reach.enabled", true)) return;
        double survival = ctx.cfgDouble("anti-cheat.reach.survival-max-distance", 3.05);
        double creative = ctx.cfgDouble("anti-cheat.reach.creative-max-distance", 5.6);
        double limit = ctx.player().getGameMode() == GameMode.CREATIVE ? creative : survival;

        org.bukkit.util.Vector eye = ctx.player().getEyeLocation().toVector();
        org.bukkit.util.BoundingBox box = event.getEntity().getBoundingBox();
        double dx = Math.max(box.getMinX() - eye.getX(), Math.max(0, eye.getX() - box.getMaxX()));
        double dy = Math.max(box.getMinY() - eye.getY(), Math.max(0, eye.getY() - box.getMaxY()));
        double dz = Math.max(box.getMinZ() - eye.getZ(), Math.max(0, eye.getZ() - box.getMaxZ()));
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist > limit + 0.1) {
            ctx.flag(name(), String.format("dist=%.2f > %.2f", dist, limit));
        }
    }
}
