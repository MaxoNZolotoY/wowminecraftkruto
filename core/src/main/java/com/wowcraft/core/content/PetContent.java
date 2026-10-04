package com.wowcraft.core.content;

import com.wowcraft.core.npc.BodyType;
import com.wowcraft.core.npc.NpcRank;
import com.wowcraft.core.npc.NpcRegistry;
import com.wowcraft.core.npc.NpcSpell;
import com.wowcraft.core.npc.NpcTemplate;
import com.wowcraft.core.spec.Role;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.spell.Effects;
import com.wowcraft.core.spell.Scaling;
import com.wowcraft.core.spell.School;
import com.wowcraft.core.spell.TargetType;

/** Class pets and temporary summons (placeholder bodies; models can be swapped through modelId). */
public final class PetContent {
    private PetContent() {
    }

    private static void melee(String id, String en, String ru, School school, double ap, double cd) {
        Registry.register(Ability.builder(id, en, ru).school(school).melee().cooldown(cd).gcd(0).hidden().noFacing()
                .effect(Effects.damage(school, Scaling.ap(ap))).vfx("pet_attack").build());
    }

    private static void bolt(String id, String en, String ru, School school, double sp, double cast, double cd) {
        Registry.register(Ability.builder(id, en, ru).school(school).range(35).cast(cast).cooldown(cd).gcd(0).hidden().noFacing()
                .effect(Effects.damage(school, Scaling.sp(sp))).vfx(school.name().toLowerCase() + "_bolt").tag("spell").build());
    }

    private static void aoe(String id, String en, String ru, School school, double ap, double radius, double cd) {
        Registry.register(Ability.builder(id, en, ru).school(school).target(TargetType.NONE).cooldown(cd).gcd(0).hidden()
                .effect(Effects.aroundSelf(radius, Effects.damage(school, Scaling.ap(ap)))).vfx("pet_aoe").build());
    }

