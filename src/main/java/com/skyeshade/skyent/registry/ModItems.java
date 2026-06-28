package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.item.CopperWireDrumItem;
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

    public static final DeferredItem<BlockItem> LV_CONNECTOR = ITEMS.registerSimpleBlockItem(
            ModBlocks.LV_CONNECTOR,
            new Item.Properties()
    );

    public static final DeferredItem<CopperWireDrumItem> COPPER_WIRE_DRUM = ITEMS.registerItem(
            "copper_wire_drum",
            CopperWireDrumItem::new,
            new Item.Properties()
    );

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
