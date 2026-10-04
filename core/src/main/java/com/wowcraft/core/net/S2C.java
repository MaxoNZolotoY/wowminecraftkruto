package com.wowcraft.core.net;

import com.wowcraft.core.item.ItemData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Server -> client messages (plain data objects). */
public final class S2C {
    private S2C() {
    }

    /** Localized text sent from the server. */
    public static final class Text {
        public String en;
        public String ru;

        public Text() {
        }

        public Text(String en, String ru) {
            this.en = en;
            this.ru = ru;
        }
    }

    /** Character basics (on join, level up, respec). */
    public static final class Character {
        public boolean classChosen;
        public String wowClass;
        public String spec;
        public int level;
        public long xp;
        public long xpNext;
        public List<String> abilities = new ArrayList<>();
        public String[] bar;
        public List<String> talents = new ArrayList<>();
        public Map<String, Long> currencies = new HashMap<>();
        public String keystoneDungeon;
        public int keystoneLevel;
        public double mythicRating;
        public double itemLevel;
        public int honorLevel;
        public String trinket1;
        public String trinket2;
        public Map<String, Integer> pvpRatings = new HashMap<>();
    }

    public static final class AuraInfo {
        public String id;
        public int stacks;
        public float remaining;
        public float duration;
        public boolean mine;
        public int absorb;
    }

    public static final class CooldownInfo {
        public String id;
        public float remaining;
        public float duration;
        public int charges;
        public int maxCharges;
    }

    public static final class CastInfo {
        public String ability;
        public float progress;
        public float total;
        public boolean channel;
        public boolean empower;
        public int stage;
        public boolean interruptible;
    }

    /** Frequent update of the local player's combat state. */
    public static final class Self {
        public int entityId;
        public double health;
        public double maxHealth;
        public double absorb;
        public Map<String, Double> resources = new HashMap<>();
        public Map<String, Double> resourceMax = new HashMap<>();
        public float[] runes;
        public boolean inCombat;
        public float gcdRemaining;
        public float gcdDuration;
        public CastInfo cast;
        public List<AuraInfo> auras = new ArrayList<>();
        public List<CooldownInfo> cooldowns = new ArrayList<>();
        /** Bar slot -> ability id actually used (after replacements like Metamorphosis). */
        public String[] resolvedBar;
        /** Bar slots that are currently usable / in range / highlighted. */
        public boolean[] usable;
        public boolean[] inRange;
        public int suggested = -1;
        public int targetId = -1;
        public boolean ghost;
        public double speedPct;
        public String form;
    }

    /** Another unit's frame (target, party member, boss). */
    public static final class Unit {
        public int entityId;
        public String uuid;
        public String name;
        public int level;
        public int color;
        public String rank;
        public String role;
        public String wowClass;
        public double health;
        public double maxHealth;
        public double absorb;
        public String power;
        public double powerCur;
        public double powerMax;
        public CastInfo cast;
        public List<AuraInfo> auras = new ArrayList<>();
        public boolean dead;
        public boolean hostile;
        public boolean inCombat;
        public float threat = -1;
        public int targetId = -1;
        public boolean boss;
    }

    public static final class Units {
        public List<Unit> units = new ArrayList<>();
        public List<Integer> party = new ArrayList<>();
        public List<Integer> bosses = new ArrayList<>();
    }

    public static final class CombatText {
        public int sourceId;
        public int targetId;
        public double amount;
        public double absorbed;
        public boolean crit;
        public boolean heal;
        public boolean periodic;
        public boolean immune;
        public String school;
        public String ability;
        public double x, y, z;
    }

    public static final class CombatTextBatch {
        public List<CombatText> events = new ArrayList<>();
    }

    public static final class Error {
        public String code;
        public String ability;
    }

    public static final class Vfx {
        public String key;
        public int sourceId = -1;
        public int targetId = -1;
        public double x, y, z;
        public boolean hasPoint;
        public double param;
    }

    public static final class Telegraph {
        public int id;
        public boolean remove;
        public String shape;
        public double x, y, z;
        public double radius, inner, angle, length, width;
        public float yaw;
        public float duration;
        public int color;
        public int followId = -1;
        public boolean soak;
    }

