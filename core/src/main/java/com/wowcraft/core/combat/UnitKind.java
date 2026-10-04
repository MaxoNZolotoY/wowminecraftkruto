package com.wowcraft.core.combat;

public enum UnitKind {
    PLAYER,
    /** AI-controlled player-like unit (follower dungeon bots, arena/BG bots). */
    BOT,
    NPC,
    PET,
    /** Static summons (totems, statues). */
    TOTEM,
    /** A vanilla Minecraft mob that got involved in combat. */
    VANILLA
}
