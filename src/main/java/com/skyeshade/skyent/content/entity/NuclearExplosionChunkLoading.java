package com.skyeshade.skyent.content.entity;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.config.SkyentNuclearExplosionConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class NuclearExplosionChunkLoading {
    public static final TicketController NUKE_TICKET_CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "nuclear_explosions"),
            (level, ticketHelper) -> ticketHelper.getEntityTickets().keySet().forEach(ticketHelper::removeAllTickets)
    );

    private NuclearExplosionChunkLoading() {
    }

    public static void registerTicketControllers(RegisterTicketControllersEvent event) {
        event.register(NUKE_TICKET_CONTROLLER);
    }

    public static int computeChunkRadius(double nukeRadius) {
        return computeChunkRadiusDetails(nukeRadius);
    }

    public static NuclearExplosionChunkLease forceImmediateChunks(ServerLevel level, Vec3 center, double nukeRadius, UUID ownerUuid) {
        if (!SkyentNuclearExplosionConfig.chunkLoadingEnabled()) {
            return new NuclearExplosionChunkLease(ownerUuid, Set.of());
        }
        Set<ChunkPos> forcedChunks = new HashSet<>();
        ChunkPos centerChunk = new ChunkPos(Mth.floor(center.x) >> 4, Mth.floor(center.z) >> 4);
        forceExplosionChunks(level, ownerUuid, centerChunk, computeChunkRadius(nukeRadius), forcedChunks);
        return new NuclearExplosionChunkLease(ownerUuid, forcedChunks);
    }

    private static int computeChunkRadiusDetails(double nukeRadius) {
        if (!SkyentNuclearExplosionConfig.chunkLoadingEnabled()) {
            return 0;
        }
        double maxRadiusSource = SkyentNuclearExplosionConfig.chunkLoadingMaxRadiusForImmediateChunkLoading();
        double radiusSourceUsed = Math.min(nukeRadius, maxRadiusSource);
        int requestedChunkRadius = Mth.ceil((radiusSourceUsed * SkyentNuclearExplosionConfig.chunkLoadingImmediateRadiusMultiplier()) / 16.0D)
                + SkyentNuclearExplosionConfig.chunkLoadingImmediateExtraChunks();
        int minRadius = SkyentNuclearExplosionConfig.chunkLoadingImmediateMinChunkRadius();
        int maxRadius = Math.max(minRadius, SkyentNuclearExplosionConfig.chunkLoadingImmediateMaxChunkRadius());
        return Mth.clamp(requestedChunkRadius, minRadius, maxRadius);
    }

    public static NuclearExplosionChunkLease forceTemporaryDetonationChunk(ServerLevel level, ChunkPos chunk, UUID ownerUuid) {
        Set<ChunkPos> forcedChunks = new HashSet<>();
        if (forcedChunks.add(chunk)) {
            NUKE_TICKET_CONTROLLER.forceChunk(
                    level,
                    ownerUuid,
                    chunk.x,
                    chunk.z,
                    true,
                    SkyentNuclearExplosionConfig.chunkLoadingTickingTickets()
            );
        }
        return new NuclearExplosionChunkLease(ownerUuid, forcedChunks);
    }

    public static int forceExplosionChunks(ServerLevel level, UUID ownerUuid, ChunkPos center, int chunkRadius, Set<ChunkPos> forcedChunks) {
        if (!SkyentNuclearExplosionConfig.chunkLoadingEnabled() || chunkRadius <= 0) {
            return 0;
        }
        return forceExplosionChunksDetailed(level, ownerUuid, center, chunkRadius, forcedChunks).added();
    }

    public static boolean forceSingleChunk(ServerLevel level, UUID ownerUuid, ChunkPos chunk, boolean ticking) {
        return NUKE_TICKET_CONTROLLER.forceChunk(
                level,
                ownerUuid,
                chunk.x,
                chunk.z,
                true,
                ticking
        );
    }

    public static boolean unforceSingleChunk(ServerLevel level, UUID ownerUuid, ChunkPos chunk, boolean ticking) {
        return NUKE_TICKET_CONTROLLER.forceChunk(
                level,
                ownerUuid,
                chunk.x,
                chunk.z,
                false,
                ticking
        );
    }

    private static ForceChunksResult forceExplosionChunksDetailed(ServerLevel level, UUID ownerUuid, ChunkPos center, int chunkRadius, Set<ChunkPos> forcedChunks) {
        int added = 0;
        List<ChunkPos> chunks = buildChunkList(center, chunkRadius);
        int maxForcedChunks = NukePerformanceBudget.scaleInt(
                SkyentNuclearExplosionConfig.chunkLoadingMaxForcedChunks(),
                1,
                level.getServer()
        );
        for (ChunkPos chunk : chunks) {
            if (forcedChunks.size() >= maxForcedChunks) {
                break;
            }
            if (forcedChunks.add(chunk)) {
                NUKE_TICKET_CONTROLLER.forceChunk(
                        level,
                        ownerUuid,
                        chunk.x,
                        chunk.z,
                        true,
                        SkyentNuclearExplosionConfig.chunkLoadingTickingTickets()
                );
                added++;
            }
        }
        return new ForceChunksResult(added);
    }

    private static List<ChunkPos> buildChunkList(ChunkPos center, int chunkRadius) {
        List<ChunkPos> chunks = new ArrayList<>();
        int radiusSqr = chunkRadius * chunkRadius;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                if (dx * dx + dz * dz > radiusSqr) {
                    continue;
                }
                chunks.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }
        return chunks;
    }

    public static int unforceExplosionChunks(ServerLevel level, UUID ownerUuid, Set<ChunkPos> forcedChunks) {
        int removed = 0;
        for (ChunkPos chunk : forcedChunks) {
            if (NUKE_TICKET_CONTROLLER.forceChunk(
                   level,
                   ownerUuid,
                   chunk.x,
                   chunk.z,
                   false,
                    SkyentNuclearExplosionConfig.chunkLoadingTickingTickets()
            )) {
                removed++;
            }
        }
        forcedChunks.clear();
        return removed;
    }

    public record NuclearExplosionChunkLease(UUID ownerUuid, Set<ChunkPos> chunks) {
    }

    private record ForceChunksResult(int added) {
    }
}
