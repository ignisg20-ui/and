package com.shield.anticheat.checks;

import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Velocity manipulation detector. When the server pushes the player back
 * (knockback) the client is expected to actually move that distance over the
 * next few ticks. We compare the recorded push velocity to the realised
 * displacement and flag when the player takes substantially less than the
 * configured minimum percentage.
 */
public final class VelocityCheck implements Check {

    @Override
    public @NotNull String name() {
        return "velocity";
    }

    @Override
    public void onMove(@NotNull CheckContext ctx, @NotNull PlayerMoveEvent event) {
        if (!ctx.cfgBool("anti-cheat.velocity.enabled", true)) return;
        long vMs = ctx.data().lastVelocityMs();
        long now = ctx.nowMs();
        // Only validate during the immediate window after a velocity event.
        if (vMs == 0L || now - vMs > 300L) return;
        double expectedX = ctx.data().lastVelocityX();
        double expectedZ = ctx.data().lastVelocityZ();
        if (Math.abs(expectedX) + Math.abs(expectedZ) < 0.05) return;
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double expected = Math.sqrt(expectedX * expectedX + expectedZ * expectedZ);
        double observed = Math.sqrt(dx * dx + dz * dz);
        double percent = expected == 0 ? 1.0 : observed / expected;
        double minTake = ctx.cfgDouble("anti-cheat.velocity.min-take-percent", 0.20);
        if (percent < minTake) {
            ctx.flag(name(), String.format("took %.0f%% knockback", percent * 100));
        }
    }
}
