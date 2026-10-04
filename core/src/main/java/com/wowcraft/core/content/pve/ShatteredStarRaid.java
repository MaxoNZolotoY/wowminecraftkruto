package com.wowcraft.core.content.pve;

import com.wowcraft.core.aura.AuraDef;
import com.wowcraft.core.aura.CcType;
import com.wowcraft.core.aura.DispelType;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.content.Registry;
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
import com.wowcraft.core.spell.Scaling;
import com.wowcraft.core.spell.School;

/** Throne of the Shattered Star: a titan observatory around a fallen star full of Void. 5 bosses. */
public final class ShatteredStarRaid extends NpcKit {
    public static final String ID = "throne_of_the_shattered_star";

    public static void register() {
        // ---- trash abilities
        cleave("arc_slam", "Arc Slam", "Дуговой удар", School.ARCANE, 2.4, 90, 9, 2);
        strike("watcher_strike", "Titanic Strike", "Удар титана", School.PHYSICAL, 1.6);
        bolt("star_bolt", "Star Bolt", "Звездная стрела", School.ARCANE, 2.2, 2.5);
        shieldAura("astral_ward", "Astral Ward", "Астральный оберег", DispelType.MAGIC, 15);
        Registry.register(com.wowcraft.core.spell.Ability.builder("cast_astral_ward", "Astral Ward", "Астральный оберег").target(com.wowcraft.core.spell.TargetType.FRIENDLY)
                .range(40).cast(2).gcd(0).noFacing().tag("spell").effect(Effects.absorb("astral_ward", Scaling.ap(12))).vfx("buff").build());
        nova("void_pulse", "Void Pulse", "Импульс Бездны", School.SHADOW, 1.2, 10, 2, false);
        strike("drone_zap", "Zap", "Разряд", School.NATURE, 0.6);
        nova("drone_overload", "Overload", "Перегрузка", School.NATURE, 3.0, 40, 6, false);
        bolt("image_bolt", "Arcane Bolt", "Чародейская стрела", School.ARCANE, 1.6, 2);
        strike("fragment_touch", "Void Touch", "Касание Бездны", School.SHADOW, 0.8);

        // ---- boss auras
        Registry.register(AuraDef.debuff("titanic_vulnerability", "Titanic Smash", "Титанический удар").duration(25).stacks(10).perCaster(false)
                .mod(Modifier.taken(0.2)).desc("Damage taken increased by 20% per stack. Taunt swap!", "Получаемый урон увеличен на 20% за заряд. Смена танков!").build());
        Registry.register(AuraDef.debuff("devour", "Devour", "Пожирание").duration(30).stacks(10).perCaster(false)
                .mod(Modifier.taken(0.3)).periodic(3, Effects.damage(School.SHADOW, Scaling.sp(0.6))).unhastedTicks()
                .desc("Damage taken increased by 30% per stack.", "Получаемый урон увеличен на 30% за заряд.").build());
        buffMod("celestial_bond", "Celestial Bond", "Небесная связь", null, 6, "Damage increased by 30% while the twins' health is unbalanced.",
                "Урон увеличен на 30%, пока здоровье близнецов не сбалансировано.", Modifier.damage(0.3));
        buffMod("gorvax_frenzy", "Ravenous Frenzy", "Ненасытное бешенство", DispelType.ENRAGE, 0, "Attack speed increased by 40%.",
                "Скорость атаки увеличена на 40%.", Modifier.haste(0.4), Modifier.damage(0.2));
        cc("time_lock", "Time Lock", "Временной замок", CcType.STUN, DispelType.MAGIC, 4);
        dot("dark_matter", "Dark Matter", "Темная материя", School.SHADOW, DispelType.MAGIC, 1.0, 12, 1);
        Registry.register(AuraDef.debuff("entropic_decay", "Entropic Decay", "Энтропийный распад").stacks(50).persistent().perCaster(false)
                .mod(Modifier.healingTaken(-0.03)).desc("Healing received reduced by 3% per stack.", "Получаемое исцеление снижено на 3% за заряд.").build());

        // ---- trash
        npc("titan_watcher", "Titan Watcher", "Смотритель титанов", NpcRank.ELITE, BodyType.HUMANOID, "husk", 0xFFB0C0E0).scale(1.8).armor(0.45).ccImmune()
                .hp(1.4).speed(0.22).spell(sp("watcher_strike", 6, 2)).spell(sp("arc_slam", 12, 5));
        npc("star_acolyte", "Starbound Acolyte", "Звездный послушник", NpcRank.ELITE, BodyType.HUMANOID, "stray", 0xFF80A0FF).ranged(22)
                .spell(sp("star_bolt", 0, 0.5)).spell(sp("cast_astral_ward", 18, 6, NpcSpell.Target.LOWEST_ALLY));
        npc("void_construct", "Voidbound Construct", "Конструкт Бездны", NpcRank.ELITE, BodyType.ELEMENTAL, "blaze", 0xFF6030A0).scale(1.5).hp(1.3)
                .spell(sp("void_pulse", 10, 4));
        npc("spark_drone", "Spark Drone", "Искрящийся дрон", NpcRank.NORMAL, BodyType.ELEMENTAL, "blaze", 0xFF80E0FF).forces(0).hp(0.5)
                .spell(sp("drone_zap", 3, 1)).spell(sp("drone_overload", 99, 12, NpcSpell.Target.SELF));
        npc("eltheris_image", "Mirror Image", "Зеркальное изображение", NpcRank.NORMAL, BodyType.HUMANOID, "stray", 0xFFC080FF).ranged(22).forces(0).hp(0.8)
                .spell(sp("image_bolt", 0, 1));
        npc("void_fragment", "Void Fragment", "Осколок Бездны", NpcRank.NORMAL, BodyType.ELEMENTAL, "blaze", 0xFF400080).forces(0).hp(0.9)
                .spell(sp("fragment_touch", 3, 1)).spell(sp("void_pulse", 12, 6));

        // ---- bosses
        boss("sentinel_prime", "Sentinel Prime", "Первичный страж", BodyType.HUMANOID, "husk", 0xFFC0D0F0, "sentinel_prime").hp(1.0).scale(2.8).armor(0.4);
        boss("starweaver_luna", "Luna the Starweaver", "Луна Ткачиха Звезд", BodyType.HUMANOID, "stray", 0xFFA0C0FF, "starweavers").hp(0.55).ranged(16).scale(1.8);
        boss("starweaver_sol", "Sol the Starweaver", "Сол Ткач Звезд", BodyType.HUMANOID, "husk", 0xFFFFD080, "starweavers").hp(0.55).scale(1.9);
        boss("gorvax", "Gorvax the Devourer", "Горвакс Пожиратель", BodyType.BEAST, "spider", 0xFF502060, "gorvax").hp(1.1).scale(3.0);
        boss("eltheris", "Archmagus Eltheris", "Верховный маг Элтерис", BodyType.HUMANOID, "stray", 0xFFE0A0FF, "eltheris").hp(1.0).ranged(16).scale(1.9);
        boss("xalzorith", "Xal'zorith, the Shattered Star", "Ксал'зорит, Расколотая Звезда", BodyType.ELEMENTAL, "blaze", 0xFF2A0050, "xalzorith")
                .hp(1.4).scale(3.2).title("Heart of the Void", "Сердце Бездны");
        BossScripts.register("sentinel_prime", SentinelPrime::new);
        BossScripts.register("starweavers", Starweavers::new);
        BossScripts.register("gorvax", Gorvax::new);
        BossScripts.register("eltheris", Eltheris::new);
        BossScripts.register("xalzorith", Xalzorith::new);

        raid(ID, "TSS", "Throne of the Shattered Star", "Трон Расколотой Звезды",
                "A titan observatory built around a fallen star. The star is cracked — and something from the Void is crawling out.",
                "Обсерватория титанов вокруг упавшей звезды. Звезда расколота — и из нее выползает нечто из Бездны.", "titan",
                RoomDef.entrance(18, 18, 10),
                RoomDef.trash(28, 28, 12, "pillars", packs(pack("titan_watcher", "star_acolyte"), pack("void_construct", "star_acolyte", "star_acolyte"))),
                RoomDef.boss(38, 14, "platform", "sentinel_prime"),
                RoomDef.hall(16, 30, 12, packs(pack("star_acolyte", "star_acolyte", "titan_watcher"))),
                RoomDef.boss(36, 14, "statues", "starweaver_luna", "starweaver_sol"),
                RoomDef.trash(28, 28, 12, "rubble", packs(pack("void_construct", "void_construct"), pack("titan_watcher", "titan_watcher", "star_acolyte"))),
                RoomDef.boss(40, 16, "pillars", "gorvax"),
                RoomDef.hall(16, 30, 12, packs(pack("void_construct", "star_acolyte", "star_acolyte"))),
                RoomDef.boss(38, 14, "platform", "eltheris"),
                RoomDef.trash(26, 26, 12, "pillars", packs(pack("void_construct", "titan_watcher", "void_construct"))),
                RoomDef.boss(44, 18, "platform", "xalzorith"));

        // ---- loot
        item(ID, "tss_greatsword", "Blade of the Fallen Star", "Клинок упавшей звезды", EquipType.TWO_HAND, WeaponType.SWORD_2H, "STRENGTH",
                stats("CRIT", "HASTE"), null, MELEE, "Forged from a sliver of the star.", "Выкован из осколка звезды.");
        item(ID, "tss_staff", "Eltheris' Astrolabe Staff", "Посох-астролябия Элтериса", EquipType.TWO_HAND, WeaponType.STAFF, "INTELLECT",
                stats("HASTE", "MASTERY"), null, CASTER, null, null);
        item(ID, "tss_glaives", "Twinlight Warglaive", "Боевая глефа двойного света", EquipType.ONE_HAND, WeaponType.WARGLAIVE, "AGILITY",
                stats("CRIT", "MASTERY"), null, MELEE, null, null);
        item(ID, "tss_bow", "Starshot Recurve", "Изогнутый лук звездного выстрела", EquipType.RANGED, WeaponType.BOW, "AGILITY", stats("HASTE", "CRIT"), null, DPS, null, null);
        item(ID, "tss_mace", "Sentinel's Core Hammer", "Молот ядра стража", EquipType.ONE_HAND, WeaponType.MACE_1H, "STRENGTH",
                stats("VERSATILITY", "HASTE"), null, TANK, null, null);
        item(ID, "tss_wand", "Wand of Collapsing Light", "Жезл гаснущего света", EquipType.RANGED, WeaponType.WAND, "INTELLECT", stats("CRIT", "VERSATILITY"), null, CASTER, null, null);
        item(ID, "tss_trinket_void", "Fragment of the Shattered Star", "Фрагмент Расколотой Звезды", EquipType.TRINKET, null, "ADAPTIVE",
                stats("CRIT", "HASTE"), "void_shard", DPS, null, null);
        item(ID, "tss_trinket_idol", "Idol of the Devourer", "Идол Пожирателя", EquipType.TRINKET, null, "ADAPTIVE", stats("MASTERY", "CRIT"), "idol_of_fury", DPS, null, null);
        item(ID, "tss_trinket_chalice", "Starweaver's Chalice", "Чаша ткачей звезд", EquipType.TRINKET, null, "ADAPTIVE", stats("HASTE", "VERSATILITY"),
                "chalice_of_renewal", HEAL, null, null);
        item(ID, "tss_trinket_heart", "Titan Core", "Ядро титана", EquipType.TRINKET, null, "ADAPTIVE", stats("VERSATILITY", "MASTERY"), "heart_of_the_mountain", TANK, null, null);
        item(ID, "tss_cloak", "Drape of the Endless Night", "Покров бесконечной ночи", EquipType.BACK, null, "ADAPTIVE", stats("CRIT", "MASTERY"), null, ANY, null, null);
        item(ID, "tss_ring", "Signet of the Observatory", "Печатка обсерватории", EquipType.FINGER, null, null, stats("HASTE", "MASTERY"), null, ANY, null, null);
        item(ID, "tss_neck", "Chain of Bound Stars", "Цепь связанных звезд", EquipType.NECK, null, null, stats("CRIT", "VERSATILITY"), null, ANY, null, null);
        item(ID, "tss_belt", "Girdle of Gravity", "Пояс гравитации", EquipType.WAIST, null, "ADAPTIVE", stats("HASTE", "VERSATILITY"), null, ANY, null, null);
        item(ID, "tss_boots", "Treads of the Event Horizon", "Сапоги горизонта событий", EquipType.FEET, null, "ADAPTIVE", stats("MASTERY", "CRIT"), null, ANY, null, null);
    }

