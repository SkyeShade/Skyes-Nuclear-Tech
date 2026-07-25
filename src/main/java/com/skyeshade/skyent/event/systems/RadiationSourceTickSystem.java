package com.skyeshade.skyent.event.systems;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.MoltenCoriumBlock;
import com.skyeshade.skyent.content.radiation.EnvironmentalRadiationRayProfile;
import com.skyeshade.skyent.content.radiation.RadioactiveSource;
import com.skyeshade.skyent.content.radiation.RadioactiveSourceRegistry;
import com.skyeshade.skyent.content.radiation.RadiationBlockProfiles;
import com.skyeshade.skyent.content.radiation.RadiationUtil;
import com.skyeshade.skyent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

public final class RadiationSourceTickSystem {
    private static final long TARGET_RAY_BUDGET_NS_PER_TICK = 500_000L;
    private static final long HARD_RAY_BUDGET_NS_PER_TICK = 850_000L;
    private static final long EMERGENCY_RAY_BUDGET_NS_PER_TICK = 2_000_000L;
    private static final int MAX_SOURCE_VALIDATIONS_PER_TICK = 2_048;
    private static final int MIN_STALE_REACTIVATION_WINDOW_TICKS = 40;
    private static final int REACTIVATION_JITTER_TICKS = 40;
    private static final int DUE_SAMPLE_LIMIT = 512;
    private static final double MIN_THROTTLE_MULTIPLIER = 1.0D;
    private static final double MAX_THROTTLE_MULTIPLIER = 512.0D;
    private static final int PERFORMANCE_LOG_INTERVAL_TICKS = 20;

    private static final Map<ResourceKey<Level>, DimensionState> STATES_BY_DIMENSION = new HashMap<>();
    private static double throttleMultiplier = MIN_THROTTLE_MULTIPLIER;
    private static int currentServerTick = Integer.MIN_VALUE;
    private static boolean currentTickRecorded;
    private static long currentTickRayNs;
    private static int currentTickSourcesProcessed;
    private static int currentTickDueLeft;
    private static int currentTickRays;
    private static int currentTickRegisteredSources;
    private static int currentTickActiveSources;
    private static int currentTickQueueSize;
    private static int lastPerformanceLogTick = Integer.MIN_VALUE;
    private static long secondTotalRayNs;
    private static long secondMaxRayNs;
    private static int secondTicks;
    private static int secondSourcesProcessed;
    private static int secondDueLeft;
    private static int secondRays;
    private static int secondRegisteredSources;
    private static int secondActiveSources;
    private static int secondQueueSize;

    private RadiationSourceTickSystem() {
    }

