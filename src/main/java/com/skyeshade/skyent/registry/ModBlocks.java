package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.BasicFluidDuctBlock;
import com.skyeshade.skyent.content.block.CombustionGeneratorBlock;
import com.skyeshade.skyent.content.block.ElectricFurnaceBlock;
import com.skyeshade.skyent.content.block.LVConverterBlock;
import com.skyeshade.skyent.content.block.LVConnectorBlock;
import com.skyeshade.skyent.content.block.LVElectricPumpBlock;
import com.skyeshade.skyent.content.block.SiltBlock;
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

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
