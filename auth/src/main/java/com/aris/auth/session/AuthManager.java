package com.aris.auth.session;

import com.aris.auth.AuthPlugin;
import com.aris.auth.model.AuthUser;
import com.aris.auth.ui.AuthHud;
import com.aris.auth.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Central coordinator that owns the per-online-player {@link AuthState} map.
 */
public final class AuthManager {

    private final AuthPlugin plugin;
    private final ConcurrentHashMap<UUID, AuthState> states = new ConcurrentHashMap<>();

    public AuthManager(@NotNull AuthPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Called from the join listener. Loads the user from storage and creates
     * an {@link AuthState} that the rest of the listeners consult.
     */
    public void onJoin(@NotNull Player player) {
        if (player.hasPermission("arisauth.bypass")) {
            // Treat bypassing players as already authenticated.
            return;
        }
        String ip = player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
        plugin.userStore().find(player.getUniqueId()).whenComplete((user, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load user", error);
            }
            // Switch back to the main thread before touching Bukkit state.
            Bukkit.getScheduler().runTask(plugin, () -> beginSession(player, user));
        });
    }

    private void beginSession(@NotNull Player player, @Nullable AuthUser user) {
        if (!player.isOnline()) return;
        boolean registered = user != null;
        AuthState state = new AuthState(player.getUniqueId(), registered,
                plugin.config().loginTimeoutSeconds, ipOf(player));
        state.setSpawn(player.getLocation());
        states.put(player.getUniqueId(), state);

        // Apply pre-auth freeze: walk speed 0 + invulnerable.
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);
        player.setInvulnerable(true);

        // Session restore?
        if (registered && plugin.sessions().canResume(player)) {
            plugin.sessions().touch(player.getUniqueId());
            authenticate(player, false);
            player.sendMessage(prefix() + Texts.colorise(plugin.config().message("chat-session-restored")));
            return;
        }

        AuthHud hud = plugin.hud();
        hud.applyBlindness(player);
        hud.showTitleFor(player, state);
        hud.attachBossBar(player, state);
        hud.startActionBarLoop(player, state);
        hud.playPromptSound(player, registered);
        hud.startWaitingParticles(player, state);
        hud.scheduleTimeout(player, state);
    }

    /**
     * Marks the player as authenticated and tears down all blocking effects.
     */
    public void authenticate(@NotNull Player player, boolean playSuccessFx) {
        AuthState state = states.get(player.getUniqueId());
        if (state == null || !state.authenticate()) return;
        plugin.sessions().open(player);
        plugin.hud().teardown(player, state);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setInvulnerable(false);
        if (playSuccessFx) {
            plugin.hud().playSuccess(player);
        }
    }

    public void onQuit(@NotNull Player player) {
        AuthState state = states.remove(player.getUniqueId());
        if (state != null) {
            plugin.hud().teardown(player, state);
        }
        plugin.sessions().touch(player.getUniqueId());
    }

    public boolean isAuthenticated(@NotNull Player player) {
        if (player.hasPermission("arisauth.bypass")) return true;
        AuthState s = states.get(player.getUniqueId());
        return s != null && s.authenticated();
    }

    public @Nullable AuthState state(@NotNull UUID uuid) {
        return states.get(uuid);
    }

    public int activeStates() {
        return states.size();
    }

    public void shutdown() {
        for (AuthState state : states.values()) {
            Player p = Bukkit.getPlayer(state.uuid());
            if (p != null) plugin.hud().teardown(p, state);
        }
        states.clear();
    }

    public @NotNull String prefix() {
        return Texts.colorise(plugin.config().message("chat-prefix"));
    }

    public static @Nullable String ipOf(@NotNull Player player) {
        return player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
    }
}
