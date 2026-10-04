package com.wowcraft.mc.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.DyeableArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Cloth / leather armor piece (tinted by quality) carrying WoW item data. */
public class WowDyeableArmorItem extends DyeableArmorItem {
    public WowDyeableArmorItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        return WowItems.name(stack, super.getName(stack));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        WowItems.tooltip(stack, tooltip);
    }
}