    public static void register() {
        melee("pet_bite", "Bite", "Укус", School.PHYSICAL, 0.9, 3);
        melee("pet_claw", "Claw", "Коготь", School.PHYSICAL, 0.8, 3);
        melee("ghoul_claw", "Claw", "Коготь", School.PHYSICAL, 0.9, 3.5);
        melee("legion_strike", "Legion Strike", "Удар Легиона", School.PHYSICAL, 1.3, 6);
        melee("dreadbite", "Dreadbite", "Жуткий укус", School.SHADOW, 1.6, 8);
        melee("shadow_bite", "Shadow Bite", "Теневой укус", School.SHADOW, 1.1, 5);
        melee("earth_slam", "Earth Slam", "Земляной удар", School.NATURE, 1.0, 5);
        melee("treant_slam", "Wrath of the Grove", "Гнев рощи", School.NATURE, 0.8, 3);
        bolt("imp_firebolt", "Firebolt", "Огненная стрела", School.FIRE, 0.55, 1.0, 0);
        bolt("elemental_fire_blast", "Fire Blast", "Огненный взрыв", School.FIRE, 0.9, 0, 3);
        bolt("demonfire", "Demonfire", "Демоническое пламя", School.FIRE, 1.3, 1.5, 0);
        bolt("glare_beam", "Eye Beam", "Луч глаза", School.SHADOW, 2.0, 0, 2);
        aoe("pet_felstorm", "Felstorm", "Буря Скверны", School.PHYSICAL, 0.6, 8, 15);
        aoe("infernal_immolation", "Immolation", "Обжигание", School.FIRE, 0.5, 8, 1.5);
        aoe("niuzao_stomp", "Stomp", "Топот", School.PHYSICAL, 1.0, 8, 5);
        aoe("voidwalker_shadows", "Consuming Shadows", "Поглощающие тени", School.SHADOW, 0.4, 8, 6);
        Registry.register(Ability.builder("yulon_breath", "Soothing Breath", "Успокаивающее дыхание").school(School.NATURE).target(TargetType.FRIENDLY)
                .range(40).cooldown(1.5).gcd(0).hidden().noFacing().effect(Effects.heal(Scaling.sp(1.2))).vfx("heal").build());

        pet("hunter_pet", "Wolf", "Волк", BodyType.BEAST, "spider", 0xFFB08850, 0.45, 0.7).spell("pet_bite", 3, 0.5);
        pet("dire_beast", "Dire Beast", "Зверь", BodyType.BEAST, "spider", 0xFF8A6A3A, 0.35, 0.4).spell("pet_claw", 3, 0.2);
        pet("spirit_wolf", "Spirit Wolf", "Волк-призрак", BodyType.BEAST, "spider", 0xFF90C8FF, 0.25, 0.4).spell("pet_bite", 3, 0.3);
        pet("ghoul", "Ghoul", "Вурдалак", BodyType.HUMANOID, "zombie", 0xFF7FA07F, 0.45, 0.6).spell("ghoul_claw", 3.5, 0.5);
        pet("army_ghoul", "Army of the Dead", "Войско мертвых", BodyType.HUMANOID, "zombie", 0xFF557055, 0.15, 0.3).spell("ghoul_claw", 3.5, 0.5);
        pet("imp", "Imp", "Бес", BodyType.ELEMENTAL, "blaze", 0xFFFF8040, 0.4, 0.4).ranged(25).spell("imp_firebolt", 0, 0.2);
        pet("wild_imp", "Wild Imp", "Дикий бес", BodyType.ELEMENTAL, "blaze", 0xFFFFA060, 0.18, 0.2).ranged(25).spell("imp_firebolt", 0, 0.2);
        pet("voidwalker", "Voidwalker", "Демон Бездны", BodyType.HUMANOID, "drowned", 0xFF6040C0, 0.3, 1.0).spell("voidwalker_shadows", 6, 1)
                .role = Role.TANK;
        pet("felhunter", "Felhunter", "Охотник Скверны", BodyType.BEAST, "cave_spider", 0xFF60C060, 0.4, 0.6).spell("shadow_bite", 5, 0.5);
        pet("felguard", "Felguard", "Страж Скверны", BodyType.HUMANOID, "husk", 0xFF70A040, 0.55, 0.75).scale(1.2)
                .spell("legion_strike", 6, 0.5).spell("pet_felstorm", 20, 3);
        pet("dreadstalker", "Dreadstalker", "Зловещий охотник", BodyType.BEAST, "spider", 0xFF503060, 0.4, 0.4).spell("dreadbite", 8, 0.2);
        pet("darkglare", "Darkglare", "Мрачный взор", BodyType.ELEMENTAL, "blaze", 0xFF8030A0, 0.6, 0.5).ranged(30).spell("glare_beam", 2, 0.2);
        pet("demonic_tyrant", "Demonic Tyrant", "Демонический тиран", BodyType.HUMANOID, "wither_skeleton", 0xFFD040D0, 0.7, 0.8).scale(1.5)
                .ranged(30).spell("demonfire", 0, 0.2);
        pet("infernal", "Infernal", "Инфернал", BodyType.HUMANOID, "husk", 0xFF40FF40, 0.5, 0.8).scale(1.6).spell("infernal_immolation", 1.5, 0.5);
        pet("earth_elemental", "Earth Elemental", "Элементаль земли", BodyType.HUMANOID, "husk", 0xFF907050, 0.3, 1.5).scale(1.5)
                .spell("earth_slam", 5, 1).role = Role.TANK;
        pet("fire_elemental", "Fire Elemental", "Элементаль огня", BodyType.ELEMENTAL, "blaze", 0xFFFF6020, 0.5, 0.6).ranged(25)
                .spell("elemental_fire_blast", 3, 0.2);
        pet("treant", "Treant", "Древень", BodyType.HUMANOID, "husk", 0xFF609040, 0.3, 0.5).spell("treant_slam", 3, 0.3);
        pet("niuzao", "Niuzao", "Нюцзао", BodyType.BEAST, "spider", 0xFF806040, 0.5, 1.2).scale(1.8).spell("niuzao_stomp", 5, 0.5).role = Role.TANK;
        NpcTemplate yulon = pet("yulon", "Yu'lon", "Юй-лун", BodyType.ELEMENTAL, "blaze", 0xFF40E0A0, 0.6, 0.8).ranged(30).healer();
        yulon.spell(NpcSpell.of("yulon_breath", 1.5, 0.2).on(NpcSpell.Target.LOWEST_ALLY));
    }

    private static NpcTemplate pet(String id, String en, String ru, BodyType body, String texture, int tint, double power, double health) {
        NpcTemplate t = new NpcTemplate(id, en, ru, NpcRank.PET).body(body).texture(texture).tint(tint).pet(power, health).forces(0);
        t.model("wowcraft:pet/" + id, "wowcraft:pet");
        NpcRegistry.register(t);
        return t;
    }
}
