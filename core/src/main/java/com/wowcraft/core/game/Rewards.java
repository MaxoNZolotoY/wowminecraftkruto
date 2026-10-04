package com.wowcraft.core.game;

import com.wowcraft.core.combat.Formulas;
import com.wowcraft.core.combat.UnitKind;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.instance.DungeonDef;
import com.wowcraft.core.instance.Dungeons;
import com.wowcraft.core.item.Currency;
import com.wowcraft.core.item.EquipSlot;
import com.wowcraft.core.item.EquipType;
import com.wowcraft.core.item.Equipment;
import com.wowcraft.core.item.ItemData;
import com.wowcraft.core.item.LootGenerator;
import com.wowcraft.core.item.Proficiency;
import com.wowcraft.core.item.TierSet;
import com.wowcraft.core.item.WeaponType;
import com.wowcraft.core.mythic.MythicTables;
import com.wowcraft.core.net.S2C;
import com.wowcraft.core.npc.Difficulty;
import com.wowcraft.core.npc.NpcRank;
import com.wowcraft.core.npc.NpcRegistry;
import com.wowcraft.core.npc.NpcTemplate;
import com.wowcraft.core.player.CharacterBuilder;
import com.wowcraft.core.player.PlayerProfile;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.util.L10n;
import com.wowcraft.core.util.Rng;

import java.util.ArrayList;
import java.util.List;

/** Experience, loot, currencies and starter equipment. */
public final class Rewards {
    private Rewards() {
    }

    // ------------------------------------------------------------------ experience

    public static long xpToNext(int level) {
        if (level >= Formulas.MAX_LEVEL) return 0;
        return Math.round(150 + 8.0 * level * level);
    }

    static long xpForKill(UnitState npc, int playerLevel) {
        NpcTemplate t = NpcRegistry.get(npc.templateId);
        double base = 10 + 3.0 * npc.level;
        double mult = t == null ? 1 : switch (t.rank) {
            case MINION -> 0.25;
            case NORMAL -> 1;
            case ELITE -> 2.5;
            case RARE -> 6;
            case MINIBOSS -> 5;
            case BOSS -> 15;
            default -> 0;
        };
        int diff = npc.level - playerLevel;
        if (diff < -8) mult *= 0.1;
        else if (diff < 0) mult *= 1 + diff * 0.1;
        else mult *= 1 + Math.min(4, diff) * 0.05;
        return Math.max(0, Math.round(base * mult));
    }

    static void giveXp(GameServer server, PlayerSession s, long amount) {
        PlayerProfile p = s.profile;
        if (p.level >= Formulas.MAX_LEVEL || amount <= 0) return;
        amount = Math.round(amount * server.config.xpRate);
        p.xp += amount;
        boolean leveled = false;
        while (p.level < Formulas.MAX_LEVEL && p.xp >= xpToNext(p.level)) {
            p.xp -= xpToNext(p.level);
            p.level++;
            leveled = true;
        }
        if (p.level >= Formulas.MAX_LEVEL) p.xp = 0;
        if (leveled) {
            Spec spec = p.spec();
            if (spec != null) {
                List<String> valid = CharacterBuilder.validTalents(com.wowcraft.core.content.Content.kit(spec.wowClass), spec, p.level, p.talentsFor(spec));
                p.talents.put(spec.id(), new ArrayList<>(valid));
            }
            server.applyCharacter(s);
            if (s.unit != null && s.unit.isAlive()) server.engine.setHealth(s.unit, s.unit.maxHealth());
            server.warn(s.uuid, L10n.of("Level " + p.level + "!", "Уровень " + p.level + "!"), 0xFFFFD040);
            server.platform.sound(s.unit.worldKey(), s.unit.position(), "level_up");
            if (p.level % 5 == 0 && p.level >= 10) {
                server.msg(s, L10n.of("You have a new talent point. Open talents with [N].", "У вас новое очко талантов. Откройте таланты клавишей [N]."),
                        0xFF40FF40);
            }
        }
        s.characterDirty = true;
    }

