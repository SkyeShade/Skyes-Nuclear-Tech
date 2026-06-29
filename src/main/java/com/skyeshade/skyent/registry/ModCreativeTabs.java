package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB,
            SkyesNuclearTech.MOD_ID
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SKYENT_TAB = CREATIVE_TABS.register(
            "skyent_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.skyent.skyent_tab"))
                    .icon(() -> ModItems.COMBUSTION_GENERATOR.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.COMBUSTION_GENERATOR.get());
                        output.accept(ModItems.ELECTRIC_FURNACE.get());
                        output.accept(ModItems.BRICK_BLAST_FURNACE.get());
                        output.accept(ModItems.LV_ELECTRIC_PUMP.get());
                        output.accept(ModItems.BASIC_FLUID_DUCT.get());
                        output.accept(ModItems.SILT.get());
                        output.accept(ModItems.DEAD_GRASS.get());
                        output.accept(ModItems.DEAD_SHORT_GRASS.get());
                        output.accept(ModItems.DEAD_TALL_GRASS.get());
                        output.accept(ModItems.DEAD_OAK_LEAVES.get());
                        output.accept(ModItems.DEAD_BIRCH_LEAVES.get());
                        output.accept(ModItems.DEAD_SPRUCE_LEAVES.get());
                        output.accept(ModItems.DEAD_JUNGLE_LEAVES.get());
                        output.accept(ModItems.DEAD_ACACIA_LEAVES.get());
                        output.accept(ModItems.DEAD_DARK_OAK_LEAVES.get());
                        output.accept(ModItems.DEAD_MANGROVE_LEAVES.get());
                        output.accept(ModItems.DEAD_CHERRY_LEAVES.get());
                        output.accept(ModItems.DEAD_AZALEA_LEAVES.get());
                        output.accept(ModItems.DEAD_FLOWERING_AZALEA_LEAVES.get());
                        output.accept(ModItems.TITANIUM_ORE.get());
                        output.accept(ModItems.DEEPSLATE_TITANIUM_ORE.get());
                        output.accept(ModItems.ALUMINUM_ORE.get());
                        output.accept(ModItems.DEEPSLATE_ALUMINUM_ORE.get());
                        output.accept(ModItems.TUNGSTEN_ORE.get());
                        output.accept(ModItems.DEEPSLATE_TUNGSTEN_ORE.get());
                        output.accept(ModItems.URANIUM_ORE.get());
                        output.accept(ModItems.DEEPSLATE_URANIUM_ORE.get());
                        output.accept(ModItems.URANIUM_BLOCK.get());
                        output.accept(ModItems.CORIUM_BLOCK.get());
                        output.accept(ModItems.LV_CONNECTOR.get());
                        output.accept(ModItems.STEEL_INGOT.get());
                        output.accept(ModItems.RAW_TITANIUM.get());
                        output.accept(ModItems.RAW_TUNGSTEN.get());
                        output.accept(ModItems.RAW_ALUMINUM.get());
                        output.accept(ModItems.RAW_URANIUM.get());
                        output.accept(ModItems.TITANIUM_INGOT.get());
                        output.accept(ModItems.TUNGSTEN_INGOT.get());
                        output.accept(ModItems.ALUMINUM_INGOT.get());
                        output.accept(ModItems.URANIUM_INGOT.get());
                        output.accept(ModItems.IRON_LIGHT_PLATING.get());
                        output.accept(ModItems.IRON_MEDIUM_PLATING.get());
                        output.accept(ModItems.IRON_HEAVY_PLATING.get());
                        output.accept(ModItems.LIGHT_ELECTRIC_MOTOR.get());
                        output.accept(ModItems.MEDIUM_ELECTRIC_MOTOR.get());
                        output.accept(ModItems.INDUSTRIAL_ELECTRIC_MOTOR.get());
                        output.accept(ModItems.HEAVY_ELECTRIC_MOTOR.get());
                        output.accept(ModItems.SUPERHEAVY_ELECTRIC_MOTOR.get());
                        output.accept(ModItems.LIGHT_HYDRAULIC.get());
                        output.accept(ModItems.MEDIUM_HYDRAULIC.get());
                        output.accept(ModItems.INDUSTRIAL_HYDRAULIC.get());
                        output.accept(ModItems.HEAVY_HYDRAULIC.get());
                        output.accept(ModItems.GEIGER_COUNTER.get());
                        output.accept(ModItems.TITANIUM_SWORD.get());
                        output.accept(ModItems.TITANIUM_PICKAXE.get());
                        output.accept(ModItems.TITANIUM_AXE.get());
                        output.accept(ModItems.TITANIUM_SHOVEL.get());
                        output.accept(ModItems.TITANIUM_HOE.get());
                        output.accept(ModItems.TUNGSTEN_SWORD.get());
                        output.accept(ModItems.TUNGSTEN_PICKAXE.get());
                        output.accept(ModItems.TUNGSTEN_AXE.get());
                        output.accept(ModItems.TUNGSTEN_SHOVEL.get());
                        output.accept(ModItems.TUNGSTEN_HOE.get());
                        output.accept(ModItems.LV_COPPER_WIRE_DRUM.get());
                        output.accept(ModItems.LV_STEEL_WIRE_DRUM.get());
                        output.accept(ModItems.LV_RJ_CONVERTER.get());
                        output.accept(ModItems.LV_FE_CONVERTER.get());
                    })
                    .build()
    );

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}
