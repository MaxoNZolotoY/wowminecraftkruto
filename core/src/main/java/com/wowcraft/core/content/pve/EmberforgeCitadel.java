package com.wowcraft.core.content.pve;

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

/** Emberforge Citadel: a fortress in a volcano where fire cultists bind elementals. */
public final class EmberforgeCitadel extends NpcKit {
    public static final String ID = "emberforge_citadel";

    public static void register() {
        bolt("ember_fireball", "Fireball", "Огненный шар", School.FIRE, 2.4, 2.5);
        dot("npc_conflagrate", "Conflagrate", "Воспламенение", School.FIRE, DispelType.MAGIC, 0.6, 10, 3);
        afflict("cast_conflagrate", "Conflagrate", "Воспламенение", School.FIRE, "npc_conflagrate", 1.5, 40, true);
        strike("magma_bite", "Magma Bite", "Магматический укус", School.FIRE, 1.4, Effects.aura("magma_burn"));
        dot("magma_burn", "Magma Burn", "Магматический ожог", School.FIRE, null, 0.3, 8, 5);
        cleave("molten_slam", "Molten Slam", "Расплавленный удар", School.FIRE, 2.4, 80, 8, 2);
        mend("cauterize_ally", "Cauterize", "Прижигание", 0.25, 2);
        buffMod("flame_ward_buff", "Flame Ward", "Огненный оберег", DispelType.MAGIC, 15, "Damage taken reduced by 40%.", "Получаемый урон снижен на 40%.",
                Modifier.taken(-0.4));
        allyBuff("cast_flame_ward", "Flame Ward", "Огненный оберег", "flame_ward_buff", 1.5);
        nova("cinder_burst", "Cinder Burst", "Взрыв пепла", School.FIRE, 1.2, 6, 1.5, false);
        strike("ember_claw", "Ember Claw", "Огненный коготь", School.FIRE, 1.0);
        shieldAura("ashkar_shield", "Pyre Shield", "Щит костра", null, 0);
        com.wowcraft.core.content.Registry.register(com.wowcraft.core.aura.AuraDef.buff("inferno_heat", "Rising Heat", "Нарастающий жар").stacks(20)
                .mod(Modifier.damage(0.1)).desc("Damage increased by 10% per stack.", "Урон увеличен на 10% за заряд.").build());

        npc("ember_cultist", "Ember Cultist", "Культист углей", NpcRank.NORMAL, BodyType.HUMANOID, "zombie", 0xFFD04020).ranged(22).hp(0.8)
                .spell(sp("ember_fireball", 0, 0.5)).spell(sp("cast_conflagrate", 14, 4, NpcSpell.Target.RANDOM_PLAYER));
        npc("magma_hound", "Magma Hound", "Магмовая гончая", NpcRank.ELITE, BodyType.BEAST, "spider", 0xFFFF6010).speed(0.34).spell(sp("magma_bite", 5, 1));
        npc("forge_golem", "Forge Golem", "Кузнечный голем", NpcRank.ELITE, BodyType.HUMANOID, "husk", 0xFF505050).scale(1.6).armor(0.5).ccImmune()
                .speed(0.2).spell(sp("molten_slam", 12, 5));
        npc("flame_warden", "Flame Warden", "Хранитель пламени", NpcRank.NORMAL, BodyType.HUMANOID, "zombie", 0xFFFFB040).healer().hp(0.8)
                .spell(sp("cauterize_ally", 8, 3, NpcSpell.Target.LOWEST_ALLY)).spell(sp("cast_flame_ward", 18, 6, NpcSpell.Target.LOWEST_ALLY));
        npc("cinder_imp", "Cinder Imp", "Пепельный бес", NpcRank.MINION, BodyType.ELEMENTAL, "blaze", 0xFFFF9030).scale(0.6).spell(sp("ember_claw", 3, 1))
                .spell(sp("cinder_burst", 12, 6));
        npc("ember_guardian", "Ember Guardian", "Страж углей", NpcRank.ELITE, BodyType.ELEMENTAL, "blaze", 0xFFFFD040).forces(0).hp(0.6)
                .spell(sp("ember_claw", 3, 1)).spell(sp("cinder_burst", 10, 4));

        boss("kragg", "Forgemaster Kragg", "Мастер кузни Крагг", BodyType.HUMANOID, "husk", 0xFF8A4A2A, "kragg").hp(0.55).scale(1.9);
        boss("living_inferno", "The Living Inferno", "Живое пекло", BodyType.ELEMENTAL, "blaze", 0xFFFF5000, "living_inferno").hp(0.6).scale(2.2);
        boss("ashkar", "Pyrelord Ashkar", "Повелитель костров Ашкар", BodyType.HUMANOID, "wither_skeleton", 0xFFFF3020, "ashkar").hp(0.7).scale(2.0);
        BossScripts.register("kragg", Kragg::new);
        BossScripts.register("living_inferno", LivingInferno::new);
        BossScripts.register("ashkar", Ashkar::new);

        dungeon(ID, "EC", "Emberforge Citadel", "Цитадель Угольной Кузни",
                "Deep inside a volcano, fire cultists chain elementals to their forges to arm the Pyrelord's legion.",
                "Глубоко в вулкане культисты огня приковывают элементалей к кузням, чтобы вооружить легион Повелителя костров.", "ember", 25 * 60,
                RoomDef.entrance(15, 15, 8),
                RoomDef.trash(24, 24, 10, "lava", packs(pack("ember_cultist", "ember_cultist", "flame_warden"), pack("magma_hound", "magma_hound"),
                        pack("forge_golem", "cinder_imp", "cinder_imp"))),
                RoomDef.boss(26, 11, "lava", "kragg"),
                RoomDef.hall(14, 28, 9, packs(pack("cinder_imp", "cinder_imp", "cinder_imp", "cinder_imp"), pack("magma_hound", "ember_cultist", "flame_warden"))),
                RoomDef.boss(28, 12, "platform", "living_inferno"),
                RoomDef.trash(24, 26, 10, "pillars", packs(pack("forge_golem", "flame_warden"), pack("ember_cultist", "ember_cultist", "magma_hound"),
                        pack("forge_golem", "forge_golem"))),
                RoomDef.boss(30, 13, "lava", "ashkar"));

        item(ID, "ec_greatsword", "Pyrelord's Brand", "Клеймо Повелителя костров", EquipType.TWO_HAND, WeaponType.SWORD_2H, "STRENGTH",
                stats("CRIT", "MASTERY"), null, MELEE, "Its edge never cools.", "Его лезвие никогда не остывает.");
        item(ID, "ec_wand", "Cinderlash Wand", "Жезл пепельной плети", EquipType.RANGED, WeaponType.WAND, "INTELLECT", stats("HASTE", "CRIT"), null, CASTER, null, null);
        item(ID, "ec_daggers", "Molten Kris", "Расплавленный крис", EquipType.ONE_HAND, WeaponType.DAGGER, "AGILITY", stats("HASTE", "VERSATILITY"), null, DPS, null, null);
        item(ID, "ec_trinket_idol", "Idol of Fury", "Идол ярости", EquipType.TRINKET, null, "ADAPTIVE", stats("CRIT", "HASTE"), "idol_of_fury", DPS, null, null);
        item(ID, "ec_trinket_shard", "Shard of Annihilation", "Осколок аннигиляции", EquipType.TRINKET, null, "ADAPTIVE", stats("MASTERY", "VERSATILITY"),
                "shard_of_annihilation", DPS, null, null);
        item(ID, "ec_gloves", "Forgemaster's Grips", "Хватка мастера кузни", EquipType.HANDS, null, "ADAPTIVE", stats("CRIT", "HASTE"), null, ANY, null, null);
        item(ID, "ec_shoulders", "Ashen Pauldrons", "Пепельные наплечники", EquipType.SHOULDER, null, "ADAPTIVE", stats("MASTERY", "HASTE"), null, ANY, null, null);
        item(ID, "ec_ring", "Ring of Smoldering Will", "Кольцо тлеющей воли", EquipType.FINGER, null, null, stats("CRIT", "MASTERY"), null, ANY, null, null);
    }

