package com.wowcraft.core.content.pve;

import com.wowcraft.core.aura.CcType;
import com.wowcraft.core.aura.DispelType;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.instance.RoomDef;
import com.wowcraft.core.item.EquipType;
import com.wowcraft.core.item.WeaponType;
import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.npc.BodyType;
import com.wowcraft.core.npc.BossScript;
import com.wowcraft.core.npc.BossScripts;
import com.wowcraft.core.npc.NpcRank;
import com.wowcraft.core.npc.NpcSpell;
import com.wowcraft.core.spell.Effects;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.util.Vec3;

/** Sandscour Tombs: a buried necropolis of god-kings guarded by constructs and scarabs. */
public final class SandscourTombs extends NpcKit {
    public static final String ID = "sandscour_tombs";

    public static void register() {
        cleave("sand_slash", "Sand Slash", "Песчаный разрез", School.PHYSICAL, 2.2, 90, 7, 1.5);
        strike("scarab_nip", "Nip", "Щипок", School.NATURE, 0.6);
        mend("sun_blessing", "Blessing of the Sun", "Благословение солнца", 0.3, 2.5);
        bolt("sand_bolt", "Sand Bolt", "Песчаная стрела", School.NATURE, 2.0, 2);
        cc("blinding_sand", "Blinding Sand", "Слепящий песок", CcType.DISORIENT, DispelType.MAGIC, 4);
        afflict("cast_blinding_sand", "Blinding Sand", "Слепящий песок", School.NATURE, "blinding_sand", 1.5, 25, true);
        debuffMod("mummy_curse", "Curse of the Tomb", "Проклятие гробницы", DispelType.CURSE, 20, 1, "Damage taken increased by 15%, maximum health reduced by 10%.",
                "Получаемый урон увеличен на 15%, максимальный запас здоровья снижен на 10%.", Modifier.taken(0.15), Modifier.maxHealth(-0.1));
        afflict("cast_mummy_curse", "Curse of the Tomb", "Проклятие гробницы", School.SHADOW, "mummy_curse", 0, 30, false);
        strike("mummy_strike", "Withering Strike", "Иссушающий удар", School.SHADOW, 1.6);
        strike("guardian_strike", "Guardian Strike", "Удар стража", School.PHYSICAL, 1.5);
        debuffMod("acid_spit", "Acid", "Кислота", DispelType.POISON, 10, 5, "Armor reduced by 10% per stack.", "Броня снижена на 10% за заряд.",
                Modifier.of(com.wowcraft.core.mod.ModType.ARMOR_PCT, -0.1));

        npc("tomb_guardian", "Tomb Guardian", "Страж гробницы", NpcRank.ELITE, BodyType.HUMANOID, "husk", 0xFFC0A060).scale(1.4).armor(0.4)
                .spell(sp("guardian_strike", 6, 2)).spell(sp("sand_slash", 12, 5));
        npc("scarab_swarm", "Scarab Swarm", "Рой скарабеев", NpcRank.MINION, BodyType.BEAST, "cave_spider", 0xFF406030).scale(0.6).speed(0.32)
                .spell(sp("scarab_nip", 2, 1));
        npc("sun_priest", "Sun Priest", "Жрец солнца", NpcRank.NORMAL, BodyType.HUMANOID, "husk", 0xFFFFE080).healer().hp(0.8)
                .spell(sp("sun_blessing", 7, 3, NpcSpell.Target.LOWEST_ALLY)).spell(sp("sand_bolt", 0, 1));
        npc("dust_caster", "Dustcaller", "Призыватель пыли", NpcRank.NORMAL, BodyType.HUMANOID, "husk", 0xFFD0B080).ranged(22).hp(0.8)
                .spell(sp("sand_bolt", 0, 0.5)).spell(sp("cast_blinding_sand", 18, 6, NpcSpell.Target.RANDOM_PLAYER));
        npc("mummy_lord", "Mummified Lord", "Мумифицированный лорд", NpcRank.ELITE, BodyType.HUMANOID, "husk", 0xFFE0D0B0).scale(1.3).speed(0.2)
                .spell(sp("mummy_strike", 6, 2)).spell(sp("cast_mummy_curse", 14, 4, NpcSpell.Target.RANDOM_PLAYER));

        boss("khet", "Scarab Queen Khet", "Королева скарабеев Кхет", BodyType.BEAST, "spider", 0xFF305020, "khet").hp(0.55).scale(2.6);
        boss("colossus", "Guardian Colossus", "Колосс-страж", BodyType.HUMANOID, "husk", 0xFFB09060, "colossus").hp(0.65).scale(2.6).armor(0.4);
        boss("anubet", "Pharaoh Anubet", "Фараон Анубет", BodyType.HUMANOID, "wither_skeleton", 0xFFFFD060, "anubet").hp(0.7).scale(1.9);
        BossScripts.register("khet", Khet::new);
        BossScripts.register("colossus", Colossus::new);
        BossScripts.register("anubet", Anubet::new);

        dungeon(ID, "SST", "Sandscour Tombs", "Гробницы Песчаной Бури",
                "A necropolis of god-kings swallowed by the dunes. Its guardians still obey a pharaoh who refused to die.",
                "Некрополь богов-царей, поглощенный дюнами. Его стражи все еще служат фараону, отказавшемуся умирать.", "sand", 24 * 60,
                RoomDef.entrance(15, 15, 8),
                RoomDef.trash(24, 24, 10, "statues", packs(pack("tomb_guardian", "sun_priest", "dust_caster"), pack("scarab_swarm", "scarab_swarm", "scarab_swarm", "scarab_swarm"),
                        pack("mummy_lord", "dust_caster"))),
                RoomDef.boss(28, 11, "platform", "khet"),
                RoomDef.hall(14, 28, 9, packs(pack("tomb_guardian", "tomb_guardian"), pack("sun_priest", "dust_caster", "mummy_lord"))),
                RoomDef.boss(30, 14, "pillars", "colossus"),
                RoomDef.trash(22, 26, 10, "rubble", packs(pack("mummy_lord", "mummy_lord", "sun_priest"), pack("scarab_swarm", "scarab_swarm", "scarab_swarm", "tomb_guardian"))),
                RoomDef.boss(30, 12, "statues", "anubet"));

        item(ID, "st_polearm", "Khopesh of the God-King", "Хопеш бога-царя", EquipType.ONE_HAND, WeaponType.SWORD_1H, "STRENGTH",
                stats("CRIT", "MASTERY"), null, MELEE, null, null);
        item(ID, "st_gun", "Sandblaster Hand Cannon", "Ручная пескоструйная пушка", EquipType.RANGED, WeaponType.GUN, "AGILITY", stats("HASTE", "CRIT"), null, DPS, null, null);
        item(ID, "st_scepter", "Scepter of the Sun", "Скипетр солнца", EquipType.ONE_HAND, WeaponType.MACE_1H, "INTELLECT",
                stats("CRIT", "VERSATILITY"), null, CASTER, null, null);
        item(ID, "st_trinket_idol", "Scarab Idol", "Идол скарабея", EquipType.TRINKET, null, "ADAPTIVE", stats("HASTE", "MASTERY"), "idol_of_fury", ANY, null, null);
        item(ID, "st_trinket_heart", "Sunstone Heart", "Сердце солнечного камня", EquipType.TRINKET, null, "ADAPTIVE", stats("VERSATILITY", "CRIT"),
                "heart_of_the_mountain", TANK, null, null);
        item(ID, "st_shoulders", "Mantle of the Pharaoh", "Мантия фараона", EquipType.SHOULDER, null, "ADAPTIVE", stats("CRIT", "HASTE"), null, ANY, null, null);
        item(ID, "st_wrist", "Linen-Wrapped Bindings", "Обмотанные льном наручи", EquipType.WRIST, null, "ADAPTIVE", stats("MASTERY", "VERSATILITY"), null, ANY, null, null);
        item(ID, "st_ring", "Seal of Anubet", "Печать Анубет", EquipType.FINGER, null, null, stats("CRIT", "HASTE"), null, ANY, null, null);
    }