    public static void tick(ServerLevel level) {
        beginServerTick(level.getServer().getTickCount());
        currentTickRegisteredSources += RadioactiveSourceRegistry.get(level).size();
        DimensionState state = STATES_BY_DIMENSION.get(level.dimension());
        if (state == null || state.activeSources.isEmpty()) {
            return;
        }

        currentTickActiveSources += state.activeSources.size();
        currentTickQueueSize += state.schedules.size();

        long gameTime = level.getGameTime();
        long tickStartNs = System.nanoTime();
        int validations = 0;
        int processed = 0;
        int rays = 0;
        boolean stoppedWithDueSources = false;

        while (validations < MAX_SOURCE_VALIDATIONS_PER_TICK) {
            if (System.nanoTime() - tickStartNs >= HARD_RAY_BUDGET_NS_PER_TICK) {
                stoppedWithDueSources = hasDueSource(state, gameTime);
                break;
            }
            ScheduledSource scheduled = nextValidDueSource(state, gameTime);
            if (scheduled == null) {
                break;
            }

            SourceSchedule schedule = state.schedules.get(scheduled.pos());
            if (schedule == null) {
                continue;
            }

            validations++;
            BlockPos pos = scheduled.pos();
            if (!level.hasChunkAt(pos) || !isChunkTicking(level, pos)) {
                deactivateActiveSource(state, pos);
                continue;
            }

            BlockState blockState = level.getBlockState(pos);
            if (!(blockState.getBlock() instanceof RadioactiveSource)) {
                unregisterActiveSource(level, pos);
                RadioactiveSourceRegistry.unregister(level, pos);
                continue;
            }

            Optional<EnvironmentalRadiationRayProfile> profileOptional = RadiationBlockProfiles.getEnvironmentalRayProfile(blockState);
            if (profileOptional.isEmpty()) {
                unregisterActiveSource(level, pos);
                continue;
            }

            EnvironmentalRadiationRayProfile profile = profileOptional.get();
            int raysTraced = runEnvironmentalSource(level, pos, blockState, profile);
            processed++;
            rays += raysTraced;
            reschedule(level, state, pos, blockState, profile, schedule, gameTime);

            if (System.nanoTime() - tickStartNs >= HARD_RAY_BUDGET_NS_PER_TICK) {
                stoppedWithDueSources = hasDueSource(state, gameTime);
                break;
            }
        }

        if ((stoppedWithDueSources || validations >= MAX_SOURCE_VALIDATIONS_PER_TICK) && hasDueSource(state, gameTime)) {
            currentTickDueLeft += Math.max(1, estimateDueSources(state, gameTime));
        }
        currentTickRayNs += System.nanoTime() - tickStartNs;
        currentTickSourcesProcessed += processed;
        currentTickRays += rays;
    }

    public static void finishServerTick(int serverTick) {
        beginServerTick(serverTick);
        if (currentTickRecorded) {
            return;
        }

        currentTickRecorded = true;
        secondTotalRayNs += currentTickRayNs;
        secondMaxRayNs = Math.max(secondMaxRayNs, currentTickRayNs);
        secondSourcesProcessed += currentTickSourcesProcessed;
        secondDueLeft += currentTickDueLeft;
        secondRays += currentTickRays;
        secondRegisteredSources = currentTickRegisteredSources;
        secondActiveSources = currentTickActiveSources;
        secondQueueSize = currentTickQueueSize;
        secondTicks++;

        adjustThrottleForTick(currentTickRayNs, currentTickDueLeft);

        if (lastPerformanceLogTick == Integer.MIN_VALUE) {
            lastPerformanceLogTick = serverTick;
        }
        if (serverTick - lastPerformanceLogTick < PERFORMANCE_LOG_INTERVAL_TICKS) {
            return;
        }

        double averageMs = secondTicks <= 0 ? 0.0D : secondTotalRayNs / (double) secondTicks / 1_000_000.0D;

        adjustThrottleForSecond(averageMs, secondMaxRayNs, secondDueLeft);



        lastPerformanceLogTick = serverTick;
        secondTotalRayNs = 0L;
        secondMaxRayNs = 0L;
        secondTicks = 0;
        secondSourcesProcessed = 0;
        secondDueLeft = 0;
        secondRays = 0;
        secondRegisteredSources = 0;
        secondActiveSources = 0;
        secondQueueSize = 0;
    }

    public static void registerActiveSourceIfNeeded(ServerLevel level, BlockPos pos, BlockState state) {
        Optional<EnvironmentalRadiationRayProfile> profile = RadiationBlockProfiles.getEnvironmentalRayProfile(state);
        if (profile.isEmpty()) {
            return;
        }
        if (!level.hasChunkAt(pos) || !isChunkTicking(level, pos)) {
            return;
        }

        DimensionState dimensionState = STATES_BY_DIMENSION.computeIfAbsent(level.dimension(), ignored -> new DimensionState());
        BlockPos immutablePos = pos.immutable();
        dimensionState.activeSources.add(immutablePos);
        dimensionState.activeSourcesByChunk.computeIfAbsent(chunkKey(immutablePos), ignored -> new HashSet<>()).add(immutablePos);
        SourceSchedule schedule = dimensionState.schedules.get(immutablePos);
        if (schedule == null) {
            schedule = new SourceSchedule(initialNextTick(level, immutablePos, state, profile.get()));
            dimensionState.schedules.put(immutablePos, schedule);
            enqueue(dimensionState, immutablePos, schedule);
            return;
        }

        long gameTime = level.getGameTime();
        if (shouldReactivateSchedule(gameTime, schedule, profile.get())) {
            schedule.nextTick = gameTime + reactivationJitter(level, immutablePos, state);
            enqueue(dimensionState, immutablePos, schedule);
        }
    }