    /** Sentinel Prime: tank swap stacks, arc lines, soak overcharges, spark drones. */
    static final class SentinelPrime extends BossScript {
        @Override
        public void onPull() {
            say("ANOMALY DETECTED. ERADICATE.", "ОБНАРУЖЕНА АНОМАЛИЯ. УНИЧТОЖИТЬ.");
            enrageAfter(6 * 60);
            every("smash", 6, 10, "Titanic Smash", "Титанический удар", () -> {
                UnitState t = tank();
                if (t == null) return;
                coneAt(t, 70, 9, 1.5, RED, hit(School.PHYSICAL, 6));
                applyAura(t, "titanic_vulnerability");
                if (t.auras().stacks("titanic_vulnerability") >= 3) warn("Taunt swap!", "Смена танков!", RED);
            });
            every("arcs", 12, 15, "Arc Lines", "Дуговые линии", () -> {
                for (UnitState p : randomNonTanks(3)) lineTo(p, 40, 3, 2.5, BLUE, hit(School.ARCANE, 5));
            });
            every("overcharge", 20, 28, "Overcharge", "Перегрузка", () -> {
                for (int i = 0; i < 2; i++) soakAt(randomPointAround(7, 13), 3.5, 5, 2, hit(School.ARCANE, 12), hit(School.ARCANE, 4));
                warn("Overcharge: 2 players in each circle!", "Перегрузка: по 2 игрока в каждый круг!", YELLOW);
            });
            every("drones", 30, 40, "Spark Drones", "Искрящиеся дроны", () -> {
                warn("Kill the Spark Drones before they overload!", "Убейте дронов до перегрузки!", ORANGE);
                addsAround("spark_drone", 3, 12);
            });
        }
    }

