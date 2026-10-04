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

/** The Frozen Ossuary: a crypt in the ice where a lich raises an army of bones. */
public final class FrozenOssuary extends NpcKit {
    public static final String ID = "frozen_ossuary";

    public static void register() {
        strike("bone_slash", "Bone Slash", "Костяной разрез", School.PHYSICAL, 1.5);
        bolt("necro_frostbolt", "Frostbolt", "Ледяная стрела", School.FROST, 2.0, 2, Effects.aura("frost_chill"));
        debuffMod("frost_chill", "Chilled", "Озноб", DispelType.MAGIC, 6, 1, "Movement speed reduced by 30%.", "Скорость передвижения снижена на 30%.",
                Modifier.speed(-0.3));
        Registry_raise.register();
        dot("soul_chill", "Soul Chill", "Холод души", School.SHADOW, DispelType.DISEASE, 0.6, 12, 3);
        strike("wraith_touch", "Wraith Touch", "Касание призрака", School.SHADOW, 1.4, Effects.aura("soul_chill"));
        cleave("wyrm_frost_breath", "Frost Breath", "Морозное дыхание", School.FROST, 2.4, 80, 10, 2, Effects.aura("frost_chill"));
        strike("feaster_bite", "Feast", "Пиршество", School.PHYSICAL, 0.8);
        cc("ice_tomb", "Ice Tomb", "Ледяная гробница", CcType.STUN, DispelType.MAGIC, 6);
        dot("grave_chill", "Grave Chill", "Могильный холод", School.FROST, DispelType.DISEASE, 0.8, 10, 1);
        debuffMod("soul_reaper_mark", "Soul Reaper", "Жнец душ", null, 5, 1, "Explodes for heavy Shadow damage.", "Взрывается, нанося большой урон.",
                Modifier.taken(0.1));

        npc("bone_warrior", "Ossuary Bonewarrior", "Костяной воин склепа", NpcRank.ELITE, BodyType.HUMANOID, "skeleton", 0xFFE0E0E0).scale(1.2)
                .spell(sp("bone_slash", 6, 2));
        npc("frost_necromancer", "Frostbound Necromancer", "Скованный льдом некромант", NpcRank.NORMAL, BodyType.HUMANOID, "stray", 0xFF80B0FF)
                .ranged(22).hp(0.8).spell(sp("necro_frostbolt", 0, 0.5)).spell(sp("raise_dead_npc", 25, 8, NpcSpell.Target.SELF));
        npc("ghoul_feaster", "Ghoul Feaster", "Вурдалак-пожиратель", NpcRank.MINION, BodyType.HUMANOID, "zombie", 0xFF708070).spell(sp("feaster_bite", 3, 1));
        npc("ossuary_wraith", "Howling Wraith", "Воющий призрак", NpcRank.ELITE, BodyType.HUMANOID, "stray", 0xFF6080A0).speed(0.32)
                .spell(sp("wraith_touch", 6, 2));
        npc("frost_wyrmling", "Frost Wyrmling", "Ледяной дракончик", NpcRank.ELITE, BodyType.BEAST, "spider", 0xFFA0D0FF).scale(1.4)
                .spell(sp("wyrm_frost_breath", 12, 4));
        npc("risen_skeleton", "Risen Skeleton", "Восставший скелет", NpcRank.MINION, BodyType.HUMANOID, "skeleton", 0xFFC0C0C0).forces(0)
                .spell(sp("bone_slash", 6, 2));

        boss("ulric", "Gravewarden Ulric", "Хранитель могил Ульрик", BodyType.HUMANOID, "wither_skeleton", 0xFFB0B0C0, "ulric").hp(0.55).scale(1.8);
        boss("frostweave", "Lady Frostweave", "Леди Морозная Пряжа", BodyType.HUMANOID, "stray", 0xFF90E0FF, "frostweave").hp(0.55).ranged(18);
        boss("vharzul", "Lich-Lord Vhar'zul", "Король-лич Вар'зул", BodyType.HUMANOID, "wither_skeleton", 0xFF4080FF, "vharzul").hp(0.7).scale(2.0);
        BossScripts.register("ulric", Ulric::new);
        BossScripts.register("frostweave", Frostweave::new);
        BossScripts.register("vharzul", Vharzul::new);

        dungeon(ID, "FO", "The Frozen Ossuary", "Ледяной оссуарий",
                "Bones of a forgotten army sleep in the glacier. The Lich-Lord's song is waking them up.",
                "В леднике спят кости забытой армии. Песнь Короля-лича пробуждает их.", "frost", 25 * 60,
                RoomDef.entrance(15, 15, 8),
                RoomDef.trash(22, 26, 9, "pillars", packs(pack("bone_warrior", "frost_necromancer", "ghoul_feaster", "ghoul_feaster"),
                        pack("ossuary_wraith", "ossuary_wraith"), pack("frost_necromancer", "bone_warrior"))),
                RoomDef.boss(26, 10, "statues", "ulric"),
                RoomDef.hall(14, 28, 9, packs(pack("ghoul_feaster", "ghoul_feaster", "ghoul_feaster", "ghoul_feaster", "ghoul_feaster"),
                        pack("frost_wyrmling"))),
                RoomDef.boss(26, 11, "platform", "frostweave"),
                RoomDef.trash(24, 24, 10, "rubble", packs(pack("frost_wyrmling", "bone_warrior"), pack("frost_necromancer", "frost_necromancer", "ossuary_wraith"))),
                RoomDef.boss(30, 13, "pillars", "vharzul"));

        item(ID, "fo_runeblade", "Frostbitten Runeblade", "Обмороженный рунический клинок", EquipType.TWO_HAND, WeaponType.SWORD_2H, "STRENGTH",
                stats("MASTERY", "HASTE"), null, MELEE, null, null);
        item(ID, "fo_staff", "Staff of the Lich-Lord", "Посох Короля-лича", EquipType.TWO_HAND, WeaponType.STAFF, "INTELLECT",
                stats("MASTERY", "CRIT"), null, CASTER, "Cold enough to burn.", "Холодный настолько, что обжигает.");
        item(ID, "fo_bow", "Bonestring Longbow", "Длинный лук с костяной тетивой", EquipType.RANGED, WeaponType.BOW, "AGILITY",
                stats("CRIT", "MASTERY"), null, DPS, null, null);
        item(ID, "fo_trinket_ward", "Ward of Ages", "Оберег веков", EquipType.TRINKET, null, "ADAPTIVE", stats("VERSATILITY", "HASTE"), "ward_of_ages", TANK, null, null);
        item(ID, "fo_trinket_scroll", "Scroll of Wisdom", "Свиток мудрости", EquipType.TRINKET, null, "INTELLECT", stats("HASTE", "MASTERY"),
                "scroll_of_wisdom", CASTER, null, null);
        item(ID, "fo_chest", "Glacial Bone Hauberk", "Ледяной костяной хауберк", EquipType.CHEST, null, "ADAPTIVE", stats("CRIT", "VERSATILITY"), null, ANY, null, null);
        item(ID, "fo_bracers", "Shackles of the Ossuary", "Кандалы оссуария", EquipType.WRIST, null, "ADAPTIVE", stats("HASTE", "MASTERY"), null, ANY, null, null);
        item(ID, "fo_neck", "Phylactery Shard Pendant", "Подвеска с осколком филактерии", EquipType.NECK, null, null, stats("CRIT", "HASTE"), null, ANY, null, null);
    }

