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

/** Spire of the Void Choir: cultists sing hymns to open a rift to the Void. */
public final class SpireOfTheVoidChoir extends NpcKit {
    public static final String ID = "spire_of_the_void_choir";

    public static void register() {
        bolt("void_shadow_bolt", "Shadow Bolt", "Стрела Тьмы", School.SHADOW, 2.2, 2.5);
        nova("void_dirge", "Dirge of the Void", "Панихида Бездны", School.SHADOW, 1.6, 30, 3, true);
        strike("terror_rend", "Rend Mind", "Разрыв разума", School.SHADOW, 1.6, Effects.aura("mind_rend"));
        debuffMod("mind_rend", "Rent Mind", "Разорванный разум", DispelType.MAGIC, 10, 3, "Damage dealt reduced by 8% per stack.",
                "Наносимый урон снижен на 8% за заряд.", Modifier.damage(-0.08));
        mend("dark_mending_void", "Dark Mending", "Темное исцеление", 0.3, 2.5);
        strike("fiend_claw", "Void Claw", "Коготь Бездны", School.SHADOW, 0.7);
        cc("void_silence", "Hush", "Тишина", CcType.SILENCE, DispelType.MAGIC, 4);
        afflict("cast_void_silence", "Hush", "Тишина", School.SHADOW, "void_silence", 0, 30, false);
        buffMod("voice_shield", "Harmonic Barrier", "Гармонический барьер", null, 0, "Damage taken reduced by 75%.", "Получаемый урон снижен на 75%.",
                Modifier.taken(-0.75));
        buffMod("choir_crescendo", "Crescendo", "Крещендо", DispelType.MAGIC, 20, "Haste increased by 30%.", "Скорость увеличена на 30%.", Modifier.haste(0.3));
        allyBuff("cast_crescendo", "Crescendo", "Крещендо", "choir_crescendo", 2);

        npc("void_acolyte", "Void Acolyte", "Послушник Бездны", NpcRank.NORMAL, BodyType.HUMANOID, "stray", 0xFF6A40A0).ranged(22).hp(0.8)
                .spell(sp("void_shadow_bolt", 0, 0.5)).spell(sp("cast_void_silence", 20, 8, NpcSpell.Target.HEALER));
        npc("choir_singer", "Choir Singer", "Певчий хора", NpcRank.ELITE, BodyType.HUMANOID, "stray", 0xFFA060E0).ranged(18)
                .spell(sp("void_dirge", 14, 4)).spell(sp("cast_crescendo", 22, 6, NpcSpell.Target.LOWEST_ALLY)).spell(sp("void_shadow_bolt", 0, 1));
        npc("void_terror", "Void Terror", "Ужас Бездны", NpcRank.ELITE, BodyType.HUMANOID, "wither_skeleton", 0xFF40205A).scale(1.4)
                .spell(sp("terror_rend", 7, 2));
        npc("umbral_mender", "Umbral Mender", "Сумрачный целитель", NpcRank.NORMAL, BodyType.HUMANOID, "stray", 0xFF8070C0).healer().hp(0.8)
                .spell(sp("dark_mending_void", 7, 3, NpcSpell.Target.LOWEST_ALLY)).spell(sp("void_shadow_bolt", 0, 1));
        npc("void_fiend", "Void Fiend", "Исчадие Бездны", NpcRank.MINION, BodyType.ELEMENTAL, "blaze", 0xFF5020A0).scale(0.7).spell(sp("fiend_claw", 2.5, 1));

        boss("nyxis", "Cantor Nyxis", "Кантор Никсис", BodyType.HUMANOID, "stray", 0xFF9040E0, "nyxis").hp(0.55).ranged(15).scale(1.8);
        boss("choir_soprano", "Soprano Vaelis", "Сопрано Ваэлис", BodyType.HUMANOID, "stray", 0xFFE080FF, "hollow_choir").hp(0.35).ranged(16);
        boss("choir_bass", "Basso Grummoth", "Бас Груммот", BodyType.HUMANOID, "wither_skeleton", 0xFF502080, "hollow_choir").hp(0.35).scale(2.0);
        boss("xalveth", "Herald Xal'veth", "Глашатай Ксал'вет", BodyType.HUMANOID, "wither_skeleton", 0xFF300060, "xalveth").hp(0.7).scale(2.2);
        BossScripts.register("nyxis", Nyxis::new);
        BossScripts.register("hollow_choir", HollowChoir::new);
        BossScripts.register("xalveth", Xalveth::new);

        dungeon(ID, "SVC", "Spire of the Void Choir", "Шпиль Хора Бездны",
                "A tower of black glass where a choir of zealots sings the Void into the world.",
                "Башня из черного стекла, где хор фанатиков песнью призывает Бездну в мир.", "void", 26 * 60,
                RoomDef.entrance(15, 15, 8),
                RoomDef.trash(22, 26, 12, "pillars", packs(pack("void_acolyte", "void_acolyte", "umbral_mender"), pack("void_terror", "void_fiend", "void_fiend"),
                        pack("choir_singer", "void_acolyte"))),
                RoomDef.boss(26, 12, "platform", "nyxis"),
                RoomDef.hall(14, 28, 10, packs(pack("void_fiend", "void_fiend", "void_fiend", "void_fiend"), pack("choir_singer", "umbral_mender", "void_terror"))),
                RoomDef.boss(28, 12, "statues", "choir_soprano", "choir_bass"),
                RoomDef.trash(24, 24, 12, "rubble", packs(pack("void_terror", "void_terror"), pack("choir_singer", "choir_singer", "umbral_mender"))),
                RoomDef.boss(32, 14, "platform", "xalveth"));

        item(ID, "svc_dagger", "Voidsong Stiletto", "Стилет песни Бездны", EquipType.ONE_HAND, WeaponType.DAGGER, "INTELLECT", stats("HASTE", "MASTERY"), null, CASTER, null, null);
        item(ID, "svc_glaive", "Herald's Riftglaive", "Глефа разлома глашатая", EquipType.ONE_HAND, WeaponType.WARGLAIVE, "AGILITY",
                stats("CRIT", "VERSATILITY"), null, MELEE, null, null);
        item(ID, "svc_axe", "Choirbreaker", "Разрушитель хора", EquipType.TWO_HAND, WeaponType.AXE_2H, "STRENGTH", stats("HASTE", "CRIT"), null, MELEE, null, null);
        item(ID, "svc_trinket_void", "Shard of the Void", "Осколок Бездны", EquipType.TRINKET, null, "ADAPTIVE", stats("HASTE", "CRIT"), "void_shard", DPS, null, null);
        item(ID, "svc_trinket_prism", "Prism of Focus", "Призма сосредоточенности", EquipType.TRINKET, null, "ADAPTIVE", stats("MASTERY", "HASTE"),
                "prism_of_focus", ANY, null, null);
        item(ID, "svc_head", "Cowl of Whispers", "Капюшон шепотов", EquipType.HEAD, null, "ADAPTIVE", stats("HASTE", "MASTERY"), null, ANY, null, null);
        item(ID, "svc_waist", "Cord of the Hymn", "Шнур гимна", EquipType.WAIST, null, "ADAPTIVE", stats("CRIT", "VERSATILITY"), null, ANY, null, null);
        item(ID, "svc_neck", "Choker of Dark Harmony", "Ошейник темной гармонии", EquipType.NECK, null, null, stats("VERSATILITY", "MASTERY"), null, ANY, null, null);
    }