    /** The Twin Starweavers: keep their health balanced, spread lunar beams, stack on solar flares. */
    static final class Starweavers extends BossScript {
        private boolean widowed;

        @Override
        public void onPull() {
            say("Light and dark, as one!", "Свет и тьма — как одно целое!");
            enrageAfter(7 * 60);
            every("lunar", 8, 14, "Lunar Beams", "Лунные лучи", () -> {
                for (UnitState p : randomPlayers(3)) circleOn(p, 5, 3.5, BLUE, hit(School.ARCANE, 6));
                warn("Lunar Beams — spread!", "Лунные лучи — разойдитесь!", BLUE);
            });
            every("solar", 16, 22, "Solar Flare", "Солнечная вспышка", () -> {
                var t = randomNonTanks(1);
                if (!t.isEmpty()) stackOn(t.get(0), 5, 5, hit(School.FIRE, 30));
                warn("Solar Flare — stack!", "Солнечная вспышка — соберитесь!", ORANGE);
            });
            every("eclipse", 30, 45, "Eclipse", "Затмение", () -> {
                raidDamage(hit(School.ARCANE, 3));
                for (int i = 0; i < 8; i++) circleAt(randomPointAround(3, 16), 3, 3, PURPLE, hit(School.SHADOW, 5));
            });
        }

