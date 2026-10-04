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

/** Grove of Withering: a sacred forest rotting from a blighted heart. */
public final class GroveOfWithering extends NpcKit {
    public static final String ID = "grove_of_withering";

    public static void register() {
        strike("root_lash", "Root Lash", "Удар корнями", School.NATURE, 1.6, Effects.aura("blight_roots"));
        cc("blight_roots", "Blighted Roots", "Гнилые корни", CcType.ROOT, DispelType.MAGIC, 4);
        bolt("plague_bolt", "Plague Bolt", "Чумная стрела", School.NATURE, 1.8, 2, Effects.aura("rot_poison"));
        dot("rot_poison", "Rot Poison", "Гнилой яд", School.NATURE, DispelType.POISON, 0.4, 12, 5);
        mend("rejuvenate_npc", "Rejuvenate", "Омоложение", 0.25, 2);
        strike("bat_bite", "Spore Bite", "Спорный укус", School.NATURE, 0.7);
        charge("stag_charge", "Antler Charge", "Удар рогами", 2.0, Effects.knockback(0.8));
        strike("stag_gore", "Gore", "Боднуть", School.PHYSICAL, 1.5);
        buffMod("bark_skin_npc", "Withered Bark", "Иссохшая кора", DispelType.MAGIC, 12, "Damage taken reduced by 35%.", "Получаемый урон снижен на 35%.",
                Modifier.taken(-0.35));
        allyBuff("cast_bark_skin", "Withered Bark", "Иссохшая кора", "bark_skin_npc", 1.5);
        com.wowcraft.core.content.Registry.register(com.wowcraft.core.aura.AuraDef.debuff("rotting_wound", "Rotting Wound", "Гниющая рана")
                .duration(15).stacks(10).perCaster(false).dispel(DispelType.POISON).mod(Modifier.healingTaken(-0.05))
                .periodic(2, Effects.damage(School.NATURE, com.wowcraft.core.spell.Scaling.sp(0.3))).unhastedTicks()
                .desc("Healing taken reduced by 5% per stack, taking Nature damage.", "Получаемое исцеление снижено на 5% за заряд, получает урон от сил природы.")
                .build());
        strike("sapling_whack", "Whack", "Хлесть", School.NATURE, 0.6);
        buffMod("druid_bear", "Bear Form", "Облик медведя", null, 0, "Armor and health increased.", "Броня и здоровье увеличены.", Modifier.taken(-0.25));
        buffMod("druid_cat", "Cat Form", "Облик кошки", null, 0, "Attack speed increased.", "Скорость атаки увеличена.", Modifier.haste(0.3));

        npc("blight_treant", "Blighted Treant", "Гнилой древень", NpcRank.ELITE, BodyType.HUMANOID, "husk", 0xFF5A6A2A).scale(1.6).speed(0.2)
                .spell(sp("root_lash", 10, 3));
        npc("rot_shaman", "Rot Shaman", "Шаман гнили", NpcRank.NORMAL, BodyType.HUMANOID, "zombie", 0xFF6A8A3A).ranged(22).hp(0.8)
                .spell(sp("plague_bolt", 0, 0.5)).spell(sp("cast_bark_skin", 20, 6, NpcSpell.Target.LOWEST_ALLY));
        npc("spore_bat", "Spore Bat", "Спорная мышь", NpcRank.MINION, BodyType.BEAST, "cave_spider", 0xFF9AAA50).scale(0.6).speed(0.34).spell(sp("bat_bite", 2.5, 1));
        npc("withered_stag", "Withered Stag", "Иссохший олень", NpcRank.ELITE, BodyType.BEAST, "spider", 0xFF8A7A5A).scale(1.3).speed(0.36)
                .spell(sp("stag_charge", 15, 3, NpcSpell.Target.RANDOM_RANGED)).spell(sp("stag_gore", 6, 1));
        npc("thorn_druid", "Thorn Druid", "Друид шипов", NpcRank.NORMAL, BodyType.HUMANOID, "zombie", 0xFF40A040).healer().hp(0.8)
                .spell(sp("rejuvenate_npc", 6, 2, NpcSpell.Target.LOWEST_ALLY)).spell(sp("plague_bolt", 0, 1));
        npc("blight_sapling", "Blight Sapling", "Гнилой росток", NpcRank.MINION, BodyType.HUMANOID, "husk", 0xFF7A9A3A).scale(0.6).forces(0)
                .spell(sp("sapling_whack", 3, 1));

        boss("ysra", "Thornmother Ysra", "Матерь шипов Исра", BodyType.HUMANOID, "husk", 0xFF609030, "ysra").hp(0.55).scale(2.0);
        boss("rotheart", "Rotheart the Blighted", "Гнилосердый", BodyType.HUMANOID, "husk", 0xFF5A4A2A, "rotheart").hp(0.6).scale(2.2);
        boss("malgoth", "Archdruid Malgoth", "Верховный друид Малгот", BodyType.HUMANOID, "zombie", 0xFF3A7A3A, "malgoth").hp(0.7).scale(1.8);
        BossScripts.register("ysra", Ysra::new);
        BossScripts.register("rotheart", Rotheart::new);
        BossScripts.register("malgoth", Malgoth::new);

        dungeon(ID, "GW", "Grove of Withering", "Роща Увядания",
                "The heart of an ancient grove is rotting. Its guardians have turned on everything alive.",
                "Сердце древней рощи гниет. Ее стражи обратились против всего живого.", "grove", 24 * 60,
                RoomDef.entrance(15, 15, 8),
                RoomDef.trash(24, 24, 10, "rubble", packs(pack("blight_treant", "rot_shaman", "thorn_druid"), pack("spore_bat", "spore_bat", "spore_bat", "spore_bat"),
                        pack("withered_stag", "rot_shaman"))),
                RoomDef.boss(26, 11, "platform", "ysra"),
                RoomDef.hall(14, 28, 9, packs(pack("withered_stag", "withered_stag"), pack("rot_shaman", "rot_shaman", "thorn_druid"))),
                RoomDef.boss(26, 11, "water", "rotheart"),
                RoomDef.trash(22, 26, 10, "pillars", packs(pack("blight_treant", "blight_treant"), pack("spore_bat", "spore_bat", "spore_bat", "thorn_druid"))),
                RoomDef.boss(30, 12, "platform", "malgoth"));

        item(ID, "gw_glaive", "Thornrender Glaive", "Глефа разрывателя шипов", EquipType.ONE_HAND, WeaponType.WARGLAIVE, "AGILITY",
                stats("CRIT", "HASTE"), null, MELEE, null, null);
        item(ID, "gw_staff", "Branch of the Elder Grove", "Ветвь древней рощи", EquipType.TWO_HAND, WeaponType.STAFF, "AGILITY",
                stats("MASTERY", "VERSATILITY"), null, MELEE, null, null);
        item(ID, "gw_offhand", "Bloom of Renewal", "Цветок обновления", EquipType.OFF_HAND, WeaponType.OFF_HAND_HELD, "INTELLECT",
                stats("HASTE", "VERSATILITY"), null, CASTER, null, null);
        item(ID, "gw_trinket_fang", "Fang of the Beast", "Клык зверя", EquipType.TRINKET, null, "ADAPTIVE", stats("CRIT", "MASTERY"), "fang_of_the_beast", MELEE, null, null);
        item(ID, "gw_trinket_hourglass", "Hourglass of Haste", "Песочные часы скорости", EquipType.TRINKET, null, "ADAPTIVE", stats("HASTE", "VERSATILITY"),
                "hourglass_of_haste", ANY, null, null);
        item(ID, "gw_legs", "Barkwoven Leggings", "Поножи из коры", EquipType.LEGS, null, "ADAPTIVE", stats("HASTE", "MASTERY"), null, ANY, null, null);
        item(ID, "gw_cloak", "Mosscloak of the Grove", "Моховой плащ рощи", EquipType.BACK, null, "ADAPTIVE", stats("CRIT", "HASTE"), null, ANY, null, null);
        item(ID, "gw_ring", "Seedheart Loop", "Кольцо сердца-семени", EquipType.FINGER, null, null, stats("MASTERY", "VERSATILITY"), null, ANY, null, null);
    }

