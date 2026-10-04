package com.wowcraft.core.game;

import com.wowcraft.core.combat.UnitState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A party (up to 5) or raid. Bots are full members. */
public final class Group {
    public final String id;
    public UUID leader;
    public final Set<UUID> members = new LinkedHashSet<>();
    public final List<UnitState> bots = new ArrayList<>();
    public boolean raid;
    public final Meter meter = new Meter();
    public double readyCheckUntil = -1;
    public final Map<UUID, Integer> ready = new HashMap<>();
    /** Roles chosen by players for the group finder. */
    public final Map<UUID, String> roles = new HashMap<>();

    public Group(String id, UUID leader) {
        this.id = id;
        this.leader = leader;
        members.add(leader);
    }

    public int size() {
        return members.size() + bots.size();
    }
}
