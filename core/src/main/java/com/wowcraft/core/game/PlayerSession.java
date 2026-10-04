package com.wowcraft.core.game;

import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.player.PlayerProfile;
import com.wowcraft.core.util.L10n;

import java.util.UUID;

/** Runtime state of an online player. */
public final class PlayerSession {
    public final UUID uuid;
    public String name;
    public final PlayerProfile profile;
    public UnitState unit;
    public L10n.Lang lang = L10n.Lang.EN;
    public String instanceId;
    public String pvpMatchId;
    public boolean ghost;
    public double ghostSince;
    public double lastSelfSync;
    public double lastUnitsSync;
    public double lastCharacterSync = -1e9;
    public boolean characterDirty = true;
    public boolean equipmentDirty = true;
    public double lastEquipmentCheck;
    public long equipmentHash;
    public String pendingInviteFrom;
    public double pendingInviteAt;
    public int readyState;
    public double lastSuggest;
    public int suggested = -1;
    public double lastMeterSync;
    public double lastInstanceSync;
    public double lastPvpSync;
    /** Pending ready-check answer. */
    public double releaseAvailableAt;
    /** Damage meter used while not in a group. */
    public final Meter meter = new Meter();

    public PlayerSession(UUID uuid, String name, PlayerProfile profile) {
        this.uuid = uuid;
        this.name = name;
        this.profile = profile;
    }
}
