package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.BasicConveyorBeltBlockEntity;
import com.skyeshade.skyent.content.blockentity.BasicFluidDuctBlockEntity;
import com.skyeshade.skyent.content.blockentity.BrickBlastFurnaceBlockEntity;
import com.skyeshade.skyent.content.blockentity.CoalForgeBlockEntity;
import com.skyeshade.skyent.content.blockentity.CombustionGeneratorBlockEntity;
import com.skyeshade.skyent.content.blockentity.ConveyorChuteBlockEntity;
import com.skyeshade.skyent.content.blockentity.ConveyorElevatorBlockEntity;
import com.skyeshade.skyent.content.blockentity.ConveyorExporterBlockEntity;
import com.skyeshade.skyent.content.blockentity.ConveyorSplitterBlockEntity;
import com.skyeshade.skyent.content.blockentity.CoriumBlockEntity;
import com.skyeshade.skyent.content.blockentity.ElectricFurnaceBlockEntity;
import com.skyeshade.skyent.content.blockentity.ForgingAnvilBlockEntity;
import com.skyeshade.skyent.content.blockentity.GeigerCounterPlacedBlockEntity;
import com.skyeshade.skyent.content.blockentity.HeatingChamberBlockEntity;
import com.skyeshade.skyent.content.blockentity.IndustrialPressBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVCrusherBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVFEConverterBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVElectricPumpBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVMVTransformerBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVSteamTurbineBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVRJConverterBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVConnectorBlockEntity;
import com.skyeshade.skyent.content.blockentity.RollingMillBlockEntity;
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LVCrusherBlockEntity>> LV_CRUSHER =
            BLOCK_ENTITIES.register("lv_crusher", () -> BlockEntityType.Builder.of(
                    LVCrusherBlockEntity::new,
                    ModBlocks.LV_CRUSHER.get()
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeatingChamberBlockEntity>> HEATING_CHAMBER =
            BLOCK_ENTITIES.register("heating_chamber", () -> BlockEntityType.Builder.of(
                    HeatingChamberBlockEntity::new,
                    ModBlocks.HEATING_CHAMBER.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RollingMillBlockEntity>> ROLLING_MILL =
            BLOCK_ENTITIES.register("rolling_mill", () -> BlockEntityType.Builder.of(
                    RollingMillBlockEntity::new,
                    ModBlocks.ROLLING_MILL.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IndustrialPressBlockEntity>> INDUSTRIAL_PRESS =
            BLOCK_ENTITIES.register("industrial_press", () -> BlockEntityType.Builder.of(
                    IndustrialPressBlockEntity::new,
                    ModBlocks.INDUSTRIAL_PRESS.get()
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BasicConveyorBeltBlockEntity>> BASIC_CONVEYOR_BELT =
            BLOCK_ENTITIES.register("basic_conveyor_belt", () -> BlockEntityType.Builder.of(
                    BasicConveyorBeltBlockEntity::new,
                    ModBlocks.BASIC_CONVEYOR_BELT.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConveyorSplitterBlockEntity>> CONVEYOR_SPLITTER =
            BLOCK_ENTITIES.register("conveyor_splitter", () -> BlockEntityType.Builder.of(
                    ConveyorSplitterBlockEntity::new,
                    ModBlocks.CONVEYOR_SPLITTER.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConveyorExporterBlockEntity>> CONVEYOR_EXPORTER =
            BLOCK_ENTITIES.register("conveyor_exporter", () -> BlockEntityType.Builder.of(
                    ConveyorExporterBlockEntity::new,
                    ModBlocks.CONVEYOR_EXPORTER.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConveyorElevatorBlockEntity>> CONVEYOR_ELEVATOR =
            BLOCK_ENTITIES.register("conveyor_elevator", () -> BlockEntityType.Builder.of(
                    ConveyorElevatorBlockEntity::new,
                    ModBlocks.CONVEYOR_ELEVATOR.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConveyorChuteBlockEntity>> CONVEYOR_CHUTE =
            BLOCK_ENTITIES.register("conveyor_chute", () -> BlockEntityType.Builder.of(
                    ConveyorChuteBlockEntity::new,
                    ModBlocks.CONVEYOR_CHUTE.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LVConnectorBlockEntity>> LV_CONNECTOR =
            BLOCK_ENTITIES.register("lv_connector", () -> BlockEntityType.Builder.of(
                    LVConnectorBlockEntity::new,
                    ModBlocks.LV_CONNECTOR.get(),
                    ModBlocks.MV_CONNECTOR.get()
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LVMVTransformerBlockEntity>> LV_MV_TRANSFORMER =
            BLOCK_ENTITIES.register("lv_mv_transformer", () -> BlockEntityType.Builder.of(
                    LVMVTransformerBlockEntity::new,
                    ModBlocks.LV_MV_TRANSFORMER.get()
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
