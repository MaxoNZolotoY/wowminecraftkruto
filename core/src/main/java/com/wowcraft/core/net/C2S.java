package com.wowcraft.core.net;

/** Client -> server messages. */
public final class C2S {
    private C2S() {
    }

    public static final class Cast {
        /** Ability id, or empty to use the bar slot. */
        public String ability;
        public int slot = -1;
        public int targetId = -1;
        public double x, y, z;
        public boolean hasPoint;
    }

    public static final class Release {
        public String ability;
    }

    public static final class CancelCast {
    }

    public static final class Target {
        public int entityId = -1;
    }

    public static final class ChooseClass {
        public String wowClass;
        public String spec;
    }

    public static final class Talent {
        public String node;
        public boolean learn;
        public boolean reset;
    }

    public static final class SetBar {
        public int slot;
        public String ability;
    }

    public static final class GroupAction {
        /** invite, accept, decline, leave, kick, promote, role, ready_check, ready, not_ready, raid, add_bot, remove_bot */
        public String action;
        public String name;
        public String value;
    }

    public static final class LfgAction {
        /** queue, leave, list_key, delist, apply, accept, decline, start_followers, refresh */
        public String action;
        public String dungeon;
        public String difficulty;
        public int level;
        public String role;
        public String listing;
        public String applicant;
    }

    public static final class InstanceAction {
        /** start_key, leave, release, reset */
        public String action;
    }

    public static final class PvpAction {
        /** queue, leave, ready */
        public String action;
        public String bracket;
        public boolean rated;
    }

    public static final class Request {
        /** vault, meter, leaderboard, stats, character, lfg */
        public String what;
        public String arg;
    }

    public static final class VaultClaim {
        public int index;
    }

    public static final class Upgrade {
        public String slot;
    }

    public static final class UseItem {
        /** trinket_1, trinket_2, potion, healthstone */
        public String which;
    }

    public static final class Hello {
        public int protocol;
        public String language;
    }
}
