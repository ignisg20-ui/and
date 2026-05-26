package com.shield.log;

/**
 * Severity classification used for both file logging and alert dispatch.
 */
public enum Severity {

    INFO(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int weight;

    Severity(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }

    public boolean isAtLeast(Severity other) {
        return this.weight >= other.weight;
    }

    public static Severity parse(String raw, Severity fallback) {
        if (raw == null) return fallback;
        try {
            return Severity.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
