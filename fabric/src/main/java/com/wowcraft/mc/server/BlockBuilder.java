package com.wowcraft.mc.server;

import com.wowcraft.core.instance.BlockOp;
import com.wowcraft.core.instance.Layout;
import com.wowcraft.core.instance.Material;
import com.wowcraft.core.instance.Palette;
import com.wowcraft.core.util.Vec3;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Places instance maps block by block, spread over several ticks. */
final class BlockBuilder {
    static final int BLOCKS_PER_TICK = 40_000;
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    private static final class Job {
        final ServerWorld world;
        final int ox, oy, oz;
        final List<BlockOp> ops;
        final Map<Material, BlockState> states = new EnumMap<>(Material.class);
        final Runnable done;
        int opIndex;
        int x, y, z;
        boolean started;

        Job(ServerWorld world, Vec3 origin, List<BlockOp> ops, Palette palette, Runnable done) {
            this.world = world;
            this.ox = (int) Math.floor(origin.x());
            this.oy = (int) Math.floor(origin.y());
            this.oz = (int) Math.floor(origin.z());
            this.ops = ops;
            this.done = done;
            for (Material m : Material.values()) {
                BlockState s = Blocks.AIR.getDefaultState();
                if (palette != null) {
                    Block b = Registries.BLOCK.get(new Identifier(palette.block(m)));
                    if (b != null) s = b.getDefaultState();
                }
                if (m == Material.AIR) s = Blocks.AIR.getDefaultState();
                states.put(m, s);
            }
        }
    }

    private final ArrayDeque<Job> jobs = new ArrayDeque<>();

    void build(ServerWorld world, Vec3 origin, Layout layout, Palette palette, Runnable done) {
        jobs.add(new Job(world, origin, layout.ops, palette, done));
    }

    void clear(ServerWorld world, Vec3 origin, Layout layout, Runnable done) {
        List<BlockOp> ops = new ArrayList<>();
        ops.add(BlockOp.box(layout.minX - 2, layout.minY - 2, layout.minZ - 2, layout.maxX + 2, layout.maxY + 3, layout.maxZ + 2, Material.AIR));
        jobs.add(new Job(world, origin, ops, null, done));
    }

    boolean busy() {
        return !jobs.isEmpty();
    }

    void tick() {
        int budget = BLOCKS_PER_TICK;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        while (budget > 0 && !jobs.isEmpty()) {
            Job j = jobs.peek();
            while (budget > 0 && j.opIndex < j.ops.size()) {
                BlockOp op = j.ops.get(j.opIndex);
                if (!j.started) {
                    j.x = op.x0();
                    j.y = op.y0();
                    j.z = op.z0();
                    j.started = true;
                }
                BlockState state = j.states.get(op.material());
                while (budget > 0) {
                    pos.set(j.ox + j.x, j.oy + j.y, j.oz + j.z);
                    if (j.world.getBlockState(pos) != state) j.world.setBlockState(pos, state, FLAGS);
                    budget--;
                    if (++j.x > op.x1()) {
                        j.x = op.x0();
                        if (++j.z > op.z1()) {
                            j.z = op.z0();
                            if (++j.y > op.y1()) {
                                j.opIndex++;
                                j.started = false;
                                break;
                            }
                        }
                    }
                }
            }
            if (j.opIndex >= j.ops.size()) {
                jobs.poll();
                try {
                    j.done.run();
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