    /** XP and gold for open-world and instance kills. */
    static void onNpcKilled(GameServer server, UnitState npc, UnitState killer) {
        if (npc.owner != null || npc.kind == UnitKind.PET || npc.kind == UnitKind.TOTEM) return;
        List<PlayerSession> credit = new ArrayList<>();
        UnitState k = killer != null ? killer.master() : null;
        // everyone who is engaged and in the killer's group gets credit
        for (PlayerSession s : server.sessions()) {
            if (s.unit == null || s.unit.isDead()) continue;
            boolean engaged = npc.threat() != null && npc.threat().get(s.unit) > 0;
            boolean groupKill = k != null && k.groupId != null && k.groupId.equals(s.unit.groupId)
                    && server.engine.sameWorld(s.unit, npc) && s.unit.position().distance(npc.position()) < 60;
            if (engaged || groupKill || (k != null && k == s.unit)) credit.add(s);
        }
        if (credit.isEmpty()) return;
        NpcTemplate t = NpcRegistry.get(npc.templateId);
        boolean instanced = npc.instanceId != null;
        for (PlayerSession s : credit) {
            long xp = xpForKill(npc, s.profile.level);
            if (instanced) xp = Math.round(xp * 1.5);
            if (credit.size() > 1) xp = Math.round(xp * (1.0 / credit.size() + 0.3));
            giveXp(server, s, xp);
            if (t != null && t.rank != NpcRank.MINION) {
                long copper = Math.round((20 + npc.level * 12) * t.rank.healthMult * (0.7 + server.rng.nextDouble() * 0.6));
                s.profile.addCurrency(Currency.GOLD, copper);
            }
            s.profile.stat("kills", 1);
        }
        // vanilla world rares/elites can drop gear in the open world
        if (!instanced && t != null && (t.rank == NpcRank.RARE || t.rank == NpcRank.ELITE && server.rng.chance(0.15))) {
            for (PlayerSession s : credit) {
                Spec spec = s.profile.spec();
                if (spec == null) continue;
                int ilvl = Math.max(1, Math.min(MythicTables.NORMAL_DUNGEON_ILVL - 6, 10 + s.profile.level + (s.profile.level >= Formulas.MAX_LEVEL ? 20 : 0)));
                ItemData item = LootGenerator.generate(spec, LootGenerator.randomSlotFor(spec, server.rng), ilvl, server.rng, "world", "world");
                giveLoot(server, s, List.of(item), L10n.of(t.name.en(), t.name.ru()));
                s.profile.vault.worldActivities++;
            }
        }
    }

    // ------------------------------------------------------------------ loot

    static void giveLoot(GameServer server, PlayerSession s, List<ItemData> items, L10n source) {
        S2C.Loot msg = new S2C.Loot();
        msg.source = new S2C.Text(source.en(), source.ru());
        for (ItemData d : items) {
            server.platform.giveItem(s.uuid, d);
            msg.items.add(d);
        }
        server.send(s, msg);
        s.characterDirty = true;
    }

    static void giveCurrencies(GameServer server, PlayerSession s, java.util.Map<Currency, Long> amounts, L10n source) {
        S2C.Loot msg = new S2C.Loot();
        msg.source = new S2C.Text(source.en(), source.ru());
        for (var en : amounts.entrySet()) {
            s.profile.addCurrency(en.getKey(), en.getValue());
            msg.currencies.put(en.getKey().name(), en.getValue());
        }
        server.send(s, msg);
        s.characterDirty = true;
    }

    /** Personal loot roll for a boss kill: ~20% chance per player in dungeons, with bad luck protection. */
    static ItemData rollBossLoot(GameServer server, PlayerSession s, InstanceRun run, int ilvl, double chance, double tierChance) {
        Spec spec = s.profile.spec();
        if (spec == null) return null;
        double c = chance + s.profile.lootMisses * 0.1;
        if (!server.rng.chance(c)) {
            s.profile.lootMisses++;
            return null;
        }
        s.profile.lootMisses = 0;
        return LootGenerator.personalLoot(spec, run.def.lootTable, ilvl, server.rng, run.def.id, run.def.palette, tierChance);
    }

