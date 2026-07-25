package com.skyeshade.skyent.event;

import com.skyeshade.skyent.content.radiation.ModDamageSources;
import com.skyeshade.skyent.content.entity.NukePerformanceBudget;
import com.skyeshade.skyent.content.shape.MultiblockShapeRegistry;
import com.skyeshade.skyent.event.systems.BootstrapSystem;
import com.skyeshade.skyent.event.systems.CraftingSoundSystem;
import com.skyeshade.skyent.event.systems.HotItemSystem;
import com.skyeshade.skyent.event.systems.RadiationEntityUpdateScheduler;
import com.skyeshade.skyent.event.systems.RadiationDebugSystem;
import com.skyeshade.skyent.event.systems.RadiationExposureSystem;
import com.skyeshade.skyent.event.systems.RadiationSourceTickSystem;
import com.skyeshade.skyent.event.systems.ToxicitySystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ServerEvents {
    private ServerEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerEvents::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onServerStarting);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onServerStopping);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onItemCrafted);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onLivingIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onServerTickPre);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onChunkUnload);
    }

    public static void onServerStarting(ServerStartingEvent event) {
        BootstrapSystem.onServerStarting(event);
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        RadiationEntityUpdateScheduler.clear();
        RadiationSourceTickSystem.clearActiveSources();
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener((barrier, resourceManager, preparationsProfiler, reloadProfiler, backgroundExecutor, gameExecutor) ->
                java.util.concurrent.CompletableFuture.supplyAsync(() -> resourceManager, backgroundExecutor)
                        .thenCompose(barrier::wait)
                        .thenAcceptAsync(MultiblockShapeRegistry::reload, gameExecutor)
        );
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RadiationDebugSystem.registerCommands(event);
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        RadiationDebugSystem.onPlayerLoggedOut(event);
        RadiationExposureSystem.onPlayerLoggedOut(event);
    }

    public static void onPlayerClone(PlayerEvent.Clone event) {
        RadiationExposureSystem.onPlayerClone(event);
    }

    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        CraftingSoundSystem.onItemCrafted(event);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        NukePerformanceBudget.onServerTickPost(event);
        for (ServerLevel level : event.getServer().getAllLevels()) {
            RadiationSourceTickSystem.tick(level);
        }
        RadiationSourceTickSystem.finishServerTick(event.getServer().getTickCount());

        for (var player : event.getServer().getPlayerList().getPlayers()) {
            RadiationExposureSystem.tickPlayer(player);
            ToxicitySystem.tickLivingEntity(player);
            HotItemSystem.tickLivingEntity(player);
        }
        RadiationEntityUpdateScheduler.processServerTick(event.getServer());
    }

    public static void onServerTickPre(ServerTickEvent.Pre event) {
        NukePerformanceBudget.onServerTickPre(event);
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RadiationSourceTickSystem.discoverSourcesInChunk(level, event.getChunk());
        }
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RadiationSourceTickSystem.deactivateSourcesInChunk(level, event.getChunk().getPos());
        }
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity entity && !(entity instanceof ServerPlayer)) {
            RadiationExposureSystem.tickLivingEntity(entity);
            ToxicitySystem.tickLivingEntity(entity);
            HotItemSystem.tickLivingEntity(entity);
        } else if (event.getEntity() instanceof ItemEntity itemEntity) {
            HotItemSystem.tickItemEntity(itemEntity);
        }
    }

    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!ModDamageSources.isRadiation(event.getSource())) {
            return;
        }

        if (event.getEntity() instanceof Player player && (player.isCreative() || player.isSpectator())) {
            event.setCanceled(true);
        }
    }
}
