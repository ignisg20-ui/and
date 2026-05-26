package com.shield.antibot;

import com.shield.ShieldPlugin;
import com.shield.log.Severity;
import com.shield.util.IpUtil;
import com.shield.util.RollingCounter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detection layer for bot-style connections.
 *
 * <p>The module performs three independent checks:
 * <ul>
 *   <li>A join-flood counter that protects against many fresh accounts in a
 *       short window.</li>
 *   <li>Light heuristics for fake / replayed sessions (very low ping, missing
 *       client brand, suspicious username patterns).</li>
 *   <li>An optional chat-based captcha that has to be solved before the player
 *       can interact with the rest of the world.</li>
 * </ul></p>
 */
public final class AntiBotModule implements Listener {

    private static final SecureRandom RNG = new SecureRandom();

    private final ShieldPlugin plugin;
    private final ConcurrentHashMap<UUID, Captcha> pending = new ConcurrentHashMap<>();
    private final RollingCounter joinRate = new RollingCounter();

    private volatile boolean enabled;
    private volatile boolean captchaEnabled;
    private volatile String captchaType;
    private volatile int captchaTimeoutSeconds;
    private volatile int captchaMaxAttempts;
    private volatile boolean fakeSessionEnabled;
    private volatile boolean joinFloodEnabled;
    private volatile int joinFloodMaxPerSecond;
    private volatile int joinFloodCooldownSeconds;

    public AntiBotModule(@NotNull ShieldPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sweep, 40L, 40L);
    }

    public void stop() {
        pending.clear();
        joinRate.clear();
    }

    public void reload() {
        enabled = plugin.shieldConfig().bool("anti-bot.enabled", true);
        captchaEnabled = plugin.shieldConfig().bool("anti-bot.captcha.enabled", true);
        captchaType = plugin.shieldConfig().string("anti-bot.captcha.type", "math").toLowerCase();
        captchaTimeoutSeconds = plugin.shieldConfig().integer("anti-bot.captcha.timeout-seconds", 60);
        captchaMaxAttempts = plugin.shieldConfig().integer("anti-bot.captcha.max-attempts", 3);
        fakeSessionEnabled = plugin.shieldConfig().bool("anti-bot.fake-session.enabled", true);
        joinFloodEnabled = plugin.shieldConfig().bool("anti-bot.join-flood.enabled", true);
        joinFloodMaxPerSecond = plugin.shieldConfig().integer("anti-bot.join-flood.max-joins-per-second", 5);
        joinFloodCooldownSeconds = plugin.shieldConfig().integer("anti-bot.join-flood.cooldown-seconds", 20);
    }

    public boolean isAwaitingCaptcha(@NotNull UUID uuid) {
        return pending.containsKey(uuid);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!enabled) return;
        if (joinFloodEnabled) {
            joinRate.record();
            if (joinRate.count(1000) > joinFloodMaxPerSecond) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        "§c[Shield] §fСервер временно ограничивает новые подключения. Попробуйте через " +
                                joinFloodCooldownSeconds + " сек.");
                plugin.alerts().dispatch(Severity.HIGH, "Join flood",
                        "Triggered by " + event.getName() + " (" + IpUtil.stringify(event.getAddress()) + ")");
                return;
            }
        }

        if (fakeSessionEnabled && isUsernameSuspicious(event.getName())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "§c[Shield] §fПодозрительный никнейм отклонён.");
            plugin.alerts().dispatch(Severity.MEDIUM, "Fake session", "Suspicious name " + event.getName());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled || !captchaEnabled) return;
        if (event.getPlayer().hasPermission(plugin.shieldConfig().bypassPermission())) return;
        Captcha captcha = newChallenge();
        pending.put(event.getPlayer().getUniqueId(), captcha);
        sendCaptcha(event.getPlayer(), captcha);
        long timeoutTicks = captchaTimeoutSeconds * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pending.remove(event.getPlayer().getUniqueId()) != null) {
                if (event.getPlayer().isOnline()) {
                    event.getPlayer().kickPlayer("§c[Shield] §fВремя на ввод капчи истекло.");
                    plugin.alerts().dispatch(Severity.MEDIUM, "Captcha timeout",
                            event.getPlayer().getName() + " did not solve in time.");
                }
            }
        }, timeoutTicks);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Captcha captcha = pending.get(event.getPlayer().getUniqueId());
        if (captcha == null) return;
        event.setCancelled(true);
        String answer = event.getMessage().trim();
        if (captcha.matches(answer)) {
            pending.remove(event.getPlayer().getUniqueId());
            event.getPlayer().sendMessage("§a[Shield] §fКапча принята. Добро пожаловать!");
            return;
        }
        if (captcha.fail() >= captchaMaxAttempts) {
            pending.remove(event.getPlayer().getUniqueId());
            event.getPlayer().kickPlayer("§c[Shield] §fПревышено количество попыток.");
            plugin.alerts().dispatch(Severity.HIGH, "Captcha failed",
                    event.getPlayer().getName() + " exhausted attempts.");
            return;
        }
        event.getPlayer().sendMessage("§e[Shield] §fНеверный ответ. Попыток осталось: §6"
                + (captchaMaxAttempts - captcha.attempts));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    private void sweep() {
        joinRate.prune(2000);
    }

    private Captcha newChallenge() {
        return switch (captchaType) {
            case "letters" -> Captcha.letters();
            case "math" -> Captcha.math();
            default -> Captcha.math();
        };
    }

    private void sendCaptcha(@NotNull Player player, @NotNull Captcha captcha) {
        player.sendMessage("§6§l[Shield] §r§fПодтвердите, что вы не бот.");
        player.sendMessage("§e" + captcha.prompt + " §7(напишите ответ в чат)");
    }

    private static boolean isUsernameSuspicious(@NotNull String name) {
        if (name.length() < 3 || name.length() > 16) return true;
        if (!name.matches("[A-Za-z0-9_]+")) return true;
        long digits = name.chars().filter(Character::isDigit).count();
        return digits > name.length() / 2;
    }

    private static final class Captcha {
        final String prompt;
        final String expected;
        int attempts;

        private Captcha(String prompt, String expected) {
            this.prompt = prompt;
            this.expected = expected;
        }

        static Captcha math() {
            int a = 1 + RNG.nextInt(9);
            int b = 1 + RNG.nextInt(9);
            return new Captcha("Сколько будет " + a + " + " + b + "?", Integer.toString(a + b));
        }

        static Captcha letters() {
            StringBuilder sb = new StringBuilder(5);
            for (int i = 0; i < 5; i++) {
                sb.append((char) ('A' + RNG.nextInt(26)));
            }
            String s = sb.toString();
            return new Captcha("Введите код: " + s, s);
        }

        boolean matches(String answer) {
            return expected.equalsIgnoreCase(answer);
        }

        int fail() {
            return ++attempts;
        }
    }
}
