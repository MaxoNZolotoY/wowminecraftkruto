package com.wowcraft.core.item;

import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.spec.WowClass;
import com.wowcraft.core.stat.Stat;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.wowcraft.core.item.WeaponType.*;

/** Which weapons classes can equip and which ones each spec looks for in loot. */
public final class Proficiency {
    private Proficiency() {
    }

    private static final Map<WowClass, Set<WeaponType>> ALLOWED = new EnumMap<>(WowClass.class);
    private static final Map<Spec, Set<WeaponType>> PREFERRED = new EnumMap<>(Spec.class);

    static {
        ALLOWED.put(WowClass.WARRIOR, EnumSet.of(SWORD_1H, SWORD_2H, AXE_1H, AXE_2H, MACE_1H, MACE_2H, DAGGER, FIST, POLEARM, STAFF, BOW, GUN, SHIELD));
        ALLOWED.put(WowClass.PALADIN, EnumSet.of(SWORD_1H, SWORD_2H, AXE_1H, AXE_2H, MACE_1H, MACE_2H, POLEARM, SHIELD, OFF_HAND_HELD));
        ALLOWED.put(WowClass.HUNTER, EnumSet.of(BOW, GUN, AXE_1H, AXE_2H, SWORD_1H, SWORD_2H, DAGGER, FIST, POLEARM, STAFF));
        ALLOWED.put(WowClass.ROGUE, EnumSet.of(DAGGER, FIST, SWORD_1H, AXE_1H, MACE_1H, BOW, GUN));
        ALLOWED.put(WowClass.PRIEST, EnumSet.of(STAFF, MACE_1H, DAGGER, WAND, OFF_HAND_HELD));
        ALLOWED.put(WowClass.DEATH_KNIGHT, EnumSet.of(SWORD_1H, SWORD_2H, AXE_1H, AXE_2H, MACE_1H, MACE_2H, POLEARM));
        ALLOWED.put(WowClass.SHAMAN, EnumSet.of(AXE_1H, AXE_2H, MACE_1H, MACE_2H, DAGGER, FIST, STAFF, SHIELD, OFF_HAND_HELD));
        ALLOWED.put(WowClass.MAGE, EnumSet.of(STAFF, SWORD_1H, DAGGER, WAND, OFF_HAND_HELD));
        ALLOWED.put(WowClass.WARLOCK, EnumSet.of(STAFF, SWORD_1H, DAGGER, WAND, OFF_HAND_HELD));
        ALLOWED.put(WowClass.MONK, EnumSet.of(FIST, AXE_1H, MACE_1H, SWORD_1H, POLEARM, STAFF, OFF_HAND_HELD));
        ALLOWED.put(WowClass.DRUID, EnumSet.of(STAFF, POLEARM, MACE_1H, MACE_2H, DAGGER, FIST, OFF_HAND_HELD));
        ALLOWED.put(WowClass.DEMON_HUNTER, EnumSet.of(WARGLAIVE, FIST, AXE_1H, SWORD_1H));
        ALLOWED.put(WowClass.EVOKER, EnumSet.of(STAFF, DAGGER, FIST, AXE_1H, MACE_1H, SWORD_1H, AXE_2H, MACE_2H, SWORD_2H, OFF_HAND_HELD));

        Set<WeaponType> twoHandStr = EnumSet.of(SWORD_2H, AXE_2H, MACE_2H, POLEARM);
        Set<WeaponType> oneHandStr = EnumSet.of(SWORD_1H, AXE_1H, MACE_1H, SHIELD);
        Set<WeaponType> caster = EnumSet.of(STAFF, DAGGER, MACE_1H, SWORD_1H, OFF_HAND_HELD, WAND);
        PREFERRED.put(Spec.ARMS, twoHandStr);
        PREFERRED.put(Spec.FURY, EnumSet.of(SWORD_2H, AXE_2H, MACE_2H, SWORD_1H, AXE_1H));
        PREFERRED.put(Spec.PROTECTION_WARRIOR, oneHandStr);
        PREFERRED.put(Spec.HOLY_PALADIN, EnumSet.of(MACE_1H, SWORD_1H, SHIELD, OFF_HAND_HELD));
        PREFERRED.put(Spec.PROTECTION_PALADIN, oneHandStr);
        PREFERRED.put(Spec.RETRIBUTION, twoHandStr);
        PREFERRED.put(Spec.BEAST_MASTERY, EnumSet.of(BOW, GUN));
        PREFERRED.put(Spec.MARKSMANSHIP, EnumSet.of(BOW, GUN));
        PREFERRED.put(Spec.SURVIVAL, EnumSet.of(POLEARM, STAFF));
        PREFERRED.put(Spec.ASSASSINATION, EnumSet.of(DAGGER));
        PREFERRED.put(Spec.OUTLAW, EnumSet.of(SWORD_1H, AXE_1H, MACE_1H, FIST));
        PREFERRED.put(Spec.SUBTLETY, EnumSet.of(DAGGER));
        PREFERRED.put(Spec.DISCIPLINE, caster);
        PREFERRED.put(Spec.HOLY_PRIEST, caster);
        PREFERRED.put(Spec.SHADOW, caster);
        PREFERRED.put(Spec.BLOOD, twoHandStr);
        PREFERRED.put(Spec.FROST_DK, EnumSet.of(SWORD_2H, AXE_2H, SWORD_1H, AXE_1H));
        PREFERRED.put(Spec.UNHOLY, twoHandStr);
        PREFERRED.put(Spec.ELEMENTAL, EnumSet.of(STAFF, MACE_1H, DAGGER, SHIELD, OFF_HAND_HELD));
        PREFERRED.put(Spec.ENHANCEMENT, EnumSet.of(AXE_1H, MACE_1H, FIST));
        PREFERRED.put(Spec.RESTORATION_SHAMAN, EnumSet.of(STAFF, MACE_1H, SHIELD, OFF_HAND_HELD));
        PREFERRED.put(Spec.ARCANE, caster);
        PREFERRED.put(Spec.FIRE, caster);
        PREFERRED.put(Spec.FROST_MAGE, caster);
        PREFERRED.put(Spec.AFFLICTION, caster);
        PREFERRED.put(Spec.DEMONOLOGY, caster);
        PREFERRED.put(Spec.DESTRUCTION, caster);
        PREFERRED.put(Spec.BREWMASTER, EnumSet.of(STAFF, POLEARM));
        PREFERRED.put(Spec.MISTWEAVER, EnumSet.of(STAFF, MACE_1H, SWORD_1H, OFF_HAND_HELD));
        PREFERRED.put(Spec.WINDWALKER, EnumSet.of(FIST, AXE_1H, SWORD_1H, MACE_1H));
        PREFERRED.put(Spec.BALANCE, EnumSet.of(STAFF, MACE_1H, DAGGER, OFF_HAND_HELD));
        PREFERRED.put(Spec.FERAL, EnumSet.of(STAFF, POLEARM));
        PREFERRED.put(Spec.GUARDIAN, EnumSet.of(STAFF, POLEARM));
        PREFERRED.put(Spec.RESTORATION_DRUID, EnumSet.of(STAFF, MACE_1H, OFF_HAND_HELD));
        PREFERRED.put(Spec.HAVOC, EnumSet.of(WARGLAIVE));
        PREFERRED.put(Spec.VENGEANCE, EnumSet.of(WARGLAIVE));
        PREFERRED.put(Spec.DEVASTATION, EnumSet.of(STAFF, DAGGER, OFF_HAND_HELD));
        PREFERRED.put(Spec.PRESERVATION, EnumSet.of(STAFF, DAGGER, OFF_HAND_HELD));
        PREFERRED.put(Spec.AUGMENTATION, EnumSet.of(STAFF, DAGGER, OFF_HAND_HELD));
    }

    public static boolean canEquip(WowClass c, WeaponType w) {
        return w == null || ALLOWED.getOrDefault(c, Set.of()).contains(w);
    }

    public static Set<WeaponType> preferred(Spec s) {
        return PREFERRED.getOrDefault(s, Set.of());
    }

    /** Spec can dual wield one-handers. */
    public static boolean dualWields(Spec s) {
        return switch (s) {
            case FURY, ASSASSINATION, OUTLAW, SUBTLETY, FROST_DK, ENHANCEMENT, WINDWALKER, HAVOC, VENGEANCE -> true;
            default -> false;
        };
    }

    /** Weapon primary stat that suits a spec. */
    public static Stat weaponPrimary(Spec s) {
        return s.primaryStat;
    }
}
