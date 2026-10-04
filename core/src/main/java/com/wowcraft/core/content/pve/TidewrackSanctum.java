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

/** Tidewrack Sanctum: a drowned temple of the sea-serpent cult. */
public final class TidewrackSanctum extends NpcKit {
    public static final String ID = "tidewrack_sanctum";

    public static void register() {
        cleave("crushing_wave", "Crushing Wave", "Сокрушающая волна", School.FROST, 2.0, 90, 8, 2, Effects.knockback(0.6));
        strike("trident_jab", "Trident Jab", "Удар трезубцем", School.PHYSICAL, 1.6);
        bolt("hydro_bolt", "Hydro Bolt", "Гидрострела", School.FROST, 2.1, 2.5);
        debuffMod("drowning_mark", "Mark of the Drowned", "Метка утопленника", DispelType.CURSE, 15, 1,
                "Movement speed reduced by 40%, healing taken reduced by 20%.", "Скорость передвижения снижена на 40%, получаемое исцеление на 20%.",
                Modifier.speed(-0.4), Modifier.healingTaken(-0.2));
        afflict("cast_drowning_mark", "Mark of the Drowned", "Метка утопленника", School.FROST, "drowning_mark", 1.5, 40, true);
        mend("tidal_mending", "Tidal Mending", "Приливное исцеление", 0.3, 2.5);
        shieldAura("shell_shield", "Shell Shield", "Панцирный щит", DispelType.MAGIC, 10);
        selfBuff("cast_shell_shield", "Shell Shield", "Панцирный щит", "shell_shield", 0);
        strike("pincer", "Pincer", "Клешня", School.PHYSICAL, 1.4);
        strike("murk_slap", "Murk Slap", "Шлепок мути", School.FROST, 0.8);
        cc("siren_charm", "Siren's Charm", "Чары сирены", CcType.DISORIENT, DispelType.MAGIC, 5);
        afflict("cast_siren_charm", "Siren's Charm", "Чары сирены", School.ARCANE, "siren_charm", 2, 30, true);
        strike("globule_touch", "Brine Touch", "Касание рассола", School.FROST, 0.5);
        buffMod("coral_harden", "Hardened Shell", "Затвердевший панцирь", null, 8, "Damage taken reduced by 50%.", "Получаемый урон снижен на 50%.",
                Modifier.taken(-0.5));

        npc("tide_warrior", "Tidewrack Warrior", "Воин Разбитого Прилива", NpcRank.ELITE, BodyType.HUMANOID, "drowned", 0xFF4FA0A0).scale(1.2)
                .spell(sp("trident_jab", 7, 2)).spell(sp("crushing_wave", 14, 5));
        npc("tide_siren", "Tidewrack Siren", "Сирена Разбитого Прилива", NpcRank.NORMAL, BodyType.HUMANOID, "drowned", 0xFF60C0E0).ranged(22).hp(0.8)
                .spell(sp("hydro_bolt", 0, 0.5)).spell(sp("cast_drowning_mark", 16, 5, NpcSpell.Target.RANDOM_PLAYER))
                .spell(sp("cast_siren_charm", 25, 9, NpcSpell.Target.RANDOM_RANGED));
        npc("tide_oracle", "Tidewrack Oracle", "Оракул Разбитого Прилива", NpcRank.NORMAL, BodyType.HUMANOID, "drowned", 0xFF2080C0).healer().hp(0.8)
                .spell(sp("tidal_mending", 7, 3, NpcSpell.Target.LOWEST_ALLY)).spell(sp("hydro_bolt", 0, 1));
        npc("deep_crab", "Deepshell Crab", "Глубинный краб", NpcRank.ELITE, BodyType.BEAST, "spider", 0xFFC05030).scale(1.3).armor(0.5)
                .spell(sp("pincer", 5, 1)).spell(sp("cast_shell_shield", 20, 6, NpcSpell.Target.SELF));
        npc("murk_spawn", "Murk Spawn", "Отродье мути", NpcRank.MINION, BodyType.HUMANOID, "drowned", 0xFF305050).scale(0.8).spell(sp("murk_slap", 3, 1));
        npc("tide_globule", "Briny Globule", "Соленая капля", NpcRank.MINION, BodyType.ELEMENTAL, "blaze", 0xFF40A0FF).forces(0).speed(0.16).hp(0.6)
                .spell(sp("globule_touch", 3, 1));

        boss("coralhide", "Coralhide the Ancient", "Древний Коралловый Панцирь", BodyType.BEAST, "spider", 0xFFD06040, "coralhide").hp(0.6).scale(2.4);
        boss("shalassa", "High Oracle Shalassa", "Верховная провидица Шаласса", BodyType.HUMANOID, "drowned", 0xFF3070D0, "shalassa").hp(0.55).ranged(18);
        boss("myrrah", "Myrrah, Voice of the Abyss", "Мирра, Глас Бездны", BodyType.HUMANOID, "drowned", 0xFF60E0D0, "myrrah").hp(0.7).scale(2.0);
        BossScripts.register("coralhide", Coralhide::new);
        BossScripts.register("shalassa", Shalassa::new);
        BossScripts.register("myrrah", Myrrah::new);

        dungeon(ID, "TS", "Tidewrack Sanctum", "Святилище Разбитого Прилива",
                "A temple swallowed by the sea, where sirens sing to wake something ancient in the deep.",
                "Храм, поглощенный морем, где сирены поют, чтобы пробудить нечто древнее в глубине.", "tide", 24 * 60,
                RoomDef.entrance(15, 15, 8),
                RoomDef.trash(24, 24, 10, "water", packs(pack("tide_warrior", "tide_siren", "tide_oracle"), pack("deep_crab", "murk_spawn", "murk_spawn"),
                        pack("tide_siren", "tide_siren", "tide_warrior"))),
                RoomDef.boss(26, 10, "water", "coralhide"),
                RoomDef.hall(14, 30, 9, packs(pack("murk_spawn", "murk_spawn", "murk_spawn", "tide_oracle"), pack("tide_warrior", "deep_crab"))),
                RoomDef.boss(24, 10, "statues", "shalassa"),
                RoomDef.trash(22, 26, 10, "pillars", packs(pack("deep_crab", "deep_crab"), pack("tide_warrior", "tide_siren", "tide_oracle", "murk_spawn"))),
                RoomDef.boss(30, 12, "platform", "myrrah"));

        item(ID, "ts_trident", "Trident of the Drowned Choir", "Трезубец утонувшего хора", EquipType.TWO_HAND, WeaponType.POLEARM, "AGILITY",
                stats("HASTE", "MASTERY"), null, MELEE, null, null);
        item(ID, "ts_staff", "Oracle's Tidecaller", "Призыватель приливов оракула", EquipType.TWO_HAND, WeaponType.STAFF, "INTELLECT",
                stats("CRIT", "HASTE"), null, CASTER, "It hums with the voice of the sea.", "Он гудит голосом моря.");
        item(ID, "ts_shield", "Coralhide Bulwark", "Оплот Кораллового Панциря", EquipType.SHIELD, WeaponType.SHIELD, "STRENGTH",
                stats("VERSATILITY", "MASTERY"), null, TANK, null, null);
        item(ID, "ts_trinket_chalice", "Chalice of Renewal", "Чаша обновления", EquipType.TRINKET, null, "ADAPTIVE", stats("HASTE", "MASTERY"),
                "chalice_of_renewal", HEAL, null, null);
        item(ID, "ts_trinket_storm", "Totem of Storms", "Тотем бурь", EquipType.TRINKET, null, "ADAPTIVE", stats("MASTERY", "CRIT"),
                "storm_totem", DPS, null, null);
        item(ID, "ts_cloak", "Sirensong Drape", "Накидка песни сирен", EquipType.BACK, null, "ADAPTIVE", stats("CRIT", "VERSATILITY"), null, ANY, null, null);
        item(ID, "ts_neck", "Pearl of the Abyss", "Жемчужина Бездны", EquipType.NECK, null, null, stats("MASTERY", "HASTE"), null, ANY, null, null);
        item(ID, "ts_boots", "Barnacled Greaves", "Поножи с ракушками", EquipType.FEET, null, "ADAPTIVE", stats("VERSATILITY", "CRIT"), null, ANY, null, null);
    }

