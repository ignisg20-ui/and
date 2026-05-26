package com.shield.util;

import java.util.Collection;

/**
 * Small numeric helpers.
 */
public final class Numbers {

    private Numbers() {
        throw new AssertionError();
    }

    public static double mean(Collection<? extends Number> values) {
        if (values == null || values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (Number n : values) sum += n.doubleValue();
        return sum / values.size();
    }

    public static double stddev(Collection<? extends Number> values) {
        if (values == null || values.size() < 2) return 0.0;
        double mean = mean(values);
        double acc = 0.0;
        for (Number n : values) {
            double d = n.doubleValue() - mean;
            acc += d * d;
        }
        return Math.sqrt(acc / (values.size() - 1));
    }

    public static double gcd(double a, double b) {
        double x = Math.abs(a);
        double y = Math.abs(b);
        while (y > 1e-9) {
            double t = y;
            y = x % y;
            x = t;
        }
        return x;
    }
}
