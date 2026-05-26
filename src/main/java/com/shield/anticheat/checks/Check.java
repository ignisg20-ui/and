package com.shield.anticheat.checks;

import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Common interface for every detection check. Individual checks override only
 * the hooks they actually care about so the engine can dispatch through the
 * full list cheaply on every event.
 */
public interface Check {

    @NotNull String name();

    default void onMove(@NotNull CheckContext ctx, @NotNull PlayerMoveEvent event) {}

    default void onAttack(@NotNull CheckContext ctx, @NotNull EntityDamageByEntityEvent event) {}

    default void onTakeDamage(@NotNull CheckContext ctx, @NotNull EntityDamageEvent event) {}

    default void onInteract(@NotNull CheckContext ctx, @NotNull PlayerInteractEvent event) {}

    default void onBlockPlace(@NotNull CheckContext ctx, @NotNull BlockPlaceEvent event) {}
}
