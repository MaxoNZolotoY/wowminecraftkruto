package com.wowcraft.core.util;

public final class Mth {
    private Mth() {
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Signed smallest difference between two angles in degrees (-180..180]. */
    public static double angleDiff(double a, double b) {
        double d = (a - b) % 360.0;
        if (d > 180) d -= 360;
        if (d <= -180) d += 360;
        return d;
    }

    public static String formatTime(double seconds) {
        boolean neg = seconds < 0;
        long s = (long) Math.floor(Math.abs(seconds));
        long m = s / 60;
        s %= 60;
        return (neg ? "-" : "") + m + ":" + (s < 10 ? "0" : "") + s;
    }

    /** Formats big numbers WoW-style: 1234 -> 1.2K, 1234567 -> 1.23M. */
    public static String shortNumber(double v) {
        double a = Math.abs(v);
        if (a >= 1_000_000_000) return String.format(java.util.Locale.ROOT, "%.2fB", v / 1_000_000_000.0);
        if (a >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.2fM", v / 1_000_000.0);
        if (a >= 10_000) return String.format(java.util.Locale.ROOT, "%.1fK", v / 1000.0);
        return String.valueOf(Math.round(v));
    }

    public static String pct(double fraction) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", fraction * 100.0);
    }
}