    /** Khet: scarab swarms, burrow ring, acid lines. */
    static final class Khet extends BossScript {
        @Override
        public void onPull() {
            every("swarm", 12, 25, "Scarab Swarm", "Рой скарабеев", () -> {
                warn("Scarabs! AoE them down!", "Скарабеи! Уничтожьте их по площади!");
                addsAround("scarab_swarm", 5, 9);
            });
            every("burrow", 20, 30, "Burrow", "Зарывание", () -> ringAround(5, 16, 3, ORANGE, Effects.all(hit(School.NATURE, 5), knock(1.0))));
            every("acid", 8, 14, "Acid Spit", "Кислотный плевок", () -> {
                for (UnitState p : randomPlayers(2)) lineTo(p, 25, 3, 2, GREEN, Effects.all(hit(School.NATURE, 4), Effects.aura("acid_spit")));
            });
            every("bite", 4, 7, null, null, () -> {
                UnitState t = tank();
                if (t != null) {
                    hitUnit(t, hit(School.PHYSICAL, 3));
                    applyAura(t, "acid_spit");
                }
            });
        }
    }

    /** Colossus: sand storms, pulverize soak, stone gaze cone. */
    static final class Colossus extends BossScript {
        @Override
        public void onPull() {
            say("INTRUDERS. PURGE.", "НАРУШИТЕЛИ. УНИЧТОЖИТЬ.");
            every("storm", 10, 20, "Sand Storm", "Песчаная буря", () -> {
                for (int i = 0; i < 3; i++) poolAt(randomPointAround(4, 14), 3.5, 15, ORANGE, hit(School.NATURE, 1.0));
            });
            every("pulverize", 15, 25, "Pulverize", "Сокрушение", () -> {
                Vec3 p = randomPointAround(5, 10);
                soakAt(p, 4, 4.5, 3, hit(School.PHYSICAL, 15), hit(School.PHYSICAL, 6));
                warn("Pulverize: 3 players must soak!", "Сокрушение: нужно 3 игрока в круге!", YELLOW);
            });
            every("gaze", 18, 22, "Stone Gaze", "Каменный взор", () -> coneFromBoss(70, 22, 3, RED, Effects.all(hit(School.NATURE, 6), Effects.aura("generic_stun"))));
            every("fist", 5, 9, null, null, () -> {
                UnitState t = tank();
                if (t != null) hitUnit(t, hit(School.PHYSICAL, 4));
            });
        }
    }