        @Override
        public void onTick() {
            if (e.bosses.size() < 2) return;
            UnitState a = e.bosses.get(0), b = e.bosses.get(1);
            if (!widowed && (a.isDead() || b.isDead())) {
                widowed = true;
                UnitState survivor = a.isDead() ? b : a;
                if (survivor.isAlive()) {
                    applyAura(survivor, "boss_enrage");
                    warn(survivor.name + " is enraged by grief!", survivor.name + " в ярости от горя!", RED);
                }
                return;
            }
            if (!widowed && Math.abs(a.healthFraction() - b.healthFraction()) > 0.1) {
                for (UnitState x : e.bosses) {
                    if (x.isAlive() && !x.auras().has("celestial_bond")) applyAura(x, "celestial_bond");
                }
                if (((long) (e.elapsed() * 20)) % 200 == 0) warn("Balance damage between the twins!", "Распределяйте урон между близнецами поровну!", YELLOW);
            }
        }
    }

    /** Gorvax: devour stacks (tank swap), acid pools, roar, hungering maw pull. */
    static final class Gorvax extends BossScript {
        @Override
        public void onPull() {
            say("*ravenous roar*", "*голодный рев*");
            enrageAfter(6 * 60);
            every("devour", 8, 14, "Devour", "Пожирание", () -> {
                UnitState t = tank();
                if (t == null) return;
                hitUnit(t, hit(School.PHYSICAL, 5));
                applyAura(t, "devour");
                if (t.auras().stacks("devour") >= 2) warn("Devour stacks high — taunt swap!", "Много зарядов Пожирания — смена танков!", RED);
            });
            every("acid", 10, 14, "Acid Spray", "Кислотные брызги", () -> {
                for (UnitState p : randomPlayers(3)) poolAt(p.position(), 3.5, 25, GREEN, hit(School.NATURE, 1.2));
            });
            every("roar", 25, 30, "Deafening Roar", "Оглушительный рев", () -> raidDamage(Effects.all(hit(School.PHYSICAL, 2.5), knock(0.8))));
            every("maw", 40, 45, "Hungering Maw", "Голодная пасть", () -> {
                warn("Hungering Maw — run away from Gorvax!", "Голодная пасть — бегите от Горвакса!", RED);
                for (UnitState p : alivePlayers()) {
                    if (p.position().distance(bossPos()) > 6) p.body.setVelocity(bossPos().sub(p.position()).normalize().mul(0.6));
                }
                circleAt(bossPos(), 12, 4, RED, hit(School.PHYSICAL, 9));
            });
        }

