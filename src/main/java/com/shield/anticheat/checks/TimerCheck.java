package com.shield.anticheat.checks;

import com.shield.ShieldPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Timer-hack check. We count the number of move events per real second; sane
 * vanilla play caps at ~20 events/sec. A consistently elevated ratio implies
 * the client is rendering / sending packets faster than the wall clock.
 */
public final class TimerCheck implements Check {

    private final ConcurrentHashMap<java.util.UUID, AtomicInteger> moves = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<java.util.UUID, AtomicLong> windowStart = new ConcurrentHashMap<>();

    @Override
    public @NotNull String name() {
        return "timer";
    }

    @Override
    public void onMove(@NotNull CheckContext ctx, @NotNull PlayerMoveEvent event) {
        if (!ctx.cfgBool("anti-cheat.timer.enabled", true)) return;
        AtomicInteger counter = moves.computeIfAbsent(ctx.player().getUniqueId(), uuid -> new AtomicInteger());
        counter.incrementAndGet();
    }

    /**
     * Called from the engine's central tick loop once per server tick.
     */
    public void tick(@NotNull CheckContext ctx) {
        if (!ctx.cfgBool("anti-cheat.timer.enabled", true)) return;
        Player p = ctx.player();
        AtomicLong start = windowStart.computeIfAbsent(p.getUniqueId(), uuid -> new AtomicLong(ctx.nowMs()));
        AtomicInteger counter = moves.computeIfAbsent(p.getUniqueId(), uuid -> new AtomicInteger());
        long elapsed = ctx.nowMs() - start.get();
        if (elapsed < 1000L) return;
        int events = counter.getAndSet(0);
        start.set(ctx.nowMs());
        double ratio = events / 20.0;
        double max = ctx.cfgDouble("anti-cheat.timer.max-ratio", 1.18);
        double min = ctx.cfgDouble("anti-cheat.timer.min-ratio", 0.85);
        if (ratio > max && events > 24) {
            ctx.flag(name(), String.format("timer ratio %.2f (%d events)", ratio, events));
        } else if (ratio < min && events > 0 && events < 12) {
            // Only flag suspiciously slow timers when the player is actually moving — being idle is normal.
            if (p.getVelocity().lengthSquared() > 0.01) {
                ctx.flag(name(), String.format("slow timer ratio %.2f", ratio));
            }
        }
    }
}