    /** Anubet: curse dispels, sands of time raid damage, sun beams. */
    static final class Anubet extends BossScript {
        @Override
        public void onPull() {
            say("I am eternal. You are dust.", "Я вечен. Вы — прах.");
            every("curse", 10, 18, "Curse of the Tomb", "Проклятие гробницы", () -> {
                for (UnitState p : randomPlayers(2)) applyAura(p, "mummy_curse");
                warn("Remove the curses!", "Снимите проклятия!", PURPLE);
            });
            every("sands", 22, 30, "Sands of Time", "Пески времени", () -> {
                warn("Sands of Time!", "Пески времени!");
                after(2.5, () -> raidDamage(hit(School.ARCANE, 3)));
            });
            every("beam", 7, 13, "Sun Beam", "Солнечный луч", () -> {
                for (UnitState p : randomNonTanks(2)) lineTo(p, 30, 2.5, 2, YELLOW, hit(School.HOLY, 5));
            });
            every("smite", 4, 7, null, null, () -> {
                UnitState t = tank();
                if (t != null) hitUnit(t, hit(School.HOLY, 3.5));
            });
        }

        @Override
        public double[] thresholds() {
            return new double[]{0.5};
        }

        @Override
        public void onHealthBelow(double f) {
            phase(2);
            say("Rise, my servants!", "Восстаньте, мои слуги!");
            addsAround("mummy_lord", 2, 9);
        }
    }
}
