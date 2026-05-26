package com.shield.anticheat.checks;

import com.shield.util.Numbers;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * AutoClicker / "double-click" detector. Records left-click timestamps and
 * flags both high absolute CPS and pathologically low click-interval variance.
 */
public final class AutoClickerCheck implements Check {

    @Override
    public @NotNull String name() {
        return "autoclicker";
    }

    @Override
    public void onAttack(@NotNull CheckContext ctx, @NotNull EntityDamageByEntityEvent event) {
        record(ctx);
    }

    @Override
    public void onInteract(@NotNull CheckContext ctx, @NotNull PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            record(ctx);
        }
    }

    private void record(@NotNull CheckContext ctx) {
        if (!ctx.cfgBool("anti-cheat.auto-clicker.enabled", true)) return;
        Deque<Long> times = ctx.data().times("clicks", 32);
        long now = ctx.nowMs();
        synchronized (times) {
            times.addLast(now);
            while (!times.isEmpty() && times.peekFirst() < now - 1000L) times.pollFirst();
            int cps = times.size();
            int maxCps = ctx.cfgInt("anti-cheat.auto-clicker.max-cps", 18);
            if (cps > maxCps) {
                ctx.flag(name(), "cps=" + cps);
            }
            if (times.size() >= 12) {
                List<Long> snapshot = new ArrayList<>(times);
                List<Number> intervals = new ArrayList<>();
                for (int i = 1; i < snapshot.size(); i++) intervals.add(snapshot.get(i) - snapshot.get(i - 1));
                double stddev = Numbers.stddev(intervals);
                double threshold = ctx.cfgDouble("anti-cheat.auto-clicker.consistency-stddev-threshold", 6.0);
                if (stddev < threshold && cps > 8) {
                    ctx.flag(name(), String.format("stddev=%.2f at %d cps", stddev, cps));
                }
            }
        }
    }
}
