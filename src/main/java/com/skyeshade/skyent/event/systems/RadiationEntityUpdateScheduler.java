package com.skyeshade.skyent.event.systems;

import com.skyeshade.skyent.config.SkyentRadiationConfig;
import java.util.ArrayDeque;
import java.util.HashSet;
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

    private static final ArrayDeque<LivingEntity> QUEUE = new ArrayDeque<>();
    private static final Set<UUID> QUEUED_ENTITY_IDS = new HashSet<>();

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
                continue;
            }

            int elapsedTicks = elapsedTicks(entity);
            RadiationExposureSystem.tickScheduledNonPlayerEntity(entity, elapsedTicks);
            remainingBudget--;
        }
    }

    public static void clear() {
        QUEUE.clear();
        QUEUED_ENTITY_IDS.clear();
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

}