    public static final class Area {
        public int id;
        public boolean remove;
        public double x, y, z;
        public double radius;
        public float duration;
        public int color;
        public String key;
        public boolean hostile;
        public int followId = -1;
    }

    public static final class TimerBar {
        public int id;
        public Text label;
        public float remaining;
        public float total;
        public int color;
    }

    public static final class BossTimers {
        public List<TimerBar> bars = new ArrayList<>();
    }

    public static final class Warning {
        public Text text;
        public int color;
        public String sound;
    }

    public static final class Chat {
        public Text text;
        public int color = 0xFFFFFFFF;
    }

    public static final class InstanceStatus {
        public boolean active;
        public String dungeon;
        public Text name;
        public String difficulty;
        public int keyLevel;
        public List<String> affixes = new ArrayList<>();
        public float timer;
        public float elapsed;
        public boolean running;
        public float countdown;
        public int deaths;
        public float deathPenalty;
        public double forces;
        public List<Text> bossNames = new ArrayList<>();
        public List<Boolean> bossKilled = new ArrayList<>();
        public boolean completed;
        public int upgrades;
        public int battleRes;
    }

    public static final class GroupMember {
        public String uuid;
        public String name;
        public String wowClass;
        public String spec;
        public String role;
        public boolean online;
        public boolean leader;
        public boolean bot;
        public int entityId = -1;
        public int ready;
        public double itemLevel;
        public double rating;
    }

    public static final class Group {
        public boolean inGroup;
        public boolean raid;
        public List<GroupMember> members = new ArrayList<>();
        public boolean readyCheck;
        public float readyCheckRemaining;
    }

    public static final class Invite {
        public String from;
        public String fromName;
    }

    public static final class Listing {
        public String id;
        public String leader;
        public String title;
        public String dungeon;
        public int keyLevel;
        public int members;
        public double minRating;
        public List<String> roles = new ArrayList<>();
        public boolean applied;
    }

    public static final class Applicant {
        public String uuid;
        public String name;
        public String wowClass;
        public String spec;
        public String role;
        public double rating;
        public double itemLevel;
    }

    public static final class Lfg {
        public String queue;
        public float queueTime;
        public List<Listing> listings = new ArrayList<>();
        public List<Applicant> applicants = new ArrayList<>();
        public List<String> weeklyAffixes = new ArrayList<>();
        public List<String> mythicPool = new ArrayList<>();
    }

    public static final class PvpTeam {
        public String name;
        public int color;
        public int score;
        public List<String> players = new ArrayList<>();
        public List<Boolean> alive = new ArrayList<>();
    }

    public static final class PvpStatus {
        public boolean active;
        public String mode;
        public Text name;
        public float countdown;
        public float elapsed;
        public float timeLimit;
        public List<PvpTeam> teams = new ArrayList<>();
        public List<String> objectives = new ArrayList<>();
        public int dampening;
        public String result;
        public int ratingChange;
        public int myTeam;
    }

    public static final class Loot {
        public List<ItemData> items = new ArrayList<>();
        public Text source;
        public Map<String, Long> currencies = new HashMap<>();
    }

    public static final class Vault {
        public int[][] slots;
        public List<Integer> mythicRuns = new ArrayList<>();
        public List<Integer> raidKills = new ArrayList<>();
        public int world;
        public List<ItemData> rewards = new ArrayList<>();
        public boolean claimed;
    }

    public static final class MeterRow {
        public String name;
        public int color;
        public double damage;
        public double healing;
        public double damageTaken;
        public int deaths;
        public int interrupts;
    }

    public static final class Meter {
        public float duration;
        public String segment;
        public List<MeterRow> rows = new ArrayList<>();
    }

    public static final class LeaderboardRow {
        public String dungeon;
        public int level;
        public float time;
        public boolean timed;
        public List<String> party = new ArrayList<>();
    }

    public static final class Leaderboard {
        public List<LeaderboardRow> rows = new ArrayList<>();
        public List<String> topPlayers = new ArrayList<>();
        public List<Double> topRatings = new ArrayList<>();
    }

    public static final class OpenScreen {
        public String screen;
    }

    public static final class Stats {
        public Map<String, Double> values = new HashMap<>();
    }
}
