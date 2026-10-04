package com.wowcraft.core.combat;

import java.util.HashMap;
import java.util.Map;

/** PvP diminishing returns: 100% -> 50% -> 25% -> immune, resetting 18s after the last application. */
public final class DrTracker {
    public static final double RESET_TIME = 18.0;

    private static final class State {
        int count;
        double resetAt;
    }

    private final Map<String, State> states = new HashMap<>();

    /** Returns the duration multiplier for a new application (0 = immune) and records it. */
    public double apply(String category, double now, double baseDuration) {
        if (category == null) return 1.0;
        State s = states.computeIfAbsent(category, k -> new State());
        if (now >= s.resetAt) s.count = 0;
        double mult = switch (s.count) {
            case 0 -> 1.0;
            case 1 -> 0.5;
            case 2 -> 0.25;
            default -> 0.0;
        };
        if (mult > 0) {
            s.count++;
            s.resetAt = Math.max(s.resetAt, now + baseDuration * mult + RESET_TIME);
        }
        return mult;
    }

    /** Called when a CC of the category ends: the DR window starts from that moment. */
    public void ccEnded(String category, double now) {
        if (category == null) return;
        State s = states.get(category);
        if (s != null) s.resetAt = Math.min(s.resetAt, now + RESET_TIME);
    }

    public double peek(String category, double now) {
        if (category == null) return 1.0;
        State s = states.get(category);
        if (s == null || now >= s.resetAt) return 1.0;
        return switch (s.count) {
            case 0 -> 1.0;
            case 1 -> 0.5;
            case 2 -> 0.25;
            default -> 0.0;
        };
    }

    public void clear() {
        states.clear();
    }
}
