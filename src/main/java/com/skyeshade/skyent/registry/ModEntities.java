package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.content.entity.NuclearExplosionEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
            BuiltInRegistries.ENTITY_TYPE,
            SkyesNuclearTech.MOD_ID
    );

    public static final DeferredHolder<EntityType<?>, EntityType<ConveyorMovingItemEntity>> CONVEYOR_MOVING_ITEM =
            ENTITIES.register("conveyor_moving_item", () -> EntityType.Builder
                    .of((EntityType.EntityFactory<ConveyorMovingItemEntity>) ConveyorMovingItemEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("conveyor_moving_item"));

    public static final DeferredHolder<EntityType<?>, EntityType<NuclearExplosionEntity>> NUCLEAR_EXPLOSION =
            ENTITIES.register("nuclear_explosion", () -> EntityType.Builder
                    .of((EntityType.EntityFactory<NuclearExplosionEntity>) NuclearExplosionEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(512)
                    .updateInterval(10)
                    .build("nuclear_explosion"));

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