    /** Ysra: bramble circles, roots, saplings. */
    static final class Ysra extends BossScript {
        @Override
        public void onPull() {
            say("The grove will feed on you!", "Роща напитается вами!");
            every("bramble", 7, 14, "Bramble Burst", "Взрыв терновника", () -> {
                for (UnitState p : randomPlayers(3)) circleAt(p.position(), 3, 2, GREEN, hit(School.NATURE, 4));
            });
            every("roots", 12, 20, "Entangling Roots", "Опутывающие корни", () -> {
                for (UnitState p : randomNonTanks(2)) applyAura(p, "blight_roots");
            });
            every("saplings", 18, 30, "Summon Saplings", "Призыв ростков", () -> {
                warn("Kill the saplings!", "Убейте ростки!");
                addsAround("blight_sapling", 4, 8);
            });
            every("lash", 4, 8, null, null, () -> {
                UnitState t = tank();
                if (t != null) hitUnit(t, hit(School.NATURE, 3.5));
            });
        }
    }

    /** Rotheart: stacking wound on the tank (dispel / swap), plague clouds, rot bursts. */
    static final class Rotheart extends BossScript {
        @Override
        public void onPull() {
            every("wound", 4, 6, "Rotting Wound", "Гниющая рана", () -> {
                UnitState t = tank();
                if (t != null) applyAura(t, "rotting_wound");
            });
            every("cloud", 10, 18, "Plague Cloud", "Чумное облако", () -> {
                for (UnitState p : randomPlayers(2)) poolAt(p.position(), 3.5, 18, GREEN, Effects.all(hit(School.NATURE, 0.8), Effects.aura("rot_poison")));
            });
            every("burst", 20, 25, "Burst of Rot", "Гнилой взрыв", () -> {
                warn("Burst of Rot!", "Гнилой взрыв!");
                after(2, () -> raidDamage(hit(School.NATURE, 2.2)));
            });
            every("smash", 15, 20, "Rot Smash", "Гнилой удар", () -> {
                UnitState t = tank();
                if (t != null) coneAt(t, 120, 9, 2.5, RED, hit(School.PHYSICAL, 6));
            });
        }
    }