    /** NPC raise dead ability (summons skeletons next to the necromancer). */
    static final class Registry_raise {
        static void register() {
            com.wowcraft.core.content.Registry.register(com.wowcraft.core.spell.Ability.builder("raise_dead_npc", "Raise Dead", "Поднятие мертвых")
                    .target(com.wowcraft.core.spell.TargetType.SELF).cast(3).gcd(0).tag("spell")
                    .effect(Effects.custom("Raises two skeletons", "Поднимает двух скелетов", ctx -> {
                        for (int i = 0; i < 2; i++) {
                            var pos = ctx.caster.position().add(com.wowcraft.core.util.Vec3.fromYaw(i * 180 + 90).mul(2));
                            UnitState s = ctx.engine.world().spawnNpc(ctx.caster.worldKey(), pos, ctx.caster.yaw(), "risen_skeleton", ctx.caster, ctx.caster.level);
                            if (s != null) {
                                if (ctx.caster.target() != null) ctx.engine.aggro(s, ctx.caster.target(), 1);
                            }
                        }
                    })).vfx("raise_dead").build());
        }
    }

    /** Ulric: bone storm whirl, grave chill, shield of bones. */
    static final class Ulric extends BossScript {
        @Override
        public void onPull() {
            say("None leave the ossuary!", "Никто не покинет оссуарий!");
            every("bonestorm", 20, 35, "Bone Storm", "Костяная буря", () -> {
                warn("Bone Storm — keep away!", "Костяная буря — держитесь подальше!", RED);
                for (int i = 0; i < 4; i++) after(i * 1.5, () -> circleAt(bossPos(), 8, 1.4, RED, hit(School.PHYSICAL, 3)));
            });
            every("chill", 8, 16, "Grave Chill", "Могильный холод", () -> {
                for (UnitState p : randomPlayers(2)) applyAura(p, "grave_chill");
            });
            every("cleave", 5, 10, null, null, () -> {
                UnitState t = tank();
                if (t != null) coneAt(t, 90, 7, 1.5, RED, hit(School.PHYSICAL, 4.5));
            });
            every("bones", 25, 30, "Call of Bones", "Зов костей", () -> addsAround("risen_skeleton", 3, 8));
        }
    }