    static int bossItemLevel(InstanceRun run, int bossIndex) {
        if (run.difficulty.isRaid()) return MythicTables.raidItemLevel(run.difficulty.raidKey(), bossIndex);
        return switch (run.difficulty) {
            case NORMAL -> MythicTables.NORMAL_DUNGEON_ILVL;
            case HEROIC -> MythicTables.HEROIC_DUNGEON_ILVL;
            case MYTHIC -> MythicTables.MYTHIC_ZERO_ILVL;
            case MYTHIC_PLUS -> MythicTables.endOfRunItemLevel(run.keyLevel());
            default -> MythicTables.NORMAL_DUNGEON_ILVL;
        };
    }

    // ------------------------------------------------------------------ starter equipment

    static void starterKit(GameServer server, PlayerSession s) {
        PlayerProfile p = s.profile;
        Spec spec = p.spec();
        if (spec == null) return;
        if (!p.starterGearGiven) {
            p.starterGearGiven = true;
            starterGearForSpec(server, s, spec);
            p.addCurrency(Currency.GOLD, 50_0000);
            p.addCurrency(Currency.VALORSTONES, 500);
            p.addCurrency(Currency.CREST_WEATHERED, 30);
            server.platform.giveVanilla(s.uuid, "minecraft:cooked_beef", 32);
        }
        if (p.keystone == null && p.level >= Formulas.MAX_LEVEL) {
            List<String> pool = Dungeons.mythicPool();
            if (!pool.isEmpty()) {
                p.keystone = new PlayerProfile.Keystone(pool.get(server.rng.nextInt(pool.size())), 2);
                DungeonDef d = Dungeons.get(p.keystone.dungeonId);
                server.msg(s, L10n.of("You received a Mythic Keystone: " + d.name.en() + " +2.",
                        "Вы получили эпохальный ключ: " + d.name.ru() + " +2."), 0xFFA335EE);
            }
        }
    }

    /** A full set of starter equipment for a spec (replaces worn items, previous ones go to the inventory). */
    static void starterGearForSpec(GameServer server, PlayerSession s, Spec spec) {
        int ilvl = server.config.startAtMaxLevel ? server.config.starterItemLevel : Math.max(5, s.profile.level + 5);
        Equipment eq = fullSet(spec, ilvl, server.rng, "starter", false);
        for (var en : eq.all().entrySet()) {
            EquipSlot slot = en.getKey();
            if (slot.isVanillaSlot()) server.platform.equip(s.uuid, slot, en.getValue());
            else {
                ItemData old = s.profile.extraSlots.put(slot.name(), en.getValue());
                if (old != null) server.platform.giveItem(s.uuid, old);
            }
        }
        if (eq.get(EquipSlot.OFF_HAND) == null) {
            // two-hander: make sure a stale off-hand doesn't stay equipped
            server.platform.equip(s.uuid, EquipSlot.OFF_HAND, null);
        }
        server.msg(s, L10n.of("You received a set of equipment (item level " + ilvl + ").",
                "Вы получили комплект снаряжения (уровень предметов " + ilvl + ")."), 0xFF40FF40);
    }

