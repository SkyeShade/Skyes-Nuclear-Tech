package com.skyeshade.skyent.content.explosion;

import com.skyeshade.skyent.content.entity.NuclearExplosionEntity;
import com.skyeshade.skyent.network.NukeDetonationEffectsPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public final class NuclearExplosion {
    private static final double EFFECT_RANGE = 256.0D;
    private static final double EFFECT_RANGE_SQR = EFFECT_RANGE * EFFECT_RANGE;

    private NuclearExplosion() {
    }

    public static void explode(
            ServerLevel level,
            Vec3 center,
            @Nullable Entity source,
            boolean destroyBlocks,
            boolean spawnCloud,
            boolean flashSky,
            boolean playSounds
    ) {
        NuclearExplosionEntity explosion = new NuclearExplosionEntity(level, center);
        explosion.configure(NuclearExplosionEntity.VANILLA_EXPLOSION_STRENGTH, destroyBlocks, spawnCloud, flashSky, playSounds, source);
        level.addFreshEntity(explosion);
        if (flashSky) {
            sendScreenFlash(level, center);
        }
    }

    private static void sendScreenFlash(ServerLevel level, Vec3 center) {
        NukeDetonationEffectsPayload payload = new NukeDetonationEffectsPayload(
                center.x,
                center.y,
                center.z,
                false,
                true,
                false,
                level.random.nextLong()
        );
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= EFFECT_RANGE_SQR) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
