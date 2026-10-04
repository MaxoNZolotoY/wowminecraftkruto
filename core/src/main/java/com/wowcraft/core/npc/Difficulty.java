package com.wowcraft.core.npc;

import com.wowcraft.core.util.L10n;

/** Instance difficulty. */
public enum Difficulty {
    NORMAL("Normal", "Обычный", 0.55, 0.55, false),
    HEROIC("Heroic", "Героический", 0.8, 0.8, false),
    MYTHIC("Mythic", "Эпохальный", 1.0, 1.0, false),
    MYTHIC_PLUS("Mythic+", "Эпохальный+", 1.0, 1.0, true),
    LFR("Raid Finder", "Поиск рейда", 0.5, 0.5, false),
    RAID_NORMAL("Normal", "Обычный", 0.8, 0.8, false),
    RAID_HEROIC("Heroic", "Героический", 1.0, 1.0, false),
    RAID_MYTHIC("Mythic", "Эпохальный", 1.3, 1.25, false),
    /** Open world (no instance). */
    WORLD("World", "Мир", 1.0, 1.0, false);

    public final L10n name;
    public final double health;
    public final double damage;
    public final boolean keystone;

    Difficulty(String en, String ru, double health, double damage, boolean keystone) {
        this.name = L10n.of(en, ru);
        this.health = health;
        this.damage = damage;
        this.keystone = keystone;
    }

    public boolean isRaid() {
        return this == LFR || this == RAID_NORMAL || this == RAID_HEROIC || this == RAID_MYTHIC;
    }

    public String raidKey() {
        return switch (this) {
            case LFR -> "lfr";
            case RAID_HEROIC -> "heroic";
            case RAID_MYTHIC -> "mythic";
            default -> "normal";
        };
    }

    public static Difficulty byName(String n) {
        for (Difficulty d : values()) if (d.name().equalsIgnoreCase(n)) return d;
        return null;
    }
}
