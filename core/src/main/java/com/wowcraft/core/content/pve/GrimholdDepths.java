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

/** Grimhold Depths: a dwarven forge-mine taken over by the Grim Iron clan and awakened earth spirits. */
public final class GrimholdDepths extends NpcKit {
    public static final String ID = "grimhold_depths";

    public static void register() {
        // ---- abilities
        strike("brute_smash", "Brutal Smash", "Жестокий удар", School.PHYSICAL, 1.8);
        cleave("brute_cleave", "Pick Sweep", "Взмах киркой", School.PHYSICAL, 2.2, 100, 7, 1.5);
        bolt("flame_bolt", "Flame Bolt", "Огненная стрела", School.FIRE, 2.2, 2.5);
        dot("searing_brand", "Searing Brand", "Обжигающее клеймо", School.FIRE, DispelType.MAGIC, 0.5, 12, 1);
        afflict("cast_searing_brand", "Searing Brand", "Обжигающее клеймо", School.FIRE, "searing_brand", 0, 40, false);
        mend("dark_mend", "Dark Mending", "Темное лечение", 0.3, 2.5);
        bolt("shadow_lash", "Shadow Lash", "Плеть Тьмы", School.SHADOW, 1.2, 1.5);
        nova("ground_slam", "Ground Slam", "Удар о землю", School.PHYSICAL, 1.6, 8, 2.5, false, Effects.knockback(0.9));
        strike("gnaw", "Gnaw", "Грызть", School.PHYSICAL, 0.8);
        strike("molten_touch", "Molten Touch", "Расплавленное касание", School.FIRE, 1.2);
        buffMod("grim_battle_cry", "Battle Cry", "Боевой клич", DispelType.ENRAGE, 12, "Damage dealt increased by 40%.", "Наносимый урон увеличен на 40%.",
                Modifier.damage(0.4), Modifier.haste(0.2));
        selfBuff("cast_battle_cry", "Battle Cry", "Боевой клич", "grim_battle_cry", 0);
        buffMod("grukk_frenzy", "Frenzy", "Бешенство", DispelType.ENRAGE, 0, "Attack speed and damage increased.", "Скорость атаки и урон увеличены.",
                Modifier.damage(0.3), Modifier.haste(0.3));
        buffMod("vorra_overheat", "Overheat", "Перегрев", null, 0, "Damage increased by 25%.", "Урон увеличен на 25%.", Modifier.damage(0.25));
        shieldAura("korgath_bulwark", "Unbroken Bulwark", "Несокрушимый оплот", null, 12);

        // ---- trash
        npc("grim_brute", "Grim Iron Brute", "Громила из клана Мрачного Железа", NpcRank.ELITE, BodyType.HUMANOID, "husk", 0xFF9A7B5A).scale(1.25)
                .spell(sp("brute_smash", 8, 3)).spell(sp("brute_cleave", 12, 6)).spell(sp("cast_battle_cry", 30, 10, NpcSpell.Target.SELF));
        npc("grim_flamecaller", "Grim Iron Flamecaller", "Огнезаклинатель из клана Мрачного Железа", NpcRank.NORMAL, BodyType.HUMANOID, "zombie", 0xFFE07040)
                .ranged(22).hp(0.8).spell(sp("flame_bolt", 0, 0.5)).spell(sp("cast_searing_brand", 15, 4, NpcSpell.Target.RANDOM_PLAYER));
        npc("grim_mender", "Grim Iron Shadowmender", "Целитель тьмы из клана Мрачного Железа", NpcRank.NORMAL, BodyType.HUMANOID, "zombie", 0xFF8060A0)
                .healer().hp(0.8).spell(sp("dark_mend", 8, 3, NpcSpell.Target.LOWEST_ALLY)).spell(sp("shadow_lash", 0, 1));
        npc("stone_sentinel", "Awakened Stone Sentinel", "Пробужденный каменный страж", NpcRank.ELITE, BodyType.HUMANOID, "husk", 0xFF8C8C8C).scale(1.5)
                .armor(0.45).speed(0.22).spell(sp("ground_slam", 15, 6));
        npc("tunnel_rat", "Tunnel Rat", "Туннельная крыса", NpcRank.MINION, BodyType.BEAST, "cave_spider", 0xFF7A6A5A).scale(0.7).spell(sp("gnaw", 3, 1));
        npc("molten_elemental", "Molten Spawn", "Расплавленное порождение", NpcRank.NORMAL, BodyType.ELEMENTAL, "blaze", 0xFFFF7020).hp(0.5).forces(0)
                .spell(sp("molten_touch", 4, 1));

        // ---- bosses
        boss("grukk", "Foreman Grukk", "Бригадир Грукк", BodyType.HUMANOID, "husk", 0xFFB08050, "grukk").hp(0.55).title("The Slavedriver", "Надсмотрщик");
        boss("vorra", "Magmasmith Vorra", "Магмокузнец Ворра", BodyType.HUMANOID, "zombie", 0xFFFF6030, "vorra").hp(0.6).ranged(15);
        boss("korgath", "Thane Korgath the Unbroken", "Тан Коргат Несокрушимый", BodyType.HUMANOID, "husk", 0xFF707888, "korgath").hp(0.7).scale(2.0);
        BossScripts.register("grukk", Grukk::new);
        BossScripts.register("vorra", Vorra::new);
        BossScripts.register("korgath", Korgath::new);

        // ---- map
        dungeon(ID, "GD", "Grimhold Depths", "Глубины Мрачного Оплота",
                "A dwarven forge-mine overrun by the Grim Iron clan, who woke the earth spirits sleeping below.",
                "Дворфийская кузня-шахта, захваченная кланом Мрачного Железа, пробудившим спящих духов земли.", "stone", 25 * 60,
                RoomDef.entrance(15, 15, 8),
                RoomDef.trash(22, 26, 9, "pillars", packs(pack("grim_brute", "grim_flamecaller", "grim_mender"), pack("stone_sentinel"),
                        pack("tunnel_rat", "tunnel_rat", "tunnel_rat", "grim_brute"))),
                RoomDef.boss(26, 10, "platform", "grukk"),
                RoomDef.hall(14, 28, 9, packs(pack("grim_flamecaller", "grim_flamecaller", "grim_mender"), pack("grim_brute", "stone_sentinel"))),
                RoomDef.boss(24, 10, "lava", "vorra"),
                RoomDef.trash(24, 24, 10, "rubble", packs(pack("stone_sentinel", "stone_sentinel"), pack("grim_brute", "grim_mender", "grim_flamecaller"),
                        pack("tunnel_rat", "tunnel_rat", "tunnel_rat", "tunnel_rat"))),
                RoomDef.boss(30, 12, "pillars", "korgath"));

        // ---- loot
        item(ID, "gd_pick", "Foreman's Rockbreaker", "Камнелом бригадира", EquipType.TWO_HAND, WeaponType.MACE_2H, "STRENGTH",
                stats("CRIT", "HASTE"), null, MELEE, "Still warm from the forge.", "Еще теплый после кузни.");
        item(ID, "gd_hammer", "Vorra's Forgehammer", "Кузнечный молот Ворры", EquipType.ONE_HAND, WeaponType.MACE_1H, "INTELLECT",
                stats("HASTE", "MASTERY"), null, CASTER, null, null);
        item(ID, "gd_axe", "Thane's Bearded Axe", "Бородатый топор тана", EquipType.ONE_HAND, WeaponType.AXE_1H, "AGILITY",
                stats("CRIT", "VERSATILITY"), null, DPS, null, null);
        item(ID, "gd_trinket_heart", "Heart of the Mountain", "Сердце горы", EquipType.TRINKET, null, "ADAPTIVE", stats("VERSATILITY", "MASTERY"),
                "heart_of_the_mountain", TANK, null, null);
        item(ID, "gd_trinket_ember", "Ember of Rage", "Уголек гнева", EquipType.TRINKET, null, "ADAPTIVE", stats("CRIT", "HASTE"),
                "ember_of_rage", DPS, null, null);
        item(ID, "gd_helm", "Grim Iron Faceguard", "Забрало Мрачного Железа", EquipType.HEAD, null, "ADAPTIVE", stats("CRIT", "MASTERY"), null, ANY, null, null);
        item(ID, "gd_ring", "Band of the Deep Forge", "Кольцо глубинной кузни", EquipType.FINGER, null, null, stats("HASTE", "VERSATILITY"), null, ANY, null, null);
        item(ID, "gd_belt", "Miner's Ironclasp Belt", "Пояс шахтера с железной пряжкой", EquipType.WAIST, null, "ADAPTIVE", stats("HASTE", "CRIT"), null, ANY, null, null);
    }

