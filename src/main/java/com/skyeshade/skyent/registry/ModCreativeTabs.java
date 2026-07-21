package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.item.SteelFluidBarrelVariants;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB,
            SkyesNuclearTech.MOD_ID
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MACHINES = CREATIVE_TABS.register(
            "machines_and_tools",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.skyent.machines_and_tools"))
                    .icon(() -> ModItems.LV_CRUSHER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> addMachinesAndTools(output))
                    .build()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MATERIALS = CREATIVE_TABS.register(
            "materials",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.skyent.materials"))
                    .icon(() -> ModItems.COBALT_INGOT.get().getDefaultInstance())
                    .displayItems((parameters, output) -> addMaterials(output))
                    .build()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BLOCKS = CREATIVE_TABS.register(
            "blocks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.skyent.blocks"))
                    .icon(() -> ModItems.VITRIFIED_STONE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> addBlocks(output))
                    .build()
    );

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }

    private static void addMachinesAndTools(CreativeModeTab.Output output) {
        add(output,
                ModItems.BASIC_FLUID_DUCT,
                ModItems.BASIC_CONVEYOR_BELT,
                ModItems.CONVEYOR_SPLITTER,
                ModItems.CONVEYOR_EXPORTER,
                ModItems.CONVEYOR_ELEVATOR,
                ModItems.CONVEYOR_CHUTE,
                ModItems.COMBUSTION_GENERATOR,
                ModItems.ELECTRIC_FURNACE,
                ModItems.LV_CRUSHER,
                ModItems.BRICK_BLAST_FURNACE,
                ModItems.COAL_FORGE,
                ModItems.FORGING_ANVIL,
                ModItems.STEAM_FORGE_HAMMER,
                ModItems.HEATING_CHAMBER,
                ModItems.ROLLING_MILL,
                ModItems.INDUSTRIAL_PRESS,
                ModItems.WIRE_MILL,
                ModItems.MV_ASSEMBLER,
                ModItems.LV_ELECTRIC_PUMP,
                ModItems.LV_STEAM_TURBINE,
                ModItems.LV_CONNECTOR,
                ModItems.COPPER_WIRE_DRUM,
                ModItems.MV_CONNECTOR,
                ModItems.STEEL_WIRE_DRUM,
                ModItems.COBALT_BRONZE_WIRE_DRUM,
                ModItems.LV_MV_TRANSFORMER,
                ModItems.LV_RJ_CONVERTER,
                ModItems.LV_FE_CONVERTER,
                ModItems.NUCLEAR_CHARGE,
                ModItems.STEEL_FLUID_BARREL
        );
        addSteelFluidBarrelVariants(output);
        add(output,
                ModItems.WIRE_CUTTERS,
                ModItems.WRENCH,
                ModItems.FORGING_HAMMER,
                ModItems.STEEL_TONGS,
                ModItems.TREE_TAP,
                ModItems.REMOTE_DETONATOR,
                ModItems.GEIGER_COUNTER,
                ModItems.TITANIUM_SWORD,
                ModItems.TITANIUM_PICKAXE,
                ModItems.TITANIUM_AXE,
                ModItems.TITANIUM_SHOVEL,
                ModItems.TITANIUM_HOE,
                ModItems.TUNGSTEN_SWORD,
                ModItems.TUNGSTEN_PICKAXE,
                ModItems.TUNGSTEN_AXE,
                ModItems.TUNGSTEN_SHOVEL,
                ModItems.TUNGSTEN_HOE
        );
    }

    private static void addBlocks(CreativeModeTab.Output output) {
        add(output,
                ModItems.ALUMINUM_BLOCK,
                ModItems.TITANIUM_BLOCK,
                ModItems.TUNGSTEN_BLOCK,
                ModItems.STEEL_BLOCK,
                ModItems.COBALT_BLOCK,
                ModItems.NICKEL_BLOCK,
                ModItems.LEAD_BLOCK,
                ModItems.URANIUM_BLOCK,
                ModItems.CORIUM_BLOCK,
                ModItems.RADIOACTIVE_SCRAP_METAL,
                ModItems.ALUMINUM_ORE,
                ModItems.DEEPSLATE_ALUMINUM_ORE,
                ModItems.TITANIUM_ORE,
                ModItems.DEEPSLATE_TITANIUM_ORE,
                ModItems.TUNGSTEN_ORE,
                ModItems.DEEPSLATE_TUNGSTEN_ORE,
                ModItems.LEAD_ORE,
                ModItems.NETHER_SULFUR_ORE,
                ModItems.URANIUM_ORE,
                ModItems.DEEPSLATE_URANIUM_ORE,
                ModItems.FIRE_BRICKS,
                ModItems.CRACKED_CONCRETE_BRICKS,
                ModItems.CONCRETE_BRICKS,
                ModItems.REINFORCED_CONCRETE,
                ModItems.REINFORCED_GLASS,
                ModItems.TUNGSTEN_REINFORCED_CONCRETE,
                ModItems.PLATED_CONCRETE,
                ModItems.SILT,
                ModItems.CHARRED_LOG,
                ModItems.RUBBER_LOG,
                ModItems.RUBBER_PLANKS,
                ModItems.RUBBER_LEAVES,
                ModItems.RUBBER_SAPLING,
                ModItems.CONTAMINATED_GRASS_BLOCK,
                ModItems.DEAD_GRASS,
                ModItems.DEAD_SHORT_GRASS,
                ModItems.DEAD_TALL_GRASS,
                ModItems.DEAD_OAK_LEAVES,
                ModItems.DEAD_BIRCH_LEAVES,
                ModItems.DEAD_SPRUCE_LEAVES,
                ModItems.DEAD_JUNGLE_LEAVES,
                ModItems.DEAD_ACACIA_LEAVES,
                ModItems.DEAD_DARK_OAK_LEAVES,
                ModItems.DEAD_MANGROVE_LEAVES,
                ModItems.DEAD_CHERRY_LEAVES,
                ModItems.DEAD_AZALEA_LEAVES,
                ModItems.DEAD_FLOWERING_AZALEA_LEAVES,
                ModItems.DEAD_RUBBER_LEAVES,
                ModItems.VITRIFIED_STONE,
                ModItems.BAKED_VITRIFIED_STONE,
                ModItems.SCORCHED_VITRIFIED_STONE,
                ModItems.IRRADIATED_VITRIFIED_STONE,
                ModItems.RADIANT_VITRIFIED_STONE,
                ModItems.INFERNAL_VITRIFIED_STONE
        );
    }

    private static void addMaterials(CreativeModeTab.Output output) {
        add(output,
                ModItems.TIN_INGOT,
                ModItems.COBALT_BRONZE_INGOT,
                ModItems.STEEL_INGOT,
                ModItems.NICKEL_INGOT,
                ModItems.COBALT_INGOT,
                ModItems.CUPRONICKEL_INGOT,
                ModItems.ALUMINUM_INGOT,
                ModItems.TITANIUM_INGOT,
                ModItems.TUNGSTEN_INGOT,
                ModItems.LEAD_INGOT,
                ModItems.URANIUM_INGOT,
                ModItems.RAW_NICKEL,
                ModItems.RAW_COBALT,
                ModItems.RAW_ALUMINUM,
                ModItems.RAW_TITANIUM,
                ModItems.RAW_TUNGSTEN,
                ModItems.SULFUR,
                ModItems.RAW_LEAD,
                ModItems.RAW_URANIUM,
                ModItems.IRON_POWDER,
                ModItems.COPPER_POWDER,
                ModItems.TIN_POWDER,
                ModItems.SMALL_TIN_POWDER,
                ModItems.STEEL_POWDER,
                ModItems.NICKEL_POWDER,
                ModItems.COBALT_POWDER,
                ModItems.ALUMINUM_POWDER,
                ModItems.TITANIUM_POWDER,
                ModItems.TUNGSTEN_POWDER,
                ModItems.GOLD_POWDER,
                ModItems.LEAD_POWDER,
                ModItems.URANIUM_POWDER,
                ModItems.IRON_PLATE,
                ModItems.COPPER_PLATE,
                ModItems.COBALT_BRONZE_PLATE,
                ModItems.STEEL_PLATE,
                ModItems.NICKEL_PLATE,
                ModItems.COBALT_PLATE,
                ModItems.CUPRONICKEL_PLATE,
                ModItems.ALUMINUM_PLATE,
                ModItems.TITANIUM_PLATE,
                ModItems.TUNGSTEN_PLATE,
                ModItems.GOLD_PLATE,
                ModItems.LEAD_PLATE,
                ModItems.IRON_ROD,
                ModItems.COPPER_ROD,
                ModItems.TIN_ROD,
                ModItems.COBALT_BRONZE_ROD,
                ModItems.STEEL_ROD,
                ModItems.NICKEL_ROD,
                ModItems.COBALT_ROD,
                ModItems.CUPRONICKEL_ROD,
                ModItems.ALUMINUM_ROD,
                ModItems.TITANIUM_ROD,
                ModItems.TUNGSTEN_ROD,
                ModItems.IRON_BOLT,
                ModItems.COPPER_BOLT,
                ModItems.STEEL_BOLT,
                ModItems.NICKEL_BOLT,
                ModItems.COBALT_BOLT,
                ModItems.ALUMINUM_BOLT,
                ModItems.TITANIUM_BOLT,
                ModItems.TUNGSTEN_BOLT,
                ModItems.COPPER_WIRE,
                ModItems.TIN_WIRE,
                ModItems.STEEL_WIRE,
                ModItems.COBALT_WIRE,
                ModItems.COBALT_BRONZE_WIRE,
                ModItems.CUPRONICKEL_WIRE,
                ModItems.COPPER_COIL,
                ModItems.TRANSFORMER_COIL,
                ModItems.STEEL_BEARING,
                ModItems.FIRE_CLAY,
                ModItems.FIRE_BRICK,
                ModItems.NICKEL_TURBINE,
                ModItems.TITANIUM_TURBINE_BLADE,
                ModItems.HEATING_ELEMENT,
                ModItems.ELECTRIC_MOTOR,
                ModItems.HYDRAULIC_COMPONENT,
                ModItems.RESIN,
                ModItems.RUBBER,
                ModItems.MOLTEN_CORIUM_BUCKET
        );
    }

    @SafeVarargs
    private static void add(CreativeModeTab.Output output, DeferredItem<? extends Item>... items) {
        for (DeferredItem<? extends Item> item : items) {
            if (!isHotItem(item)) {
                output.accept(item.get());
            }
        }
    }

    private static boolean isHotItem(DeferredItem<? extends Item> item) {
        String id = item.getId().getPath();
        return id.contains("hot_") || id.contains("_hot") || id.contains("heated_") || id.contains("glowing_");
    }

    private static void addSteelFluidBarrelVariants(CreativeModeTab.Output output) {
        SteelFluidBarrelVariants.createFilledVariants().forEach(output::accept);
    }
}
