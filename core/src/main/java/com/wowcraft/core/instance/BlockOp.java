package com.wowcraft.core.instance;

/** Fill an inclusive box with a material (coordinates relative to the instance origin). */
public record BlockOp(int x0, int y0, int z0, int x1, int y1, int z1, Material material) {
    public static BlockOp box(int x0, int y0, int z0, int x1, int y1, int z1, Material m) {
        return new BlockOp(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1), m);
    }

    public static BlockOp at(int x, int y, int z, Material m) {
        return new BlockOp(x, y, z, x, y, z, m);
    }

    public long volume() {
        return (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
    }
}