    /** Coralhide: tank cone, burrow ring, hardened shell phase (switch to adds). */
    static final class Coralhide extends BossScript {
        @Override
        public void onPull() {
            every("slam", 6, 12, "Shell Slam", "Удар панцирем", () -> {
                UnitState t = tank();
                if (t != null) coneAt(t, 70, 8, 2, RED, hit(School.PHYSICAL, 6));
            });
            every("burrow", 18, 28, "Burrowing Surge", "Подземный рывок", () -> {
                warn("Stay close to Coralhide!", "Держитесь рядом с Коралловым Панцирем!");
                ringAround(6, 20, 3, BLUE, Effects.all(hit(School.FROST, 5), knock(1.0)));
            });
            every("surge", 25, 30, "Tidal Surge", "Приливная волна", () -> {
                raidDamage(Effects.all(hit(School.FROST, 1.8), knock(0.8)));
                addsAround("murk_spawn", 2, 8);
            });
        }

        @Override
        public double[] thresholds() {
            return new double[]{0.6, 0.3};
        }

        @Override
        public void onHealthBelow(double f) {
            buffBoss("coral_harden");
            warn("Coralhide hardens its shell! Kill the spawn!", "Панцирь затвердел! Убейте отродий!");
            addsAround("murk_spawn", 4, 9);
        }
    }

