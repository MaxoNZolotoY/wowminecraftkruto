package com.wowcraft.mc.block;

import com.wowcraft.core.game.GameServer;
import com.wowcraft.core.game.PlayerSession;
import com.wowcraft.core.net.C2S;
import com.wowcraft.mc.WowCraftMod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Interactive blocks: Font of Power (start a keystone), exit portal, Great Vault. */
public final class WowBlocks {
    private WowBlocks() {
    }

    public static Block FONT_OF_POWER, EXIT_PORTAL, GREAT_VAULT;

    public static void register() {
        FONT_OF_POWER = reg("font_of_power", new FontOfPower(AbstractBlock.Settings.create().strength(-1.0f, 3600000.0f).luminance(s -> 12)
                .sounds(BlockSoundGroup.AMETHYST_BLOCK).nonOpaque().dropsNothing()));
        EXIT_PORTAL = reg("exit_portal", new ExitPortal(AbstractBlock.Settings.create().strength(-1.0f, 3600000.0f).luminance(s -> 15)
                .noCollision().nonOpaque().dropsNothing()));
        GREAT_VAULT = reg("great_vault", new GreatVault(AbstractBlock.Settings.create().strength(2.0f, 1200.0f).luminance(s -> 8)
                .sounds(BlockSoundGroup.METAL)));
    }

    private static Block reg(String name, Block block) {
        Identifier id = new Identifier(WowCraftMod.ID, name);
        Registry.register(Registries.BLOCK, id, block);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return block;
    }

    static PlayerSession session(PlayerEntity player) {
        GameServer g = WowCraftMod.game();
        return g == null ? null : g.session(player.getUuid());
    }

    /** Right-click to activate the Mythic Keystone. */
    public static final class FontOfPower extends Block {
        FontOfPower(Settings s) {
            super(s);
        }

        @Override
        public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
            if (world.isClient) return ActionResult.SUCCESS;
            PlayerSession s = session(player);
            if (s != null) WowCraftMod.game().instances().startKey(s);
            return ActionResult.CONSUME;
        }
    }

    /** Walk into it to leave the instance. */
    public static final class ExitPortal extends Block {
        ExitPortal(Settings s) {
            super(s);
        }

        @Override
        public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
            if (world.isClient || !(entity instanceof ServerPlayerEntity player)) return;
            PlayerSession s = session(player);
            if (s == null || s.unit == null || s.unit.instanceId == null || s.unit.inCombat()) return;
            if (WowCraftMod.game().engine().now() - s.lastPortalUse < 3) return;
            s.lastPortalUse = WowCraftMod.game().engine().now();
            WowCraftMod.game().instances().leave(s);
        }
    }

    /** The Great Vault: weekly rewards. */
    public static final class GreatVault extends Block {
        GreatVault(Settings s) {
            super(s);
        }

        @Override
        public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
            if (world.isClient) return ActionResult.SUCCESS;
            PlayerSession s = session(player);
            if (s != null) {
                C2S.Request r = new C2S.Request();
                r.what = "vault";
                WowCraftMod.game().handle(s.uuid, r);
                WowCraftMod.game().openScreen(s, "vault");
            }
            return ActionResult.CONSUME;
        }
    }
}