    /** Generates a complete equipment set (used for starter gear and bots). */
    public static Equipment fullSet(Spec spec, int ilvl, Rng rng, String source, boolean tier) {
        Equipment eq = new Equipment();
        boolean twoHander = false, ranged = false, shield = false, offHandHeld = false;
        for (WeaponType w : Proficiency.preferred(spec)) {
            if (w.equipType == EquipType.RANGED) ranged = true;
            if (w.equipType == EquipType.TWO_HAND) twoHander = true;
            if (w == WeaponType.SHIELD) shield = true;
            if (w == WeaponType.OFF_HAND_HELD) offHandHeld = true;
        }
        for (EquipType t : new EquipType[]{EquipType.HEAD, EquipType.NECK, EquipType.SHOULDER, EquipType.BACK, EquipType.CHEST, EquipType.WRIST,
                EquipType.HANDS, EquipType.WAIST, EquipType.LEGS, EquipType.FEET}) {
            boolean isTier = false;
            if (tier) for (EquipType tp : TierSet.PIECES) if (tp == t) isTier = true;
            ItemData d = isTier ? LootGenerator.tierPiece(spec, t, ilvl, rng, source) : LootGenerator.generate(spec, t, ilvl, rng, source, "starter");
            eq.set(t.slots.get(0), d);
        }
        eq.set(EquipSlot.TRINKET_1, LootGenerator.generate(spec, EquipType.TRINKET, ilvl, rng, source, "starter"));
        eq.set(EquipSlot.TRINKET_2, LootGenerator.generate(spec, EquipType.TRINKET, ilvl, rng, source, "starter"));
        eq.set(EquipSlot.FINGER_1, LootGenerator.generate(spec, EquipType.FINGER, ilvl, rng, source, "starter"));
        eq.set(EquipSlot.FINGER_2, LootGenerator.generate(spec, EquipType.FINGER, ilvl, rng, source, "starter"));
        if (ranged) {
            eq.set(EquipSlot.MAIN_HAND, LootGenerator.generate(spec, EquipType.RANGED, ilvl, rng, source, "starter"));
        } else if (Proficiency.dualWields(spec)) {
            EquipType wt = twoHander && spec.wowClass == com.wowcraft.core.spec.WowClass.WARRIOR ? EquipType.TWO_HAND : EquipType.ONE_HAND;
            eq.set(EquipSlot.MAIN_HAND, LootGenerator.generate(spec, wt, ilvl, rng, source, "starter"));
            eq.set(EquipSlot.OFF_HAND, LootGenerator.generate(spec, wt, ilvl, rng, source, "starter"));
        } else if (shield) {
            eq.set(EquipSlot.MAIN_HAND, LootGenerator.generate(spec, EquipType.ONE_HAND, ilvl, rng, source, "starter"));
            eq.set(EquipSlot.OFF_HAND, LootGenerator.generate(spec, EquipType.SHIELD, ilvl, rng, source, "starter"));
        } else if (twoHander) {
            eq.set(EquipSlot.MAIN_HAND, LootGenerator.generate(spec, EquipType.TWO_HAND, ilvl, rng, source, "starter"));
        } else {
            eq.set(EquipSlot.MAIN_HAND, LootGenerator.generate(spec, EquipType.ONE_HAND, ilvl, rng, source, "starter"));
            if (offHandHeld) eq.set(EquipSlot.OFF_HAND, LootGenerator.generate(spec, EquipType.OFF_HAND, ilvl, rng, source, "starter"));
        }
        return eq;
    }

    // ------------------------------------------------------------------ dungeons

    /** End-of-dungeon currency rewards. */
    static void dungeonCompleted(GameServer server, PlayerSession s, InstanceRun run) {
        java.util.Map<Currency, Long> cur = new java.util.LinkedHashMap<>();
        Difficulty d = run.difficulty;
        cur.put(Currency.VALORSTONES, switch (d) {
            case NORMAL -> 30L;
            case HEROIC -> 45L;
            default -> 60L;
        });
        cur.put(Currency.GOLD, 30_0000L + run.keyLevel() * 5_0000L);
        if (d == Difficulty.MYTHIC_PLUS) {
            cur.put(MythicTables.crestFor(run.keyLevel()), (long) MythicTables.crestAmount(run.keyLevel(), run.mythic != null && run.mythic.timed()));
        } else if (d == Difficulty.HEROIC) {
            cur.put(Currency.CREST_WEATHERED, 10L);
        } else if (d == Difficulty.MYTHIC) {
            cur.put(Currency.CREST_CARVED, 10L);
        }
        giveCurrencies(server, s, cur, run.def.name);
        if (s.profile.level < Formulas.MAX_LEVEL) giveXp(server, s, xpToNext(s.profile.level) / 3);
    }
}
