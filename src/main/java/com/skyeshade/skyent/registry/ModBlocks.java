package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.BasicConveyorBeltBlock;
import com.skyeshade.skyent.content.block.BasicFluidDuctBlock;
import com.skyeshade.skyent.content.block.BrickBlastFurnaceBlock;
import com.skyeshade.skyent.content.block.CoalForgeBlock;
import com.skyeshade.skyent.content.block.CombustionGeneratorBlock;
import com.skyeshade.skyent.content.block.ConveyorChuteBlock;
import com.skyeshade.skyent.content.block.ConveyorElevatorBlock;
import com.skyeshade.skyent.content.block.ConveyorExporterBlock;
import com.skyeshade.skyent.content.block.ConveyorSplitterBlock;
import com.skyeshade.skyent.content.block.ContaminatedGrassBlock;
import com.skyeshade.skyent.content.block.CoriumBlock;
import com.skyeshade.skyent.content.block.DeadGrassBlock;
import com.skyeshade.skyent.content.block.DeadLeavesBlock;
import com.skyeshade.skyent.content.block.DeadPlantBlock;
import com.skyeshade.skyent.content.block.ElectricFurnaceBlock;
import com.skyeshade.skyent.content.block.ForgingAnvilBlock;
import com.skyeshade.skyent.content.block.GeigerCounterPlacedBlock;
import com.skyeshade.skyent.content.block.HeatingChamberBlock;
import com.skyeshade.skyent.content.block.HeatingChamberPartBlock;
import com.skyeshade.skyent.content.block.IndustrialPressBlock;
import com.skyeshade.skyent.content.block.IndustrialPressPartBlock;
import com.skyeshade.skyent.content.block.LVCrusherBlock;
import com.skyeshade.skyent.content.block.LVConverterBlock;
import com.skyeshade.skyent.content.block.LVConnectorBlock;
import com.skyeshade.skyent.content.block.LVElectricPumpBlock;
import com.skyeshade.skyent.content.block.LVMVTransformerBlock;
import com.skyeshade.skyent.content.block.LVMVTransformerPartBlock;
import com.skyeshade.skyent.content.block.LVSteamTurbineBlock;
import com.skyeshade.skyent.content.block.MoltenCoriumBlock;
import com.skyeshade.skyent.content.block.NuclearChargeBlock;
import com.skyeshade.skyent.content.block.RadioactiveBlock;
import com.skyeshade.skyent.content.block.RadioactiveScrapMetalBlock;
import com.skyeshade.skyent.content.block.RollingMillBlock;
import com.skyeshade.skyent.content.block.RollingMillPartBlock;
import com.skyeshade.skyent.content.block.SiltBlock;
import com.skyeshade.skyent.content.block.SteamForgeHammerBlock;
import com.skyeshade.skyent.content.block.SteamForgeHammerPartBlock;
import com.skyeshade.skyent.content.block.UraniumBlock;
import com.skyeshade.skyent.content.block.WireMillBlock;
import com.skyeshade.skyent.content.block.WireMillPartBlock;
import com.skyeshade.skyent.content.radiation.EnvironmentalRadiationMode;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyesNuclearTech.MOD_ID);
    private static final float OBSIDIAN_BLAST_RESISTANCE = 1200.0F;

    public static final DeferredBlock<CombustionGeneratorBlock> COMBUSTION_GENERATOR = BLOCKS.registerBlock(
            "combustion_generator",
            CombustionGeneratorBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.FURNACE)
    );

    public static final DeferredBlock<ElectricFurnaceBlock> ELECTRIC_FURNACE = BLOCKS.registerBlock(
            "electric_furnace",
            ElectricFurnaceBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.FURNACE)
    );

    public static final DeferredBlock<LVCrusherBlock> LV_CRUSHER = BLOCKS.registerBlock(
            "lv_crusher",
            LVCrusherBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
    );

    public static final DeferredBlock<BrickBlastFurnaceBlock> BRICK_BLAST_FURNACE = BLOCKS.registerBlock(
            "brick_blast_furnace",
            BrickBlastFurnaceBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BLAST_FURNACE)
    );

    public static final DeferredBlock<CoalForgeBlock> COAL_FORGE = BLOCKS.registerBlock(
            "coal_forge",
            CoalForgeBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS)
                    .strength(3.5F, 6.0F)
                    .noOcclusion()
                    .lightLevel(state -> state.hasProperty(CoalForgeBlock.LIT) && state.getValue(CoalForgeBlock.LIT) ? 12 : 0)
    );

    public static final DeferredBlock<?> FIRE_BRICKS = BLOCKS.registerSimpleBlock(
            "fire_bricks",
            BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<?> CONCRETE_BRICKS = BLOCKS.registerSimpleBlock(
            "concrete_bricks",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE)
                    .strength(8.0F, OBSIDIAN_BLAST_RESISTANCE * 2.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<?> CRACKED_CONCRETE_BRICKS = BLOCKS.registerSimpleBlock(
            "cracked_concrete_bricks",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE)
                    .strength(6.0F, OBSIDIAN_BLAST_RESISTANCE)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<?> REINFORCED_CONCRETE = BLOCKS.registerSimpleBlock(
            "reinforced_concrete",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE)
                    .strength(12.0F, OBSIDIAN_BLAST_RESISTANCE * 4.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<?> TUNGSTEN_REINFORCED_CONCRETE = BLOCKS.registerSimpleBlock(
            "tungsten_reinforced_concrete",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE)
                    .strength(18.0F, OBSIDIAN_BLAST_RESISTANCE * 8.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<?> PLATED_CONCRETE = BLOCKS.registerSimpleBlock(
            "plated_concrete",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE)
                    .strength(30.0F, OBSIDIAN_BLAST_RESISTANCE * 32.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<ForgingAnvilBlock> FORGING_ANVIL = BLOCKS.registerBlock(
            "forging_anvil",
            ForgingAnvilBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    );

    public static final DeferredBlock<SteamForgeHammerBlock> STEAM_FORGE_HAMMER = BLOCKS.registerBlock(
            "steam_forge_hammer",
            SteamForgeHammerBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    );

    public static final DeferredBlock<SteamForgeHammerPartBlock> STEAM_FORGE_HAMMER_PART = BLOCKS.registerBlock(
            "steam_forge_hammer_part",
            SteamForgeHammerPartBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    );

    public static final DeferredBlock<HeatingChamberBlock> HEATING_CHAMBER = BLOCKS.registerBlock(
            "heating_chamber",
            HeatingChamberBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    );

    public static final DeferredBlock<HeatingChamberPartBlock> HEATING_CHAMBER_PART = BLOCKS.registerBlock(
            "heating_chamber_part",
            HeatingChamberPartBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<RollingMillBlock> ROLLING_MILL = BLOCKS.registerBlock(
            "rolling_mill",
            RollingMillBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    );

    public static final DeferredBlock<RollingMillPartBlock> ROLLING_MILL_PART = BLOCKS.registerBlock(
            "rolling_mill_part",
            RollingMillPartBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<IndustrialPressBlock> INDUSTRIAL_PRESS = BLOCKS.registerBlock(
            "industrial_press",
            IndustrialPressBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    );

    public static final DeferredBlock<IndustrialPressPartBlock> INDUSTRIAL_PRESS_PART = BLOCKS.registerBlock(
            "industrial_press_part",
            IndustrialPressPartBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    );

    public static final DeferredBlock<WireMillBlock> WIRE_MILL = BLOCKS.registerBlock(
            "wire_mill",
            WireMillBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    );

    public static final DeferredBlock<WireMillPartBlock> WIRE_MILL_PART = BLOCKS.registerBlock(
            "wire_mill_part",
            WireMillPartBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<NuclearChargeBlock> NUCLEAR_CHARGE = BLOCKS.registerBlock(
            "nuclear_charge",
            NuclearChargeBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<LVElectricPumpBlock> LV_ELECTRIC_PUMP = BLOCKS.registerBlock(
            "lv_electric_pump",
            LVElectricPumpBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
    );

    public static final DeferredBlock<LVSteamTurbineBlock> LV_STEAM_TURBINE = BLOCKS.registerBlock(
            "lv_steam_turbine",
            LVSteamTurbineBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
    );

    public static final DeferredBlock<BasicFluidDuctBlock> BASIC_FLUID_DUCT = BLOCKS.registerBlock(
            "basic_fluid_duct",
            BasicFluidDuctBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
    );

    public static final DeferredBlock<BasicConveyorBeltBlock> BASIC_CONVEYOR_BELT = BLOCKS.registerBlock(
            "basic_conveyor_belt",
            BasicConveyorBeltBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
    );

    public static final DeferredBlock<ConveyorSplitterBlock> CONVEYOR_SPLITTER = BLOCKS.registerBlock(
            "conveyor_splitter",
            ConveyorSplitterBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
    );

    public static final DeferredBlock<ConveyorExporterBlock> CONVEYOR_EXPORTER = BLOCKS.registerBlock(
            "conveyor_exporter",
            ConveyorExporterBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
    );

    public static final DeferredBlock<ConveyorElevatorBlock> CONVEYOR_ELEVATOR = BLOCKS.registerBlock(
            "conveyor_elevator",
            ConveyorElevatorBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
    );

    public static final DeferredBlock<ConveyorChuteBlock> CONVEYOR_CHUTE = BLOCKS.registerBlock(
            "conveyor_chute",
            ConveyorChuteBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
    );

    public static final DeferredBlock<SiltBlock> SILT = BLOCKS.registerBlock(
            "silt",
            SiltBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SAND)
    );

    public static final DeferredBlock<DeadGrassBlock> DEAD_GRASS = BLOCKS.registerBlock(
            "dead_grass",
            DeadGrassBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK).randomTicks()
    );

    public static final DeferredBlock<DeadPlantBlock> DEAD_SHORT_GRASS = BLOCKS.registerBlock(
            "dead_short_grass",
            DeadPlantBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS)
    );

    public static final DeferredBlock<DeadPlantBlock> DEAD_TALL_GRASS = BLOCKS.registerBlock(
            "dead_tall_grass",
            DeadPlantBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS)
    );

    public static final DeferredBlock<RotatedPillarBlock> CHARRED_LOG = BLOCKS.registerBlock(
            "charred_log",
            RotatedPillarBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG)
                    .strength(1.5F, 2.0F)
    );

    public static final DeferredBlock<DeadLeavesBlock> DEAD_OAK_LEAVES = deadLeaves("dead_oak_leaves", Blocks.OAK_LEAVES);
    public static final DeferredBlock<DeadLeavesBlock> DEAD_BIRCH_LEAVES = deadLeaves("dead_birch_leaves", Blocks.BIRCH_LEAVES);
    public static final DeferredBlock<DeadLeavesBlock> DEAD_SPRUCE_LEAVES = deadLeaves("dead_spruce_leaves", Blocks.SPRUCE_LEAVES);
    public static final DeferredBlock<DeadLeavesBlock> DEAD_JUNGLE_LEAVES = deadLeaves("dead_jungle_leaves", Blocks.JUNGLE_LEAVES);
    public static final DeferredBlock<DeadLeavesBlock> DEAD_ACACIA_LEAVES = deadLeaves("dead_acacia_leaves", Blocks.ACACIA_LEAVES);
    public static final DeferredBlock<DeadLeavesBlock> DEAD_DARK_OAK_LEAVES = deadLeaves("dead_dark_oak_leaves", Blocks.DARK_OAK_LEAVES);
    public static final DeferredBlock<DeadLeavesBlock> DEAD_MANGROVE_LEAVES = deadLeaves("dead_mangrove_leaves", Blocks.MANGROVE_LEAVES);
    public static final DeferredBlock<DeadLeavesBlock> DEAD_CHERRY_LEAVES = deadLeaves("dead_cherry_leaves", Blocks.CHERRY_LEAVES);
    public static final DeferredBlock<DeadLeavesBlock> DEAD_AZALEA_LEAVES = deadLeaves("dead_azalea_leaves", Blocks.AZALEA_LEAVES);
    public static final DeferredBlock<DeadLeavesBlock> DEAD_FLOWERING_AZALEA_LEAVES = deadLeaves("dead_flowering_azalea_leaves", Blocks.FLOWERING_AZALEA_LEAVES);

    public static final DeferredBlock<?> TITANIUM_ORE = BLOCKS.registerSimpleBlock(
            "titanium_ore",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)
    );

    public static final DeferredBlock<?> DEEPSLATE_TITANIUM_ORE = BLOCKS.registerSimpleBlock(
            "deepslate_titanium_ore",
            BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_IRON_ORE)
    );

    public static final DeferredBlock<?> ALUMINUM_ORE = BLOCKS.registerSimpleBlock(
            "aluminum_ore",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)
    );

    public static final DeferredBlock<?> DEEPSLATE_ALUMINUM_ORE = BLOCKS.registerSimpleBlock(
            "deepslate_aluminum_ore",
            BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_IRON_ORE)
    );

    public static final DeferredBlock<?> ALUMINUM_BLOCK = BLOCKS.registerSimpleBlock(
            "aluminum_block",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(4.5F, 8.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<?> TITANIUM_BLOCK = BLOCKS.registerSimpleBlock(
            "titanium_block",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(7.0F, 12.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<?> TUNGSTEN_ORE = BLOCKS.registerSimpleBlock(
            "tungsten_ore",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_ORE)
    );

    public static final DeferredBlock<?> DEEPSLATE_TUNGSTEN_ORE = BLOCKS.registerSimpleBlock(
            "deepslate_tungsten_ore",
            BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_GOLD_ORE)
    );

    public static final DeferredBlock<?> TUNGSTEN_BLOCK = BLOCKS.registerSimpleBlock(
            "tungsten_block",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(8.0F, 12.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<?> STEEL_BLOCK = BLOCKS.registerSimpleBlock(
            "steel_block",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(6.0F, 10.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<?> COBALT_BLOCK = BLOCKS.registerSimpleBlock(
            "cobalt_block",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 9.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<?> NICKEL_BLOCK = BLOCKS.registerSimpleBlock(
            "nickel_block",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 9.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<?> LEAD_ORE = BLOCKS.registerSimpleBlock(
            "lead_ore",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)
    );

    public static final DeferredBlock<?> LEAD_BLOCK = BLOCKS.registerSimpleBlock(
            "lead_block",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
    );

    public static final DeferredBlock<RadioactiveBlock> URANIUM_ORE = BLOCKS.registerBlock(
            "uranium_ore",
            properties -> new RadioactiveBlock(properties, EnvironmentalRadiationMode.FULL_RAY),
            BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_ORE)
    );

    public static final DeferredBlock<RadioactiveBlock> DEEPSLATE_URANIUM_ORE = BLOCKS.registerBlock(
            "deepslate_uranium_ore",
            properties -> new RadioactiveBlock(properties, EnvironmentalRadiationMode.FULL_RAY),
            BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_GOLD_ORE)
    );

    public static final DeferredBlock<UraniumBlock> URANIUM_BLOCK = BLOCKS.registerBlock(
            "uranium_block",
            UraniumBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).randomTicks()
    );

    public static final DeferredBlock<CoriumBlock> CORIUM_BLOCK = BLOCKS.registerBlock(
            "corium_block",
            CoriumBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
    );

    public static final DeferredBlock<RadioactiveScrapMetalBlock> RADIOACTIVE_SCRAP_METAL = BLOCKS.registerBlock(
            "radioactive_scrap_metal",
            RadioactiveScrapMetalBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 10.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    );

    public static final DeferredBlock<ContaminatedGrassBlock> CONTAMINATED_GRASS_BLOCK = BLOCKS.registerBlock(
            "contaminated_grass_block",
            ContaminatedGrassBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK)
                    .strength(0.6F, 0.6F)
    );

    public static final DeferredBlock<RadioactiveBlock> VITRIFIED_STONE = vitrifiedStone(
            "vitrified_stone",
            EnvironmentalRadiationMode.CHEAP
    );
    public static final DeferredBlock<RadioactiveBlock> BAKED_VITRIFIED_STONE = vitrifiedStone(
            "baked_vitrified_stone",
            EnvironmentalRadiationMode.CHEAP
    );
    public static final DeferredBlock<RadioactiveBlock> SCORCHED_VITRIFIED_STONE = vitrifiedStone(
            "scorched_vitrified_stone",
            EnvironmentalRadiationMode.CHEAP
    );
    public static final DeferredBlock<RadioactiveBlock> IRRADIATED_VITRIFIED_STONE = vitrifiedStone(
            "irradiated_vitrified_stone",
            EnvironmentalRadiationMode.CHEAP
    );
    public static final DeferredBlock<RadioactiveBlock> HOT_VITRIFIED_STONE = vitrifiedStone(
            "hot_vitrified_stone",
            EnvironmentalRadiationMode.FULL_RAY
    );
    public static final DeferredBlock<RadioactiveBlock> RADIANT_VITRIFIED_STONE = vitrifiedStone(
            "radiant_vitrified_stone",
            EnvironmentalRadiationMode.FULL_RAY
    );
    public static final DeferredBlock<RadioactiveBlock> INFERNAL_VITRIFIED_STONE = vitrifiedStone(
            "infernal_vitrified_stone",
            EnvironmentalRadiationMode.FULL_RAY
    );

    public static final DeferredBlock<GeigerCounterPlacedBlock> GEIGER_COUNTER_PLACED = BLOCKS.registerBlock(
            "geiger_counter_placed",
            GeigerCounterPlacedBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.LEVER).noOcclusion()
    );

    public static final DeferredBlock<MoltenCoriumBlock> MOLTEN_CORIUM_BLOCK = BLOCKS.register(
            "molten_corium_block",
            () -> new MoltenCoriumBlock(
                    ModFluids.MOLTEN_CORIUM.get(),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.LAVA)
                            .lightLevel(state -> 15)
                            .randomTicks()
            )
    );

    public static final DeferredBlock<LVConnectorBlock> LV_CONNECTOR = BLOCKS.registerBlock(
            "lv_connector",
            LVConnectorBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
    );

    public static final DeferredBlock<LVConnectorBlock> MV_CONNECTOR = BLOCKS.registerBlock(
            "mv_connector",
            LVConnectorBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
    );

    public static final DeferredBlock<LVConverterBlock> LV_RJ_CONVERTER = BLOCKS.registerBlock(
            "lv_rj_converter",
            properties -> new LVConverterBlock(properties, LVConverterBlock.ConverterMode.RJ_TO_FE),
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
    );

    public static final DeferredBlock<LVConverterBlock> LV_FE_CONVERTER = BLOCKS.registerBlock(
            "lv_fe_converter",
            properties -> new LVConverterBlock(properties, LVConverterBlock.ConverterMode.FE_TO_RJ),
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
    );

    public static final DeferredBlock<LVMVTransformerBlock> LV_MV_TRANSFORMER = BLOCKS.registerBlock(
            "lv_mv_transformer",
            LVMVTransformerBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    );

    public static final DeferredBlock<LVMVTransformerPartBlock> LV_MV_TRANSFORMER_PART = BLOCKS.registerBlock(
            "lv_mv_transformer_part",
            LVMVTransformerPartBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
    );

    private ModBlocks() {
    }

    private static DeferredBlock<DeadLeavesBlock> deadLeaves(String name, net.minecraft.world.level.block.Block livingLeaves) {
        return BLOCKS.registerBlock(
                name,
                properties -> new DeadLeavesBlock(properties, () -> livingLeaves),
                BlockBehaviour.Properties.ofFullCopy(livingLeaves).randomTicks().noOcclusion()
        );
    }

    private static DeferredBlock<RadioactiveBlock> vitrifiedStone(String name, EnvironmentalRadiationMode environmentalMode) {
        return BLOCKS.registerBlock(
                name,
                properties -> new RadioactiveBlock(properties, environmentalMode),
                BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                        .strength(4.0F, 8.0F)
                        .requiresCorrectToolForDrops()
                        .isValidSpawn((state, level, pos, entityType) -> false)
        );
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
