package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.CombustionGeneratorBlockEntity;
import com.skyeshade.skyent.content.blockentity.ElectricFurnaceBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVFEConverterBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVElectricPumpBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVRJConverterBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVConnectorBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            SkyesNuclearTech.MOD_ID
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CombustionGeneratorBlockEntity>> COMBUSTION_GENERATOR =
            BLOCK_ENTITIES.register("combustion_generator", () -> BlockEntityType.Builder.of(
                    CombustionGeneratorBlockEntity::new,
                    ModBlocks.COMBUSTION_GENERATOR.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricFurnaceBlockEntity>> ELECTRIC_FURNACE =
            BLOCK_ENTITIES.register("electric_furnace", () -> BlockEntityType.Builder.of(
                    ElectricFurnaceBlockEntity::new,
                    ModBlocks.ELECTRIC_FURNACE.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LVElectricPumpBlockEntity>> LV_ELECTRIC_PUMP =
            BLOCK_ENTITIES.register("lv_electric_pump", () -> BlockEntityType.Builder.of(
                    LVElectricPumpBlockEntity::new,
                    ModBlocks.LV_ELECTRIC_PUMP.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LVConnectorBlockEntity>> LV_CONNECTOR =
            BLOCK_ENTITIES.register("lv_connector", () -> BlockEntityType.Builder.of(
                    LVConnectorBlockEntity::new,
                    ModBlocks.LV_CONNECTOR.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LVRJConverterBlockEntity>> LV_RJ_CONVERTER =
            BLOCK_ENTITIES.register("lv_rj_converter", () -> BlockEntityType.Builder.of(
                    LVRJConverterBlockEntity::new,
                    ModBlocks.LV_RJ_CONVERTER.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LVFEConverterBlockEntity>> LV_FE_CONVERTER =
            BLOCK_ENTITIES.register("lv_fe_converter", () -> BlockEntityType.Builder.of(
                    LVFEConverterBlockEntity::new,
                    ModBlocks.LV_FE_CONVERTER.get()
            ).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
