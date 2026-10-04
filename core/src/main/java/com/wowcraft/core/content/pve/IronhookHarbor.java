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

/** Ironhook Harbor: a pirate haven built from wrecks, ruled by the Admiral's fleet. */
public final class IronhookHarbor extends NpcKit {
    public static final String ID = "ironhook_harbor";

    public static void register() {
        strike("deckhand_slash", "Slash", "Разрез", School.PHYSICAL, 0.8);
        strike("gut_slash", "Gut Slash", "Удар в живот", School.PHYSICAL, 1.5, Effects.aura("pirate_bleed"));
        dot("pirate_bleed", "Gutted", "Выпотрошен", School.PHYSICAL, DispelType.BLEED, 0.5, 10, 5);
        groundZone("bomb_toss", "Powder Bomb", "Пороховая бомба", School.FIRE, 0.8, 3, 6, 2);
        mend("grog_heal", "Healing Grog", "Целебный грог", 0.3, 2);
        cc("sea_hex", "Hex", "Сглаз", CcType.INCAPACITATE, DispelType.CURSE, 6);
        afflict("cast_sea_hex", "Hex", "Сглаз", School.NATURE, "sea_hex", 1.5, 30, true);
        cleave("anchor_swing", "Anchor Swing", "Взмах якорем", School.PHYSICAL, 2.5, 120, 7, 2, Effects.knockback(1.0));
        strike("parrot_peck", "Peck", "Клевок", School.PHYSICAL, 1.0, Effects.aura("pirate_bleed"));
        buffMod("grog_rage", "Grog Rage", "Ярость грога", DispelType.ENRAGE, 15, "Damage dealt increased by 50%.", "Наносимый урон увеличен на 50%.",
                Modifier.damage(0.5));
        selfBuff("cast_grog_rage", "Grog Rage", "Ярость грога", "grog_rage", 0);
        strike("tentacle_slam", "Tentacle Slam", "Удар щупальцем", School.PHYSICAL, 1.5);
        cc("harpoon_hooked", "Hooked", "На крюке", CcType.ROOT, null, 2);

        npc("deckhand", "Ironhook Deckhand", "Матрос Железного Крюка", NpcRank.MINION, BodyType.HUMANOID, "zombie", 0xFFB09070).spell(sp("deckhand_slash", 3, 1));
        npc("cutthroat", "Ironhook Cutthroat", "Головорез Железного Крюка", NpcRank.ELITE, BodyType.HUMANOID, "zombie", 0xFF804040).speed(0.32)
                .spell(sp("gut_slash", 6, 2)).spell(sp("cast_grog_rage", 25, 8, NpcSpell.Target.SELF));
        npc("powder_monkey", "Powder Monkey", "Пороховая обезьяна", NpcRank.NORMAL, BodyType.HUMANOID, "zombie", 0xFF603020).ranged(20).hp(0.7)
                .spell(sp("bomb_toss", 6, 2, NpcSpell.Target.GROUND_AT_TARGET));
        npc("sea_witch", "Sea Witch", "Морская ведьма", NpcRank.NORMAL, BodyType.HUMANOID, "drowned", 0xFF40A080).healer().hp(0.8)
                .spell(sp("grog_heal", 7, 3, NpcSpell.Target.LOWEST_ALLY)).spell(sp("cast_sea_hex", 18, 6, NpcSpell.Target.RANDOM_PLAYER));
        npc("bilge_brute", "Bilge Brute", "Трюмный громила", NpcRank.ELITE, BodyType.HUMANOID, "husk", 0xFF607050).scale(1.5).speed(0.22)
                .spell(sp("anchor_swing", 12, 5));
        npc("kraken_tentacle", "Kraken Tentacle", "Щупальце кракена", NpcRank.NORMAL, BodyType.HUMANOID, "drowned", 0xFF603080).stationary().forces(0)
                .hp(0.5).spell(sp("tentacle_slam", 4, 1));

        boss("rusk", "Quartermaster Rusk", "Квартирмейстер Раск", BodyType.HUMANOID, "zombie", 0xFF907050, "rusk").hp(0.55).scale(1.8);
        boss("varro", "Captain Varro", "Капитан Варро", BodyType.HUMANOID, "zombie", 0xFFA02020, "varro_bloodbeak").hp(0.4).scale(1.7).ranged(14);
        boss("bloodbeak", "Bloodbeak", "Кровоклюв", BodyType.BEAST, "spider", 0xFFE04040, "varro_bloodbeak").hp(0.3).scale(1.6).speed(0.36);
        boss("grimtide", "Admiral Grimtide", "Адмирал Мрачный Прилив", BodyType.HUMANOID, "drowned", 0xFF203060, "grimtide").hp(0.7).scale(2.0);
        BossScripts.register("rusk", Rusk::new);
        BossScripts.register("varro_bloodbeak", VarroBloodbeak::new);
        BossScripts.register("grimtide", Grimtide::new);

        dungeon(ID, "IH", "Ironhook Harbor", "Гавань Железного Крюка",
                "A pirate port built from shipwrecks. The Admiral pays his crews in blood and gold.",
                "Пиратский порт, построенный из обломков кораблей. Адмирал платит своим командам кровью и золотом.", "harbor", 24 * 60,
                RoomDef.entrance(15, 15, 8),
                RoomDef.trash(24, 24, 10, "water", packs(pack("deckhand", "deckhand", "deckhand", "cutthroat"), pack("powder_monkey", "powder_monkey", "sea_witch"),
                        pack("bilge_brute", "cutthroat"))),
                RoomDef.boss(26, 10, "platform", "rusk"),
                RoomDef.hall(14, 28, 9, packs(pack("cutthroat", "sea_witch", "powder_monkey"), pack("deckhand", "deckhand", "deckhand", "deckhand", "bilge_brute"))),
                RoomDef.boss(26, 10, "statues", "varro", "bloodbeak"),
                RoomDef.trash(22, 26, 10, "rubble", packs(pack("bilge_brute", "bilge_brute"), pack("cutthroat", "cutthroat", "sea_witch"))),
                RoomDef.boss(30, 12, "water", "grimtide"));

        item(ID, "ih_cutlass", "Admiral's Cutlass", "Абордажная сабля адмирала", EquipType.ONE_HAND, WeaponType.SWORD_1H, "AGILITY",
                stats("CRIT", "HASTE"), null, MELEE, "Polished with sea salt and regret.", "Начищена морской солью и сожалением.");
        item(ID, "ih_pistol", "Varro's Flintlock", "Кремневый пистолет Варро", EquipType.RANGED, WeaponType.GUN, "AGILITY", stats("MASTERY", "CRIT"), null, DPS, null, null);
        item(ID, "ih_anchor", "Bilge Anchor", "Трюмный якорь", EquipType.TWO_HAND, WeaponType.MACE_2H, "STRENGTH", stats("VERSATILITY", "HASTE"), null, MELEE, null, null);
        item(ID, "ih_trinket_badge", "Gladiator's Badge", "Знак гладиатора", EquipType.TRINKET, null, "ADAPTIVE", stats("VERSATILITY", "CRIT"),
                "gladiators_badge", DPS, null, null);
        item(ID, "ih_trinket_insignia", "Gladiator's Insignia", "Эмблема гладиатора", EquipType.TRINKET, null, "ADAPTIVE", stats("VERSATILITY", "HASTE"),
                "gladiators_insignia", DPS, null, null);
        item(ID, "ih_hat", "Tricorne of the Seven Seas", "Треуголка семи морей", EquipType.HEAD, null, "ADAPTIVE", stats("CRIT", "MASTERY"), null, ANY, null, null);
        item(ID, "ih_gloves", "Tar-Stained Gloves", "Перчатки в смоле", EquipType.HANDS, null, "ADAPTIVE", stats("HASTE", "VERSATILITY"), null, ANY, null, null);
        item(ID, "ih_neck", "Doubloon Necklace", "Ожерелье из дублонов", EquipType.NECK, null, null, stats("CRIT", "HASTE"), null, ANY, null, null);
    }

