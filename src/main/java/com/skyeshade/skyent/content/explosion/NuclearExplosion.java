package com.skyeshade.skyent.content.explosion;

import com.skyeshade.skyent.content.entity.NuclearExplosionEntity;
import com.skyeshade.skyent.content.entity.NuclearExplosionChunkLoading;
import com.skyeshade.skyent.network.NukeDetonationEffectsPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class NuclearExplosion {
    private static final double EFFECT_RANGE = 1024.0D;
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
        UUID chunkLoadingOwnerUuid = UUID.randomUUID();
        NuclearExplosionChunkLoading.NuclearExplosionChunkLease chunkLoadLease = NuclearExplosionChunkLoading.forceImmediateChunks(
                level,
                center,
                NuclearExplosionEntity.DEFAULT_NUKE_RADIUS,
                chunkLoadingOwnerUuid
        );
        NuclearExplosionEntity explosion = new NuclearExplosionEntity(level, center);
        explosion.configure(NuclearExplosionEntity.VANILLA_EXPLOSION_STRENGTH, destroyBlocks, spawnCloud, flashSky, playSounds, source);
        explosion.adoptChunkLoadLease(chunkLoadLease);
        boolean spawned = level.addFreshEntity(explosion);
        if (!spawned) {
            NuclearExplosionChunkLoading.unforceExplosionChunks(level, chunkLoadLease.ownerUuid(), chunkLoadLease.chunks());
        }
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
