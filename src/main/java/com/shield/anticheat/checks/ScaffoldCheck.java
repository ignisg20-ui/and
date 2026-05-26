package com.shield.anticheat.checks;

import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Deque;

/**
 * Detects bridging-style "scaffold" cheats. We track place-events per second
 * and additionally watch for placements directly behind the player while they
 * keep moving forward - a classic scaffold signature.
 */
public final class ScaffoldCheck implements Check {

    @Override
    public @NotNull String name() {
        return "scaffold";
    }

    @Override
    public void onBlockPlace(@NotNull CheckContext ctx, @NotNull BlockPlaceEvent event) {
        if (!ctx.cfgBool("anti-cheat.scaffold.enabled", true)) return;
        Player p = ctx.player();
        long now = ctx.nowMs();
        Deque<Long> times = ctx.data().times("scaffold-place", 64);
        synchronized (times) {
            times.addLast(now);
            while (!times.isEmpty() && times.peekFirst() < now - 1000L) times.pollFirst();
            int max = ctx.cfgInt("anti-cheat.scaffold.max-place-rate", 12);
            if (times.size() > max) {
                ctx.flag(name(), "place rate " + times.size() + "/s");
                return;
            }
        }
        if (!p.isOnGround()) {
            org.bukkit.util.Vector direction = p.getEyeLocation().getDirection();
            org.bukkit.util.Vector toBlock = event.getBlockPlaced().getLocation().toVector()
                    .subtract(p.getLocation().toVector()).normalize();
            double dot = direction.dot(toBlock);
            if (dot < -0.4) { // Placed firmly behind the player while airborne.
                ctx.flag(name(), "behind-back bridging dot=" + String.format("%.2f", dot));
            }
        }
    }
}