    /** Nyxis: stack hymn, rifts, silences. */
    static final class Nyxis extends BossScript {
        @Override
        public void onPull() {
            say("Sing with us... forever.", "Пойте с нами... вечно.");
            every("hymn", 12, 20, "Discordant Hymn", "Диссонирующий гимн", () -> {
                var t = randomNonTanks(1);
                if (!t.isEmpty()) stackOn(t.get(0), 4, 4, hit(School.SHADOW, 10));
            });
            every("rift", 8, 16, "Void Rift", "Разлом Бездны", () -> {
                for (UnitState p : randomPlayers(2)) poolAt(p.position(), 3, 20, PURPLE, hit(School.SHADOW, 1.0));
            });
            every("hush", 15, 22, "Hush", "Тишина", () -> {
                for (UnitState p : randomNonTanks(2)) applyAura(p, "void_silence");
            });
            every("bolt", 2, 4, null, null, () -> {
                UnitState t = tank();
                if (t != null) hitUnit(t, hit(School.SHADOW, 2.5));
            });
        }
    }

    /** The Hollow Choir: council of two; when one sings, the other is shielded. Kill them together. */
    static final class HollowChoir extends BossScript {
        private boolean deathCry;

        @Override
        public void onPull() {
            say("Our duet will be your requiem!", "Наш дуэт станет вашим реквиемом!");
            every("duet", 15, 30, "Harmonic Barrier", "Гармонический барьер", () -> {
                // alternate the shielded singer
                for (UnitState b : e.bosses) e.engine.removeAura(b, "voice_shield", null);
                UnitState shielded = e.bosses.get(((int) (e.elapsed() / 30)) % e.bosses.size());
                if (shielded.isAlive()) applyAura(shielded, "voice_shield");
                warn(shielded.name + " is shielded — switch target!", shielded.name + " под щитом — смените цель!", PURPLE);
            });
            every("aria", 8, 12, "Piercing Aria", "Пронзительная ария", () -> {
                for (UnitState p : randomPlayers(2)) circleOn(p, 4, 3, PURPLE, hit(School.SHADOW, 4));
            });
            every("quake", 10, 16, "Basso Quake", "Басовый толчок", () -> {
                UnitState bass = e.bosses.size() > 1 ? e.bosses.get(1) : boss();
                if (bass.isAlive()) {
                    e.addTelegraph(com.wowcraft.core.npc.Telegraph.ring(bass, bass.position(), 5, 14, e.now(), 2.5, PURPLE,
                            Effects.all(hit(School.SHADOW, 4), knock(0.8))));
                }
            });
        }

