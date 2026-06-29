package com.skyeshade.skyent.content.radiation;

import com.skyeshade.skyent.network.RadiationRayBatchPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RadiationDebugRays {
    private static final double SEND_RADIUS = 64.0D;
    private static final double SEND_RADIUS_SQUARED = SEND_RADIUS * SEND_RADIUS;
    private static final Set<UUID> ENABLED_PLAYERS = ConcurrentHashMap.newKeySet();

    private RadiationDebugRays() {
    }

    public static void setEnabled(ServerPlayer player, boolean enabled) {
        if (enabled) {
            ENABLED_PLAYERS.add(player.getUUID());
        } else {
            ENABLED_PLAYERS.remove(player.getUUID());
        }
    }

    public static void remove(ServerPlayer player) {
        ENABLED_PLAYERS.remove(player.getUUID());
    }

    public static boolean hasNearbyEnabledPlayers(ServerLevel level, Vec3 sourceCenter) {
        if (ENABLED_PLAYERS.isEmpty()) {
            return false;
        }

        for (ServerPlayer player : level.players()) {
            if (ENABLED_PLAYERS.contains(player.getUUID()) && player.position().distanceToSqr(sourceCenter) <= SEND_RADIUS_SQUARED) {
                return true;
            }
        }

        return false;
    }

    public static void send(ServerLevel level, Vec3 sourceCenter, List<RadiationRayBatchPayload.Ray> rays) {
        if (rays.isEmpty() || ENABLED_PLAYERS.isEmpty()) {
            return;
        }

        RadiationRayBatchPayload payload = new RadiationRayBatchPayload(rays);
        for (ServerPlayer player : level.players()) {
            if (ENABLED_PLAYERS.contains(player.getUUID()) && player.position().distanceToSqr(sourceCenter) <= SEND_RADIUS_SQUARED) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