    /** Kragg: soak circles, hammer lines, forge-fire pools. */
    static final class Kragg extends BossScript {
        @Override
        public void onPull() {
            say("Another batch for the anvil!", "Еще партия для наковальни!");
            every("anvil", 12, 24, "Anvil Smash", "Удар наковальни", () -> {
                Vec3 p = randomPointAround(5, 9);
                soakAt(p, 3.5, 4, 2, hit(School.PHYSICAL, 10), hit(School.FIRE, 4));
                warn("Soak the Anvil Smash (2 players)!", "Примите Удар наковальни (2 игрока)!", YELLOW);
            });
            every("hammer", 8, 15, "Hammer Throw", "Бросок молота", () -> {
                UnitState f = farthestPlayer();
                if (f != null) lineTo(f, 30, 3, 2, RED, Effects.all(hit(School.PHYSICAL, 5), Effects.aura("generic_knockdown")));
            });
            every("forgefire", 18, 25, "Forge Fire", "Огонь кузни", () -> {
                for (UnitState p : randomPlayers(3)) poolAt(p.position(), 2.5, 20, ORANGE, hit(School.FIRE, 0.8));
            });
            every("strike", 4, 8, null, null, () -> {
                UnitState t = tank();
                if (t != null) hitUnit(t, hit(School.PHYSICAL, 3.5));
            });
        }
    }

