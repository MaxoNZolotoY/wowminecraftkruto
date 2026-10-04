package com.wowcraft.core.util;

/** Immutable 3D vector (Minecraft coordinates: Y is up). */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 add(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public Vec3 add(double dx, double dy, double dz) {
        return new Vec3(x + dx, y + dy, z + dz);
    }

    public Vec3 sub(Vec3 o) {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    public Vec3 mul(double f) {
        return new Vec3(x * f, y * f, z * f);
    }

    public double dot(Vec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double lengthSq() {
        return x * x + y * y + z * z;
    }

    public double horizontalLength() {
        return Math.sqrt(x * x + z * z);
    }

    public Vec3 normalize() {
        double l = length();
        return l < 1.0E-9 ? ZERO : new Vec3(x / l, y / l, z / l);
    }

    public Vec3 horizontal() {
        return new Vec3(x, 0, z);
    }

    public double distance(Vec3 o) {
        return sub(o).length();
    }

    public double distanceSq(Vec3 o) {
        return sub(o).lengthSq();
    }

    public double horizontalDistance(Vec3 o) {
        double dx = x - o.x, dz = z - o.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Unit direction for a Minecraft yaw (degrees). Yaw 0 looks towards +Z. */
    public static Vec3 fromYaw(double yawDeg) {
        double r = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    /** Minecraft yaw (degrees) looking from this point towards another. */
    public double yawTo(Vec3 o) {
        double dx = o.x - x, dz = o.z - z;
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    public Vec3 lerp(Vec3 o, double t) {
        return new Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "(%.1f, %.1f, %.1f)", x, y, z);
    }
}
