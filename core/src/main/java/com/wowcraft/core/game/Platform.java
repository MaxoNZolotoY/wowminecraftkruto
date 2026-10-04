package com.wowcraft.core.game;

import com.wowcraft.core.combat.UnitKind;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.combat.WorldAccess;
import com.wowcraft.core.instance.Layout;
import com.wowcraft.core.instance.Palette;
import com.wowcraft.core.item.Equipment;
import com.wowcraft.core.item.ItemData;
import com.wowcraft.core.npc.NpcTemplate;
import com.wowcraft.core.util.Vec3;

import java.util.UUID;

/** Everything the game server needs from Minecraft. Implemented by the Fabric adapter. */
public interface Platform {
    // ---------------------------------------------------------------- queries

    boolean lineOfSight(String worldKey, Vec3 from, Vec3 to);

    Vec3 safeDestination(String worldKey, Vec3 from, Vec3 to);

    /** Key of the dimension used for instances (dungeons, raids, arenas, battlegrounds). */
    String instanceWorld();

    String defaultWorld();

    // ---------------------------------------------------------------- players

    void send(UUID player, Object message);

    void teleport(UUID player, String worldKey, Vec3 pos, float yaw);

    /** Ghost mode for players who died inside an instance (spectator-like, waiting for a resurrection). */
    void setGhost(UUID player, boolean ghost);

    /** Items in the vanilla equipment slots (head, chest, legs, feet, main hand, off hand) that carry WoW item data. */
    Equipment readEquipment(UUID player);

    /** Puts an item into a vanilla equipment slot, moving the previous item into the inventory. */
    void equip(UUID player, com.wowcraft.core.item.EquipSlot slot, ItemData item);

    /** Replaces the item data of the item currently in a vanilla equipment slot (upgrades, gems). */
    void replaceEquipped(UUID player, com.wowcraft.core.item.EquipSlot slot, ItemData item);

    /** Puts an item into the player's inventory (drops it at their feet when full). */
    void giveItem(UUID player, ItemData item);

    /** Gives a raw Minecraft item (e.g. potions, keystone display item). */
    default void giveVanilla(UUID player, String itemId, int count) {
    }

    // ---------------------------------------------------------------- npcs

    /** Creates the entity for an NPC / pet / bot and registers its unit with the engine. */
    UnitState createNpcBody(String worldKey, Vec3 pos, float yaw, NpcTemplate template, UnitKind kind, String displayName);

    void removeBody(UnitState unit);

    /** Moves an NPC / bot entity to another world (or position) keeping its unit. */
    void moveBody(UnitState unit, String worldKey, Vec3 pos);

    // ---------------------------------------------------------------- world

    /** Places the layout's blocks at origin (asynchronously over several ticks), then runs done. */
    void build(String worldKey, Vec3 origin, Layout layout, Palette palette, Runnable done);

    /** Clears the area used by a layout. */
    void clear(String worldKey, Vec3 origin, Layout layout, Runnable done);

    void forceLoad(String worldKey, Vec3 origin, Layout layout, boolean load);

    /** Places a special block / marker ("font_of_power", "exit_portal", "flag_alliance", "node_0"...). */
    void marker(String worldKey, Vec3 pos, String kind, boolean place);

    void sound(String worldKey, Vec3 pos, String sound);

    void log(String message);

    default WorldAccess asWorldAccess(java.util.function.Supplier<WorldAccess> fallback) {
        return fallback.get();
    }
}
