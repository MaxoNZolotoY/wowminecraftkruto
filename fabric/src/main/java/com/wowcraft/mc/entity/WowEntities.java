package com.wowcraft.mc.entity;

import com.wowcraft.core.npc.BodyType;
import com.wowcraft.mc.WowCraftMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

/** One entity type per placeholder body family (biped, four-legged, floating elemental, totem). */
public final class WowEntities {
    private WowEntities() {
    }

    public static final Map<BodyType, EntityType<WowNpcEntity>> TYPES = new EnumMap<>(BodyType.class);

    public static void register() {
        TYPES.put(BodyType.HUMANOID, reg("npc_humanoid", 0.6f, 1.95f));
        TYPES.put(BodyType.BEAST, reg("npc_beast", 1.2f, 0.9f));
        TYPES.put(BodyType.ELEMENTAL, reg("npc_elemental", 0.6f, 1.8f));
        TYPES.put(BodyType.TOTEM, reg("npc_totem", 0.5f, 1.0f));
    }

    private static EntityType<WowNpcEntity> reg(String name, float w, float h) {
        EntityType<WowNpcEntity> type = FabricEntityTypeBuilder.<WowNpcEntity>create(SpawnGroup.MISC, WowNpcEntity::new)
                .dimensions(EntityDimensions.changing(w, h))
                .trackRangeBlocks(80)
                .trackedUpdateRate(2)
                .build();
        Registry.register(Registries.ENTITY_TYPE, new Identifier(WowCraftMod.ID, name), type);
        FabricDefaultAttributeRegistry.register(type, WowNpcEntity.createAttributes());
        return type;
    }
}
