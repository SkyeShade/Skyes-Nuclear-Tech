package com.skyeshade.skyent;

import com.mojang.logging.LogUtils;
import com.skyeshade.skyent.config.SkyentClientConfig;
import com.skyeshade.skyent.config.SkyentNuclearExplosionConfig;
import com.skyeshade.skyent.config.SkyentRadiationConfig;
import com.skyeshade.skyent.content.entity.NuclearExplosionChunkLoading;
import com.skyeshade.skyent.event.ClientEvents;
import com.skyeshade.skyent.event.CommonEvents;
import com.skyeshade.skyent.event.ServerEvents;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModCreativeTabs;
import com.skyeshade.skyent.registry.ModEntities;
import com.skyeshade.skyent.registry.ModFeatures;
import com.skyeshade.skyent.registry.ModFluids;
import com.skyeshade.skyent.registry.ModFluidTypes;
import com.skyeshade.skyent.registry.ModItems;
import com.skyeshade.skyent.registry.ModMenus;
import com.skyeshade.skyent.registry.ModParticles;
import com.skyeshade.skyent.registry.ModRecipes;
import com.skyeshade.skyent.registry.ModSounds;
import com.skyeshade.skyent.registry.ModStructures;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(SkyesNuclearTech.MOD_ID)
public final class SkyesNuclearTech {
    public static final String MOD_ID = "skyent";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SkyesNuclearTech(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SkyentNuclearExplosionConfig.SPEC, SkyentNuclearExplosionConfig.FILE_NAME);
        modContainer.registerConfig(ModConfig.Type.COMMON, SkyentRadiationConfig.SPEC, SkyentRadiationConfig.FILE_NAME);
        modContainer.registerConfig(ModConfig.Type.CLIENT, SkyentClientConfig.SPEC, SkyentClientConfig.FILE_NAME);
        modEventBus.addListener(SkyentNuclearExplosionConfig::onConfigLoad);
        modEventBus.addListener(SkyentRadiationConfig::onConfigLoad);
        register(modEventBus);
    }

    private static void register(IEventBus modEventBus) {
        ModFluidTypes.register(modEventBus);
        ModFluids.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModEntities.register(modEventBus);
        ModFeatures.register(modEventBus);
        ModStructures.register(modEventBus);
        ModMenus.register(modEventBus);
        ModSounds.register(modEventBus);
        ModParticles.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        modEventBus.addListener(NuclearExplosionChunkLoading::registerTicketControllers);

        CommonEvents.register(modEventBus);
        ServerEvents.register();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEvents.register(modEventBus);
        }
    }
}