    /** Rusk: cannon barrage, powder keg spread, crew adds. */
    static final class Rusk extends BossScript {
        @Override
        public void onPull() {
            say("Fire the cannons!", "Огонь из пушек!");
            every("barrage", 8, 16, "Cannon Barrage", "Пушечный залп", () -> {
                for (int i = 0; i < 6; i++) circleAt(randomPointAround(2, 14), 3, 2.5, ORANGE, hit(School.FIRE, 4));
            });
            every("keg", 14, 22, "Powder Keg", "Пороховая бочка", () -> {
                for (UnitState p : randomNonTanks(2)) circleOn(p, 5, 4, ORANGE, Effects.all(hit(School.FIRE, 5), knock(1.0)));
                warn("Powder Keg: spread out!", "Пороховая бочка: разойдитесь!", ORANGE);
            });
            every("crew", 20, 30, "All Hands!", "Свистать всех наверх!", () -> addsAround("deckhand", 4, 8));
            every("punch", 4, 7, null, null, () -> {
                UnitState t = tank();
                if (t != null) hitUnit(t, hit(School.PHYSICAL, 3.5));
            });
        }
    }

    /** Varro and Bloodbeak: council; the parrot swoops and bleeds, the captain shoots. */
    static final class VarroBloodbeak extends BossScript {
        private boolean grief;

