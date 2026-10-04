package com.wowcraft.core.content;

import com.wowcraft.core.instance.DungeonDef;
import com.wowcraft.core.instance.Dungeons;
import com.wowcraft.core.instance.Layout;
import com.wowcraft.core.instance.LayoutGenerator;
import com.wowcraft.core.instance.RoomDef;
import com.wowcraft.core.item.ItemRegistry;
import com.wowcraft.core.item.ItemTemplate;
import com.wowcraft.core.npc.BossScripts;
import com.wowcraft.core.npc.NpcRank;
import com.wowcraft.core.npc.NpcRegistry;
import com.wowcraft.core.npc.NpcSpell;
import com.wowcraft.core.npc.NpcTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Referential integrity of NPCs, bosses, dungeons, raids and loot. */
class PveContentTest {
    @BeforeAll
    static void boot() {
        Content.bootstrap();
    }

    @Test
    void npcTemplatesReferenceExistingAbilitiesAndScripts() {
        List<String> problems = new ArrayList<>();
        for (NpcTemplate t : NpcRegistry.all()) {
            for (NpcSpell s : t.spells) if (!Registry.hasAbility(s.abilityId())) problems.add(t.id + ": missing ability " + s.abilityId());
            if (t.rank == NpcRank.BOSS && (t.bossScript == null || !BossScripts.has(t.bossScript))) problems.add(t.id + ": missing boss script " + t.bossScript);
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void dungeonsAreCompleteAndPlayable() {
        List<String> problems = new ArrayList<>();
        int dungeons = 0, raids = 0, arenas = 0, bgs = 0;
        for (DungeonDef d : Dungeons.all()) {
            switch (d.type) {
                case DUNGEON -> dungeons++;
                case RAID -> raids++;
                case ARENA -> arenas++;
                case BATTLEGROUND -> bgs++;
            }
            if (d.type == DungeonDef.Type.ARENA || d.type == DungeonDef.Type.BATTLEGROUND) continue;
            for (RoomDef r : d.rooms) {
                for (List<String> pack : r.packs()) for (String id : pack) if (NpcRegistry.get(id) == null) problems.add(d.id + ": unknown npc " + id);
                for (String id : r.bosses()) if (NpcRegistry.get(id) == null) problems.add(d.id + ": unknown boss " + id);
            }
            if (d.bossIds().isEmpty()) problems.add(d.id + ": no bosses");
            Layout l = LayoutGenerator.generate(d);
            if (l.spawns.isEmpty()) problems.add(d.id + ": no spawns");
            if (l.maxX - l.minX > 1000 || l.maxZ - l.minZ > 1000) problems.add(d.id + ": layout too big for an instance slot");
            List<String> loot = ItemRegistry.lootTable(d.lootTable);
            if (loot.size() < 5) problems.add(d.id + ": loot table too small");
            for (String id : loot) {
                ItemTemplate t = ItemRegistry.template(id);
                if (t == null) problems.add(d.id + ": missing item " + id);
                else if (t.effectId() != null && ItemRegistry.effect(t.effectId()) == null) problems.add(id + ": missing effect " + t.effectId());
            }
            if (d.type == DungeonDef.Type.DUNGEON && !Dungeons.mythicPool().contains(d.id)) problems.add(d.id + ": not in the Mythic+ pool");
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertTrue(dungeons >= 8, "8 dungeons");
        assertTrue(raids >= 1, "a raid");
        assertTrue(arenas >= 3, "arenas");
        assertTrue(bgs >= 2, "battlegrounds");
    }

    /** The creative "gear" tab rolls these items on the client; any exception there would break the creative inventory. */
    @Test
    void creativeGearCatalogBuildsForEverySpec() {
        List<String> problems = new ArrayList<>();
        for (com.wowcraft.core.spec.Spec spec : com.wowcraft.core.spec.Spec.values()) {
            var eq = com.wowcraft.core.game.Rewards.fullSet(spec, 150, new com.wowcraft.core.util.Rng(spec.ordinal()), "creative", true);
            if (eq.all().size() < 14) problems.add(spec + ": only " + eq.all().size() + " slots in a full set");
            for (var piece : com.wowcraft.core.item.TierSet.PIECES) {
                var d = com.wowcraft.core.item.LootGenerator.tierPiece(spec, piece, 150, new com.wowcraft.core.util.Rng(1), "creative");
                if (d.setId == null) problems.add(spec + ": tier " + piece + " has no set");
            }
        }
        for (ItemTemplate t : ItemRegistry.templates()) {
            com.wowcraft.core.spec.Spec user = null;
            for (com.wowcraft.core.spec.Spec spec : com.wowcraft.core.spec.Spec.values()) {
                if (com.wowcraft.core.item.LootGenerator.usableBy(t, spec)) {
                    user = spec;
                    break;
                }
            }
            if (user == null) {
                problems.add(t.id() + ": nobody can use it");
                continue;
            }
            var d = com.wowcraft.core.item.LootGenerator.fromTemplate(t, user, 150, new com.wowcraft.core.util.Rng(t.id().hashCode()), "creative");
            if (d.ilvl < 147) problems.add(t.id() + ": item level " + d.ilvl);
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
