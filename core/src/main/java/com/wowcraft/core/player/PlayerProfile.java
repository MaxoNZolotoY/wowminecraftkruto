package com.wowcraft.core.player;

import com.wowcraft.core.content.ClassKit;
import com.wowcraft.core.content.Content;
import com.wowcraft.core.item.Currency;
import com.wowcraft.core.item.ItemData;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.spec.WowClass;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything persistent about a player's WoW character (equipment lives in the Minecraft inventory).
 * Plain fields so it can be stored as JSON.
 */
public final class PlayerProfile {
    public String uuid;
    public String name = "";
    public String wowClass;
    public String spec;
    public int level = 1;
    public long xp;
    /** Talent choices per spec id. */
    public Map<String, List<String>> talents = new HashMap<>();
    /** Action bar layouts per spec id (12 ability ids, nulls allowed). */
    public Map<String, String[]> actionBars = new HashMap<>();
    public Map<String, Long> currencies = new HashMap<>();
    public Keystone keystone;
    /** Best Mythic+ run per dungeon id. */
    public Map<String, MythicBest> mythicBest = new HashMap<>();
    public int mythicSeason = 1;
    public VaultState vault = new VaultState();
    /** "raidId:difficulty" -> boss ids killed this lockout week. */
    public Map<String, Set<String>> lockouts = new HashMap<>();
    public long lockoutWeek = -1;
    public Map<String, PvpRating> pvp = new HashMap<>();
    public int honorLevel = 1;
    public long honorProgress;
    public Set<String> achievements = new LinkedHashSet<>();
    public Map<String, Long> statistics = new HashMap<>();
    /** Where to return after leaving an instance. */
    public String returnWorld;
    public double returnX, returnY, returnZ;
    /** Bad luck protection: failed personal loot rolls in a row. */
    public int lootMisses;
    public boolean starterGearGiven;
    /** Extra equipment slots (neck, shoulders, back, wrist, hands, waist, rings, trinkets) as item JSON per slot name. */
    public Map<String, ItemData> extraSlots = new HashMap<>();
    /** Pet name chosen by hunters. */
    public String petName;
    public boolean classChosen;

    public WowClass wowClass() {
        return WowClass.byId(wowClass);
    }

    public Spec spec() {
        return Spec.byId(spec);
    }

    public long currency(Currency c) {
        return currencies.getOrDefault(c.name(), 0L);
    }

    public void addCurrency(Currency c, long amount) {
        long v = Math.max(0, Math.min(c.cap, currency(c) + amount));
        currencies.put(c.name(), v);
    }

    public boolean spend(Currency c, long amount) {
        if (currency(c) < amount) return false;
        currencies.put(c.name(), currency(c) - amount);
        return true;
    }

    public List<String> talentsFor(Spec s) {
        return talents.computeIfAbsent(s.id(), k -> new ArrayList<>());
    }

    public String[] barFor(Spec s) {
        String[] bar = actionBars.get(s.id());
        if (bar == null || bar.length != ClassKit.BAR_SIZE) {
            String[] def = Content.kit(s.wowClass).defaultBars.get(s);
            bar = def == null ? new String[ClassKit.BAR_SIZE] : def.clone();
            actionBars.put(s.id(), bar);
        }
        return bar;
    }

    public void stat(String key, long delta) {
        statistics.merge(key, delta, Long::sum);
    }

    public PvpRating pvp(String bracket) {
        return pvp.computeIfAbsent(bracket, k -> new PvpRating());
    }

    /** A Mythic Keystone. */
    public static final class Keystone {
        public String dungeonId;
        public int level;

        public Keystone() {
        }

        public Keystone(String dungeonId, int level) {
            this.dungeonId = dungeonId;
            this.level = level;
        }
    }

    /** Best run in a dungeon this season. */
    public static final class MythicBest {
        public int level;
        public double time;
        public boolean timed;
        public double score;
        public long when;
        public List<String> affixes = new ArrayList<>();
        public List<String> party = new ArrayList<>();
    }

    /** PvP rating in one bracket. */
    public static final class PvpRating {
        public int rating = 0;
        public int mmr = 1500;
        public int seasonHigh;
        public int played;
        public int won;
        public int weeklyPlayed;
        public int weeklyWon;
    }

    /** Great Vault progress for the current week plus rewards waiting from last week. */
    public static final class VaultState {
        public long week = -1;
        /** Keystone levels completed this week (0 = M0). */
        public List<Integer> mythicRuns = new ArrayList<>();
        /** Item levels of raid bosses killed this week. */
        public List<Integer> raidKills = new ArrayList<>();
        /** PvP wins / world activities completed this week. */
        public int pvpWins;
        public int worldActivities;
        /** Rewards to pick one from (generated at weekly reset). */
        public List<ItemData> pending = new ArrayList<>();
        public boolean claimed;
    }
}
