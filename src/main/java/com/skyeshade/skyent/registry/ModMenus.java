package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.menu.BrickBlastFurnaceMenu;
import com.skyeshade.skyent.content.menu.CombustionGeneratorMenu;
import com.skyeshade.skyent.content.menu.ConveyorExporterMenu;
import com.skyeshade.skyent.content.menu.ElectricFurnaceMenu;
import com.skyeshade.skyent.content.menu.LVCrusherMenu;
import com.skyeshade.skyent.content.menu.LVElectricPumpMenu;
import com.skyeshade.skyent.content.menu.LVSteamTurbineMenu;
import com.skyeshade.skyent.content.menu.MediumTankMenu;
import com.skyeshade.skyent.content.menu.MVAssemblerMenu;
import com.skyeshade.skyent.content.menu.MVAssemblerRecipeSelectMenu;
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

    public static final DeferredHolder<MenuType<?>, MenuType<ConveyorExporterMenu>> CONVEYOR_EXPORTER =
            MENUS.register("conveyor_exporter", () -> IMenuTypeExtension.create(ConveyorExporterMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MVAssemblerMenu>> MV_ASSEMBLER =
            MENUS.register("mv_assembler", () -> IMenuTypeExtension.create(MVAssemblerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MVAssemblerRecipeSelectMenu>> ASSEMBLER_RECIPE_SELECT =
            MENUS.register("assembler_recipe_select", () -> IMenuTypeExtension.create(MVAssemblerRecipeSelectMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
