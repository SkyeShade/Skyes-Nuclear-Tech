package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.menu.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(
            BuiltInRegistries.MENU,
            SkyesNuclearTech.MOD_ID
    );

    public static final DeferredHolder<MenuType<?>, MenuType<CombustionGeneratorMenu>> COMBUSTION_GENERATOR =
            MENUS.register("combustion_generator", () -> IMenuTypeExtension.create(CombustionGeneratorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ElectricFurnaceMenu>> ELECTRIC_FURNACE =
            MENUS.register("electric_furnace", () -> IMenuTypeExtension.create(ElectricFurnaceMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<LVCrusherMenu>> LV_CRUSHER =
            MENUS.register("lv_crusher", () -> IMenuTypeExtension.create(LVCrusherMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<BrickBlastFurnaceMenu>> BRICK_BLAST_FURNACE =
            MENUS.register("brick_blast_furnace", () -> IMenuTypeExtension.create(BrickBlastFurnaceMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<LVElectricPumpMenu>> LV_ELECTRIC_PUMP =
            MENUS.register("lv_electric_pump", () -> IMenuTypeExtension.create(LVElectricPumpMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MediumTankMenu>> MEDIUM_TANK =
            MENUS.register("medium_tank", () -> IMenuTypeExtension.create(MediumTankMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<LVSteamTurbineMenu>> LV_STEAM_TURBINE =
            MENUS.register("lv_steam_turbine", () -> IMenuTypeExtension.create(LVSteamTurbineMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MVInlinePumpMenu>> MV_INLINE_PUMP =
            MENUS.register("mv_inline_pump", () -> IMenuTypeExtension.create(MVInlinePumpMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ConveyorExporterMenu>> CONVEYOR_EXPORTER =
            MENUS.register("conveyor_exporter", () -> IMenuTypeExtension.create(ConveyorExporterMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MVAssemblerMenu>> MV_ASSEMBLER =
            MENUS.register("mv_assembler", () -> IMenuTypeExtension.create(MVAssemblerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MVChemicalReactorMenu>> MV_CHEMICAL_REACTOR =
            MENUS.register("mv_chemical_reactor", () -> IMenuTypeExtension.create(MVChemicalReactorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<CentrifugeMenu>> CENTRIFUGE =
            MENUS.register("centrifuge", () -> IMenuTypeExtension.create(CentrifugeMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ArcFurnaceMenu>> ARC_FURNACE =
            MENUS.register("arc_furnace", () -> IMenuTypeExtension.create(ArcFurnaceMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<SteelCrateMenu>> STEEL_CRATE =
            MENUS.register("steel_crate", () -> IMenuTypeExtension.create(SteelCrateMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<StainlessSteelCrateMenu>> STAINLESS_STEEL_CRATE =
            MENUS.register("stainless_steel_crate", () -> IMenuTypeExtension.create(StainlessSteelCrateMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MVAssemblerRecipeSelectMenu>> ASSEMBLER_RECIPE_SELECT =
            MENUS.register("assembler_recipe_select", () -> IMenuTypeExtension.create(MVAssemblerRecipeSelectMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
