package com.skyeshade.skyent.content.entity;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class NuclearExplosionChunkLoading {
    private static final int NUKE_MIN_CHUNK_RADIUS = 4;
    private static final int NUKE_MAX_CHUNK_RADIUS = 24;
    private static final int NUKE_MAX_FORCED_CHUNKS = 2048;
    private static final double NUKE_CHUNK_RADIUS_MULTIPLIER = 2.5D;
    private static final boolean NUKE_FORCE_TICKING_CHUNKS = true;
    private static final boolean DEBUG_NUKE_CHUNK_LOADING = Boolean.getBoolean("skyent.debugNukeChunkLoading");

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
        return Mth.clamp(radius, NUKE_MIN_CHUNK_RADIUS, NUKE_MAX_CHUNK_RADIUS);
    }

    public static NuclearExplosionChunkLease forceImmediateChunks(ServerLevel level, Vec3 center, double nukeRadius, UUID ownerUuid) {
        Set<ChunkPos> forcedChunks = new HashSet<>();
        ChunkPos centerChunk = new ChunkPos(Mth.floor(center.x) >> 4, Mth.floor(center.z) >> 4);
        int chunkRadius = computeChunkRadius(nukeRadius);
        int added = forceExplosionChunks(level, ownerUuid, centerChunk, chunkRadius, forcedChunks);
        debugDetonationForced(ownerUuid, center, centerChunk, chunkRadius, forcedChunks.size(), added);
        return new NuclearExplosionChunkLease(ownerUuid, forcedChunks);
    }

    public static int forceExplosionChunks(ServerLevel level, UUID ownerUuid, ChunkPos center, int chunkRadius, Set<ChunkPos> forcedChunks) {
        int added = 0;
        int radiusSqr = chunkRadius * chunkRadius;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                if (dx * dx + dz * dz > radiusSqr) {
                    continue;
                }
                if (forcedChunks.size() >= NUKE_MAX_FORCED_CHUNKS) {
                    logForceLimit(center, chunkRadius, forcedChunks.size());
                    return added;
                }

                ChunkPos chunk = new ChunkPos(center.x + dx, center.z + dz);
                if (forcedChunks.add(chunk)) {
                    NUKE_TICKET_CONTROLLER.forceChunk(
                            level,
                            ownerUuid,
                            chunk.x,
                            chunk.z,
                            true,
                            NUKE_FORCE_TICKING_CHUNKS
                    );
                    added++;
                }
            }
        }
        return added;
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
}