        @Override
        public double[] thresholds() {
            return new double[]{0.3};
        }

        @Override
        public void onHealthBelow(double f) {
            buffBoss("gorvax_frenzy");
            warn("Gorvax is frenzied!", "Горвакс в бешенстве!", RED);
        }
    }

    /** Eltheris: arcane orbs, time lock (stop casting!), mirror images at 66% / 33%. */
    static final class Eltheris extends BossScript {
        @Override
        public void onPull() {
            say("Time itself bends to my will.", "Само время подчиняется моей воле.");
            enrageAfter(6 * 60);
            every("orbs", 6, 10, "Arcane Orbs", "Чародейские сферы", () -> {
                for (int i = 0; i < 5; i++) circleAt(randomPointAround(3, 16), 3, 2.5, PURPLE, hit(School.ARCANE, 5));
            });
            every("timelock", 20, 35, "Time Lock", "Временной замок", () -> {
                warn("Time Lock in 3s — STOP CASTING!", "Временной замок через 3 с — ПРЕКРАТИТЕ ЧТЕНИЕ!", RED);
                after(3, () -> {
                    for (UnitState p : alivePlayers()) {
                        if (p.isCasting()) {
                            applyAura(p, "time_lock");
                            hitUnit(p, hit(School.ARCANE, 4));
                        }
                    }
                });
            });
            every("barrage", 4, 6, null, null, () -> {
                UnitState t = tank();
                if (t != null) hitUnit(t, hit(School.ARCANE, 4));
            });
        }