    /** Foreman Grukk: tank cone, adds, avoidable stomp, enrage at 30%. */
    static final class Grukk extends BossScript {
        @Override
        public void onPull() {
            say("Back to work, or you'll be buried down here!", "За работу, или вас тут и закопают!");
            every("shatter", 8, 16, "Shattering Swing", "Сокрушающий взмах", () -> {
                UnitState t = tank();
                if (t != null) coneAt(t, 90, 9, 2.5, RED, hit(School.PHYSICAL, 6));
            });
            every("workers", 20, 32, "Call Workers", "Призыв рабочих", () -> {
                warn("Grukk calls for workers!", "Грукк зовет рабочих!");
                addsAround("tunnel_rat", 3, 7);
            });
            every("stomp", 14, 22, "Seismic Stomp", "Сейсмический удар", () -> {
                warn("Get away from Grukk!", "Отойдите от Грукка!");
                circleAt(bossPos(), 11, 3, ORANGE, Effects.all(hit(School.PHYSICAL, 5), knock(1.2)));
            });
        }

        @Override
        public double[] thresholds() {
            return new double[]{0.3};
        }

        @Override
        public void onHealthBelow(double f) {
            say("You'll pay for every scratch!", "Вы заплатите за каждую царапину!");
            buffBoss("grukk_frenzy");
            warn("Grukk is frenzied! (Soothe)", "Грукк в бешенстве! (Успокойте)", RED);
        }
    }

