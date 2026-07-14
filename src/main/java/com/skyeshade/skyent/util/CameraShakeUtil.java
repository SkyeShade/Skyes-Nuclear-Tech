package com.skyeshade.skyent.util;

import com.skyeshade.skyent.network.CameraShakeS2CPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CameraShakeUtil {
    private CameraShakeUtil() {
    }

    public static void shake(ServerLevel level, Vec3 pos, float strength, int duration, double radius) {
        double radiusSqr = radius * radius;
        for (ServerPlayer player : level.players()) {
            double distanceSqr = player.distanceToSqr(pos);
            if (distanceSqr > radiusSqr) {
                continue;
            }

            double distance = Math.sqrt(distanceSqr);
            float falloff = (float) (1.0D - distance / radius);
            float finalStrength = strength * falloff;
            if (finalStrength <= 0.01F) {
                continue;
            }

            PacketDistributor.sendToPlayer(player, new CameraShakeS2CPacket(finalStrength, duration));
        }
    }
}
