package com.wowcraft.core.game;

import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.instance.DungeonDef;
import com.wowcraft.core.instance.Layout;
import com.wowcraft.core.mythic.MythicRun;
import com.wowcraft.core.npc.Difficulty;
import com.wowcraft.core.npc.Encounter;
import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A live instance (dungeon, raid, arena or battleground map). */
public final class InstanceRun {
    public enum State {BUILDING, READY, RUNNING, COMPLETED, CLOSING}

    public final String id;
    public final DungeonDef def;
    public final Difficulty difficulty;
    public final Layout layout;
    public final Vec3 origin;
    public final String worldKey;
    public final int slot;
    public State state = State.BUILDING;
    public final Set<UUID> members = new LinkedHashSet<>();
    public final List<UnitState> bots = new ArrayList<>();
    public final List<UnitState> npcs = new ArrayList<>();
    public final Map<String, UnitState> bossUnits = new LinkedHashMap<>();
    public final Set<String> bossesKilled = new LinkedHashSet<>();
    public final List<Encounter> encounters = new ArrayList<>();
    public MythicRun mythic;
    public AffixHandler affixes;
    public Vec3 checkpoint;
    public String groupId;
    public double createdAt;
    public double emptySince = -1;
    public int groupSize = 5;
    public boolean lootGiven;
    /** Mythic+ font of power activated by this player. */
    public UUID keyOwner;
    /** PvP match using this map, if any. */
    public String pvpMatchId;
    public double lastStatus;

    public InstanceRun(String id, DungeonDef def, Difficulty difficulty, Layout layout, Vec3 origin, String worldKey, int slot) {
        this.id = id;
        this.def = def;
        this.difficulty = difficulty;
        this.layout = layout;
        this.origin = origin;
        this.worldKey = worldKey;
        this.slot = slot;
        this.checkpoint = abs(layout.entrance);
    }

    /** Layout-relative -> world position. */
    public Vec3 abs(Vec3 rel) {
        return origin.add(rel);
    }

    public Vec3 rel(Vec3 world) {
        return world.sub(origin);
    }

    public int keyLevel() {
        return mythic != null ? mythic.level : 0;
    }

    public Encounter activeEncounter() {
        for (Encounter e : encounters) if (e.active) return e;
        return null;
    }

    public boolean allBossesDead() {
        return bossesKilled.size() >= def.bossIds().size();
    }
}
