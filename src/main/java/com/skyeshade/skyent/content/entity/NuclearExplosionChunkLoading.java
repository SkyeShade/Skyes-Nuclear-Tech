package com.skyeshade.skyent.content.entity;

import com.skyeshade.skyent.SkyesNuclearTech;
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
    private static final int NUKE_MIN_CHUNK_RADIUS = 4;
    private static final int NUKE_MAX_CHUNK_RADIUS = 24;
    private static final int NUKE_MAX_FORCED_CHUNKS = 2048;
    private static final double NUKE_CHUNK_RADIUS_MULTIPLIER = 2.5D;
    private static final boolean NUKE_FORCE_TICKING_CHUNKS = true;
    private static final boolean DEBUG_NUKE_CHUNK_LOADING = Boolean.getBoolean("skyent.debugNukeChunkLoading");
    private static final boolean DEBUG_NUKE_DETONATION_TIMING = Boolean.getBoolean("skyent.debugNukeDetonationTiming");
    private static final int DEBUG_NUKE_FORCE_CHUNK_RADIUS_OVERRIDE = Integer.getInteger("skyent.debugNukeForceChunkRadiusOverride", -1);

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
        int radius = Mth.ceil((nukeRadius * NUKE_CHUNK_RADIUS_MULTIPLIER) / 16.0D);
        int clamped = Mth.clamp(radius, NUKE_MIN_CHUNK_RADIUS, NUKE_MAX_CHUNK_RADIUS);
        if (DEBUG_NUKE_FORCE_CHUNK_RADIUS_OVERRIDE >= 0) {
            return Mth.clamp(DEBUG_NUKE_FORCE_CHUNK_RADIUS_OVERRIDE, 0, NUKE_MAX_CHUNK_RADIUS);
        }
        return clamped;
    }

    public static NuclearExplosionChunkLease forceImmediateChunks(ServerLevel level, Vec3 center, double nukeRadius, UUID ownerUuid) {
        long totalStartNs = NuclearExplosionEntity.detonationTimingNowNs();
        Set<ChunkPos> forcedChunks = new HashSet<>();
        long computeStartNs = NuclearExplosionEntity.detonationTimingNowNs();
        ChunkPos centerChunk = new ChunkPos(Mth.floor(center.x) >> 4, Mth.floor(center.z) >> 4);
        int requestedChunkRadius = Mth.ceil((nukeRadius * NUKE_CHUNK_RADIUS_MULTIPLIER) / 16.0D);
        int cappedChunkRadius = Mth.clamp(requestedChunkRadius, NUKE_MIN_CHUNK_RADIUS, NUKE_MAX_CHUNK_RADIUS);
        int chunkRadius = computeChunkRadius(nukeRadius);
        double computeMs = NuclearExplosionEntity.detonationTimingElapsedMs(computeStartNs);
        long forceStartNs = NuclearExplosionEntity.detonationTimingNowNs();
        ForceChunksResult result = forceExplosionChunksDetailed(level, ownerUuid, centerChunk, chunkRadius, forcedChunks);
        double forceLoopMs = NuclearExplosionEntity.detonationTimingElapsedMs(forceStartNs);
        debugDetonationForced(ownerUuid, center, centerChunk, chunkRadius, forcedChunks.size(), result.added());
        logChunkLoadingTiming(
                "forceImmediateChunks",
                totalStartNs,
                "dimension=" + level.dimension().location()
                        + " center=" + center
                        + " centerChunk=" + centerChunk
                        + " nukeRadius=" + nukeRadius
                        + " requestedChunkRadius=" + requestedChunkRadius
                        + " cappedChunkRadius=" + cappedChunkRadius
                        + " chunkRadius=" + chunkRadius
                        + " overrideActive=" + (DEBUG_NUKE_FORCE_CHUNK_RADIUS_OVERRIDE >= 0)
                        + " overrideValue=" + DEBUG_NUKE_FORCE_CHUNK_RADIUS_OVERRIDE
                        + " computeMs=" + computeMs
                        + " chunkListMs=" + result.chunkListMs()
                        + " forceLoopMs=" + forceLoopMs
                        + " chunkCount=" + result.chunkCount()
                        + " safeLoadedBeforeForce=" + result.loadedBeforeForce()
                        + " notLoadedBeforeForce=" + (result.chunkCount() - result.loadedBeforeForce())
                        + " attempted=" + result.attempted()
                        + " capped=" + result.capped()
                        + " slowestForceMs=" + result.slowestForceMs()
                        + " forceChunkMayBeBlocking=" + (result.slowestForceMs() > 50.0D || forceLoopMs > 50.0D)
                        + " forcedChunks=" + forcedChunks.size()
                        + " added=" + result.added()
                        + " thread=" + Thread.currentThread().getName()
        );
        return new NuclearExplosionChunkLease(ownerUuid, forcedChunks);
    }

    public static NuclearExplosionChunkLease forceTemporaryDetonationChunk(ServerLevel level, ChunkPos chunk, UUID ownerUuid) {
        long totalStartNs = NuclearExplosionEntity.detonationTimingNowNs();
        Set<ChunkPos> forcedChunks = new HashSet<>();
        long slowestForceNs = 0L;
        if (forcedChunks.add(chunk)) {
            long chunkStartNs = NuclearExplosionEntity.detonationTimingNowNs();
            NUKE_TICKET_CONTROLLER.forceChunk(
                    level,
                    ownerUuid,
                    chunk.x,
                    chunk.z,
                    true,
                    NUKE_FORCE_TICKING_CHUNKS
            );
            slowestForceNs = Math.max(slowestForceNs, NuclearExplosionEntity.detonationTimingNowNs() - chunkStartNs);
        }
        debugTemporaryDetonationChunkForced(ownerUuid, chunk);
        logChunkLoadingTiming(
                "forceTemporaryDetonationChunk",
                totalStartNs,
                "dimension=" + level.dimension().location()
                        + " chunk=" + chunk
                        + " forcedChunks=" + forcedChunks.size()
                        + " slowestForceMs=" + NuclearExplosionEntity.detonationTimingElapsedMs(0L, slowestForceNs)
        );
        return new NuclearExplosionChunkLease(ownerUuid, forcedChunks);
    }

    public static int forceExplosionChunks(ServerLevel level, UUID ownerUuid, ChunkPos center, int chunkRadius, Set<ChunkPos> forcedChunks) {
        return forceExplosionChunksDetailed(level, ownerUuid, center, chunkRadius, forcedChunks).added();
    }

    private static ForceChunksResult forceExplosionChunksDetailed(ServerLevel level, UUID ownerUuid, ChunkPos center, int chunkRadius, Set<ChunkPos> forcedChunks) {
        long totalStartNs = NuclearExplosionEntity.detonationTimingNowNs();
        int added = 0;
        boolean capped = false;
        long slowestForceNs = 0L;
        long listStartNs = NuclearExplosionEntity.detonationTimingNowNs();
        List<ChunkPos> chunks = buildChunkList(center, chunkRadius);
        double chunkListMs = NuclearExplosionEntity.detonationTimingElapsedMs(listStartNs);
        int loadedBeforeForce = countLoadedChunks(level, chunks);
        int attempted = 0;
        long forceLoopStartNs = NuclearExplosionEntity.detonationTimingNowNs();
        for (ChunkPos chunk : chunks) {
            if (forcedChunks.size() >= NUKE_MAX_FORCED_CHUNKS) {
                capped = true;
                logForceLimit(center, chunkRadius, forcedChunks.size());
                break;
            }
            attempted++;
            if (forcedChunks.add(chunk)) {
                long chunkStartNs = NuclearExplosionEntity.detonationTimingNowNs();
                NUKE_TICKET_CONTROLLER.forceChunk(
                        level,
                        ownerUuid,
                        chunk.x,
                        chunk.z,
                        true,
                        NUKE_FORCE_TICKING_CHUNKS
                );
                slowestForceNs = Math.max(slowestForceNs, NuclearExplosionEntity.detonationTimingNowNs() - chunkStartNs);
                added++;
            }
        }
        double forceLoopMs = NuclearExplosionEntity.detonationTimingElapsedMs(forceLoopStartNs);
        ForceChunksResult result = new ForceChunksResult(
                chunks.size(),
                attempted,
                added,
                forcedChunks.size(),
                loadedBeforeForce,
                capped,
                chunkListMs,
                forceLoopMs,
                NuclearExplosionEntity.detonationTimingElapsedMs(0L, slowestForceNs)
        );
        logChunkLoadingTiming(
                "forceExplosionChunks",
                totalStartNs,
                "dimension=" + level.dimension().location()
                        + " centerChunk=" + center
                        + " radius=" + chunkRadius
                        + " chunkCount=" + result.chunkCount()
                        + " attempted=" + result.attempted()
                        + " added=" + result.added()
                        + " totalForced=" + result.totalForced()
                        + " safeLoadedBeforeForce=" + result.loadedBeforeForce()
                        + " notLoadedBeforeForce=" + (result.chunkCount() - result.loadedBeforeForce())
                        + " capped=" + result.capped()
                        + " chunkListMs=" + result.chunkListMs()
                        + " forceLoopMs=" + result.forceLoopMs()
                        + " slowestForceMs=" + result.slowestForceMs()
                        + " forceChunkMayBeBlocking=" + (result.slowestForceMs() > 50.0D || result.forceLoopMs() > 50.0D)
                        + " thread=" + Thread.currentThread().getName()
        );
        return result;
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

    private static int countLoadedChunks(ServerLevel level, List<ChunkPos> chunks) {
        int loaded = 0;
        for (ChunkPos chunk : chunks) {
            if (level.hasChunk(chunk.x, chunk.z)) {
                loaded++;
            }
        }
        return loaded;
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
                    NUKE_FORCE_TICKING_CHUNKS
            )) {
                removed++;
            }
        }
        forcedChunks.clear();
        return removed;
    }

    public static boolean isDebugEnabled() {
        return DEBUG_NUKE_CHUNK_LOADING;
    }

    private static void logChunkLoadingTiming(String label, long startNs, String details) {
        if (!DEBUG_NUKE_DETONATION_TIMING && !DEBUG_NUKE_CHUNK_LOADING) {
            return;
        }
        double elapsedMs = NuclearExplosionEntity.detonationTimingElapsedMs(startNs);
        if (elapsedMs > 50.0D) {
            SkyesNuclearTech.LOGGER.warn(
                    "Nuke chunk loading timing: {} took {} ms {}",
                    label,
                    elapsedMs,
                    details
            );
        } else {
            SkyesNuclearTech.LOGGER.info(
                    "Nuke chunk loading timing: {} took {} ms {}",
                    label,
                    elapsedMs,
                    details
            );
        }
    }

    public static void debugDetonationForced(UUID explosionUuid, Vec3 center, ChunkPos centerChunk, int chunkRadius, int forcedCount, int addedCount) {
        if (!DEBUG_NUKE_CHUNK_LOADING) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke chunk loading detonation-time force: uuid={} center={} centerChunk={} radius={} totalForced={} added={}",
                explosionUuid,
                center,
                centerChunk,
                chunkRadius,
                forcedCount,
                addedCount
        );
    }

    public static void debugAdopted(UUID explosionUuid, int entityId, int forcedCount) {
        if (!DEBUG_NUKE_CHUNK_LOADING) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke chunk loading adopted by entity: entityId={} uuid={} forcedCount={}",
                entityId,
                explosionUuid,
                forcedCount
        );
    }

    public static void debugFallbackForce(UUID explosionUuid, int entityId) {
        if (!DEBUG_NUKE_CHUNK_LOADING) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke chunk loading fallback first-tick force: entityId={} uuid={}",
                entityId,
                explosionUuid
        );
    }

    public static void debugAlreadyForced(UUID explosionUuid, int entityId, int forcedCount) {
        if (!DEBUG_NUKE_CHUNK_LOADING) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke chunks already forced before first tick: entityId={} uuid={} forcedCount={}",
                entityId,
                explosionUuid,
                forcedCount
        );
    }

    public static void debugForced(UUID explosionUuid, int entityId, ChunkPos center, int chunkRadius, int forcedCount, int addedCount) {
        if (!DEBUG_NUKE_CHUNK_LOADING) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke chunk loading forced: entityId={} uuid={} centerChunk={} radius={} totalForced={} added={}",
                entityId,
                explosionUuid,
                center,
                chunkRadius,
                forcedCount,
                addedCount
        );
    }

    public static void debugUnforced(UUID explosionUuid, int entityId, int releasedCount) {
        if (!DEBUG_NUKE_CHUNK_LOADING) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke chunk loading released: entityId={} uuid={} released={}",
                entityId,
                explosionUuid,
                releasedCount
        );
    }

    public static void debugTemporaryDetonationChunkForced(UUID ownerUuid, ChunkPos chunk) {
        if (!DEBUG_NUKE_CHUNK_LOADING) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke detonation target chunk forced: uuid={} chunk={}",
                ownerUuid,
                chunk
        );
    }

    public static void debugTemporaryDetonationChunkReleased(UUID ownerUuid, int releasedCount, String reason) {
        if (!DEBUG_NUKE_CHUNK_LOADING) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke detonation target chunk released: uuid={} released={} reason={}",
                ownerUuid,
                releasedCount,
                reason
        );
    }

    public static void debugRemoteDetonationTarget(UUID ownerUuid, ChunkPos chunk, boolean validCharge) {
        if (!DEBUG_NUKE_CHUNK_LOADING) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke remote detonation target checked: uuid={} chunk={} validCharge={}",
                ownerUuid,
                chunk,
                validCharge
        );
    }

    private static void logForceLimit(ChunkPos center, int chunkRadius, int forcedCount) {
        if (!DEBUG_NUKE_CHUNK_LOADING) {
            return;
        }

        SkyesNuclearTech.LOGGER.warn(
                "Nuke chunk loading cap hit: centerChunk={} requestedRadius={} forcedCount={} maxForced={}",
                center,
                chunkRadius,
                forcedCount,
                NUKE_MAX_FORCED_CHUNKS
        );
    }

    public record NuclearExplosionChunkLease(UUID ownerUuid, Set<ChunkPos> chunks) {
    }

    private record ForceChunksResult(
            int chunkCount,
            int attempted,
            int added,
            int totalForced,
            int loadedBeforeForce,
            boolean capped,
            double chunkListMs,
            double forceLoopMs,
            double slowestForceMs
    ) {
    }
}
