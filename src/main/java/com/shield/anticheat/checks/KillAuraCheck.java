package com.shield.anticheat.checks;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Deque;

/**
 * Catches the typical KillAura "many distinct targets per second + impossibly
 * fast yaw snaps". Conservative thresholds keep legit PvP unaffected.
 */
public final class KillAuraCheck implements Check {

    @Override
    public @NotNull String name() {
        return "killaura";
    }

    @Override
    public void onAttack(@NotNull CheckContext ctx, @NotNull EntityDamageByEntityEvent event) {
        if (!ctx.cfgBool("anti-cheat.killaura.enabled", true)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        long now = ctx.nowMs();
        Deque<Long> times = ctx.data().times("killaura-hits", 32);
        synchronized (times) {
            times.addLast(now);
            while (!times.isEmpty() && times.peekFirst() < now - 1000L) times.pollFirst();
            int maxPerSecond = ctx.cfgInt("anti-cheat.killaura.max-targets-per-second", 6);
            if (times.size() > maxPerSecond) {
                ctx.flag(name(), "hit rate " + times.size() + "/s");
            }
        }

        Deque<Double> yawDeltas = ctx.data().samples("killaura-yaw", 8);
        synchronized (yawDeltas) {
            double yaw = ctx.player().getLocation().getYaw();
            Double previous = yawDeltas.peekLast();
            yawDeltas.addLast(yaw);
            if (yawDeltas.size() > 8) yawDeltas.pollFirst();
            if (previous != null) {
                double delta = Math.abs(((yaw - previous) % 360 + 540) % 360 - 180);
                double max = ctx.cfgDouble("anti-cheat.killaura.max-yaw-delta-degrees", 75.0);
                if (delta > max) {
                    ctx.flag(name(), "yaw snap " + String.format("%.1f", delta) + "°");
                }
            }
        }
    }
}