    public static void unregisterActiveSource(ServerLevel level, BlockPos pos) {
        DimensionState state = STATES_BY_DIMENSION.get(level.dimension());
        if (state == null) {
            return;
        }

        BlockPos immutablePos = pos.immutable();
        deactivateActiveSource(state, immutablePos);
        if (state.activeSources.isEmpty()) {
            STATES_BY_DIMENSION.remove(level.dimension());
        }
    }

    public static void deactivateSourcesInChunk(ServerLevel level, ChunkPos chunkPos) {
        DimensionState state = STATES_BY_DIMENSION.get(level.dimension());
        if (state == null) {
            return;
        }

        Set<BlockPos> positions = state.activeSourcesByChunk.remove(chunkPos.toLong());
        if (positions == null || positions.isEmpty()) {
            return;
        }

        for (BlockPos pos : positions) {
            state.activeSources.remove(pos);
            state.schedules.remove(pos);
        }
        if (state.activeSources.isEmpty()) {
            STATES_BY_DIMENSION.remove(level.dimension());
        }
    }

    public static void clearActiveSources() {
        STATES_BY_DIMENSION.clear();
        throttleMultiplier = MIN_THROTTLE_MULTIPLIER;
        currentServerTick = Integer.MIN_VALUE;
        currentTickRecorded = false;
        currentTickRayNs = 0L;
        currentTickSourcesProcessed = 0;
        currentTickDueLeft = 0;
        currentTickRays = 0;
        currentTickRegisteredSources = 0;
        currentTickActiveSources = 0;
        currentTickQueueSize = 0;
        lastPerformanceLogTick = Integer.MIN_VALUE;
        secondTotalRayNs = 0L;
        secondMaxRayNs = 0L;
        secondTicks = 0;
        secondSourcesProcessed = 0;
        secondDueLeft = 0;
        secondRays = 0;
        secondRegisteredSources = 0;
        secondActiveSources = 0;
        secondQueueSize = 0;
    }

    public static void discoverSourcesInChunk(ServerLevel level, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        LevelChunkSection[] sections = chunk.getSections();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.maybeHas(state -> state.getBlock() instanceof RadioactiveSource)) {
                continue;
            }

