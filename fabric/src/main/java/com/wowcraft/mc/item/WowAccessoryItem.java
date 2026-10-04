package com.wowcraft.mc.item;

import com.wowcraft.core.item.ItemData;
import com.wowcraft.mc.WowCraftMod;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Neck, shoulders, back, wrists, hands, waist, rings and trinkets: equipped into WoW slots with right-click. */
public class WowAccessoryItem extends Item {
    public WowAccessoryItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);
        ItemData d = WowItems.read(stack);
        if (d == null || !(user instanceof ServerPlayerEntity sp) || WowCraftMod.game() == null) return TypedActionResult.pass(stack);
        if (WowCraftMod.game().equipExtra(sp.getUuid(), d)) {
            stack.decrement(1);
            return TypedActionResult.success(stack);
        }
        return TypedActionResult.fail(stack);
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
