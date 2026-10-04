package com.wowcraft.core.game;

import com.wowcraft.core.instance.BlockOp;
import com.wowcraft.core.instance.DungeonDef;
import com.wowcraft.core.instance.Layout;
import com.wowcraft.core.instance.Material;
import com.wowcraft.core.instance.RoomDef;
import com.wowcraft.core.util.Vec3;

/**
 * Arena and battleground maps. Markers: [0] team 1 start, [1] team 2 start, then mode specific points
 * (CTF: [2] team 1 flag, [3] team 2 flag; Domination: [2..] capture nodes).
 */
final class PvpMaps {
    private PvpMaps() {
    }

    static Layout generate(DungeonDef def) {
        RoomDef room = def.rooms.get(0);
        String f = room.feature() == null ? "" : room.feature();
        if (f.startsWith("ctf")) return ctf(room);
        if (f.startsWith("domination")) return domination(room);
        return arena(room, f);
    }

    private static void shell(Layout l, int x0, int z0, int x1, int z1, int h, boolean roof) {
        l.include(BlockOp.box(x0 - 1, -2, z0 - 1, x1 + 1, h, z1 + 1, Material.WALL));
        l.include(BlockOp.box(x0, 0, z0, x1, roof ? h - 1 : h, z1, Material.AIR));
        l.include(BlockOp.box(x0, -1, z0, x1, -1, z1, Material.FLOOR));
        if (roof) l.include(BlockOp.box(x0 - 1, h, z0 - 1, x1 + 1, h, z1 + 1, Material.CEILING));
        else l.include(BlockOp.box(x0, h, z0, x1, h, z1, Material.AIR));
    }

    private static void floorLights(Layout l, int x0, int z0, int x1, int z1, int step) {
        for (int x = x0 + 2; x <= x1 - 2; x += step)
            for (int z = z0 + 2; z <= z1 - 2; z += step) l.include(BlockOp.at(x, -1, z, Material.LIGHT));
    }

    /** Arena: central field with line-of-sight pillars, start rooms at both ends. */
    private static Layout arena(RoomDef room, String f) {
        Layout l = new Layout();
        int w = room.width(), d = room.depth(), h = room.height();
        int x0 = -w / 2, x1 = x0 + w - 1, z0 = 0, z1 = d - 1;
        // start rooms (7x7) behind the short walls
        shell(l, -3, -8, 3, -2, 5, true);
        shell(l, -3, d + 1, 3, d + 7, 5, true);
        shell(l, x0, z0, x1, z1, h, !f.contains("open"));
        // gates
        l.include(BlockOp.box(-1, 0, -1, 1, 3, -1, Material.AIR));
        l.include(BlockOp.box(-1, 0, d, 1, 3, d, Material.AIR));
        l.include(BlockOp.box(-1, -1, -1, 1, -1, -1, Material.PATH));
        l.include(BlockOp.box(-1, -1, d, 1, -1, d, Material.PATH));
        // accent ring
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        int rad = Math.min(w, d) / 3;
        for (int x = -rad; x <= rad; x++)
            for (int z = -rad; z <= rad; z++) {
                double dd = Math.sqrt(x * x + z * z);
                if (dd <= rad && dd > rad - 1) l.include(BlockOp.at(cx + x, -1, cz + z, Material.FLOOR_ACCENT));
            }
        // line of sight objects
        if (f.contains("tomb")) {
            l.include(BlockOp.box(cx - 3, 0, cz - 2, cx + 3, 3, cz + 2, Material.PILLAR));
            l.include(BlockOp.box(x0 + 3, 0, cz - 6, x0 + 5, 4, cz - 4, Material.PILLAR));
            l.include(BlockOp.box(x1 - 5, 0, cz + 4, x1 - 3, 4, cz + 6, Material.PILLAR));
            l.include(BlockOp.box(x0 + 4, 0, z0 + 6, x0 + 8, 2, z0 + 7, Material.RUBBLE));
            l.include(BlockOp.box(x1 - 8, 0, z1 - 7, x1 - 4, 2, z1 - 6, Material.RUBBLE));
        } else if (f.contains("bridge")) {
            l.include(BlockOp.box(x0, -2, cz - 4, x1, -1, cz + 4, Material.LIQUID));
            l.include(BlockOp.box(cx - 2, -1, cz - 4, cx + 2, -1, cz + 4, Material.PLATFORM));
            l.include(BlockOp.box(x0 + 4, 0, cz - 8, x0 + 6, h - 2, cz - 6, Material.PILLAR));
            l.include(BlockOp.box(x1 - 6, 0, cz + 6, x1 - 4, h - 2, cz + 8, Material.PILLAR));
        } else {
            int px = Math.max(4, w / 4), pz = Math.max(4, d / 4);
            l.include(BlockOp.box(cx - px - 1, 0, cz - pz - 1, cx - px + 1, h - 2, cz - pz + 1, Material.PILLAR));
            l.include(BlockOp.box(cx + px - 1, 0, cz - pz - 1, cx + px + 1, h - 2, cz - pz + 1, Material.PILLAR));
            l.include(BlockOp.box(cx - px - 1, 0, cz + pz - 1, cx - px + 1, h - 2, cz + pz + 1, Material.PILLAR));
            l.include(BlockOp.box(cx + px - 1, 0, cz + pz - 1, cx + px + 1, h - 2, cz + pz + 1, Material.PILLAR));
        }
        floorLights(l, x0, z0, x1, z1, 7);
        for (int x = x0 + 3; x <= x1 - 3; x += 6) {
            l.include(BlockOp.at(x, 2, z0 - 1, Material.LIGHT));
            l.include(BlockOp.at(x, 2, z1 + 1, Material.LIGHT));
        }
        l.include(BlockOp.at(0, 4, -5, Material.LIGHT));
        l.include(BlockOp.at(0, 4, d + 4, Material.LIGHT));
        l.markers.add(new Vec3(0.5, 0, -5));
        l.markers.add(new Vec3(0.5, 0, d + 4.5));
        l.entrance = l.markers.get(0);
        l.exit = new Vec3(0.5, 0, -7);
        l.font = new Vec3(cx + 0.5, 0, cz + 0.5);
        l.rooms.add(new Layout.PlacedRoom(0, room, x0, z0, x1, z1, h, new Vec3(cx + 0.5, 0, cz + 0.5)));
        return l;
    }