    /** Magmasmith Vorra: spread circles leaving magma pools, raid damage, overheat at 50%. */
    static final class Vorra extends BossScript {
        @Override
        public void onPull() {
            say("The forge hungers!", "Кузня жаждет!");
            every("molten", 6, 18, "Molten Spray", "Брызги магмы", () -> {
                for (UnitState p : randomNonTanks(2)) {
                    circleOn(p, 4, 3, ORANGE, hit(School.FIRE, 4));
                    UnitState target = p;
                    after(3, () -> poolAt(target.position(), 3, 25, ORANGE, hit(School.FIRE, 1.0)));
                }
            });
            every("blast", 15, 25, "Forge Blast", "Взрыв кузни", () -> {
                warn("Forge Blast incoming!", "Скоро Взрыв кузни!");
                after(2, () -> raidDamage(hit(School.FIRE, 2.5)));
            });
            every("hammer", 10, 14, null, null, () -> {
                UnitState t = tank();
                if (t != null) coneAt(t, 60, 8, 2, RED, hit(School.PHYSICAL, 5));
            });
        }

        @Override
        public double[] thresholds() {
            return new double[]{0.5};
        }

        @Override
        public void onHealthBelow(double f) {
            phase(2);
            say("Feel the heat of the true forge!", "Почувствуйте жар истинной кузни!");
            buffBoss("vorra_overheat");
            addsAround("molten_elemental", 2, 8);
        }
    }

    /** Thane Korgath: lines, a shield to burst down, avalanche circles. */
    static final class Korgath extends BossScript {
        @Override
        public void onPull() {
            say("Stone does not break!", "Камень не ломается!");
            every("shatter", 8, 18, "Earthshatter", "Раскол земли", () -> {
                for (UnitState p : randomNonTanks(2)) lineTo(p, 30, 3, 2.5, ORANGE, Effects.all(hit(School.NATURE, 4), knock(1.0)));
            });
            every("bulwark", 25, 45, "Unbroken Bulwark", "Несокрушимый оплот", () -> {
                shieldBoss("korgath_bulwark", 0.08);
                warn("Break Korgath's shield!", "Сломайте щит Коргата!", PURPLE);
                after(10, () -> {
                    if (boss().auras().has("korgath_bulwark")) {
                        warn("Unbroken Fury!", "Несокрушимая ярость!", RED);
                        raidDamage(hit(School.NATURE, 4));
                    }
                });
            });
            every("avalanche", 15, 30, "Avalanche", "Лавина", () -> {
                for (int i = 0; i < 6; i++) circleAt(randomPointAround(3, 14), 3.5, 2.5, ORANGE, hit(School.NATURE, 5));
            });
            every("crush", 5, 12, null, null, () -> {
                UnitState t = tank();
                if (t != null) coneAt(t, 50, 7, 1.5, RED, hit(School.PHYSICAL, 4.5));
            });
        }

        @Override
        public double[] thresholds() {
            return new double[]{0.25};
        }

        @Override
        public void onHealthBelow(double f) {
            say("The mountain itself rises!", "Сама гора встает!");
            e.cancel("avalanche");
            every("avalanche", 2, 15, "Avalanche", "Лавина", () -> {
                for (int i = 0; i < 8; i++) circleAt(randomPointAround(3, 15), 3.5, 2.5, ORANGE, hit(School.NATURE, 5));
            });
        }
    }
}
