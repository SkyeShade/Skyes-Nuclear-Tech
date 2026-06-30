package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.BasicFluidDuctBlock;
import com.skyeshade.skyent.content.block.BrickBlastFurnaceBlock;
import com.skyeshade.skyent.content.block.CombustionGeneratorBlock;
import com.skyeshade.skyent.content.block.CoriumBlock;
import com.skyeshade.skyent.content.block.DeadGrassBlock;
import com.skyeshade.skyent.content.block.DeadLeavesBlock;
import com.skyeshade.skyent.content.block.DeadPlantBlock;
import com.skyeshade.skyent.content.block.ElectricFurnaceBlock;
import com.skyeshade.skyent.content.block.LVConverterBlock;
import com.skyeshade.skyent.content.block.LVConnectorBlock;
import com.skyeshade.skyent.content.block.LVElectricPumpBlock;
import com.skyeshade.skyent.content.block.MoltenCoriumBlock;
import com.skyeshade.skyent.content.block.RadioactiveBlock;
import com.skyeshade.skyent.content.block.SiltBlock;
import com.skyeshade.skyent.content.block.UraniumBlock;
import com.skyeshade.skyent.content.radiation.EnvironmentalRadiationMode;
import com.skyeshade.skyent.content.radiation.RadiationConstants;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyesNuclearTech.MOD_ID);

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

    public static final DeferredBlock<BrickBlastFurnaceBlock> BRICK_BLAST_FURNACE = BLOCKS.registerBlock(
            "brick_blast_furnace",
            BrickBlastFurnaceBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BLAST_FURNACE)
    );

    public static final DeferredBlock<LVElectricPumpBlock> LV_ELECTRIC_PUMP = BLOCKS.registerBlock(
            "lv_electric_pump",
            LVElectricPumpBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
    );

    public static final DeferredBlock<BasicFluidDuctBlock> BASIC_FLUID_DUCT = BLOCKS.registerBlock(
            "basic_fluid_duct",
            BasicFluidDuctBlock::new,
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

    public static final DeferredBlock<?> TUNGSTEN_ORE = BLOCKS.registerSimpleBlock(
            "tungsten_ore",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_ORE)
    );

    public static final DeferredBlock<?> DEEPSLATE_TUNGSTEN_ORE = BLOCKS.registerSimpleBlock(
            "deepslate_tungsten_ore",
            BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_GOLD_ORE)
    );

    public static final DeferredBlock<RadioactiveBlock> URANIUM_ORE = BLOCKS.registerBlock(
            "uranium_ore",
            properties -> new RadioactiveBlock(
                    properties,
                    RadiationConstants.URANIUM_ORE_RADIATION_STRENGTH,
                    RadiationConstants.URANIUM_ORE_RADIATION_RANGE,
                    RadiationConstants.URANIUM_ORE_ENTITY_RADIATION_RANGE,
                    EnvironmentalRadiationMode.FULL_RAY
            ),
            BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_ORE)
    );

    public static final DeferredBlock<RadioactiveBlock> DEEPSLATE_URANIUM_ORE = BLOCKS.registerBlock(
            "deepslate_uranium_ore",
            properties -> new RadioactiveBlock(
                    properties,
                    RadiationConstants.URANIUM_ORE_RADIATION_STRENGTH,
                    RadiationConstants.URANIUM_ORE_RADIATION_RANGE,
                    RadiationConstants.URANIUM_ORE_ENTITY_RADIATION_RANGE,
                    EnvironmentalRadiationMode.FULL_RAY
            ),
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

    private ModBlocks() {
    }

    private static DeferredBlock<DeadLeavesBlock> deadLeaves(String name, net.minecraft.world.level.block.Block livingLeaves) {
        return BLOCKS.registerBlock(
                name,
                properties -> new DeadLeavesBlock(properties, () -> livingLeaves),
                BlockBehaviour.Properties.ofFullCopy(livingLeaves).randomTicks().noOcclusion()
        );
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