    /** Capture the flag: two bases joined by a long field with two lanes. */
    private static Layout ctf(RoomDef room) {
        Layout l = new Layout();
        int w = room.width(), d = room.depth(), h = room.height();
        int x0 = -w / 2, x1 = x0 + w - 1, z0 = 0, z1 = d - 1;
        shell(l, x0, z0, x1, z1, h, false);
        int cx = (x0 + x1) / 2;
        // bases
        int base = 16;
        l.include(BlockOp.box(x0, -1, z0, x1, -1, z0 + base, Material.FLOOR_ACCENT));
        l.include(BlockOp.box(x0, -1, z1 - base, x1, -1, z1, Material.FLOOR_ACCENT));
        l.include(BlockOp.box(x0 + 2, 0, z0 + base, cx - 4, 3, z0 + base, Material.WALL_ACCENT));
        l.include(BlockOp.box(cx + 4, 0, z0 + base, x1 - 2, 3, z0 + base, Material.WALL_ACCENT));
        l.include(BlockOp.box(x0 + 2, 0, z1 - base, cx - 4, 3, z1 - base, Material.WALL_ACCENT));
        l.include(BlockOp.box(cx + 4, 0, z1 - base, x1 - 2, 3, z1 - base, Material.WALL_ACCENT));
        // middle field: central divider (two lanes) and cover
        int mz0 = z0 + base + 8, mz1 = z1 - base - 8;
        l.include(BlockOp.box(cx - 2, 0, mz0, cx + 2, 4, mz1, Material.WALL));
        l.include(BlockOp.box(cx - 2, 0, (mz0 + mz1) / 2 - 2, cx + 2, 4, (mz0 + mz1) / 2 + 2, Material.AIR));
        for (int z = mz0 + 4; z < mz1 - 4; z += 12) {
            l.include(BlockOp.box(x0 + 5, 0, z, x0 + 7, 2, z + 2, Material.RUBBLE));
            l.include(BlockOp.box(x1 - 7, 0, z + 6, x1 - 5, 2, z + 8, Material.RUBBLE));
        }
        l.include(BlockOp.box(x0, -1, (mz0 + mz1) / 2 - 1, x1, -1, (mz0 + mz1) / 2 + 1, Material.PATH));
        floorLights(l, x0, z0, x1, z1, 9);
        // flag platforms
        l.include(BlockOp.box(cx - 1, -1, z0 + 5, cx + 1, -1, z0 + 7, Material.PLATFORM));
        l.include(BlockOp.box(cx - 1, -1, z1 - 7, cx + 1, -1, z1 - 5, Material.PLATFORM));
        l.markers.add(new Vec3(cx + 0.5, 0, z0 + 11.5));
        l.markers.add(new Vec3(cx + 0.5, 0, z1 - 10.5));
        l.markers.add(new Vec3(cx + 0.5, 0, z0 + 6.5));
        l.markers.add(new Vec3(cx + 0.5, 0, z1 - 5.5));
        l.entrance = l.markers.get(0);
        l.exit = new Vec3(x0 + 2.5, 0, z0 + 2.5);
        l.rooms.add(new Layout.PlacedRoom(0, room, x0, z0, x1, z1, h, new Vec3(cx + 0.5, 0, (z0 + z1) / 2.0)));
        return l;
    }

