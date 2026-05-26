package com.shield.packet;

import com.shield.ShieldPlugin;
import com.shield.log.Severity;
import com.shield.util.IpUtil;
import com.shield.util.RollingCounter;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Per-connection Netty handler that performs the lowest-level protections:
 * packet rate-limiting, oversized-frame detection, and malformed-payload
 * filtering. We intentionally avoid version-locked NMS imports - the handler
 * works against raw {@code ByteBuf}s and the Bukkit {@link Player} reflection
 * surface, which is stable across the 1.20/1.21 cycle.
 *
 * <p>If the underlying server doesn't expose its network channel (e.g. some
 * heavily-patched forks rename internal fields), the module logs a warning
 * and falls back to event-only enforcement.</p>
 */
public final class PacketInterceptor implements Listener {

    public static final String HANDLER_NAME = "shield-packet-handler";

    private final ShieldPlugin plugin;
    private final Set<UUID> installed = ConcurrentHashMap.newKeySet();

    private volatile int maxPacketsPerSecond;
    private volatile int maxPacketSizeBytes;
    private volatile boolean disconnectOnViolation;

    public PacketInterceptor(@NotNull ShieldPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player p : Bukkit.getOnlinePlayers()) install(p);
    }

    public void stop() {
        Set<UUID> snapshot = new HashSet<>(installed);
        for (UUID id : snapshot) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) uninstall(p);
        }
        installed.clear();
    }

    public void reload() {
        maxPacketsPerSecond = plugin.shieldConfig().integer("anti-exploit.packet.max-packets-per-second", 500);
        maxPacketSizeBytes = plugin.shieldConfig().integer("anti-exploit.packet.max-packet-size-bytes", 2_097_152);
        disconnectOnViolation = plugin.shieldConfig().bool("anti-exploit.packet.disconnect-on-violation", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        install(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        uninstall(event.getPlayer());
    }

    private void install(@NotNull Player player) {
        if (!installed.add(player.getUniqueId())) return;
        ChannelPipeline pipeline = pipelineFor(player);
        if (pipeline == null) return;
        try {
            pipeline.addBefore(firstInbound(pipeline), HANDLER_NAME,
                    new ShieldHandler(plugin, player));
        } catch (IllegalArgumentException ex) {
            // Already installed for this connection.
            plugin.securityLogger().write(Severity.INFO, "PACKET",
                    "Handler already installed for " + player.getName());
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Could not install packet handler for " + player.getName(), t);
            installed.remove(player.getUniqueId());
        }
    }

    private void uninstall(@NotNull Player player) {
        if (!installed.remove(player.getUniqueId())) return;
        ChannelPipeline pipeline = pipelineFor(player);
        if (pipeline == null) return;
        try {
            if (pipeline.get(HANDLER_NAME) != null) pipeline.remove(HANDLER_NAME);
        } catch (Throwable ignored) {
            // Channel might already be closed.
        }
    }

    /**
     * Best-effort pipeline accessor. Falls back to {@code null} when the
     * server does not expose its network channel through the Bukkit reflection
     * surface (e.g. a heavily modified Spigot fork).
     */
    private @org.jetbrains.annotations.Nullable ChannelPipeline pipelineFor(@NotNull Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Class<?> handleClass = handle.getClass();
            // The "connection" field has had two stable names: 'connection' on Paper 1.20+
            // and 'b' on obfuscated Spigot. We try both.
            Object connection = readFirstField(handle, handleClass, "connection", "f", "c");
            if (connection == null) return null;
            Object networkManager = readFirstField(connection, connection.getClass(),
                    "connection", "networkManager", "h", "m");
            if (networkManager == null) networkManager = connection; // newer mappings already point to NetworkManager
            Object channel = readFirstField(networkManager, networkManager.getClass(),
                    "channel", "m", "k");
            if (channel instanceof io.netty.channel.Channel ch) {
                return ch.pipeline();
            }
        } catch (Throwable ignored) {
            // Falls through to null.
        }
        return null;
    }

    private static @org.jetbrains.annotations.Nullable Object readFirstField(@NotNull Object owner,
                                                                             @NotNull Class<?> from,
                                                                             @NotNull String... names) {
        Class<?> current = from;
        while (current != null && current != Object.class) {
            for (String name : names) {
                try {
                    java.lang.reflect.Field f = current.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(owner);
                } catch (NoSuchFieldException ignored) {
                    // try next name
                } catch (IllegalAccessException ignored) {
                    return null;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static @NotNull String firstInbound(@NotNull ChannelPipeline pipeline) {
        // Insert before whichever decoder handles the first inbound packet.
        for (String name : pipeline.names()) {
            if (!"head".equals(name) && !"tail".equals(name)) return name;
        }
        return "tail";
    }

    /**
     * Per-connection handler.
     */
    private static final class ShieldHandler extends ChannelInboundHandlerAdapter {

        private final ShieldPlugin plugin;
        private final Player player;
        private final RollingCounter packetRate = new RollingCounter();

        ShieldHandler(ShieldPlugin plugin, Player player) {
            this.plugin = plugin;
            this.player = player;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                packetRate.record();
                int rate = packetRate.count(1000);
                int maxRate = plugin.packets().maxPacketsPerSecond;
                if (rate > maxRate) {
                    plugin.securityLogger().write(Severity.HIGH, "PACKET",
                            player.getName() + " :: rate " + rate + " > " + maxRate);
                    if (plugin.packets().disconnectOnViolation) {
                        ctx.close();
                    }
                    return;
                }
                if (msg instanceof ByteBuf buf) {
                    int size = buf.readableBytes();
                    if (size > plugin.packets().maxPacketSizeBytes) {
                        plugin.securityLogger().write(Severity.CRITICAL, "PACKET",
                                player.getName() + " :: oversized " + size + " bytes");
                        plugin.alerts().dispatch(Severity.HIGH, "Oversized packet",
                                player.getName() + " sent " + size + " bytes");
                        if (plugin.packets().disconnectOnViolation) {
                            ctx.close();
                        }
                        return;
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Packet inspection failed for " + player.getName(), t);
            }
            super.channelRead(ctx, msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            // Malformed packets throw here; log + close cleanly.
            String ip = ctx.channel().remoteAddress() == null ? "unknown"
                    : IpUtil.stringify(ctx.channel().remoteAddress());
            plugin.securityLogger().write(Severity.HIGH, "PACKET",
                    player.getName() + " (" + ip + ") :: malformed: " + cause.getClass().getSimpleName());
            ctx.close();
        }
    }
}
