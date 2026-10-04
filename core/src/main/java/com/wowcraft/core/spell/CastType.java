package com.wowcraft.core.spell;

public enum CastType {
    INSTANT,
    CAST,
    CHANNEL,
    /** Evoker-style: hold to charge stages, release to fire. */
    EMPOWER
}
