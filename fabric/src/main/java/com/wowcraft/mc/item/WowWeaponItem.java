package com.wowcraft.mc.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A weapon (or shield / off-hand) held in the main or off hand. Damage comes from the WoW combat engine. */
public class WowWeaponItem extends Item {
    public WowWeaponItem(Settings settings) {
        super(settings);
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