    /** Frostweave: ice tombs (dispel), blizzard pools, frost nova ring. */
    static final class Frostweave extends BossScript {
        @Override
        public void onPull() {
            say("Let the cold embrace you.", "Пусть холод обнимет вас.");
            every("tomb", 12, 22, "Ice Tomb", "Ледяная гробница", () -> {
                for (UnitState p : randomNonTanks(1)) {
                    applyAura(p, "ice_tomb");
                    warn(p.name + " is frozen — dispel!", p.name + " заморожен(а) — рассейте!", BLUE);
                }
            });
            every("blizzard", 8, 15, "Blizzard", "Снежная буря", () -> {
                for (UnitState p : randomPlayers(2)) poolAt(p.position(), 4, 12, BLUE, Effects.all(hit(School.FROST, 1.0), Effects.aura("frost_chill")));
            });
            every("nova", 18, 24, "Frost Nova", "Кольцо льда", () -> circleAt(bossPos(), 9, 2.5, BLUE, Effects.all(hit(School.FROST, 4), Effects.aura("generic_root"))));
            every("bolt", 3, 5, null, null, () -> {
                UnitState t = tank();
                if (t != null) hitUnit(t, hit(School.FROST, 2.5));
            });
        }
    }

    /** Vhar'zul: soul reaper on the tank, raise adds, death and decay, remorseless winter below 30%. */
    static final class Vharzul extends BossScript {
        @Override
        public void onPull() {
            say("Kneel before the Lich-Lord!", "Преклонитесь перед Королем-личом!");
            every("reaper", 10, 20, "Soul Reaper", "Жнец душ", () -> {
                UnitState t = tank();
                if (t == null) return;
                applyAura(t, "soul_reaper_mark");
                warn("Soul Reaper on " + t.name + "!", "Жнец душ на " + t.name + "!", PURPLE);
                after(5, () -> {
                    if (t.isAlive()) {
                        hitUnit(t, hit(School.SHADOW, 9));
                        circleAt(t.position(), 5, 0.5, PURPLE, hit(School.SHADOW, 4));
                    }
                });
            });
            every("dnd", 14, 20, "Death and Decay", "Смерть и разложение", () -> {
                for (UnitState p : randomPlayers(2)) poolAt(p.position(), 4, 15, PURPLE, hit(School.SHADOW, 1.0));
            });
            every("army", 25, 40, "Army of Bones", "Армия костей", () -> {
                warn("The dead rise!", "Мертвые встают!");
                addsAround("risen_skeleton", 4, 9);
            });
        }

        @Override
        public double[] thresholds() {
            return new double[]{0.3};
        }

        @Override
        public void onHealthBelow(double f) {
            phase(2);
            say("Feel the endless winter!", "Почувствуйте бесконечную зиму!");
            every("winter", 1, 3, "Remorseless Winter", "Беспощадная зима", () -> raidDamage(hit(School.FROST, 0.6)));
        }
    }
}
