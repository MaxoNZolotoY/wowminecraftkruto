package com.wowcraft.core.instance;

import com.wowcraft.core.util.Rng;
import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a deterministic dungeon map from a {@link DungeonDef}: rooms chained by corridors with turns,
 * decorated per theme, with trash packs, bosses, a Font of Power and checkpoints.
 */
public final class LayoutGenerator {
    private LayoutGenerator() {
    }

    private static final int[][] DIRS = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}}; // +Z, +X, -Z, -X

    private record Rect(int x0, int z0, int x1, int z1) {
        boolean overlaps(Rect o, int margin) {
            return x0 - margin <= o.x1 && x1 + margin >= o.x0 && z0 - margin <= o.z1 && z1 + margin >= o.z0;
        }
    }

    public static Layout generate(DungeonDef def) {
        Rng rng = new Rng(def.id.hashCode() * 31L + 7);
        Layout layout = new Layout();
        List<Rect> placed = new ArrayList<>();
        int dir = 0;
        // first room: entry side at z = 0, centered on x = 0
        RoomDef first = def.rooms.get(0);
        Rect cur = new Rect(-first.width() / 2, 0, -first.width() / 2 + first.width() - 1, first.depth() - 1);
        placed.add(cur);
        buildRoom(layout, def, 0, first, cur, dir, rng);
        for (int i = 1; i < def.rooms.size(); i++) {
            RoomDef rd = def.rooms.get(i);
            int corridor = 6 + rng.nextInt(6);
            Rect next = null;
            int nextDir = dir;
            int[] order = rng.chance(0.5) ? new int[]{0, 1, 3} : (rng.chance(0.5) ? new int[]{1, 0, 3} : new int[]{3, 0, 1});
            for (int turn : order) {
                int d = (dir + turn) % 4;
                Rect cand = placeNext(cur, rd, d, corridor);
                boolean ok = true;
                for (Rect r : placed) if (r != cur && r.overlaps(cand, 4)) ok = false;
                if (ok) {
                    next = cand;
                    nextDir = d;
                    break;
                }
            }
            if (next == null) {
                for (int extra = 10; next == null && extra < 200; extra += 10) {
                    Rect cand = placeNext(cur, rd, dir, corridor + extra);
                    boolean ok = true;
                    for (Rect r : placed) if (r != cur && r.overlaps(cand, 4)) ok = false;
                    if (ok) next = cand;
                }
                if (next == null) next = placeNext(cur, rd, dir, corridor + 200);
                nextDir = dir;
            }
            placed.add(next);
            buildRoom(layout, def, i, rd, next, nextDir, rng);
            // corridor last so it cuts openings through both room walls
            buildCorridor(layout, cur, next, nextDir);
            cur = next;
            dir = nextDir;
        }
        return layout;
    }

    private static Rect placeNext(Rect from, RoomDef rd, int d, int corridor) {
        int cx = (from.x0 + from.x1) / 2, cz = (from.z0 + from.z1) / 2;
        int w = rd.width(), dep = rd.depth();
        return switch (d) {
            case 0 -> new Rect(cx - w / 2, from.z1 + 1 + corridor, cx - w / 2 + w - 1, from.z1 + corridor + dep);
            case 1 -> new Rect(from.x1 + 1 + corridor, cz - dep / 2, from.x1 + corridor + w, cz - dep / 2 + dep - 1);
            case 2 -> new Rect(cx - w / 2, from.z0 - corridor - dep, cx - w / 2 + w - 1, from.z0 - 1 - corridor);
            default -> new Rect(from.x0 - corridor - w, cz - dep / 2, from.x0 - 1 - corridor, cz - dep / 2 + dep - 1);
        };
    }

    private static void buildCorridor(Layout l, Rect a, Rect b, int d) {
        int h = 5;
        int x0, z0, x1, z1;
        if (d == 0 || d == 2) {
            int cx = Math.max(Math.max(a.x0, b.x0) + 2, Math.min((a.x0 + a.x1) / 2, Math.min(a.x1, b.x1) - 2));
            x0 = cx - 1;
            x1 = cx + 1;
            z0 = d == 0 ? a.z1 + 1 : b.z1 + 1;
            z1 = d == 0 ? b.z0 - 1 : a.z0 - 1;
            // carve through both walls
            l.include(BlockOp.box(x0 - 1, -1, z0, x1 + 1, h, z1, Material.WALL));
            l.include(BlockOp.box(x0, 0, z0, x1, h - 1, z1, Material.AIR));
            l.include(BlockOp.box(x0, -1, z0, x1, -1, z1, Material.PATH));
        } else {
            int cz = Math.max(Math.max(a.z0, b.z0) + 2, Math.min((a.z0 + a.z1) / 2, Math.min(a.z1, b.z1) - 2));
            z0 = cz - 1;
            z1 = cz + 1;
            x0 = d == 1 ? a.x1 + 1 : b.x1 + 1;
            x1 = d == 1 ? b.x0 - 1 : a.x0 - 1;
            l.include(BlockOp.box(x0, -1, z0 - 1, x1, h, z1 + 1, Material.WALL));
            l.include(BlockOp.box(x0, 0, z0, x1, h - 1, z1, Material.AIR));
            l.include(BlockOp.box(x0, -1, z0, x1, -1, z1, Material.PATH));
        }
        // lights along the corridor ceiling
        if (d == 0 || d == 2) {
            for (int z = z0 + 2; z <= z1 - 1; z += 5) l.include(BlockOp.at((x0 + x1) / 2, h, z, Material.LIGHT));
        } else {
            for (int x = x0 + 2; x <= x1 - 1; x += 5) l.include(BlockOp.at(x, h, (z0 + z1) / 2, Material.LIGHT));
        }
    }

    private static void buildRoom(Layout l, DungeonDef def, int index, RoomDef rd, Rect r, int dir, Rng rng) {
        int h = rd.height();
        // shell
        l.include(BlockOp.box(r.x0 - 1, -2, r.z0 - 1, r.x1 + 1, h, r.z1 + 1, Material.WALL));
        l.include(BlockOp.box(r.x0 - 1, h / 2, r.z0 - 1, r.x1 + 1, h / 2, r.z1 + 1, Material.WALL_ACCENT));
        l.include(BlockOp.box(r.x0, 0, r.z0, r.x1, h - 1, r.z1, Material.AIR));
        l.include(BlockOp.box(r.x0, -1, r.z0, r.x1, -1, r.z1, Material.FLOOR));
        l.include(BlockOp.box(r.x0 - 1, h, r.z0 - 1, r.x1 + 1, h, r.z1 + 1, Material.CEILING));
        // floor border accent
        l.include(BlockOp.box(r.x0, -1, r.z0, r.x1, -1, r.z0, Material.FLOOR_ACCENT));
        l.include(BlockOp.box(r.x0, -1, r.z1, r.x1, -1, r.z1, Material.FLOOR_ACCENT));
        l.include(BlockOp.box(r.x0, -1, r.z0, r.x0, -1, r.z1, Material.FLOOR_ACCENT));
        l.include(BlockOp.box(r.x1, -1, r.z0, r.x1, -1, r.z1, Material.FLOOR_ACCENT));
        int cx = (r.x0 + r.x1) / 2, cz = (r.z0 + r.z1) / 2;
        // central path along the travel direction
        if (dir == 0 || dir == 2) l.include(BlockOp.box(cx - 1, -1, r.z0 + 1, cx + 1, -1, r.z1 - 1, Material.PATH));
        else l.include(BlockOp.box(r.x0 + 1, -1, cz - 1, r.x1 - 1, -1, cz + 1, Material.PATH));
        // wall lights
        for (int x = r.x0 + 3; x <= r.x1 - 3; x += 6) {
            l.include(BlockOp.at(x, 2, r.z0 - 1, Material.LIGHT));
            l.include(BlockOp.at(x, 2, r.z1 + 1, Material.LIGHT));
        }
        for (int z = r.z0 + 3; z <= r.z1 - 3; z += 6) {
            l.include(BlockOp.at(r.x0 - 1, 2, z, Material.LIGHT));
            l.include(BlockOp.at(r.x1 + 1, 2, z, Material.LIGHT));
        }
        for (int x = r.x0 + 4; x <= r.x1 - 4; x += 7)
            for (int z = r.z0 + 4; z <= r.z1 - 4; z += 7) l.include(BlockOp.at(x, h, z, Material.LIGHT));

        String feature = rd.feature() == null ? "" : rd.feature();
        int w = r.x1 - r.x0 + 1, dep = r.z1 - r.z0 + 1;
        if (feature.contains("pillars") && w >= 14 && dep >= 14) {
            int[] xs = {r.x0 + w / 4, r.x1 - w / 4};
            int[] zs = {r.z0 + dep / 4, r.z1 - dep / 4};
            for (int x : xs)
                for (int z : zs) l.include(BlockOp.box(x, 0, z, x + 1, h - 1, z + 1, Material.PILLAR));
        }
        if ((feature.contains("lava") || feature.contains("water")) && w >= 16 && dep >= 16) {
            int s = 3;
            int[][] corners = {{r.x0 + 1, r.z0 + 1}, {r.x1 - s, r.z1 - s}, {r.x0 + 1, r.z1 - s}, {r.x1 - s, r.z0 + 1}};
            for (int i = 0; i < 2; i++) {
                int[] c = corners[(index + i) % 4];
                l.include(BlockOp.box(c[0], -1, c[1], c[0] + s - 1, -1, c[1] + s - 1, Material.LIQUID));
            }
        }
        if (feature.contains("statues")) {
            for (int x = r.x0 + 2; x <= r.x1 - 2; x += 5) {
                l.include(BlockOp.box(x, 0, r.z0, x, 2, r.z0, Material.WALL_ACCENT));
                l.include(BlockOp.box(x, 0, r.z1, x, 2, r.z1, Material.WALL_ACCENT));
            }
        }
        if (feature.contains("rubble")) {
            for (int i = 0; i < Math.max(2, w * dep / 120); i++) {
                int x = rng.range(r.x0 + 2, r.x1 - 2), z = rng.range(r.z0 + 2, r.z1 - 2);
                if (Math.abs(x - cx) <= 2 || Math.abs(z - cz) <= 2) continue;
                l.include(BlockOp.at(x, 0, z, rng.chance(0.5) ? Material.RUBBLE : Material.DECOR));
            }
        }
        Vec3 center = new Vec3(cx + 0.5, 0, cz + 0.5);
        if (rd.kind() == RoomDef.Kind.BOSS || feature.contains("platform")) {
            int rad = Math.min(w, dep) / 3;
            for (int x = -rad; x <= rad; x++) {
                for (int z = -rad; z <= rad; z++) {
                    double dd = Math.sqrt(x * x + z * z);
                    if (dd <= rad && dd > rad - 1) l.include(BlockOp.at(cx + x, -1, cz + z, Material.FLOOR_ACCENT));
                }
            }
            if (feature.contains("platform")) l.include(BlockOp.box(cx - 2, -1, cz - 2, cx + 2, -1, cz + 2, Material.PLATFORM));
        }
        l.rooms.add(new Layout.PlacedRoom(index, rd, r.x0, r.z0, r.x1, r.z1, h, center));

        // facing back towards where we came from
        float faceBack = (float) ((dir * 90 + 180) % 360);
        float faceForward = (float) ((dir * 90) % 360);
        switch (rd.kind()) {
            case ENTRANCE -> {
                Vec3 back = backPoint(r, dir, 3);
                l.entrance = back;
                l.entranceYaw = yawFor(dir);
                l.font = center;
                l.exit = backPoint(r, dir, 1.5);
                l.checkpoints.add(back);
            }
            case BOSS -> {
                int bi = 0;
                for (String boss : rd.bosses()) {
                    Vec3 pos = center.add(bi == 0 ? 0 : (bi % 2 == 1 ? 5 : -5), 0, 0);
                    l.spawns.add(new Layout.Spawn(boss, pos, yawFor((dir + 2) % 4), "boss_" + index, index, true));
                    bi++;
                }
                l.checkpoints.add(center);
            }
            default -> {
            }
        }
        // trash packs spread along the room
        int packs = rd.packs().size();
        for (int p = 0; p < packs; p++) {
            List<String> pack = rd.packs().get(p);
            double t = (p + 1.0) / (packs + 1.0);
            double side = (p % 2 == 0 ? 1 : -1) * Math.min(w, dep) * 0.18;
            Vec3 pc;
            if (dir == 0 || dir == 2) {
                double z = r.z0 + dep * (dir == 0 ? t : 1 - t);
                pc = new Vec3(cx + 0.5 + side, 0, z + 0.5);
            } else {
                double x = r.x0 + w * (dir == 1 ? t : 1 - t);
                pc = new Vec3(x + 0.5, 0, cz + 0.5 + side);
            }
            String packId = "pack_" + index + "_" + p;
            for (int m = 0; m < pack.size(); m++) {
                double ang = 360.0 * m / Math.max(1, pack.size());
                Vec3 pos = pack.size() == 1 ? pc : pc.add(Vec3.fromYaw(ang).mul(1.8));
                l.spawns.add(new Layout.Spawn(pack.get(m), pos, yawFor((dir + 2) % 4), packId, index, false));
            }
        }
    }

    private static Vec3 backPoint(Rect r, int dir, double inset) {
        double cx = (r.x0 + r.x1) / 2.0 + 0.5, cz = (r.z0 + r.z1) / 2.0 + 0.5;
        return switch (dir) {
            case 0 -> new Vec3(cx, 0, r.z0 + inset);
            case 1 -> new Vec3(r.x0 + inset, 0, cz);
            case 2 -> new Vec3(cx, 0, r.z1 + 1 - inset);
            default -> new Vec3(r.x1 + 1 - inset, 0, cz);
        };
    }

    /** Minecraft yaw for a travel direction (0 = +Z). */
    static float yawFor(int dir) {
        return switch (dir) {
            case 0 -> 0f;
            case 1 -> -90f;
            case 2 -> 180f;
            default -> 90f;
        };
    }
}