        @Override
        public double[] thresholds() {
            return new double[]{0.66, 0.33};
        }

        @Override
        public void onHealthBelow(double f) {
            say("Which of us is real?", "Кто из нас настоящий?");
            addsAround("eltheris_image", 3, 12);
            warn("Mirror Images! Interrupt their bolts.", "Зеркальные изображения! Прерывайте их заклинания.", PURPLE);
        }
    }

    /** Xal'zorith: three phases. */
    static final class Xalzorith extends BossScript {
        @Override
        public void onPull() {
            say("You stand at the edge of nothing.", "Вы стоите на краю пустоты.");
            enrageAfter(8 * 60);
            every("starfall", 6, 10, "Starfall", "Звездопад", () -> {
                for (int i = 0; i < 6; i++) circleAt(randomPointAround(3, 18), 3.5, 2.5, PURPLE, hit(School.ARCANE, 5));
            });
            every("fragments", 20, 35, "Void Fragments", "Осколки Бездны", () -> {
                warn("Void Fragments! Kill them!", "Осколки Бездны! Убейте их!", PURPLE);
                addsAround("void_fragment", 3, 14);
            });
            every("gravity", 15, 25, "Gravity Well", "Гравитационный колодец", () -> {
                soakAt(randomPointAround(6, 14), 4.5, 5, 3, hit(School.SHADOW, 18), hit(School.SHADOW, 5));
                warn("Gravity Well: 3 players soak!", "Гравитационный колодец: 3 игрока в круг!", YELLOW);
            });
            every("crush", 5, 8, null, null, () -> {
                UnitState t = tank();
                if (t != null) coneAt(t, 60, 10, 1.5, RED, hit(School.SHADOW, 6));
            });
        }

        @Override
        public double[] thresholds() {
            return new double[]{0.6, 0.3};
        }

        @Override
        public void onHealthBelow(double f) {
            if (f > 0.45) {
                phase(2);
                say("The star collapses!", "Звезда коллапсирует!");
                e.cancel("fragments");
                every("collapse", 3, 16, "Collapse", "Коллапс", () -> {
                    ringAround(8, 22, 3, PURPLE, hit(School.SHADOW, 7));
                    after(4, () -> circleAt(bossPos(), 8, 2.5, PURPLE, hit(School.SHADOW, 7)));
                });
                every("matter", 6, 15, "Dark Matter", "Темная материя", () -> {
                    for (UnitState p : randomPlayers(3)) applyAura(p, "dark_matter");
                    warn("Dispel Dark Matter!", "Рассейте Темную материю!", BLUE);
                });
            } else {
                phase(3);
                say("ALL LIGHT ENDS.", "ВЕСЬ СВЕТ УГАСНЕТ.");
                e.cancel("gravity");
                every("annihilation", 2, 6, "Annihilation", "Аннигиляция", () -> {
                    for (UnitState p : alivePlayers()) {
                        applyAura(p, "entropic_decay");
                        hitUnit(p, hit(School.SHADOW, 1.8));
                    }
                });
            }
        }

        @Override
        public void onVictory() {
            for (UnitState p : alivePlayers()) e.engine.removeAura(p, "entropic_decay", null);
            say("The... light... returns...", "Свет... возвращается...");
        }

        @Override
        public void onWipe() {
            for (UnitState p : e.host.participants(e)) e.engine.removeAura(p, "entropic_decay", null);
        }
    }
}
