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
                    .displayItems((parameters, output) -> output.accept(ModItems.COMBUSTION_GENERATOR.get()))
                    .build()
    );

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}