    /** The Living Inferno: expanding fire rings, ignites, heat stacks over time. */
    static final class LivingInferno extends BossScript {
        @Override
        public void onPull() {
            every("wave", 10, 20, "Flame Wave", "Огненная волна", () -> {
                ringAround(4, 10, 2.5, ORANGE, hit(School.FIRE, 5));
                after(3, () -> ringAround(10, 18, 2.5, ORANGE, hit(School.FIRE, 5)));
                after(6, () -> circleAt(bossPos(), 4, 2.5, ORANGE, hit(School.FIRE, 5)));
            });
            every("ignite", 6, 14, "Ignite", "Возгорание", () -> {
                for (UnitState p : randomPlayers(2)) applyAura(p, "npc_conflagrate");
            });
            every("heat", 30, 30, "Rising Heat", "Нарастающий жар", () -> {
                buffBoss("inferno_heat");
                raidDamage(hit(School.FIRE, 2));
            });
            every("slap", 4, 7, null, null, () -> {
                UnitState t = tank();
                if (t != null) hitUnit(t, hit(School.FIRE, 3));
            });
        }
    }

    /** Ashkar: cone, meteor stack, shield phase with two guardians. */
    static final class Ashkar extends BossScript {
        private boolean shielded;

        @Override
        public void onPull() {
            say("Burn, and be reborn in my fire!", "Горите и возродитесь в моем огне!");
            every("lash", 6, 12, "Fire Lash", "Огненная плеть", () -> {
                UnitState t = tank();
                if (t != null) coneAt(t, 100, 10, 2, RED, hit(School.FIRE, 6));
            });
            every("meteor", 15, 25, "Meteor", "Метеор", () -> {
                var t = randomNonTanks(1);
                if (!t.isEmpty()) {
                    stackOn(t.get(0), 4, 5, hit(School.FIRE, 15));
                    warn("Meteor on " + t.get(0).name + " — stack!", "Метеор на " + t.get(0).name + " — соберитесь!", ORANGE);
                }
            });
            every("pyre", 20, 28, "Pyre Eruption", "Извержение костра", () -> {
                for (int i = 0; i < 5; i++) circleAt(randomPointAround(2, 14), 3, 2.5, ORANGE, hit(School.FIRE, 4));
            });
        }

        @Override
        public double[] thresholds() {
            return new double[]{0.5};
        }

        @Override
        public void onHealthBelow(double f) {
            phase(2);
            shielded = true;
            say("Guardians, protect your master!", "Стражи, защитите господина!");
            shieldBoss("ashkar_shield", 0.5);
            addsAround("ember_guardian", 2, 10);
            warn("Kill the Ember Guardians to break the shield!", "Убейте Стражей углей, чтобы снять щит!", PURPLE);
        }

        @Override
        public void onAddDeath(UnitState add) {
            if (shielded && "ember_guardian".equals(add.templateId) && !addsAlive()) {
                shielded = false;
                e.engine.removeAura(boss(), "ashkar_shield", null);
                warn("The Pyre Shield shatters!", "Щит костра разрушен!", GREEN);
            }
        }
    }
}
