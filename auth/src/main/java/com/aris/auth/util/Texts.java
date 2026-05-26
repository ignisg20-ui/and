package com.aris.auth.util;

import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;

/**
 * Lightweight text helpers. We deliberately avoid the modern Adventure API
 * here because legacy color codes are already understood by both Spigot and
 * Paper, and keep the plugin compatible with older forks.
 */
public final class Texts {

    private Texts() { }

    public static @NotNull String colorise(@NotNull String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    /**
     * Renders a left-to-right gradient by interpolating between two hex colors.
     * Falls back to legacy color codes on servers that don't understand
     * §x§R§R§G§G§B§B sequences.
     */
    public static @NotNull String gradient(@NotNull String raw, @NotNull String startHex, @NotNull String endHex) {
        if (raw.isEmpty()) return raw;
        int[] start = hexToRgb(startHex);
        int[] end = hexToRgb(endHex);
        int len = raw.length();
        StringBuilder out = new StringBuilder(len * 14);
        for (int i = 0; i < len; i++) {
            float t = len == 1 ? 0f : (float) i / (len - 1);
            int r = (int) (start[0] + (end[0] - start[0]) * t);
            int g = (int) (start[1] + (end[1] - start[1]) * t);
            int b = (int) (start[2] + (end[2] - start[2]) * t);
            out.append(toBukkitHex(r, g, b)).append(raw.charAt(i));
        }
        return out.toString();
    }

    private static int[] hexToRgb(@NotNull String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        return new int[] {
                Integer.parseInt(h.substring(0, 2), 16),
                Integer.parseInt(h.substring(2, 4), 16),
                Integer.parseInt(h.substring(4, 6), 16)
        };
    }

    private static String toBukkitHex(int r, int g, int b) {
        String hex = String.format("%02x%02x%02x", r & 0xff, g & 0xff, b & 0xff);
        StringBuilder out = new StringBuilder(14);
        out.append('§').append('x');
        for (char c : hex.toCharArray()) {
            out.append('§').append(c);
        }
        return out.toString();
    }
}
