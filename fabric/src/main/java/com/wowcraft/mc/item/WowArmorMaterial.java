package com.wowcraft.mc.item;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/**
 * Armor materials for WoW armor types. Protection is zero: damage is handled by the WoW combat engine.
 * The name selects the vanilla armor texture (leather / chainmail / iron) until custom armor models are added.
 */
public enum WowArmorMaterial implements ArmorMaterial {
    CLOTH("leather", SoundEvents.ITEM_ARMOR_EQUIP_LEATHER),
    LEATHER("leather", SoundEvents.ITEM_ARMOR_EQUIP_LEATHER),
    MAIL("chainmail", SoundEvents.ITEM_ARMOR_EQUIP_CHAIN),
    PLATE("iron", SoundEvents.ITEM_ARMOR_EQUIP_IRON);

    private final String texture;
    private final SoundEvent sound;

    WowArmorMaterial(String texture, SoundEvent sound) {
        this.texture = texture;
        this.sound = sound;
    }

    @Override
    public int getDurability(ArmorItem.Type type) {
        return 0;
    }

    @Override
    public int getProtection(ArmorItem.Type type) {
        return 0;
    }

    @Override
    public int getEnchantability() {
        return 0;
    }

    @Override
    public SoundEvent getEquipSound() {
        return sound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }

    @Override
    public String getName() {
        return texture;
    }

    @Override
    public float getToughness() {
        return 0;
    }

    @Override
    public float getKnockbackResistance() {
        return 0;
    }
}
