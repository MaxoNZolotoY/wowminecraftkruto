package com.wowcraft.core.game;

/** Server configuration (stored as JSON in config/wowcraft.json by the adapter). */
public final class Config {
    /** Start new characters at max level with starter gear (quick play with friends). */
    public boolean startAtMaxLevel = true;
    public int starterItemLevel = 112;
    /** Allow bots to fill dungeon / raid / arena / battleground groups. */
    public boolean allowBots = true;
    /** Players can attack each other in the open world (otherwise only in arenas/BGs/duels). */
    public boolean openWorldPvp = false;
    /** Vanilla mobs get WoW-style health so abilities feel right against them. */
    public double vanillaMobHealthScale = 1.0;
    /** Experience multiplier. */
    public double xpRate = 1.0;
    /** Allow changing class at any time out of combat. */
    public boolean freeClassChange = true;
    public int maxPartySize = 5;
    public int maxRaidSize = 20;
    /** Items dropped on death outside instances (vanilla behaviour). Instances never drop items. */
    public boolean keepInventoryInWorld = false;
    /** Respawn players in instances at the last checkpoint instead of becoming a ghost. */
    public boolean instantReleaseInInstances = false;
    /** Debug: show extra info. */
    public boolean debug = false;
    public int battlegroundTeamSize = 5;
}