            int minY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            for (int localX = 0; localX < 16; localX++) {
                int x = chunkPos.getMinBlockX() + localX;
                for (int localZ = 0; localZ < 16; localZ++) {
                    int z = chunkPos.getMinBlockZ() + localZ;
                    for (int localY = 0; localY < 16; localY++) {
                        BlockState state = section.getBlockState(localX, localY, localZ);
                        if (state.getBlock() instanceof RadioactiveSource) {
                            mutablePos.set(x, minY + localY, z);
                            RadioactiveSourceRegistry.register(level, mutablePos);
                            registerActiveSourceIfNeeded(level, mutablePos, state);
                        }
                    }
                }
            }
        }
    }

    private static ScheduledSource nextValidDueSource(DimensionState state, long gameTime) {
        while (!state.dueQueue.isEmpty()) {
            ScheduledSource scheduled = state.dueQueue.peek();
            if (scheduled.nextTick() > gameTime) {
                return null;
            }

            state.dueQueue.poll();
            SourceSchedule schedule = state.schedules.get(scheduled.pos());
            if (schedule == null
                    || schedule.sequence != scheduled.sequence()
                    || schedule.nextTick != scheduled.nextTick()
                    || !state.activeSources.contains(scheduled.pos())) {
                continue;
            }
            return scheduled;
        }
        return null;
    }

    private static boolean hasDueSource(DimensionState state, long gameTime) {
        ScheduledSource scheduled = state.dueQueue.peek();
        return scheduled != null && scheduled.nextTick() <= gameTime;
    }

    private static int estimateDueSources(DimensionState state, long gameTime) {
        int due = 0;
        int sampled = 0;
        for (ScheduledSource scheduled : state.dueQueue) {
            if (sampled++ >= DUE_SAMPLE_LIMIT) {
                break;
            }
            SourceSchedule schedule = state.schedules.get(scheduled.pos());
            if (schedule != null
                    && schedule.sequence == scheduled.sequence()
                    && schedule.nextTick == scheduled.nextTick()
                    && scheduled.nextTick() <= gameTime) {
                due++;
            }
        }
        return due;
    }

    private static int runEnvironmentalSource(ServerLevel level, BlockPos pos, BlockState state, EnvironmentalRadiationRayProfile profile) {
        RadioactiveSourceRegistry.register(level, pos);
        int raysTraced = RadiationUtil.applyFullEnvironmentalRadiation(
                level,
                pos,
                profile.strength(),
                profile.range(),
                profile.rayCount(),
                profile.maxConversions(),
                level.random
        );
        if (state.is(ModBlocks.MOLTEN_CORIUM_BLOCK.get())) {
            MoltenCoriumBlock.tickFromSourceRegistry(level, pos, state, level.random);
        }
        return raysTraced;
    }

    private static boolean isChunkTicking(ServerLevel level, BlockPos pos) {
        return level.shouldTickBlocksAt(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
    }

    private static void reschedule(ServerLevel level, DimensionState state, BlockPos pos, BlockState blockState, EnvironmentalRadiationRayProfile profile, SourceSchedule schedule, long gameTime) {
        schedule.nextTick = gameTime + effectiveInterval(profile) + phaseJitter(level, pos, blockState, profile);
        enqueue(state, pos, schedule);
    }

    private static void enqueue(DimensionState state, BlockPos pos, SourceSchedule schedule) {
        schedule.sequence = ++state.nextSequence;
        state.dueQueue.add(new ScheduledSource(schedule.nextTick, schedule.sequence, pos.immutable()));
    }

    private static int effectiveInterval(EnvironmentalRadiationRayProfile profile) {
        int interval = Mth.ceil(profile.baseTickInterval() * throttleMultiplier);
        return Mth.clamp(interval, Math.max(1, profile.baseTickInterval()), Math.max(profile.baseTickInterval(), profile.maxTickInterval()));
    }

    private static long initialNextTick(ServerLevel level, BlockPos pos, BlockState state, EnvironmentalRadiationRayProfile profile) {
        int interval = effectiveInterval(profile);
        return level.getGameTime() + Math.floorMod(sourceHash(level, pos, state), interval);
    }

    private static boolean shouldReactivateSchedule(long gameTime, SourceSchedule schedule, EnvironmentalRadiationRayProfile profile) {
        long stalePastWindow = Math.max(MIN_STALE_REACTIVATION_WINDOW_TICKS, profile.maxTickInterval());
        if (schedule.nextTick < gameTime - stalePastWindow) {
            return true;
        }
        return schedule.nextTick > gameTime + profile.maxTickInterval() + REACTIVATION_JITTER_TICKS;
    }

    private static int reactivationJitter(ServerLevel level, BlockPos pos, BlockState state) {
        return Math.floorMod(sourceHash(level, pos, state), REACTIVATION_JITTER_TICKS + 1);
    }

    private static void deactivateActiveSource(DimensionState state, BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        if (!state.activeSources.remove(immutablePos)) {
            state.schedules.remove(immutablePos);
            return;
        }

        state.schedules.remove(immutablePos);
        Set<BlockPos> chunkSources = state.activeSourcesByChunk.get(chunkKey(immutablePos));
        if (chunkSources == null) {
            return;
        }

        chunkSources.remove(immutablePos);
        if (chunkSources.isEmpty()) {
            state.activeSourcesByChunk.remove(chunkKey(immutablePos));
        }
    }

    private static long chunkKey(BlockPos pos) {
        return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static int phaseJitter(ServerLevel level, BlockPos pos, BlockState state, EnvironmentalRadiationRayProfile profile) {
        int interval = effectiveInterval(profile);
        int jitterWindow = Math.max(1, Math.min(interval / 4, 80));
        return Math.floorMod(sourceHash(level, pos, state) >>> 8, jitterWindow);
    }

    private static int sourceHash(ServerLevel level, BlockPos pos, BlockState state) {
        int hash = sourceHash(level, pos);
        hash = 31 * hash + state.getBlock().hashCode();
        return hash;
    }

    private static int sourceHash(ServerLevel level, BlockPos pos) {
        int hash = Long.hashCode(pos.asLong());
        hash = 31 * hash + level.dimension().location().hashCode();
        return hash;
    }

    private static void adjustThrottleForTick(long elapsedNs, int dueLeft) {
        if (elapsedNs > EMERGENCY_RAY_BUDGET_NS_PER_TICK) {
            throttleMultiplier *= 1.5D;
        } else if (elapsedNs > HARD_RAY_BUDGET_NS_PER_TICK) {
            throttleMultiplier *= 1.25D;
        } else if (dueLeft > 0) {
            throttleMultiplier *= 1.03D;
        }
        throttleMultiplier = Mth.clamp(throttleMultiplier, MIN_THROTTLE_MULTIPLIER, MAX_THROTTLE_MULTIPLIER);
    }

    private static void adjustThrottleForSecond(double averageMs, long maxNs, int dueLeft) {
        double targetMs = TARGET_RAY_BUDGET_NS_PER_TICK / 1_000_000.0D;
        if (maxNs > EMERGENCY_RAY_BUDGET_NS_PER_TICK) {
            throttleMultiplier *= 1.5D;
        } else if (averageMs > targetMs) {
            throttleMultiplier *= 1.1D;
        } else if (averageMs < targetMs * 0.5D && dueLeft <= 0) {
            throttleMultiplier *= 0.98D;
        }
        throttleMultiplier = Mth.clamp(throttleMultiplier, MIN_THROTTLE_MULTIPLIER, MAX_THROTTLE_MULTIPLIER);
    }

    private static void beginServerTick(int serverTick) {
        if (currentServerTick == serverTick) {
            return;
        }

        currentServerTick = serverTick;
        currentTickRecorded = false;
        currentTickRayNs = 0L;
        currentTickSourcesProcessed = 0;
        currentTickDueLeft = 0;
        currentTickRays = 0;
        currentTickRegisteredSources = 0;
        currentTickActiveSources = 0;
        currentTickQueueSize = 0;
    }

    private static String formatMetric(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static final class DimensionState {
        private final Set<BlockPos> activeSources = new HashSet<>();
        private final Map<Long, Set<BlockPos>> activeSourcesByChunk = new HashMap<>();
        private final Map<BlockPos, SourceSchedule> schedules = new HashMap<>();
        private final PriorityQueue<ScheduledSource> dueQueue = new PriorityQueue<>(
                Comparator.comparingLong(ScheduledSource::nextTick)
                        .thenComparingLong(ScheduledSource::sequence)
        );
        private long nextSequence;
    }

    private static final class SourceSchedule {
        private long nextTick;
        private long sequence;

        private SourceSchedule(long nextTick) {
            this.nextTick = nextTick;
        }
    }

    private record ScheduledSource(long nextTick, long sequence, BlockPos pos) {
    }
}
