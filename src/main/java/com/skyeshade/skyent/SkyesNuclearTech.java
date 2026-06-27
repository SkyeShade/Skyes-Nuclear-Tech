package com.skyeshade.skyent;

import com.mojang.logging.LogUtils;
import com.skyeshade.skyent.event.ClientEvents;
import com.skyeshade.skyent.event.CommonEvents;
import com.skyeshade.skyent.event.ServerEvents;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModCreativeTabs;
import com.skyeshade.skyent.registry.ModItems;
import com.skyeshade.skyent.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(SkyesNuclearTech.MOD_ID)
public final class SkyesNuclearTech {
    public static final String MOD_ID = "skyent";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SkyesNuclearTech(IEventBus modEventBus) {
        register(modEventBus);
    }

    private static void register(IEventBus modEventBus) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        CommonEvents.register(modEventBus);
        ServerEvents.register();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEvents.register(modEventBus);
        }
    }
}
