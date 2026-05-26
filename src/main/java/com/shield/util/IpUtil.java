package com.shield.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Helpers around {@link SocketAddress} / {@link InetAddress} parsing.
 */
public final class IpUtil {

    private IpUtil() {
        throw new AssertionError();
    }

    public static @NotNull String stringify(@Nullable SocketAddress address) {
        if (address instanceof InetSocketAddress isa) {
            InetAddress addr = isa.getAddress();
            return addr == null ? isa.getHostString() : addr.getHostAddress();
        }
        return address == null ? "unknown" : address.toString();
    }

    public static @NotNull String stringify(@Nullable InetAddress address) {
        return address == null ? "unknown" : address.getHostAddress();
    }
}
