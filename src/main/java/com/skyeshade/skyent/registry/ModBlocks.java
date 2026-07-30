package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.*;
import com.skyeshade.skyent.content.radiation.EnvironmentalRadiationMode;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyesNuclearTech.MOD_ID);
    private static final float OBSIDIAN_BLAST_RESISTANCE = 1200.0F;
    // The door is a 3x3 multiblock, so its resistance is scaled above plated concrete
    // to make the whole structure survive comparably when explosions can hit any part.
    private static final float BLAST_DOOR_BLAST_RESISTANCE = 345600.0F;
    private static final float ZONE_GATE_BLAST_RESISTANCE = 12000.0F;

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

    public static final DeferredBlock<?> REINFORCED_GLOWSTONE = BLOCKS.registerSimpleBlock(
            "reinforced_glowstone",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE)
                    .strength(12.0F, OBSIDIAN_BLAST_RESISTANCE * 4.0F)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 15)
    );

    public static final DeferredBlock<ReinforcedGlassBlock> REINFORCED_GLASS = BLOCKS.registerBlock(
            "reinforced_glass",
            ReinforcedGlassBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                    .strength(12.0F, OBSIDIAN_BLAST_RESISTANCE * 4.0F)
                    .sound(SoundType.GLASS)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, entityType) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
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
                    .dynamicShape()
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
                    .dynamicShape()
    );

    public static final DeferredBlock<RollingMillPartBlock> ROLLING_MILL_PART = BLOCKS.registerBlock(
            "rolling_mill_part",
            RollingMillPartBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .dynamicShape()
    );

    public static final DeferredBlock<IndustrialPressBlock> INDUSTRIAL_PRESS = BLOCKS.registerBlock(
            "industrial_press",
            IndustrialPressBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .dynamicShape()
    );

    public static final DeferredBlock<IndustrialPressPartBlock> INDUSTRIAL_PRESS_PART = BLOCKS.registerBlock(
            "industrial_press_part",
            IndustrialPressPartBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .dynamicShape()
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

    public static final DeferredBlock<MVAssemblerBlock> MV_ASSEMBLER = BLOCKS.registerBlock(
            "mv_assembler",
            MVAssemblerBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    );

    public static final DeferredBlock<MVAssemblerPartBlock> MV_ASSEMBLER_PART = BLOCKS.registerBlock(
            "mv_assembler_part",
            MVAssemblerPartBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<MVChemicalReactorBlock> MV_CHEMICAL_REACTOR = BLOCKS.registerBlock(
            "mv_chemical_reactor",
            MVChemicalReactorBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    );

    public static final DeferredBlock<MVChemicalReactorPartBlock> MV_CHEMICAL_REACTOR_PART = BLOCKS.registerBlock(
            "mv_chemical_reactor_part",
            MVChemicalReactorPartBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<BlastDoorBlock> BLAST_DOOR = BLOCKS.registerBlock(
            "blast_door",
            BlastDoorBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(8.0F, BLAST_DOOR_BLAST_RESISTANCE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .dynamicShape()
    );

    public static final DeferredBlock<BlastDoorPartBlock> BLAST_DOOR_PART = BLOCKS.registerBlock(
            "blast_door_part",
            BlastDoorPartBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(8.0F, BLAST_DOOR_BLAST_RESISTANCE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .dynamicShape()
    );

    public static final DeferredBlock<ZoneGateBlock> ZONE_GATE = BLOCKS.registerBlock(
            "zone_gate",
            ZoneGateBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(8.0F, ZONE_GATE_BLAST_RESISTANCE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .dynamicShape()
    );

    public static final DeferredBlock<ZoneGatePartBlock> ZONE_GATE_PART = BLOCKS.registerBlock(
            "zone_gate_part",
            ZoneGatePartBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(8.0F, ZONE_GATE_BLAST_RESISTANCE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .dynamicShape()
    );

    public static final DeferredBlock<MediumTankBlock> MEDIUM_TANK = BLOCKS.registerBlock(
            "medium_tank",
            MediumTankBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .dynamicShape()
    );

    public static final DeferredBlock<MediumTankPartBlock> MEDIUM_TANK_PART = BLOCKS.registerBlock(
            "medium_tank_part",
            MediumTankPartBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .dynamicShape()
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

    public static final DeferredBlock<MVInlinePumpBlock> MV_INLINE_PUMP = BLOCKS.registerBlock(
            "mv_inline_pump",
            MVInlinePumpBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
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

    public static final DeferredBlock<RubberLogBlock> RUBBER_LOG = BLOCKS.registerBlock(
            "rubber_log",
            RubberLogBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG).randomTicks()
    );

    public static final DeferredBlock<?> RUBBER_PLANKS = BLOCKS.registerSimpleBlock(
            "rubber_planks",
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
    );

    public static final DeferredBlock<ResinBearingRubberLogBlock> RESIN_BEARING_RUBBER_LOG = BLOCKS.registerBlock(
            "resin_bearing_rubber_log",
            ResinBearingRubberLogBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG)
    );

    public static final DeferredBlock<LeavesBlock> RUBBER_LEAVES = BLOCKS.registerBlock(
            "rubber_leaves",
            LeavesBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)
    );

    public static final DeferredBlock<SaplingBlock> RUBBER_SAPLING = BLOCKS.registerBlock(
            "rubber_sapling",
            properties -> new SaplingBlock(ModTreeGrowers.RUBBER, properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SAPLING)
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
    public static final DeferredBlock<DeadLeavesBlock> DEAD_RUBBER_LEAVES = deadLeaves("dead_rubber_leaves", RUBBER_LEAVES);

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

    public static final DeferredBlock<?> NETHER_SULFUR_ORE = BLOCKS.registerSimpleBlock(
            "nether_sulfur_ore",
            BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_QUARTZ_ORE)
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
            EnvironmentalRadiationMode.PASSIVE_SOURCE_ONLY
    );
    public static final DeferredBlock<RadioactiveBlock> BAKED_VITRIFIED_STONE = vitrifiedStone(
            "baked_vitrified_stone",
            EnvironmentalRadiationMode.FULL_RAY
    );
    public static final DeferredBlock<RadioactiveBlock> SCORCHED_VITRIFIED_STONE = vitrifiedStone(
            "scorched_vitrified_stone",
            EnvironmentalRadiationMode.FULL_RAY
    );
    public static final DeferredBlock<RadioactiveBlock> IRRADIATED_VITRIFIED_STONE = vitrifiedStone(
            "irradiated_vitrified_stone",
            EnvironmentalRadiationMode.FULL_RAY
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
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    );

    public static final DeferredBlock<LVConnectorBlock> MV_CONNECTOR = BLOCKS.registerBlock(
            "mv_connector",
            LVConnectorBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .requiresCorrectToolForDrops()
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

    private static DeferredBlock<DeadLeavesBlock> deadLeaves(String name, Block livingLeaves) {
        return deadLeaves(name, () -> livingLeaves, livingLeaves);
    }

    private static DeferredBlock<DeadLeavesBlock> deadLeaves(String name, Supplier<? extends Block> livingLeaves) {
        return deadLeaves(name, livingLeaves, Blocks.OAK_LEAVES);
    }

    private static DeferredBlock<DeadLeavesBlock> deadLeaves(String name, Supplier<? extends Block> livingLeaves, Block propertiesSource) {
        return BLOCKS.registerBlock(
                name,
                properties -> new DeadLeavesBlock(properties, livingLeaves),
                BlockBehaviour.Properties.ofFullCopy(propertiesSource).randomTicks().noOcclusion()
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
