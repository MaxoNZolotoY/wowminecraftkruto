package com.wowcraft.core.combat;

import com.wowcraft.core.util.Vec3;

/** World queries the combat engine needs from the platform. */
public interface WorldAccess {
    boolean lineOfSight(String worldKey, Vec3 from, Vec3 to);

    /** Furthest safe position along a direction (blink / charge). */
    Vec3 safeDestination(String worldKey, Vec3 from, Vec3 to);

    /** Spawn a summon / add. Returns the registered unit or null. */
    UnitState spawnNpc(String worldKey, Vec3 pos, float yaw, String templateId, UnitState owner, int level);

    /** A no-op world for tests. */
    WorldAccess NONE = new WorldAccess() {
        @Override
        public boolean lineOfSight(String worldKey, Vec3 from, Vec3 to) {
            return true;
        }

        @Override
        public Vec3 safeDestination(String worldKey, Vec3 from, Vec3 to) {
            return to;
        }

        @Override
        public UnitState spawnNpc(String worldKey, Vec3 pos, float yaw, String templateId, UnitState owner, int level) {
            return null;
        }
    };
}
