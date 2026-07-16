package com.skyeshade.skyent.content.explosion;

import com.skyeshade.skyent.SkyesNuclearTech;
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
        explode(level, center, NuclearExplosionEntity.DEFAULT_NUKE_RADIUS, source, destroyBlocks, spawnCloud, flashSky, playSounds);
    }

    public static void explode(
            ServerLevel level,
            Vec3 center,
            float radius,
            @Nullable Entity source,
            boolean destroyBlocks,
            boolean spawnCloud,
            boolean flashSky,
            boolean playSounds
    ) {
        long totalStartNs = NuclearExplosionEntity.detonationTimingNowNs();
        if (NuclearExplosionEntity.isDetonationTimingDebugEnabled()) {
            SkyesNuclearTech.LOGGER.info(
                    "Nuke detonation timing: shared explode entered dimension={} center={} radius={} destroyBlocks={} spawnCloud={} flashSky={} playSounds={} thread={}",
                    level.dimension().location(),
                    center,
                    radius,
                    destroyBlocks,
                    spawnCloud,
                    flashSky,
                    playSounds,
                    Thread.currentThread().getName()
            );
        }

        long ownerStartNs = NuclearExplosionEntity.detonationTimingNowNs();
        UUID chunkLoadingOwnerUuid = UUID.randomUUID();
        NuclearExplosionEntity.logDetonationTimingStep("shared generate chunk loading uuid", ownerStartNs);

        long forceStartNs = NuclearExplosionEntity.detonationTimingNowNs();
        NuclearExplosionChunkLoading.NuclearExplosionChunkLease chunkLoadLease = NuclearExplosionChunkLoading.forceImmediateChunks(
                level,
                center,
                radius,
                chunkLoadingOwnerUuid
        );
        double chunkForceMs = NuclearExplosionEntity.detonationTimingElapsedMs(forceStartNs);

        long createStartNs = NuclearExplosionEntity.detonationTimingNowNs();
        NuclearExplosionEntity explosion = new NuclearExplosionEntity(level, center);
        double entityCreateMs = NuclearExplosionEntity.detonationTimingElapsedMs(createStartNs);
        NuclearExplosionEntity.logDetonationTimingStep(
                "shared create NuclearExplosionEntity",
                createStartNs,
                "dimension=" + level.dimension().location() + " center=" + center
        );

        long configureStartNs = NuclearExplosionEntity.detonationTimingNowNs();
        explosion.configure(NuclearExplosionEntity.VANILLA_EXPLOSION_STRENGTH, radius, destroyBlocks, spawnCloud, flashSky, playSounds, source);
        double configureMs = NuclearExplosionEntity.detonationTimingElapsedMs(configureStartNs);
        NuclearExplosionEntity.logDetonationTimingStep("shared configure NuclearExplosionEntity", configureStartNs);

        long adoptStartNs = NuclearExplosionEntity.detonationTimingNowNs();
        explosion.adoptChunkLoadLease(chunkLoadLease);
        double adoptMs = NuclearExplosionEntity.detonationTimingElapsedMs(adoptStartNs);
        NuclearExplosionEntity.logDetonationTimingStep(
                "shared adopt chunk lease",
                adoptStartNs,
                "forcedChunks=" + chunkLoadLease.chunks().size()
        );

        long preSpawnEndNs = NuclearExplosionEntity.detonationTimingNowNs();
        long spawnStartNs = NuclearExplosionEntity.detonationTimingNowNs();
        boolean spawned = level.addFreshEntity(explosion);
        long spawnEndNs = NuclearExplosionEntity.detonationTimingNowNs();
        double addFreshEntityMs = NuclearExplosionEntity.detonationTimingElapsedMs(spawnStartNs, spawnEndNs);
        NuclearExplosionEntity.logDetonationTimingStep(
                "shared addFreshEntity",
                spawnStartNs,
                "spawned=" + spawned + " entityId=" + explosion.getId()
        );
        if (!spawned) {
            long unforceStartNs = NuclearExplosionEntity.detonationTimingNowNs();
            NuclearExplosionChunkLoading.unforceExplosionChunks(level, chunkLoadLease.ownerUuid(), chunkLoadLease.chunks());
            NuclearExplosionEntity.logDetonationTimingStep("shared unforce chunks after failed spawn", unforceStartNs);
        }
        if (flashSky) {
            long flashStartNs = NuclearExplosionEntity.detonationTimingNowNs();
            sendScreenFlash(level, center);
            NuclearExplosionEntity.logDetonationTimingStep("shared send screen flash payloads", flashStartNs);
        }

        if (NuclearExplosionEntity.isDetonationTimingDebugEnabled()) {
            SkyesNuclearTech.LOGGER.warn(
                    "Nuke detonation timing summary: dimension={} center={} radius={} preAddFreshEntity={}ms throughAddFreshEntity={}ms chunkForce={}ms entityCreate={}ms configure={}ms adoptLease={}ms addFreshEntity={}ms total={}ms spawned={} forcedChunks={}",
                    level.dimension().location(),
                    center,
                    radius,
                    NuclearExplosionEntity.detonationTimingElapsedMs(totalStartNs, preSpawnEndNs),
                    NuclearExplosionEntity.detonationTimingElapsedMs(totalStartNs, spawnEndNs),
                    chunkForceMs,
                    entityCreateMs,
                    configureMs,
                    adoptMs,
                    addFreshEntityMs,
                    NuclearExplosionEntity.detonationTimingElapsedMs(totalStartNs),
                    spawned,
                    chunkLoadLease.chunks().size()
            );
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
