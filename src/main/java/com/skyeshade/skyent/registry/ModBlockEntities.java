package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.BasicFluidDuctBlockEntity;
import com.skyeshade.skyent.content.blockentity.BrickBlastFurnaceBlockEntity;
import com.skyeshade.skyent.content.blockentity.CoalForgeBlockEntity;
import com.skyeshade.skyent.content.blockentity.CombustionGeneratorBlockEntity;
import com.skyeshade.skyent.content.blockentity.CoriumBlockEntity;
import com.skyeshade.skyent.content.blockentity.ElectricFurnaceBlockEntity;
import com.skyeshade.skyent.content.blockentity.ForgingAnvilBlockEntity;
import com.skyeshade.skyent.content.blockentity.GeigerCounterPlacedBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVFEConverterBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVElectricPumpBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVSteamTurbineBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVRJConverterBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVConnectorBlockEntity;
import com.skyeshade.skyent.content.blockentity.SteamForgeHammerBlockEntity;
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BrickBlastFurnaceBlockEntity>> BRICK_BLAST_FURNACE =
            BLOCK_ENTITIES.register("brick_blast_furnace", () -> BlockEntityType.Builder.of(
                    BrickBlastFurnaceBlockEntity::new,
                    ModBlocks.BRICK_BLAST_FURNACE.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoalForgeBlockEntity>> COAL_FORGE =
            BLOCK_ENTITIES.register("coal_forge", () -> BlockEntityType.Builder.of(
                    CoalForgeBlockEntity::new,
                    ModBlocks.COAL_FORGE.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForgingAnvilBlockEntity>> FORGING_ANVIL =
            BLOCK_ENTITIES.register("forging_anvil", () -> BlockEntityType.Builder.of(
                    ForgingAnvilBlockEntity::new,
                    ModBlocks.FORGING_ANVIL.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamForgeHammerBlockEntity>> STEAM_FORGE_HAMMER =
            BLOCK_ENTITIES.register("steam_forge_hammer", () -> BlockEntityType.Builder.of(
                    SteamForgeHammerBlockEntity::new,
                    ModBlocks.STEAM_FORGE_HAMMER.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LVElectricPumpBlockEntity>> LV_ELECTRIC_PUMP =
            BLOCK_ENTITIES.register("lv_electric_pump", () -> BlockEntityType.Builder.of(
                    LVElectricPumpBlockEntity::new,
                    ModBlocks.LV_ELECTRIC_PUMP.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LVSteamTurbineBlockEntity>> LV_STEAM_TURBINE =
            BLOCK_ENTITIES.register("lv_steam_turbine", () -> BlockEntityType.Builder.of(
                    LVSteamTurbineBlockEntity::new,
                    ModBlocks.LV_STEAM_TURBINE.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BasicFluidDuctBlockEntity>> BASIC_FLUID_DUCT =
            BLOCK_ENTITIES.register("basic_fluid_duct", () -> BlockEntityType.Builder.of(
                    BasicFluidDuctBlockEntity::new,
                    ModBlocks.BASIC_FLUID_DUCT.get()
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoriumBlockEntity>> CORIUM_BLOCK =
            BLOCK_ENTITIES.register("corium_block", () -> BlockEntityType.Builder.of(
                    CoriumBlockEntity::new,
                    ModBlocks.CORIUM_BLOCK.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeigerCounterPlacedBlockEntity>> GEIGER_COUNTER_PLACED =
            BLOCK_ENTITIES.register("geiger_counter_placed", () -> BlockEntityType.Builder.of(
                    GeigerCounterPlacedBlockEntity::new,
                    ModBlocks.GEIGER_COUNTER_PLACED.get()
            ).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
