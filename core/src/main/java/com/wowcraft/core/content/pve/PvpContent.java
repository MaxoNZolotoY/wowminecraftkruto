package com.wowcraft.core.content.pve;

import com.wowcraft.core.aura.AuraDef;
import com.wowcraft.core.content.Registry;
import com.wowcraft.core.instance.DungeonDef;
import com.wowcraft.core.instance.Dungeons;
import com.wowcraft.core.instance.RoomDef;
import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.npc.Difficulty;
import com.wowcraft.core.util.L10n;

import java.util.List;

/** Arena and battleground maps plus PvP objective auras. */
public final class PvpContent {
    private PvpContent() {
    }

    private static void map(String id, String shortName, String en, String ru, String descEn, String descRu, DungeonDef.Type type, String palette,
                            int w, int d, int h, String feature) {
        RoomDef room = new RoomDef(RoomDef.Kind.ARENA, w, d, h, List.of(), List.of(), feature);
        Dungeons.register(new DungeonDef(id, shortName, L10n.of(en, ru), L10n.of(descEn, descRu), type, palette, 80, 0, "pvp", List.of(room),
                List.of(Difficulty.WORLD), 2, 20));
    }

    public static void register() {
        Registry.register(AuraDef.debuff("carrying_flag_blue", "Blue Flag", "Синий флаг").persistent().perCaster(false)
                .mod(Modifier.speed(-0.15)).desc("Carrying the enemy flag! Bring it to your base.", "Вы несете вражеский флаг! Доставьте его на свою базу.")
                .vfx("flag_blue").build());
        Registry.register(AuraDef.debuff("carrying_flag_red", "Red Flag", "Красный флаг").persistent().perCaster(false)
                .mod(Modifier.speed(-0.15)).desc("Carrying the enemy flag! Bring it to your base.", "Вы несете вражеский флаг! Доставьте его на свою базу.")
                .vfx("flag_red").build());
        Registry.register(AuraDef.debuff("focused_assault", "Focused Assault", "Направленная атака").persistent().stacks(10).perCaster(false)
                .mod(Modifier.taken(0.1)).desc("Damage taken increased by 10% per stack.", "Получаемый урон увеличен на 10% за заряд.").build());

        map("ring_of_trials", "RoT", "Ring of Trials", "Круг испытаний", "A classic pillar arena under the open sky.",
                "Классическая арена с колоннами под открытым небом.", DungeonDef.Type.ARENA, "arena", 34, 34, 10, "pillars open");
        map("ruined_sanctum", "RS", "Ruined Sanctum", "Разрушенное святилище", "Ruins with a central tomb to break line of sight.",
                "Руины с гробницей в центре, за которой можно скрыться из поля зрения.", DungeonDef.Type.ARENA, "stone", 32, 36, 10, "tomb");
        map("ember_bridge", "EB", "Ember Bridge", "Угольный мост", "Two platforms over lava joined by a narrow bridge.",
                "Две платформы над лавой, соединенные узким мостом.", DungeonDef.Type.ARENA, "ember", 34, 32, 12, "bridge");
        map("silverbrook_gulch", "SBG", "Silverbrook Gulch", "Ущелье Серебряного Ручья",
                "Capture the flag: steal the enemy flag and bring it to your base 3 times.",
                "Захват флага: украдите вражеский флаг и доставьте его на свою базу 3 раза.", DungeonDef.Type.BATTLEGROUND, "grove", 44, 110, 12, "ctf");
        map("basin_of_ashfall", "BoA", "Basin of Ashfall", "Котловина Пеплопада",
                "Domination: capture and hold the nodes to gather resources. First to 600 wins.",
                "Господство: захватывайте и удерживайте точки, чтобы набрать ресурсы. Побеждает тот, кто первым наберет 600.", DungeonDef.Type.BATTLEGROUND,
                "sand", 90, 70, 12, "domination");
    }
}
