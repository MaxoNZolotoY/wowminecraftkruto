package com.wowcraft.core.instance;

import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.List;

/** A generated instance map (blocks + spawn points), relative to the instance origin. */
public final class Layout {
    public record PlacedRoom(int index, RoomDef def, int x0, int z0, int x1, int z1, int height, Vec3 center) {
        public boolean contains(Vec3 p) {
            return p.x() >= x0 - 0.5 && p.x() <= x1 + 1.5 && p.z() >= z0 - 0.5 && p.z() <= z1 + 1.5;
        }
    }

    public record Spawn(String templateId, Vec3 pos, float yaw, String packId, int room, boolean boss) {
    }

    public final List<BlockOp> ops = new ArrayList<>();
    public final List<PlacedRoom> rooms = new ArrayList<>();
    public final List<Spawn> spawns = new ArrayList<>();
    public final List<Vec3> checkpoints = new ArrayList<>();
    /** Arena / battleground specific points (team starts, flags, capture nodes). */
    public final List<Vec3> markers = new ArrayList<>();
    public Vec3 entrance = Vec3.ZERO;
    public float entranceYaw;
    public Vec3 font = Vec3.ZERO;
    public Vec3 exit = Vec3.ZERO;
    public int minX, minY = -2, minZ, maxX, maxY, maxZ;

    public void include(BlockOp op) {
        ops.add(op);
        if (ops.size() == 1) {
            minX = op.x0();
            minZ = op.z0();
            maxX = op.x1();
            maxZ = op.z1();
            minY = op.y0();
            maxY = op.y1();
        }
        minX = Math.min(minX, op.x0());
        minY = Math.min(minY, op.y0());
        minZ = Math.min(minZ, op.z0());
        maxX = Math.max(maxX, op.x1());
        maxY = Math.max(maxY, op.y1());
        maxZ = Math.max(maxZ, op.z1());
    }

    public long totalBlocks() {
        long n = 0;
        for (BlockOp op : ops) n += op.volume();
        return n;
    }

    public PlacedRoom roomAt(Vec3 relative) {
        for (PlacedRoom r : rooms) if (r.contains(relative)) return r;
        return null;
    }
}