        @Override
        public void onPull() {
            say("Bloodbeak, sic 'em!", "Кровоклюв, фас!");
            every("pistol", 6, 10, "Pistol Shot", "Выстрел из пистолета", () -> {
                UnitState f = farthestPlayer();
                if (f != null) lineTo(f, 35, 2, 1.5, RED, hit(School.PHYSICAL, 5));
            });
            every("swoop", 12, 18, "Swoop", "Налет", () -> {
                for (UnitState p : randomNonTanks(2)) {
                    circleOn(p, 4, 2.5, RED, Effects.all(hit(School.PHYSICAL, 3), Effects.aura("pirate_bleed")));
                }
            });
            every("grog", 25, 35, "Grog Toss", "Бросок грога", () -> {
                for (UnitState b : e.bosses) if (b.isAlive()) applyAura(b, "grog_rage");
                warn("The bosses are enraged — Soothe!", "Боссы в ярости — успокойте!", RED);
            });
        }

        @Override
        public void onTick() {
            if (grief) return;
            for (UnitState b : e.bosses) {
                if (b.isDead()) {
                    grief = true;
                    for (UnitState o : e.bosses) if (o.isAlive()) applyAura(o, "boss_enrage");
                    say("NO! You'll pay for that!", "НЕТ! Вы за это заплатите!");
                }
            }
        }
    }

    /** Grimtide: harpoon pulls, broadside cone, tentacles. */
    static final class Grimtide extends BossScript {
        @Override
        public void onPull() {
            say("Nobody plunders my harbor!", "Никто не грабит мою гавань!");
            every("harpoon", 10, 18, "Harpoon", "Гарпун", () -> {
                for (UnitState p : randomNonTanks(1)) {
                    hitUnit(p, Effects.all(Effects.pull(), hit(School.PHYSICAL, 4)));
                    warn(p.name + " is harpooned!", p.name + " пронзен(а) гарпуном!", RED);
                }
            });
            every("broadside", 15, 22, "Broadside", "Бортовой залп", () -> coneFromBoss(90, 25, 3, ORANGE, Effects.all(hit(School.FIRE, 7), knock(1.4))));
            every("tentacles", 20, 35, "Kraken's Call", "Зов кракена", () -> {
                warn("Tentacles emerge!", "Появляются щупальца!");
                for (int i = 0; i < 3; i++) add("kraken_tentacle", randomPointAround(6, 13));
            });
            every("slash", 4, 7, null, null, () -> {
                UnitState t = tank();
                if (t != null) coneAt(t, 60, 7, 1.2, RED, hit(School.PHYSICAL, 4));
            });
        }

        @Override
        public double[] thresholds() {
            return new double[]{0.3};
        }

        @Override
        public void onHealthBelow(double f) {
            say("All hands, to the Admiral!", "Все ко мне!");
            addsAround("cutthroat", 2, 8);
        }
    }
}