        @Override
        public void onTick() {
            if (deathCry) return;
            for (UnitState b : e.bosses) {
                if (b.isDead()) {
                    deathCry = true;
                    for (UnitState o : e.bosses) {
                        if (o.isAlive()) {
                            e.engine.removeAura(o, "voice_shield", null);
                            applyAura(o, "boss_enrage");
                            say("You will sing alone no more!", "Больше ты не будешь петь один!");
                        }
                    }
                }
            }
        }
    }

    /** Xal'veth: eruptions under everyone, gaze lines, dark ascension. */
    static final class Xalveth extends BossScript {
        @Override
        public void onPull() {
            say("The Void sings through me!", "Бездна поет через меня!");
            every("eruption", 10, 18, "Void Eruption", "Извержение Бездны", () -> {
                for (UnitState p : alivePlayers()) circleAt(p.position(), 3, 2.5, PURPLE, hit(School.SHADOW, 5));
            });
            every("gaze", 14, 20, "Gaze of the Abyss", "Взор Бездны", () -> {
                for (UnitState p : randomNonTanks(2)) lineTo(p, 35, 3, 2.5, PURPLE, hit(School.SHADOW, 6));
            });
            every("claw", 5, 9, null, null, () -> {
                UnitState t = tank();
                if (t != null) coneAt(t, 80, 8, 1.5, RED, hit(School.SHADOW, 5));
            });
            every("fiends", 25, 35, "Call Fiends", "Призыв исчадий", () -> addsAround("void_fiend", 3, 9));
        }

        @Override
        public double[] thresholds() {
            return new double[]{0.4};
        }

        @Override
        public void onHealthBelow(double f) {
            phase(2);
            say("I ASCEND!", "Я ВОЗНОШУСЬ!");
            every("ascension", 2, 8, "Dark Ascension", "Темное вознесение", () -> raidDamage(hit(School.SHADOW, 1.6)));
        }
    }
}
