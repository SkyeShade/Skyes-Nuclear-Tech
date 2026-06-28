package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.energy.LVWireType;
import com.skyeshade.skyent.content.item.LVWireDrumItem;
import com.skyeshade.skyent.content.item.TooltipBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyesNuclearTech.MOD_ID);

    public static final DeferredItem<BlockItem> COMBUSTION_GENERATOR = ITEMS.registerSimpleBlockItem(
            ModBlocks.COMBUSTION_GENERATOR,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> ELECTRIC_FURNACE = ITEMS.registerSimpleBlockItem(
            ModBlocks.ELECTRIC_FURNACE,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> LV_ELECTRIC_PUMP = ITEMS.registerSimpleBlockItem(
            ModBlocks.LV_ELECTRIC_PUMP,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> BASIC_FLUID_DUCT = ITEMS.registerSimpleBlockItem(
            ModBlocks.BASIC_FLUID_DUCT,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> SILT = ITEMS.registerSimpleBlockItem(
            ModBlocks.SILT,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> LV_CONNECTOR = ITEMS.registerSimpleBlockItem(
            ModBlocks.LV_CONNECTOR,
            new Item.Properties()
    );

    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.registerSimpleItem(
            "steel_ingot",
            new Item.Properties()
    );

    public static final DeferredItem<LVWireDrumItem> LV_COPPER_WIRE_DRUM = ITEMS.register(
            "lv_copper_wire_drum",
            () -> new LVWireDrumItem(
                    new Item.Properties().stacksTo(1),
                    LVWireType.COPPER,
                    "tooltip.skyent.lv_copper_wire_drum"
            )
    );

    public static final DeferredItem<LVWireDrumItem> LV_STEEL_WIRE_DRUM = ITEMS.register(
            "lv_steel_wire_drum",
            () -> new LVWireDrumItem(
                    new Item.Properties().stacksTo(1),
                    LVWireType.STEEL,
                    "tooltip.skyent.lv_steel_wire_drum"
            )
    );

    public static final DeferredItem<TooltipBlockItem> LV_RJ_CONVERTER = ITEMS.register(
            "lv_rj_converter",
            () -> new TooltipBlockItem(
                    ModBlocks.LV_RJ_CONVERTER.get(),
                    new Item.Properties(),
                    "tooltip.skyent.lv_rj_converter.line1"
            )
    );

    public static final DeferredItem<TooltipBlockItem> LV_FE_CONVERTER = ITEMS.register(
            "lv_fe_converter",
            () -> new TooltipBlockItem(
                    ModBlocks.LV_FE_CONVERTER.get(),
                    new Item.Properties(),
                    "tooltip.skyent.lv_fe_converter.line1"
            )
    );

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
