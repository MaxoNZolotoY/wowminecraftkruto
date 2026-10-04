package com.wowcraft.core.instance;

import java.util.List;

/**
 * One room of a dungeon / raid / arena.
 *
 * @param packs     enemy packs (each pack = list of NPC template ids)
 * @param bosses    boss template ids (several = council fight)
 * @param feature   decoration hint: "pillars", "lava", "water", "platform", "pit", "statues"
 */
public record RoomDef(Kind kind, int width, int depth, int height, List<List<String>> packs, List<String> bosses, String feature) {
    public enum Kind {ENTRANCE, TRASH, BOSS, HALL, ARENA}

    public static RoomDef entrance(int w, int d, int h) {
        return new RoomDef(Kind.ENTRANCE, w, d, h, List.of(), List.of(), "statues");
    }

    public static RoomDef trash(int w, int d, int h, String feature, List<List<String>> packs) {
        return new RoomDef(Kind.TRASH, w, d, h, packs, List.of(), feature);
    }

    public static RoomDef boss(int size, int h, String feature, String... bosses) {
        return new RoomDef(Kind.BOSS, size, size, h, List.of(), List.of(bosses), feature);
    }

    public static RoomDef hall(int w, int d, int h, List<List<String>> packs) {
        return new RoomDef(Kind.HALL, w, d, h, packs, List.of(), "pillars");
    }
}
