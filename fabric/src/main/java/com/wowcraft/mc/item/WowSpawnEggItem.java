package com.wowcraft.mc.item;

import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.game.GameServer;
import com.wowcraft.core.game.PlayerSession;
import com.wowcraft.core.npc.NpcRank;
import com.wowcraft.core.npc.NpcRegistry;
import com.wowcraft.core.npc.NpcTemplate;
import com.wowcraft.core.util.L10n;
import com.wowcraft.core.util.Vec3;
import com.wowcraft.mc.WowCraftMod;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Spawn egg for any WoW creature: one item, the NPC template id is stored in the stack. */
public class WowSpawnEggItem extends Item {
    public static final String NBT_KEY = "npc";

    public WowSpawnEggItem(Settings settings) {
        super(settings);
    }

    public static ItemStack of(NpcTemplate t) {
        ItemStack stack = new ItemStack(WowItems.NPC_EGG);
        stack.getOrCreateNbt().putString(NBT_KEY, t.id);
        return stack;
    }

    public static NpcTemplate template(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null || !nbt.contains(NBT_KEY) ? null : NpcRegistry.get(nbt.getString(NBT_KEY));
    }

    /** Creatures that make sense as eggs (no pets, totems or player-like bots). */
    public static boolean spawnable(NpcTemplate t) {
        return t.rank != NpcRank.PET && t.rank != NpcRank.TOTEM && t.botSpec == null && t.ownerPowerScale <= 0 && t.ownerHealthScale <= 0
                && !t.id.startsWith("bot_");
    }

    public static int rankColor(NpcTemplate t) {
        if (t.friendly) return 0xFF40C040;
        return switch (t.rank) {
            case BOSS -> 0xFFFF8000;
            case MINIBOSS -> 0xFFA335EE;
            case ELITE -> 0xFFFFD040;
            case RARE -> 0xFFC0C0FF;
            default -> 0xFF707070;
        };
    }

    @Override
    public Text getName(ItemStack stack) {
        NpcTemplate t = template(stack);
        if (t == null) return super.getName(stack);
        return Text.literal(t.name.get(WowItems.language.get())).styled(s -> s.withColor(rankColor(t) & 0xFFFFFF));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        NpcTemplate t = template(stack);
        if (t == null) return;
        L10n.Lang lang = WowItems.language.get();
        boolean ru = lang == L10n.Lang.RU;
        if (t.title != null) tooltip.add(Text.literal("<" + t.title.get(lang) + ">").formatted(Formatting.GRAY));
        String rank = t.rank.label.get(lang);
        if (rank == null || rank.isEmpty()) rank = ru ? "Обычный" : "Normal";
        if (t.friendly) rank = ru ? "Дружелюбный" : "Friendly";
        tooltip.add(Text.literal(rank).styled(s -> s.withColor(rankColor(t) & 0xFFFFFF)));
        tooltip.add(Text.literal(ru ? "ПКМ по блоку — призвать" : "Right-click a block to summon").formatted(Formatting.DARK_GRAY));
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;
        NpcTemplate t = template(ctx.getStack());
        GameServer game = WowCraftMod.game();
        if (t == null || game == null) return ActionResult.FAIL;
        BlockPos pos = ctx.getBlockPos().offset(ctx.getSide());
        PlayerEntity player = ctx.getPlayer();
        int level = 80;
        if (player != null) {
            PlayerSession s = game.session(player.getUuid());
            if (s != null) level = s.profile.level;
        }
        float yaw = player != null ? player.getYaw() + 180 : 0;
        String worldKey = world.getRegistryKey().getValue().toString();
        UnitState u = game.spawnNpc(worldKey, new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), yaw, t.id, null, level);
        if (u == null) return ActionResult.FAIL;
        if (player == null || !player.getAbilities().creativeMode) ctx.getStack().decrement(1);
        return ActionResult.CONSUME;
    }
}
