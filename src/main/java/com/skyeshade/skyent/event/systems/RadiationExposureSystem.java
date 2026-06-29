package com.skyeshade.skyent.event.systems;

import com.skyeshade.skyent.content.radiation.RadiationExposureData;
import com.skyeshade.skyent.content.radiation.RadiationExposureUtil;
import com.skyeshade.skyent.network.GeigerExposurePayload;
import com.skyeshade.skyent.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RadiationExposureSystem {
    public static final int PLAYER_EXPOSURE_UPDATE_INTERVAL_TICKS = 5;
    public static final boolean DEBUG_GEIGER_ACTIONBAR = true;

    private static final Map<UUID, RadiationExposureData> PLAYER_DATA = new HashMap<>();

    private RadiationExposureSystem() {
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        long gameTime = level.getGameTime();
        RadiationExposureData data = PLAYER_DATA.computeIfAbsent(player.getUUID(), ignored -> new RadiationExposureData());
        if (gameTime - data.getLastExposureUpdateTick() < PLAYER_EXPOSURE_UPDATE_INTERVAL_TICKS) {
            return;
        }

        Vec3 samplePos = player.getEyePosition();
        RadiationExposureUtil.ExposureScanResult scan = RadiationExposureUtil.scanEnvironmentalExposure(
                level,
                samplePos,
                RadiationExposureUtil.DEFAULT_PLAYER_SCAN_RADIUS
        );
        double exposure = scan.exposureMillisievertsPerSecond();
        data.setCurrentEnvironmentalExposureMillisievertsPerSecond(exposure);
        data.setLastExposureUpdateTick(gameTime);
        PacketDistributor.sendToPlayer(player, new GeigerExposurePayload(exposure));

        if (DEBUG_GEIGER_ACTIONBAR && gameTime % 20 == 0 && isHoldingGeigerCounter(player)) {
            player.displayClientMessage(formatDebugActionbar(scan), true);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_DATA.remove(event.getEntity().getUUID());
    }

    private static boolean isHoldingGeigerCounter(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.GEIGER_COUNTER.get())
                || player.getOffhandItem().is(ModItems.GEIGER_COUNTER.get());
    }

    private static Component formatDebugActionbar(RadiationExposureUtil.ExposureScanResult scan) {
        String nearest = Double.isNaN(scan.nearestSourceDistance()) ? "--" : String.format("%.1fm", scan.nearestSourceDistance());
        return Component.literal(String.format(
                "Rad: %.1f mSv/s | sources %d/%d | nearest %s | strongest %.1f | registry %d/%d",
                scan.exposureMillisievertsPerSecond(),
                scan.contributingSources(),
                scan.sourcesFound(),
                nearest,
                scan.strongestSourceContribution(),
                scan.registryCandidates(),
                scan.registeredSources()
        ));
    }
}