    /** Shalassa: curses to dispel, torrent at the farthest player, globules healing her if they reach her. */
    static final class Shalassa extends BossScript {
        @Override
        public void onPull() {
            say("The deep will swallow you!", "Глубина поглотит вас!");
            every("curse", 8, 20, "Mark of the Drowned", "Метка утопленника", () -> {
                for (UnitState p : randomPlayers(2)) applyAura(p, "drowning_mark");
                warn("Remove the curses!", "Снимите проклятия!", PURPLE);
            });
            every("torrent", 12, 16, "Tidal Torrent", "Приливный поток", () -> {
                UnitState f = farthestPlayer();
                if (f != null) lineTo(f, 35, 4, 2.5, BLUE, Effects.all(hit(School.FROST, 5), knock(1.2)));
            });
            every("globules", 15, 30, "Briny Globules", "Соленые капли", () -> {
                warn("Kill the globules before they reach Shalassa!", "Убейте капли, пока они не добрались до Шалассы!");
                for (int i = 0; i < 3; i++) {
                    Vec3 p = randomPointAround(12, 14);
                    UnitState g = add("tide_globule", p);
                    if (g != null) g.tags.put("heals_boss", true);
                }
            });
            every("bolt", 3, 4, null, null, () -> {
                UnitState t = tank();
                if (t != null) hitUnit(t, hit(School.FROST, 2.5));
            });
        }

        @Override
        public void onTick() {
            for (UnitState a : e.adds) {
                if (!a.isAlive() || !a.tags.containsKey("heals_boss")) continue;
                a.move = com.wowcraft.core.combat.MoveIntent.moveTo(bossPos(), 0.6);
                if (a.position().distance(bossPos()) < 2.5) {
                    e.engine.rawHeal(boss(), boss(), boss().maxHealth() * 0.06);
                    warn("A globule reached Shalassa and healed her!", "Капля достигла Шалассы и исцелила ее!", RED);
                    e.engine.die(a, null);
                }
            }
        }
    }

    /** Myrrah: stack mechanic, whirlpool rings, spikes under everyone. */
    static final class Myrrah extends BossScript {
        @Override
        public void onPull() {
            say("Hear the song of the Abyss!", "Внемлите песне Бездны!");
            every("song", 10, 22, "Siren Song", "Песнь сирены", () -> {
                var targets = randomNonTanks(1);
                if (!targets.isEmpty()) {
                    stackOn(targets.get(0), 4, 4, hit(School.ARCANE, 12));
                    warn("Stack with " + targets.get(0).name + "!", "Соберитесь у " + targets.get(0).name + "!", BLUE);
                }
            });
            every("whirl", 20, 30, "Whirlpool", "Водоворот", () -> {
                circleAt(roomCenter(), 7, 3, BLUE, Effects.all(hit(School.FROST, 6), knock(1.4)));
                after(3.5, () -> ringAround(9, 16, 2.5, BLUE, hit(School.FROST, 6)));
            });
            every("spikes", 14, 18, "Abyssal Spikes", "Шипы Бездны", () -> {
                for (UnitState p : alivePlayers()) circleAt(p.position(), 2.5, 2, PURPLE, hit(School.SHADOW, 4));
            });
            every("lash", 5, 9, null, null, () -> {
                UnitState t = tank();
                if (t != null) hitUnit(t, hit(School.FROST, 4));
            });
        }

        @Override
        public double[] thresholds() {
            return new double[]{0.4};
        }

        @Override
        public void onHealthBelow(double f) {
            phase(2);
            say("Rise, children of the deep!", "Восстаньте, дети глубин!");
            addsAround("tide_siren", 2, 10);
            every("surge", 5, 20, "Abyssal Surge", "Всплеск Бездны", () -> raidDamage(hit(School.SHADOW, 2)));
        }
    }
}
