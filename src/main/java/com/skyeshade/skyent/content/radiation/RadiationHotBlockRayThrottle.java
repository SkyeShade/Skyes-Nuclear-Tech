package com.skyeshade.skyent.content.radiation;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.config.SkyentRadiationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class RadiationHotBlockRayThrottle {
    private static final int MAX_DELAYED_POSITIONS = 100_000;
    private static final TickState STATE = new TickState();

    private RadiationHotBlockRayThrottle() {
    }

    public static RequestResult request(ServerLevel level, BlockPos pos) {
        if (!SkyentRadiationConfig.hotBlockRaysEnabled()) {
            return RequestResult.disabledResult();
        }

        TickState state = STATE;
        state.beginTick(level.getGameTime());
        state.requests++;

        DelayedKey posKey = new DelayedKey(level.dimension(), pos.asLong());
        Long delayedUntil = state.delayedUntil.get(posKey);
        if (delayedUntil != null) {
            if (delayedUntil > level.getGameTime()) {
                state.throttled++;
                state.totalDelayAssigned += delayedUntil - level.getGameTime();
                state.maxDelayAssigned = Math.max(state.maxDelayAssigned, (int) (delayedUntil - level.getGameTime()));
                return RequestResult.throttled((int) (delayedUntil - level.getGameTime()));
            }
            state.delayedUntil.remove(posKey);
        }

        int maxEmitters = SkyentRadiationConfig.hotBlockRaysMaxEmittersPerTick();
        if (state.allowed < maxEmitters) {
            state.allowed++;
            return RequestResult.allowedResult();
        }

        int delay = assignDelay(level, pos, state.requests);
        state.delayedUntil.put(posKey, level.getGameTime() + delay);
        state.throttled++;
        state.totalDelayAssigned += delay;
        state.maxDelayAssigned = Math.max(state.maxDelayAssigned, delay);
        state.prune(level.getGameTime());
        return RequestResult.throttled(delay);
    }

    public static void logTickSummary(ServerLevel level) {
        if (!SkyentRadiationConfig.debugHotBlockRayThrottle()) {
            return;
        }

        TickState state = STATE;
        state.beginTick(level.getGameTime());
        if (state.lastLoggedTick == level.getGameTime() || level.getGameTime() % 20L != 0L) {
            return;
        }

        state.lastLoggedTick = level.getGameTime();
        if (state.requests <= 0 && state.delayedUntil.isEmpty()) {
            return;
        }

        double averageDelay = state.throttled <= 0 ? 0.0D : state.totalDelayAssigned / (double) state.throttled;
        SkyesNuclearTech.LOGGER.info(
                "Radiation hot block ray throttle: dimension={} tick={} requests={} allowed={} throttled={} maxEmitters={} softEmitters={} averageDelay={} maxDelay={} delayedPositions={}",
                level.dimension().location(),
                level.getGameTime(),
                state.requests,
                state.allowed,
                state.throttled,
                SkyentRadiationConfig.hotBlockRaysMaxEmittersPerTick(),
                SkyentRadiationConfig.hotBlockRaysSoftEmittersPerTick(),
                averageDelay,
                state.maxDelayAssigned,
                state.delayedUntil.size()
        );
    }

    private static int assignDelay(ServerLevel level, BlockPos pos, int requestsThisTick) {
        int baseDelay = SkyentRadiationConfig.hotBlockRaysBaseExtraDelayTicks();
        int maxExtraDelay = SkyentRadiationConfig.hotBlockRaysMaxExtraDelayTicks();
        int jitterTicks = SkyentRadiationConfig.hotBlockRaysThrottleRandomJitterTicks();
        int softEmitters = SkyentRadiationConfig.hotBlockRaysSoftEmittersPerTick();
        int maxEmitters = SkyentRadiationConfig.hotBlockRaysMaxEmittersPerTick();

        double overload = maxEmitters <= softEmitters
                ? 1.0D
                : Mth.clamp((requestsThisTick - softEmitters) / (double) (maxEmitters - softEmitters), 0.0D, 1.0D);
        int extraDelay = Mth.floor(maxExtraDelay * overload);
        int jitter = jitterTicks <= 0 ? 0 : RandomSource.create(delaySeed(level, pos)).nextInt(jitterTicks + 1);
        return Math.max(1, baseDelay + extraDelay + jitter);
    }

    private static long delaySeed(ServerLevel level, BlockPos pos) {
        long seed = level.getSeed() ^ (level.getGameTime() * 0x9E3779B97F4A7C15L);
        seed ^= pos.asLong() * 0xBF58476D1CE4E5B9L;
        return seed;
    }

    public record RequestResult(boolean allowed, int delayTicks, boolean disabled) {
        private static RequestResult allowedResult() {
            return new RequestResult(true, 0, false);
        }

        private static RequestResult throttled(int delayTicks) {
            return new RequestResult(false, delayTicks, false);
        }

        private static RequestResult disabledResult() {
            return new RequestResult(false, 0, true);
        }
    }

    private record DelayedKey(net.minecraft.resources.ResourceKey<Level> dimension, long pos) {
    }

    private static final class TickState {
        private long tick = Long.MIN_VALUE;
        private long lastLoggedTick = Long.MIN_VALUE;
        private int requests;
        private int allowed;
        private int throttled;
        private long totalDelayAssigned;
        private int maxDelayAssigned;
        private final Map<DelayedKey, Long> delayedUntil = new HashMap<>();

        private void beginTick(long gameTime) {
            if (tick == gameTime) {
                return;
            }

            tick = gameTime;
            requests = 0;
            allowed = 0;
            throttled = 0;
            totalDelayAssigned = 0L;
            maxDelayAssigned = 0;
            prune(gameTime);
        }

        private void prune(long gameTime) {
            if (delayedUntil.size() <= MAX_DELAYED_POSITIONS) {
                return;
            }

            Iterator<Map.Entry<DelayedKey, Long>> iterator = delayedUntil.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getValue() <= gameTime) {
                    iterator.remove();
                }
            }

            if (delayedUntil.size() > MAX_DELAYED_POSITIONS) {
                delayedUntil.clear();
            }
        }
    }
}
