package com.shield.anticheat.checks;

import com.shield.util.Numbers;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Heuristic aim-assist detector. Vanilla mouse input drifts continuously with
 * a GCD floor matching mouse sensitivity; aimbot-style "snap-to-target"
 * patterns produce yaw deltas whose GCD collapses toward 0 indicating
 * synthetic precision. We sample yaw deltas and flag when the GCD of recent
 * samples drops below a configurable threshold.
 */
public final class AimAssistCheck implements Check {

    @Override
    public @NotNull String name() {
        return "aimassist";
    }

    @Override
    public void onMove(@NotNull CheckContext ctx, @NotNull PlayerMoveEvent event) {
        if (!ctx.cfgBool("anti-cheat.aim-assist.enabled", true)) return;
        if (event.getFrom().getYaw() == event.getTo().getYaw()
                && event.getFrom().getPitch() == event.getTo().getPitch()) return;

        Deque<Double> deltas = ctx.data().samples("aim-yaw", 16);
        synchronized (deltas) {
            double delta = Math.abs(event.getTo().getYaw() - event.getFrom().getYaw());
            if (delta < 0.05) return; // ignore micro jitter — too small to matter
            deltas.addLast(delta);
            if (deltas.size() > 16) deltas.pollFirst();
            if (deltas.size() < 8) return;
            List<Double> snap = new ArrayList<>(deltas);
            double gcd = snap.get(0);
            for (int i = 1; i < snap.size(); i++) gcd = Numbers.gcd(gcd, snap.get(i));
            double threshold = ctx.cfgDouble("anti-cheat.aim-assist.suspicious-gcd-threshold", 0.0009);
            if (gcd < threshold) {
                ctx.flag(name(), String.format("aim GCD=%.6f over %d samples", gcd, snap.size()));
                deltas.clear();
            }
        }
    }
}