    /** Domination: bases in two corners, three capture nodes. */
    private static Layout domination(RoomDef room) {
        Layout l = new Layout();
        int w = room.width(), d = room.depth(), h = room.height();
        int x0 = -w / 2, x1 = x0 + w - 1, z0 = 0, z1 = d - 1;
        shell(l, x0, z0, x1, z1, h, false);
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        Vec3[] nodes = {new Vec3(x0 + 14.5, 0, cz + 0.5), new Vec3(cx + 0.5, 0, cz + 0.5), new Vec3(x1 - 13.5, 0, cz + 0.5)};
        for (Vec3 n : nodes) {
            int nx = (int) Math.floor(n.x()), nz = (int) Math.floor(n.z());
            l.include(BlockOp.box(nx - 3, -1, nz - 3, nx + 3, -1, nz + 3, Material.PLATFORM));
            l.include(BlockOp.box(nx - 3, -1, nz - 3, nx + 3, -1, nz - 3, Material.FLOOR_ACCENT));
            l.include(BlockOp.box(nx - 3, -1, nz + 3, nx + 3, -1, nz + 3, Material.FLOOR_ACCENT));
            l.include(BlockOp.at(nx, -1, nz, Material.LIGHT));
            // cover around the node
            l.include(BlockOp.box(nx - 7, 0, nz + 6, nx - 5, 3, nz + 8, Material.PILLAR));
            l.include(BlockOp.box(nx + 5, 0, nz - 8, nx + 7, 3, nz - 6, Material.PILLAR));
        }
        // bases
        l.include(BlockOp.box(cx - 8, -1, z0, cx + 8, -1, z0 + 10, Material.FLOOR_ACCENT));
        l.include(BlockOp.box(cx - 8, -1, z1 - 10, cx + 8, -1, z1, Material.FLOOR_ACCENT));
        // roads
        l.include(BlockOp.box(x0 + 2, -1, cz - 1, x1 - 2, -1, cz + 1, Material.PATH));
        l.include(BlockOp.box(cx - 1, -1, z0 + 2, cx + 1, -1, z1 - 2, Material.PATH));
        for (int i = 0; i < 6; i++) {
            int rx = x0 + 6 + i * (w - 12) / 6, rz = (i % 2 == 0) ? cz - 14 : cz + 12;
            l.include(BlockOp.box(rx, 0, rz, rx + 2, 2, rz + 2, Material.RUBBLE));
        }
        floorLights(l, x0, z0, x1, z1, 10);
        l.markers.add(new Vec3(cx + 0.5, 0, z0 + 5.5));
        l.markers.add(new Vec3(cx + 0.5, 0, z1 - 4.5));
        for (Vec3 n : nodes) l.markers.add(n);
        l.entrance = l.markers.get(0);
        l.exit = new Vec3(x0 + 2.5, 0, z0 + 2.5);
        l.rooms.add(new Layout.PlacedRoom(0, room, x0, z0, x1, z1, h, new Vec3(cx + 0.5, 0, cz + 0.5)));
        return l;
    }
}