    /** Malgoth: three forms by health. */
    static final class Malgoth extends BossScript {
        @Override
        public void onPull() {
            say("Nature takes back what was given!", "Природа забирает то, что дала!");
            buffBoss("druid_bear");
            every("maul", 5, 9, "Maul", "Трепка", () -> {
                UnitState t = tank();
                if (t != null) hitUnit(t, hit(School.PHYSICAL, 5));
            });
            every("swipe", 10, 14, "Swipe", "Размах", () -> coneFromBoss(180, 8, 1.5, RED, hit(School.PHYSICAL, 3)));
        }

        @Override
        public double[] thresholds() {
            return new double[]{0.66, 0.33};
        }

        @Override
        public void onHealthBelow(double f) {
            if (f > 0.5) {
                phase(2);
                e.engine.removeAura(boss(), "druid_bear", null);
                buffBoss("druid_cat");
                say("Swift as the hunter!", "Быстрый, как охотник!");
                e.cancel("maul");
                e.cancel("swipe");
                every("rake", 2, 8, "Rake", "Глубокая рана", () -> {
                    for (UnitState p : randomNonTanks(2)) {
                        applyAura(p, "rot_poison");
                        hitUnit(p, hit(School.PHYSICAL, 3));
                    }
                });
                every("pounce", 6, 15, "Pounce", "Наскок", () -> {
                    UnitState f2 = farthestPlayer();
                    if (f2 != null) lineTo(f2, 30, 3, 2, RED, Effects.all(hit(School.PHYSICAL, 5), Effects.aura("generic_stun")));
                });
            } else {
                phase(3);
                e.engine.removeAura(boss(), "druid_cat", null);
                say("The stars will burn you!", "Звезды сожгут вас!");
                e.cancel("rake");
                e.cancel("pounce");
                every("starfall", 2, 10, "Starfall", "Звездопад", () -> {
                    for (int i = 0; i < 7; i++) circleAt(randomPointAround(2, 14), 3, 2, PURPLE, hit(School.ARCANE, 4));
                });
                every("moonfire", 3, 5, null, null, () -> {
                    for (UnitState p : randomPlayers(1)) hitUnit(p, hit(School.ARCANE, 2.5));
                });
            }
        }
    }
}
