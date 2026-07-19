package com.skyeshade.skyent.event.systems;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.config.SkyentRadiationConfig;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class RadiationEntityUpdateScheduler {
    private static final int MAX_NON_PLAYER_RADIATION_UPDATES_PER_TICK = 5;
    private static final int NON_PLAYER_RADIATION_MIN_UPDATE_INTERVAL_TICKS = 20;
    private static final int NON_PLAYER_RADIATION_MAX_UPDATE_INTERVAL_TICKS = 20 * 10;
    private static final int DEBUG_LOG_INTERVAL_TICKS = 100;
    private static final boolean DEBUG = Boolean.getBoolean("skyent.debugRadiationEntityScheduler");

    private static final ArrayDeque<LivingEntity> QUEUE = new ArrayDeque<>();
    private static final Set<UUID> QUEUED_ENTITY_IDS = new HashSet<>();

    private static int windowProcessed;
    private static int windowDropped;
    private static long windowElapsedTicks;
    private static int windowMaxElapsedTicks;
    private static final Map<String, Integer> windowProcessedByDimension = new HashMap<>();

    private RadiationEntityUpdateScheduler() {
    }

    public static void enqueueIfDue(LivingEntity entity) {
        if (entity instanceof ServerPlayer || !isSchedulable(entity)) {
            return;
        }

        ServerLevel level = (ServerLevel) entity.level();
        long gameTime = level.getGameTime();
        int phase = Math.floorMod(entity.getUUID().hashCode(), NON_PLAYER_RADIATION_MIN_UPDATE_INTERVAL_TICKS);
        if (Math.floorMod(gameTime, NON_PLAYER_RADIATION_MIN_UPDATE_INTERVAL_TICKS) != phase) {
            return;
        }

        long lastUpdateTick = RadiationExposureSystem.lastExposureUpdateTick(entity);
        int updateInterval = effectiveUpdateIntervalTicks();
        if (lastUpdateTick != 0L && gameTime - lastUpdateTick < updateInterval) {
            return;
        }
        if (lastUpdateTick == 0L && gameTime < updateInterval) {
            return;
        }

        UUID entityId = entity.getUUID();
        if (QUEUED_ENTITY_IDS.add(entityId)) {
            QUEUE.addLast(entity);
        }
    }

    public static void processServerTick(MinecraftServer server) {
        int remainingBudget = MAX_NON_PLAYER_RADIATION_UPDATES_PER_TICK;
        while (remainingBudget > 0 && !QUEUE.isEmpty()) {
            LivingEntity entity = QUEUE.removeFirst();
            QUEUED_ENTITY_IDS.remove(entity.getUUID());

            if (!isSchedulable(entity)) {
                recordDropped();
                continue;
            }

            int elapsedTicks = elapsedTicks(entity);
            RadiationExposureSystem.tickScheduledNonPlayerEntity(entity, elapsedTicks);
            recordProcessed(entity, elapsedTicks);
            remainingBudget--;
        }

        if (DEBUG && server.getTickCount() % DEBUG_LOG_INTERVAL_TICKS == 0) {
            logDebugSummary(server);
        }
    }

    public static void clear() {
        QUEUE.clear();
        QUEUED_ENTITY_IDS.clear();
        windowProcessed = 0;
        windowDropped = 0;
        windowElapsedTicks = 0L;
        windowMaxElapsedTicks = 0;
        windowProcessedByDimension.clear();
    }

    private static boolean isSchedulable(LivingEntity entity) {
        return entity.isAlive()
                && !entity.isRemoved()
                && entity.level() instanceof ServerLevel
                && !(entity instanceof ServerPlayer);
    }

    private static int elapsedTicks(LivingEntity entity) {
        ServerLevel level = (ServerLevel) entity.level();
        long gameTime = level.getGameTime();
        long lastUpdateTick = RadiationExposureSystem.lastExposureUpdateTick(entity);
        long elapsedTicks = lastUpdateTick == 0L ? effectiveUpdateIntervalTicks() : Math.max(1L, gameTime - lastUpdateTick);
        return Mth.clamp((int) Math.min(Integer.MAX_VALUE, elapsedTicks), 1, NON_PLAYER_RADIATION_MAX_UPDATE_INTERVAL_TICKS);
    }

    private static int effectiveUpdateIntervalTicks() {
        return Math.max(NON_PLAYER_RADIATION_MIN_UPDATE_INTERVAL_TICKS, SkyentRadiationConfig.exposureEntityUpdateIntervalTicks());
    }

    private static void recordProcessed(LivingEntity entity, int elapsedTicks) {
        windowProcessed++;
        windowElapsedTicks += elapsedTicks;
        windowMaxElapsedTicks = Math.max(windowMaxElapsedTicks, elapsedTicks);
        String dimension = ((ServerLevel) entity.level()).dimension().location().toString();
        windowProcessedByDimension.merge(dimension, 1, Integer::sum);
    }

    private static void recordDropped() {
        windowDropped++;
    }

    private static void logDebugSummary(MinecraftServer server) {
        double averageElapsedTicks = windowProcessed == 0 ? 0.0D : windowElapsedTicks / (double) windowProcessed;
        SkyesNuclearTech.LOGGER.info(
                "Radiation entity scheduler: tick={} queued={} processedWindow={} droppedWindow={} averageElapsedTicks={} maxElapsedTicks={} budgetPerTick={} perDimension={}",
                server.getTickCount(),
                QUEUE.size(),
                windowProcessed,
                windowDropped,
                String.format("%.2f", averageElapsedTicks),
                windowMaxElapsedTicks,
                MAX_NON_PLAYER_RADIATION_UPDATES_PER_TICK,
                windowProcessedByDimension
        );
        windowProcessed = 0;
        windowDropped = 0;
        windowElapsedTicks = 0L;
        windowMaxElapsedTicks = 0;
        windowProcessedByDimension.clear();
    }
}
