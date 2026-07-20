package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.energy.LVWireType;
import com.skyeshade.skyent.content.item.ForgingHammerItem;
import com.skyeshade.skyent.content.item.GeigerCounterItem;
import com.skyeshade.skyent.content.item.LVWireDrumItem;
import com.skyeshade.skyent.content.item.RadioactiveBlockItem;
import com.skyeshade.skyent.content.item.RadioactiveItem;
import com.skyeshade.skyent.content.item.RadioactiveToxicItem;
import com.skyeshade.skyent.content.item.RemoteDetonatorItem;
import com.skyeshade.skyent.content.item.ShieldingBlockItem;
import com.skyeshade.skyent.content.item.SkyentToolTier;
import com.skyeshade.skyent.content.item.SteelFluidBarrelItem;
import com.skyeshade.skyent.content.item.TooltipBlockItem;
import com.skyeshade.skyent.content.item.ToxicBlockItem;
import com.skyeshade.skyent.content.item.ToxicItem;
import com.skyeshade.skyent.content.item.SteelTongsItem;
import com.skyeshade.skyent.content.item.UraniumBlockItem;
import com.skyeshade.skyent.content.item.WireCuttersItem;
import com.skyeshade.skyent.content.item.WrenchItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
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

    public static final DeferredItem<BlockItem> LV_CRUSHER = ITEMS.registerSimpleBlockItem(
            ModBlocks.LV_CRUSHER,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> BRICK_BLAST_FURNACE = ITEMS.registerSimpleBlockItem(
            ModBlocks.BRICK_BLAST_FURNACE,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> COAL_FORGE = ITEMS.registerSimpleBlockItem(
            ModBlocks.COAL_FORGE,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> FIRE_BRICKS = ITEMS.registerSimpleBlockItem(
            ModBlocks.FIRE_BRICKS,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> CONCRETE_BRICKS = ITEMS.registerSimpleBlockItem(
            ModBlocks.CONCRETE_BRICKS,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> CRACKED_CONCRETE_BRICKS = ITEMS.registerSimpleBlockItem(
            ModBlocks.CRACKED_CONCRETE_BRICKS,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> REINFORCED_CONCRETE = ITEMS.registerSimpleBlockItem(
            ModBlocks.REINFORCED_CONCRETE,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> REINFORCED_GLASS = ITEMS.registerSimpleBlockItem(
            ModBlocks.REINFORCED_GLASS,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> TUNGSTEN_REINFORCED_CONCRETE = ITEMS.registerSimpleBlockItem(
            ModBlocks.TUNGSTEN_REINFORCED_CONCRETE,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> PLATED_CONCRETE = ITEMS.registerSimpleBlockItem(
            ModBlocks.PLATED_CONCRETE,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> FORGING_ANVIL = ITEMS.registerSimpleBlockItem(
            ModBlocks.FORGING_ANVIL,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> STEAM_FORGE_HAMMER = ITEMS.registerSimpleBlockItem(
            ModBlocks.STEAM_FORGE_HAMMER,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> HEATING_CHAMBER = ITEMS.registerSimpleBlockItem(
            ModBlocks.HEATING_CHAMBER,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> ROLLING_MILL = ITEMS.registerSimpleBlockItem(
            ModBlocks.ROLLING_MILL,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> INDUSTRIAL_PRESS = ITEMS.registerSimpleBlockItem(
            ModBlocks.INDUSTRIAL_PRESS,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> WIRE_MILL = ITEMS.registerSimpleBlockItem(
            ModBlocks.WIRE_MILL,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> MV_ASSEMBLER = ITEMS.registerSimpleBlockItem(
            ModBlocks.MV_ASSEMBLER,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> NUCLEAR_CHARGE = ITEMS.registerSimpleBlockItem(
            ModBlocks.NUCLEAR_CHARGE,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> LV_ELECTRIC_PUMP = ITEMS.registerSimpleBlockItem(
            ModBlocks.LV_ELECTRIC_PUMP,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> LV_STEAM_TURBINE = ITEMS.registerSimpleBlockItem(
            ModBlocks.LV_STEAM_TURBINE,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> BASIC_FLUID_DUCT = ITEMS.registerSimpleBlockItem(
            ModBlocks.BASIC_FLUID_DUCT,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> BASIC_CONVEYOR_BELT = ITEMS.registerSimpleBlockItem(
            ModBlocks.BASIC_CONVEYOR_BELT,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> CONVEYOR_SPLITTER = ITEMS.registerSimpleBlockItem(
            ModBlocks.CONVEYOR_SPLITTER,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> CONVEYOR_EXPORTER = ITEMS.registerSimpleBlockItem(
            ModBlocks.CONVEYOR_EXPORTER,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> CONVEYOR_ELEVATOR = ITEMS.registerSimpleBlockItem(
            ModBlocks.CONVEYOR_ELEVATOR,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> CONVEYOR_CHUTE = ITEMS.registerSimpleBlockItem(
            ModBlocks.CONVEYOR_CHUTE,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> SILT = ITEMS.registerSimpleBlockItem(
            ModBlocks.SILT,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> DEAD_GRASS = ITEMS.registerSimpleBlockItem(
            ModBlocks.DEAD_GRASS,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> DEAD_SHORT_GRASS = ITEMS.registerSimpleBlockItem(
            ModBlocks.DEAD_SHORT_GRASS,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> DEAD_TALL_GRASS = ITEMS.registerSimpleBlockItem(
            ModBlocks.DEAD_TALL_GRASS,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> CHARRED_LOG = ITEMS.registerSimpleBlockItem(
            ModBlocks.CHARRED_LOG,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> DEAD_OAK_LEAVES = ITEMS.registerSimpleBlockItem(
            ModBlocks.DEAD_OAK_LEAVES,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> DEAD_BIRCH_LEAVES = ITEMS.registerSimpleBlockItem(
            ModBlocks.DEAD_BIRCH_LEAVES,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> DEAD_SPRUCE_LEAVES = ITEMS.registerSimpleBlockItem(
            ModBlocks.DEAD_SPRUCE_LEAVES,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> DEAD_JUNGLE_LEAVES = ITEMS.registerSimpleBlockItem(
            ModBlocks.DEAD_JUNGLE_LEAVES,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> DEAD_ACACIA_LEAVES = ITEMS.registerSimpleBlockItem(
            ModBlocks.DEAD_ACACIA_LEAVES,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> DEAD_DARK_OAK_LEAVES = ITEMS.registerSimpleBlockItem(
            ModBlocks.DEAD_DARK_OAK_LEAVES,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> DEAD_MANGROVE_LEAVES = ITEMS.registerSimpleBlockItem(
            ModBlocks.DEAD_MANGROVE_LEAVES,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> DEAD_CHERRY_LEAVES = ITEMS.registerSimpleBlockItem(
            ModBlocks.DEAD_CHERRY_LEAVES,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> DEAD_AZALEA_LEAVES = ITEMS.registerSimpleBlockItem(
            ModBlocks.DEAD_AZALEA_LEAVES,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> DEAD_FLOWERING_AZALEA_LEAVES = ITEMS.registerSimpleBlockItem(
            ModBlocks.DEAD_FLOWERING_AZALEA_LEAVES,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> TITANIUM_ORE = ITEMS.registerSimpleBlockItem(
            ModBlocks.TITANIUM_ORE,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> DEEPSLATE_TITANIUM_ORE = ITEMS.registerSimpleBlockItem(
            ModBlocks.DEEPSLATE_TITANIUM_ORE,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> ALUMINUM_ORE = ITEMS.registerSimpleBlockItem(
            ModBlocks.ALUMINUM_ORE,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> DEEPSLATE_ALUMINUM_ORE = ITEMS.registerSimpleBlockItem(
            ModBlocks.DEEPSLATE_ALUMINUM_ORE,
            new Item.Properties()
    );

    public static final DeferredItem<ShieldingBlockItem> ALUMINUM_BLOCK = ITEMS.register(
            "aluminum_block",
            () -> new ShieldingBlockItem(ModBlocks.ALUMINUM_BLOCK.get(), new Item.Properties())
    );

    public static final DeferredItem<ShieldingBlockItem> TITANIUM_BLOCK = ITEMS.register(
            "titanium_block",
            () -> new ShieldingBlockItem(ModBlocks.TITANIUM_BLOCK.get(), new Item.Properties())
    );

    public static final DeferredItem<BlockItem> TUNGSTEN_ORE = ITEMS.registerSimpleBlockItem(
            ModBlocks.TUNGSTEN_ORE,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> DEEPSLATE_TUNGSTEN_ORE = ITEMS.registerSimpleBlockItem(
            ModBlocks.DEEPSLATE_TUNGSTEN_ORE,
            new Item.Properties()
    );

    public static final DeferredItem<ShieldingBlockItem> TUNGSTEN_BLOCK = ITEMS.register(
            "tungsten_block",
            () -> new ShieldingBlockItem(ModBlocks.TUNGSTEN_BLOCK.get(), new Item.Properties())
    );

    public static final DeferredItem<ShieldingBlockItem> STEEL_BLOCK = ITEMS.register(
            "steel_block",
            () -> new ShieldingBlockItem(ModBlocks.STEEL_BLOCK.get(), new Item.Properties())
    );

    public static final DeferredItem<ShieldingBlockItem> COBALT_BLOCK = ITEMS.register(
            "cobalt_block",
            () -> new ShieldingBlockItem(ModBlocks.COBALT_BLOCK.get(), new Item.Properties())
    );

    public static final DeferredItem<ShieldingBlockItem> NICKEL_BLOCK = ITEMS.register(
            "nickel_block",
            () -> new ShieldingBlockItem(ModBlocks.NICKEL_BLOCK.get(), new Item.Properties())
    );

    public static final DeferredItem<ToxicBlockItem> LEAD_ORE = ITEMS.register(
            "lead_ore",
            () -> new ToxicBlockItem(ModBlocks.LEAD_ORE.get(), new Item.Properties())
    );

    public static final DeferredItem<ToxicBlockItem> LEAD_BLOCK = ITEMS.register(
            "lead_block",
            () -> new ToxicBlockItem(ModBlocks.LEAD_BLOCK.get(), new Item.Properties())
    );

    public static final DeferredItem<RadioactiveBlockItem> URANIUM_ORE = ITEMS.register(
            "uranium_ore",
            () -> new RadioactiveBlockItem(ModBlocks.URANIUM_ORE.get(), new Item.Properties())
    );

    public static final DeferredItem<RadioactiveBlockItem> DEEPSLATE_URANIUM_ORE = ITEMS.register(
            "deepslate_uranium_ore",
            () -> new RadioactiveBlockItem(ModBlocks.DEEPSLATE_URANIUM_ORE.get(), new Item.Properties())
    );

    public static final DeferredItem<UraniumBlockItem> URANIUM_BLOCK = ITEMS.register(
            "uranium_block",
            () -> new UraniumBlockItem(ModBlocks.URANIUM_BLOCK.get(), new Item.Properties())
    );

    public static final DeferredItem<RadioactiveBlockItem> CORIUM_BLOCK = ITEMS.register(
            "corium_block",
            () -> new RadioactiveBlockItem(ModBlocks.CORIUM_BLOCK.get(), new Item.Properties())
    );

    public static final DeferredItem<RadioactiveBlockItem> RADIOACTIVE_SCRAP_METAL = ITEMS.register(
            "radioactive_scrap_metal",
            () -> new RadioactiveBlockItem(ModBlocks.RADIOACTIVE_SCRAP_METAL.get(), new Item.Properties())
    );

    public static final DeferredItem<RadioactiveBlockItem> CONTAMINATED_GRASS_BLOCK = radioactiveBlockItem("contaminated_grass_block", ModBlocks.CONTAMINATED_GRASS_BLOCK);
    public static final DeferredItem<RadioactiveBlockItem> VITRIFIED_STONE = radioactiveBlockItem("vitrified_stone", ModBlocks.VITRIFIED_STONE);
    public static final DeferredItem<RadioactiveBlockItem> BAKED_VITRIFIED_STONE = radioactiveBlockItem("baked_vitrified_stone", ModBlocks.BAKED_VITRIFIED_STONE);
    public static final DeferredItem<RadioactiveBlockItem> SCORCHED_VITRIFIED_STONE = radioactiveBlockItem("scorched_vitrified_stone", ModBlocks.SCORCHED_VITRIFIED_STONE);
    public static final DeferredItem<RadioactiveBlockItem> IRRADIATED_VITRIFIED_STONE = radioactiveBlockItem("irradiated_vitrified_stone", ModBlocks.IRRADIATED_VITRIFIED_STONE);
    public static final DeferredItem<RadioactiveBlockItem> HOT_VITRIFIED_STONE = radioactiveBlockItem("hot_vitrified_stone", ModBlocks.HOT_VITRIFIED_STONE);
    public static final DeferredItem<RadioactiveBlockItem> RADIANT_VITRIFIED_STONE = radioactiveBlockItem("radiant_vitrified_stone", ModBlocks.RADIANT_VITRIFIED_STONE);
    public static final DeferredItem<RadioactiveBlockItem> INFERNAL_VITRIFIED_STONE = radioactiveBlockItem("infernal_vitrified_stone", ModBlocks.INFERNAL_VITRIFIED_STONE);

    public static final DeferredItem<BucketItem> MOLTEN_CORIUM_BUCKET = ITEMS.register(
            "molten_corium_bucket",
            () -> new BucketItem(ModFluids.MOLTEN_CORIUM.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1))
    );
    public static final DeferredItem<SteelFluidBarrelItem> STEEL_FLUID_BARREL = ITEMS.register(
            "steel_fluid_barrel",
            () -> new SteelFluidBarrelItem(new Item.Properties().stacksTo(16))
    );

    public static final DeferredItem<BlockItem> LV_CONNECTOR = ITEMS.registerSimpleBlockItem(
            ModBlocks.LV_CONNECTOR,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> MV_CONNECTOR = ITEMS.registerSimpleBlockItem(
            ModBlocks.MV_CONNECTOR,
            new Item.Properties()
    );

    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.registerSimpleItem(
            "steel_ingot",
            new Item.Properties()
    );

    public static final DeferredItem<Item> RAW_COBALT = simpleItem("raw_cobalt");
    public static final DeferredItem<Item> RAW_NICKEL = simpleItem("raw_nickel");
    public static final DeferredItem<Item> RAW_TITANIUM = simpleItem("raw_titanium");
    public static final DeferredItem<Item> RAW_TUNGSTEN = simpleItem("raw_tungsten");
    public static final DeferredItem<Item> RAW_ALUMINUM = simpleItem("raw_aluminum");
    public static final DeferredItem<ToxicItem> RAW_LEAD = ITEMS.register(
            "raw_lead",
            () -> new ToxicItem(new Item.Properties())
    );
    public static final DeferredItem<RadioactiveItem> RAW_URANIUM = ITEMS.register(
            "raw_uranium",
            () -> new RadioactiveItem(new Item.Properties())
    );
    public static final DeferredItem<Item> TITANIUM_INGOT = simpleItem("titanium_ingot");
    public static final DeferredItem<Item> TUNGSTEN_INGOT = simpleItem("tungsten_ingot");
    public static final DeferredItem<Item> ALUMINUM_INGOT = simpleItem("aluminum_ingot");
    public static final DeferredItem<ToxicItem> LEAD_INGOT = ITEMS.register(
            "lead_ingot",
            () -> new ToxicItem(new Item.Properties())
    );
    public static final DeferredItem<RadioactiveItem> URANIUM_INGOT = ITEMS.register(
            "uranium_ingot",
            () -> new RadioactiveItem(new Item.Properties())
    );
    public static final DeferredItem<Item> COBALT_INGOT = simpleItem("cobalt_ingot");
    public static final DeferredItem<Item> NICKEL_INGOT = simpleItem("nickel_ingot");
    public static final DeferredItem<Item> TIN_INGOT = simpleItem("tin_ingot");
    public static final DeferredItem<Item> COBALT_BRONZE_INGOT = simpleItem("cobalt_bronze_ingot");
    public static final DeferredItem<Item> CUPRONICKEL_INGOT = simpleItem("cupronickel_ingot");
    public static final DeferredItem<Item> HOT_IRON_INGOT = simpleItem("hot_iron_ingot");
    public static final DeferredItem<Item> HOT_COPPER_INGOT = simpleItem("hot_copper_ingot");
    public static final DeferredItem<Item> HOT_GOLD_INGOT = simpleItem("hot_gold_ingot");
    public static final DeferredItem<Item> HOT_STEEL_INGOT = simpleItem("hot_steel_ingot");
    public static final DeferredItem<Item> HOT_COBALT_INGOT = simpleItem("hot_cobalt_ingot");
    public static final DeferredItem<Item> HOT_NICKEL_INGOT = simpleItem("hot_nickel_ingot");
    public static final DeferredItem<Item> HOT_ALUMINUM_INGOT = simpleItem("hot_aluminum_ingot");
    public static final DeferredItem<Item> HOT_TITANIUM_INGOT = simpleItem("hot_titanium_ingot");
    public static final DeferredItem<Item> HOT_TUNGSTEN_INGOT = simpleItem("hot_tungsten_ingot");
    public static final DeferredItem<Item> HOT_URANIUM_INGOT = simpleItem("hot_uranium_ingot");
    public static final DeferredItem<Item> HOT_COBALT_BRONZE_INGOT = simpleItem("hot_cobalt_bronze_ingot");
    public static final DeferredItem<Item> HOT_CUPRONICKEL_INGOT = simpleItem("hot_cupronickel_ingot");
    public static final DeferredItem<Item> IRON_PLATE = simpleItem("iron_plate");
    public static final DeferredItem<Item> STEEL_PLATE = simpleItem("steel_plate");
    public static final DeferredItem<Item> GOLD_PLATE = simpleItem("gold_plate");
    public static final DeferredItem<Item> ALUMINUM_PLATE = simpleItem("aluminum_plate");
    public static final DeferredItem<Item> TITANIUM_PLATE = simpleItem("titanium_plate");
    public static final DeferredItem<Item> TUNGSTEN_PLATE = simpleItem("tungsten_plate");
    public static final DeferredItem<Item> COPPER_PLATE = simpleItem("copper_plate");
    public static final DeferredItem<Item> COBALT_PLATE = simpleItem("cobalt_plate");
    public static final DeferredItem<Item> NICKEL_PLATE = simpleItem("nickel_plate");
    public static final DeferredItem<Item> COBALT_BRONZE_PLATE = simpleItem("cobalt_bronze_plate");
    public static final DeferredItem<Item> CUPRONICKEL_PLATE = simpleItem("cupronickel_plate");
    public static final DeferredItem<ToxicItem> LEAD_PLATE = ITEMS.register(
            "lead_plate",
            () -> new ToxicItem(new Item.Properties())
    );
    public static final DeferredItem<Item> IRON_POWDER = simpleItem("iron_powder");
    public static final DeferredItem<Item> GOLD_POWDER = simpleItem("gold_powder");
    public static final DeferredItem<ToxicItem> LEAD_POWDER = ITEMS.register(
            "lead_powder",
            () -> new ToxicItem(new Item.Properties())
    );
    public static final DeferredItem<Item> TUNGSTEN_POWDER = simpleItem("tungsten_powder");
    public static final DeferredItem<Item> STEEL_POWDER = simpleItem("steel_powder");
    public static final DeferredItem<RadioactiveToxicItem> URANIUM_POWDER = ITEMS.register(
            "uranium_powder",
            () -> new RadioactiveToxicItem(new Item.Properties())
    );
    public static final DeferredItem<Item> COPPER_POWDER = simpleItem("copper_powder");
    public static final DeferredItem<Item> TITANIUM_POWDER = simpleItem("titanium_powder");
    public static final DeferredItem<Item> ALUMINUM_POWDER = simpleItem("aluminum_powder");
    public static final DeferredItem<Item> COBALT_POWDER = simpleItem("cobalt_powder");
    public static final DeferredItem<Item> NICKEL_POWDER = simpleItem("nickel_powder");
    public static final DeferredItem<Item> TIN_POWDER = simpleItem("tin_powder");
    public static final DeferredItem<Item> SMALL_TIN_POWDER = simpleItem("small_tin_powder");
    public static final DeferredItem<Item> FIRE_CLAY = simpleItem("fire_clay");
    public static final DeferredItem<Item> FIRE_BRICK = simpleItem("fire_brick");
    public static final DeferredItem<Item> IRON_BOLT = simpleItem("iron_bolt");
    public static final DeferredItem<Item> COPPER_BOLT = simpleItem("copper_bolt");
    public static final DeferredItem<Item> STEEL_BOLT = simpleItem("steel_bolt");
    public static final DeferredItem<Item> COBALT_WIRE = simpleItem("cobalt_wire");
    public static final DeferredItem<Item> TUNGSTEN_BOLT = simpleItem("tungsten_bolt");
    public static final DeferredItem<Item> COBALT_BOLT = simpleItem("cobalt_bolt");
    public static final DeferredItem<Item> NICKEL_BOLT = simpleItem("nickel_bolt");
    public static final DeferredItem<Item> ALUMINUM_BOLT = simpleItem("aluminum_bolt");
    public static final DeferredItem<Item> TITANIUM_BOLT = simpleItem("titanium_bolt");
    public static final DeferredItem<Item> IRON_ROD = simpleItem("iron_rod");
    public static final DeferredItem<Item> COPPER_ROD = simpleItem("copper_rod");
    public static final DeferredItem<Item> TIN_ROD = simpleItem("tin_rod");
    public static final DeferredItem<Item> STEEL_ROD = simpleItem("steel_rod");
    public static final DeferredItem<Item> ALUMINUM_ROD = simpleItem("aluminum_rod");
    public static final DeferredItem<Item> TITANIUM_ROD = simpleItem("titanium_rod");
    public static final DeferredItem<Item> TUNGSTEN_ROD = simpleItem("tungsten_rod");
    public static final DeferredItem<Item> COBALT_ROD = simpleItem("cobalt_rod");
    public static final DeferredItem<Item> NICKEL_ROD = simpleItem("nickel_rod");
    public static final DeferredItem<Item> COBALT_BRONZE_ROD = simpleItem("cobalt_bronze_rod");
    public static final DeferredItem<Item> CUPRONICKEL_ROD = simpleItem("cupronickel_rod");
    public static final DeferredItem<Item> HOT_IRON_ROD = simpleItem("hot_iron_rod");
    public static final DeferredItem<Item> HOT_COPPER_ROD = simpleItem("hot_copper_rod");
    public static final DeferredItem<Item> HOT_TIN_ROD = simpleItem("hot_tin_rod");
    public static final DeferredItem<Item> HOT_STEEL_ROD = simpleItem("hot_steel_rod");
    public static final DeferredItem<Item> HOT_ALUMINUM_ROD = simpleItem("hot_aluminum_rod");
    public static final DeferredItem<Item> HOT_TITANIUM_ROD = simpleItem("hot_titanium_rod");
    public static final DeferredItem<Item> HOT_TUNGSTEN_ROD = simpleItem("hot_tungsten_rod");
    public static final DeferredItem<Item> HOT_COBALT_ROD = simpleItem("hot_cobalt_rod");
    public static final DeferredItem<Item> HOT_NICKEL_ROD = simpleItem("hot_nickel_rod");
    public static final DeferredItem<Item> HOT_COBALT_BRONZE_ROD = simpleItem("hot_cobalt_bronze_rod");
    public static final DeferredItem<Item> HOT_CUPRONICKEL_ROD = simpleItem("hot_cupronickel_rod");
    public static final DeferredItem<Item> COPPER_WIRE = simpleItem("copper_wire");
    public static final DeferredItem<Item> TIN_WIRE = simpleItem("tin_wire");
    public static final DeferredItem<Item> STEEL_WIRE = simpleItem("steel_wire");
    public static final DeferredItem<Item> COBALT_BRONZE_WIRE = simpleItem("cobalt_bronze_wire");
    public static final DeferredItem<Item> CUPRONICKEL_WIRE = simpleItem("cupronickel_wire");
    public static final DeferredItem<Item> COPPER_COIL = simpleItem("copper_coil");
    public static final DeferredItem<Item> TRANSFORMER_COIL = simpleItem("transformer_coil");
    public static final DeferredItem<Item> STEEL_BEARING = simpleItem("steel_bearing");
    public static final DeferredItem<Item> NICKEL_TURBINE = simpleItem("nickel_turbine");
    public static final DeferredItem<Item> TITANIUM_TURBINE_BLADE = simpleItem("titanium_turbine_blade");
    public static final DeferredItem<Item> HEATING_ELEMENT = simpleItem("heating_element");
    public static final DeferredItem<Item> ELECTRIC_MOTOR = simpleItem("electric_motor");
    public static final DeferredItem<Item> HYDRAULIC_COMPONENT = simpleItem("hydraulic_component");
    public static final DeferredItem<WireCuttersItem> WIRE_CUTTERS = ITEMS.register(
            "wire_cutters",
            () -> new WireCuttersItem(new Item.Properties().durability(341))
    );
    public static final DeferredItem<WrenchItem> WRENCH = ITEMS.register(
            "wrench",
            () -> new WrenchItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<ForgingHammerItem> FORGING_HAMMER = ITEMS.register(
            "forging_hammer",
            () -> new ForgingHammerItem(new Item.Properties().durability(341))
    );
    public static final DeferredItem<SteelTongsItem> STEEL_TONGS = ITEMS.register(
            "steel_tongs",
            () -> new SteelTongsItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<RemoteDetonatorItem> REMOTE_DETONATOR = ITEMS.register(
            "remote_detonator",
            () -> new RemoteDetonatorItem(new Item.Properties())
    );
    public static final DeferredItem<Item> HOT_PLATE_FORGING_STAGE_1 = simpleItem("hot_plate_forging_stage_1");
    public static final DeferredItem<Item> HOT_PLATE_FORGING_STAGE_2 = simpleItem("hot_plate_forging_stage_2");
    public static final DeferredItem<GeigerCounterItem> GEIGER_COUNTER = ITEMS.register(
            "geiger_counter",
            () -> new GeigerCounterItem(new Item.Properties())
    );

    public static final DeferredItem<SwordItem> TITANIUM_SWORD = ITEMS.register(
            "titanium_sword",
            () -> new SwordItem(SkyentToolTier.TITANIUM, new Item.Properties().durability(SkyentToolTier.TITANIUM.getUses()))
    );

    public static final DeferredItem<PickaxeItem> TITANIUM_PICKAXE = ITEMS.register(
            "titanium_pickaxe",
            () -> new PickaxeItem(SkyentToolTier.TITANIUM, new Item.Properties().durability(SkyentToolTier.TITANIUM.getUses()))
    );

    public static final DeferredItem<AxeItem> TITANIUM_AXE = ITEMS.register(
            "titanium_axe",
            () -> new AxeItem(SkyentToolTier.TITANIUM, new Item.Properties().durability(SkyentToolTier.TITANIUM.getUses()))
    );

    public static final DeferredItem<ShovelItem> TITANIUM_SHOVEL = ITEMS.register(
            "titanium_shovel",
            () -> new ShovelItem(SkyentToolTier.TITANIUM, new Item.Properties().durability(SkyentToolTier.TITANIUM.getUses()))
    );

    public static final DeferredItem<HoeItem> TITANIUM_HOE = ITEMS.register(
            "titanium_hoe",
            () -> new HoeItem(SkyentToolTier.TITANIUM, new Item.Properties().durability(SkyentToolTier.TITANIUM.getUses()))
    );

    public static final DeferredItem<SwordItem> TUNGSTEN_SWORD = ITEMS.register(
            "tungsten_sword",
            () -> new SwordItem(SkyentToolTier.TUNGSTEN, new Item.Properties().durability(SkyentToolTier.TUNGSTEN.getUses()))
    );

    public static final DeferredItem<PickaxeItem> TUNGSTEN_PICKAXE = ITEMS.register(
            "tungsten_pickaxe",
            () -> new PickaxeItem(SkyentToolTier.TUNGSTEN, new Item.Properties().durability(SkyentToolTier.TUNGSTEN.getUses()))
    );

    public static final DeferredItem<AxeItem> TUNGSTEN_AXE = ITEMS.register(
            "tungsten_axe",
            () -> new AxeItem(SkyentToolTier.TUNGSTEN, new Item.Properties().durability(SkyentToolTier.TUNGSTEN.getUses()))
    );

    public static final DeferredItem<ShovelItem> TUNGSTEN_SHOVEL = ITEMS.register(
            "tungsten_shovel",
            () -> new ShovelItem(SkyentToolTier.TUNGSTEN, new Item.Properties().durability(SkyentToolTier.TUNGSTEN.getUses()))
    );

    public static final DeferredItem<HoeItem> TUNGSTEN_HOE = ITEMS.register(
            "tungsten_hoe",
            () -> new HoeItem(SkyentToolTier.TUNGSTEN, new Item.Properties().durability(SkyentToolTier.TUNGSTEN.getUses()))
    );

    public static final DeferredItem<LVWireDrumItem> COPPER_WIRE_DRUM = ITEMS.register(
            "copper_wire_drum",
            () -> new LVWireDrumItem(
                    new Item.Properties().stacksTo(64),
                    LVWireType.COPPER,
                    "tooltip.skyent.copper_wire_drum"
            )
    );

    public static final DeferredItem<LVWireDrumItem> STEEL_WIRE_DRUM = ITEMS.register(
            "steel_wire_drum",
            () -> new LVWireDrumItem(
                    new Item.Properties().stacksTo(64),
                    LVWireType.STEEL,
                    "tooltip.skyent.steel_wire_drum"
            )
    );

    public static final DeferredItem<LVWireDrumItem> COBALT_BRONZE_WIRE_DRUM = ITEMS.register(
            "cobalt_bronze_wire_drum",
            () -> new LVWireDrumItem(
                    new Item.Properties().stacksTo(64),
                    LVWireType.COBALT_BRONZE,
                    "tooltip.skyent.cobalt_bronze_wire_drum"
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

    public static final DeferredItem<TooltipBlockItem> LV_MV_TRANSFORMER = ITEMS.register(
            "lv_mv_transformer",
            () -> new TooltipBlockItem(
                    ModBlocks.LV_MV_TRANSFORMER.get(),
                    new Item.Properties(),
                    "tooltip.skyent.lv_mv_transformer.line1",
                    "tooltip.skyent.lv_mv_transformer.line2"
            )
    );

    private ModItems() {
    }

    private static DeferredItem<Item> simpleItem(String name) {
        return ITEMS.registerSimpleItem(name, new Item.Properties());
    }

    private static DeferredItem<RadioactiveBlockItem> radioactiveBlockItem(String name, net.neoforged.neoforge.registries.DeferredBlock<? extends net.minecraft.world.level.block.Block> block) {
        return ITEMS.register(
                name,
                () -> new RadioactiveBlockItem(block.get(), new Item.Properties())
        );
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
