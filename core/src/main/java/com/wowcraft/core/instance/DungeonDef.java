package com.wowcraft.core.instance;

import com.wowcraft.core.npc.Difficulty;
import com.wowcraft.core.util.L10n;

import java.util.List;

/** A dungeon or raid: rooms in order, theme, timer, loot. */
public final class DungeonDef {
    public enum Type {DUNGEON, RAID, ARENA, BATTLEGROUND}

    public final String id;
    public final L10n name;
    public final L10n description;
    public final Type type;
    public final String palette;
    public final int level;
    /** Mythic+ timer in seconds. */
    public final double timer;
    public final String lootTable;
    public final List<RoomDef> rooms;
    public final List<Difficulty> difficulties;
    /** Short code used in chat/UI (e.g. "GD"). */
    public final String shortName;
    public final int minPlayers;
    public final int maxPlayers;

    public DungeonDef(String id, String shortName, L10n name, L10n description, Type type, String palette, int level, double timer,
                      String lootTable, List<RoomDef> rooms, List<Difficulty> difficulties, int minPlayers, int maxPlayers) {
        this.id = id;
        this.shortName = shortName;
        this.name = name;
        this.description = description;
        this.type = type;
        this.palette = palette;
        this.level = level;
        this.timer = timer;
        this.lootTable = lootTable;
        this.rooms = List.copyOf(rooms);
        this.difficulties = List.copyOf(difficulties);
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
    }

    public List<String> bossIds() {
        List<String> out = new java.util.ArrayList<>();
        for (RoomDef r : rooms) out.addAll(r.bosses());
        return out;
    }

    public boolean isRaid() {
        return type == Type.RAID;
    }
}
