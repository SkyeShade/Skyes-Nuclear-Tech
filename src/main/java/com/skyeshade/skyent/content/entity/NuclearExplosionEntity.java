package com.skyeshade.skyent.content.entity;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.config.SkyentNuclearExplosionConfig;
import com.skyeshade.skyent.content.explosion.destruction.NuclearBlastRayPlanner;
import com.skyeshade.skyent.content.explosion.destruction.NuclearBlockMutationQueue;
import com.skyeshade.skyent.content.explosion.destruction.NuclearBlockSnapshot;
import com.skyeshade.skyent.content.explosion.destruction.NuclearColumnCollapsePass;
import com.skyeshade.skyent.content.explosion.destruction.NuclearDestructionMask;
import com.skyeshade.skyent.content.explosion.destruction.NuclearPlannedBlockMutationQueue;
import com.skyeshade.skyent.content.explosion.destruction.NuclearRayPlanningExecutor;
import com.skyeshade.skyent.content.explosion.destruction.NuclearResistanceCache;
import com.skyeshade.skyent.content.explosion.destruction.NuclearSectionCompletionTracker;
import com.skyeshade.skyent.content.explosion.destruction.NuclearWaterEvaporationPass;
import com.skyeshade.skyent.content.radiation.ModDamageSources;
import com.skyeshade.skyent.event.systems.RadiationExposureSystem;
import com.skyeshade.skyent.registry.ModEntities;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;

public class NuclearExplosionEntity extends Entity {
    private static final EntityDataAccessor<Boolean> DATA_SPAWN_CLOUD = SynchedEntityData.defineId(
            NuclearExplosionEntity.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Boolean> DATA_FLASH_SKY = SynchedEntityData.defineId(
            NuclearExplosionEntity.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Long> DATA_VISUAL_SEED = SynchedEntityData.defineId(
            NuclearExplosionEntity.class,
            EntityDataSerializers.LONG
    );
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(
            NuclearExplosionEntity.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Integer> DATA_VISUAL_AGE = SynchedEntityData.defineId(
            NuclearExplosionEntity.class,
            EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Boolean> DATA_HAS_VISUAL_ORIGIN = SynchedEntityData.defineId(
            NuclearExplosionEntity.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Float> DATA_ORIGIN_X = SynchedEntityData.defineId(
            NuclearExplosionEntity.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> DATA_ORIGIN_Y = SynchedEntityData.defineId(
            NuclearExplosionEntity.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> DATA_ORIGIN_Z = SynchedEntityData.defineId(
            NuclearExplosionEntity.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> DATA_GROUND_Y = SynchedEntityData.defineId(
            NuclearExplosionEntity.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Boolean> DATA_ACTIVE_BLAST_PHASE = SynchedEntityData.defineId(
            NuclearExplosionEntity.class,
            EntityDataSerializers.BOOLEAN
    );
    public static final float VANILLA_EXPLOSION_STRENGTH = 16.0F;
    public static final int ENTITY_LIFETIME_TICKS = 20 * 60 * 4;
    public static final float DEFAULT_NUKE_RADIUS = 200.0F;
    public static final double SHOCKWAVE_MAX_RADIUS_MULTIPLIER = 4.0D;
    private static final double SHOCKWAVE_DAMAGE_RADIUS_MULTIPLIER = 1.20D;
    public static final double SHOCKWAVE_SPEED_BLOCKS_PER_TICK = 2.0D;
    public static final int SHOCKWAVE_VISUAL_INTERVAL_TICKS = 1;
    public static final int SHOCKWAVE_SOUND_TICKS = 5;
    public static final float SHOCKWAVE_SOUND_VOLUME = 2000.0F;
    private static final double SHOCKWAVE_DAMAGE_BAND_WIDTH = 5.0D;
    private static final double SHOCKWAVE_DAMAGE_FALLOFF_POWER = 2.75D;
    private static final double SHOCKWAVE_KNOCKBACK_FALLOFF_POWER = 2.0D;
    private static final double SHOCKWAVE_DAMAGE_MULTIPLIER = 0.5D;
    private static final int SHOCKWAVE_COVER_SCAN_BLOCKS = 16;
    private static final float SHOCKWAVE_MIN_DAMAGE = 2.0F;
    private static final int NUCLEAR_EXPLOSION_DAMAGE_INTERVAL_TICKS = 10;
    private static final int NUCLEAR_EXPLOSION_POST_DESTRUCTION_DAMAGE_TICKS = 20;
    private static final double NUCLEAR_EXPLOSION_ACTIVE_DAMAGE_RADIUS_MULTIPLIER = 1.5D;
    private static final double NUCLEAR_EXPLOSION_CLOSE_FIRE_RADIUS = 64.0D;
    private static final double NUCLEAR_EXPLOSION_COVER_HARDNESS_THRESHOLD = 6.0D;
    private static final float NUCLEAR_EXPLOSION_DAMAGE_PER_RADIUS = 10.0F;
    private static final float NUCLEAR_EXPLOSION_MIN_DAMAGE = 0.0F;
    private static final double NUCLEAR_EXPLOSION_MAX_KNOCKBACK = 4.5D;
    private static final double NUCLEAR_EXPLOSION_MAX_VERTICAL_KNOCKBACK = 1.2D;
    private static final int NUCLEAR_EXPLOSION_FIRE_SECONDS = 10;
    private static final double NUCLEAR_EXPLOSION_COVER_RAY_STEP_BLOCKS = 0.75D;
    private static final int SHOCKWAVE_MIN_PUFFS = 32;
    private static final int SHOCKWAVE_MAX_PUFFS = 260;
    private static final int SHOCKWAVE_LIFETIME_MIN_TICKS = 18;
    private static final int SHOCKWAVE_LIFETIME_MAX_TICKS = 34;
    private static final double SHOCKWAVE_BAND_BEHIND_BLOCKS = 4.0D;
    private static final double SHOCKWAVE_BAND_AHEAD_BLOCKS = 1.0D;
    private static final int SHOCKWAVE_SURFACE_SCAN_PADDING = 8;
    private static final int ACTIVE_BLAST_SWEEP_CLOUDLETS_PER_TICK = 20;
    private static final int ACTIVE_BLAST_SWEEP_SPAWN_ATTEMPTS_PER_TICK = 40;
    private static final int ACTIVE_BLAST_SWEEP_LIFETIME_MIN_TICKS = 35;
    private static final int ACTIVE_BLAST_SWEEP_LIFETIME_RANDOM_TICKS = 45;
    private static final double ACTIVE_BLAST_SWEEP_OUTWARD_SPEED_MIN = 1.75D;
    private static final double ACTIVE_BLAST_SWEEP_OUTWARD_SPEED_MAX = 3.75D;
    private static final double ACTIVE_BLAST_SWEEP_UPWARD_SPEED_MIN = 0.04D;
    private static final double ACTIVE_BLAST_SWEEP_UPWARD_SPEED_MAX = 0.20D;
    private static final double ACTIVE_BLAST_SWEEP_RANDOM_SIDE_SPEED = 0.55D;
    private static final float ACTIVE_BLAST_SWEEP_MIN_SIZE = 4.0F;
    private static final float ACTIVE_BLAST_SWEEP_MAX_SIZE = 9.0F;
    private static final float ACTIVE_BLAST_SWEEP_MIN_GROW_SIZE = 5.0F;
    private static final float ACTIVE_BLAST_SWEEP_MAX_GROW_SIZE = 12.0F;
    private static final double CENTER_RADIATION_DURATION_RADIUS_EXPONENT = 0.35D;
    private static final double CENTER_RADIATION_SOURCE_RADIUS_EXPONENT = 1.75D;
    private static final double CENTER_RADIATION_RANGE_RADIUS_EXPONENT = 0.85D;
    private static final int CENTER_RADIATION_MIN_DURATION_TICKS = 5;
    private static final double CENTER_RADIATION_MIN_RADIUS = 24.0D;
    private static final double THERMAL_FLASH_RADIUS_MULTIPLIER = 0.35D;
    private static final double THERMAL_FLASH_MIN_RADIUS = 24.0D;
    private static final float THERMAL_FLASH_MAX_FIRE_DAMAGE = 40.0F;
    private static final float THERMAL_FLASH_MIN_FIRE_DAMAGE = 2.0F;
    private static final int THERMAL_FLASH_MAX_FIRE_SECONDS = 18;
    private static final int THERMAL_FLASH_MIN_FIRE_SECONDS = 4;
    private static final double THERMAL_FLASH_DAMAGE_POWER = 1.75D;
    public static final int MAX_CLOUDLETS = 5200;
    public static final int RAY_GROW_TICKS = 10;
    public static final int RAY_FADE_TICKS = 40;
    public static final int RAY_TOTAL_TICKS = RAY_GROW_TICKS + RAY_FADE_TICKS;
    public static final float RAY_SCALE = 56.0F;
    private static final boolean ENABLE_NUCLEAR_BLOCK_DESTRUCTION = true;
    private static final boolean NUKE_DESTRUCTION_PLAN_ONLY = false;
    private static final boolean SAVE_NUKE_DESTRUCTION_PROGRESS = false;
    private static final int NUKE_RAY_PLANNER_MAX_RAYS_PER_TICK = 8_128;
    private static final int NUKE_RAY_PLANNER_MAX_STEPS_PER_TICK = 128_000;
    private static final int NUKE_WATER_CLEAR_START_DELAY_TICKS = 1;
    private static final int COLUMN_COLLAPSE_MAX_BLOCK_WRITES_PER_TICK = 8_128;
    private static final int COLUMN_COLLAPSE_MAX_DROP_BLOCKS = 10;
    private static final boolean NUKE_CLEANUP_DROPPED_ITEMS_IN_AFTERMATH = false;
    private static final double NUKE_ITEM_CLEANUP_RADIUS_SCALE = 3.0D;
    private static final double NUKE_BASELINE_RADIUS = 200.0D;
    private static final double NUKE_DESTRUCTION_RADIUS_MULTIPLIER = 1.0D;
    private static final double NUKE_RAY_BASE_STARTING_ENERGY = 13_000_900_000.0D;
    private static final double NUKE_RAY_STARTING_ENERGY_PER_RADIUS = 100_250_500.0D;
    private static final double NUKE_RAY_STARTING_ENERGY_RADIUS_POWER = 1.35D;
    private static final double NUKE_VISUAL_RAY_SCALE_POWER = 0.75D;
    private static final double NUKE_RAY_INITIAL_ENERGY_MULTIPLIER = 1.0D;
    private static final double NUKE_CLOSE_RANGE_ARMOR_PIERCING_RADIUS_FRACTION = 0.35D;
    private static final double NUKE_CLOSE_RANGE_RESISTANCE_COST_MULTIPLIER = 0.25D;
    private static final double COLUMN_COLLAPSE_MAX_RESISTANCE = 6.0D;

    private float strength = VANILLA_EXPLOSION_STRENGTH;
    private float radius = DEFAULT_NUKE_RADIUS;
    private boolean destroyBlocks = true;
    private boolean playSounds = true;
    private boolean explosionDone;
    @Nullable
    private UUID sourceUuid;
    private final List<NuclearCloudlet> cloudlets = new ArrayList<>();
    @Nullable
    private NuclearMushroomCloudSimulation mushroomCloudSimulation;
    private final Set<UUID> shockwaveDamagedEntities = new HashSet<>();
    private final Set<ChunkPos> forcedExplosionChunks = new HashSet<>();
    private UUID chunkLoadingOwnerUuid;
    private int shockwaveSpawnMethodCalls;
    private int shockwaveSpawnConditionPasses;
    private int shockwaveCloudletsAttempted;
    private int shockwaveCloudletsAdded;
    private int shockwaveSurfaceFound;
    private int shockwaveSurfaceNotFound;
    private int shockwaveChunkMissing;
    private int shockwaveInvalidY;
    private int shockwaveHeightmapInvalid;
    private int shockwaveFoundSurfaceTopDown;
    private int shockwaveFallbackHeightmap;
    private int shockwaveFallbackEntityY;
    private int shockwaveCloudletsSkipped;
    private int activeBlastSweepAttempts;
    private int activeBlastSweepAdded;
    private int activeBlastSweepSkippedSurface;
    private int activeBlastSweepSkippedChunkMissing;
    private int centerRadiationTicks;
    private boolean appliedInitialThermalFlash;
    private boolean chunksForced;
    private ChunkPos retainedCenterChunk;
    private UUID aftermathChunkLoadingOwnerUuid;
    @Nullable
    private NuclearDestructionMask destructionMask;
    @Nullable
    private NuclearResistanceCache resistanceCache;
    @Nullable
    private NuclearBlastRayPlanner rayPlanner;
    @Nullable
    private NuclearRayPlanningExecutor.AsyncPlanningHandle asyncRayPlanningHandle;
    @Nullable
    private NuclearBlockMutationQueue mutationQueue;
    @Nullable
    private NuclearWaterEvaporationPass fluidEvaporationPass;
    private boolean waterClearStarted;
    private boolean primaryDestructionComplete;
    private int primaryDestructionCompleteAge = -1;
    @Nullable
    private NuclearColumnCollapsePass columnCollapsePass;
    @Nullable
    private NuclearPlannedBlockMutationQueue columnCollapseMutationQueue;
    @Nullable
    private NuclearSectionCompletionTracker sectionCompletionTracker;
    private boolean aftermathStarted;
    private int aftermathStartTick = -1;
    private int mutationStartTick = -1;
    private String aftermathStartReason = "not_started";
    private DestructionPhase destructionPhase = DestructionPhase.NOT_STARTED;
    private int destructionTicks;
    private long destructionProgressCounter;
    private long lastDestructionProgressGameTime = -1L;
    private String lastDestructionProgressReason = "not_started";
    private long lastAftermathThrottleSampleNs;
    private double smoothedAftermathTickMs = 50.0D;
    private double originX = Double.NaN;
    private double originY = Double.NaN;
    private double originZ = Double.NaN;
    private double explosionGroundY = Double.NaN;
    private boolean nuclearExplosionStartedLogged;
    private long rayCalculationStartNs = -1L;
    private long rayCalculationMapDataBytes;
    private boolean rayCalculationLogged;
    private long destructionMutationStartNs = -1L;
    private boolean destructionMutationLogged;

    public NuclearExplosionEntity(EntityType<NuclearExplosionEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public NuclearExplosionEntity(Level level, Vec3 center) {
        this(ModEntities.NUCLEAR_EXPLOSION.get(), level);
        setFixedOrigin(center.x, center.y, center.z);
    }

    public void configure(float strength, boolean destroyBlocks, boolean spawnCloud, boolean flashSky, boolean playSounds, @Nullable Entity source) {
        configure(strength, DEFAULT_NUKE_RADIUS, destroyBlocks, spawnCloud, flashSky, playSounds, source);
    }

    public void configure(float strength, float radius, boolean destroyBlocks, boolean spawnCloud, boolean flashSky, boolean playSounds, @Nullable Entity source) {
        this.strength = strength;
        this.radius = Math.max(1.0F, radius);
        entityData.set(DATA_RADIUS, this.radius);
        this.destroyBlocks = destroyBlocks;
        entityData.set(DATA_SPAWN_CLOUD, spawnCloud);
        entityData.set(DATA_FLASH_SKY, flashSky);
        this.playSounds = playSounds;
        this.sourceUuid = source == null ? null : source.getUUID();
        entityData.set(DATA_VISUAL_SEED, level().random.nextLong());
    }

    public void adoptChunkLoadLease(NuclearExplosionChunkLoading.NuclearExplosionChunkLease lease) {
        chunkLoadingOwnerUuid = lease.ownerUuid();
        forcedExplosionChunks.clear();
        forcedExplosionChunks.addAll(lease.chunks());
        retainedCenterChunk = chunkPosition();
        if (level() instanceof ServerLevel serverLevel
                && !forcedExplosionChunks.contains(retainedCenterChunk)
                && NuclearExplosionChunkLoading.forceSingleChunk(
                serverLevel,
                getChunkLoadingOwnerUuid(),
                retainedCenterChunk,
                SkyentNuclearExplosionConfig.chunkLoadingTickingTickets()
        )) {
            forcedExplosionChunks.add(retainedCenterChunk);
        }
        chunksForced = !forcedExplosionChunks.isEmpty();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SPAWN_CLOUD, true);
        builder.define(DATA_FLASH_SKY, true);
        builder.define(DATA_VISUAL_SEED, 0L);
        builder.define(DATA_RADIUS, DEFAULT_NUKE_RADIUS);
        builder.define(DATA_VISUAL_AGE, 0);
        builder.define(DATA_HAS_VISUAL_ORIGIN, false);
        builder.define(DATA_ORIGIN_X, 0.0F);
        builder.define(DATA_ORIGIN_Y, 0.0F);
        builder.define(DATA_ORIGIN_Z, 0.0F);
        builder.define(DATA_GROUND_Y, 0.0F);
        builder.define(DATA_ACTIVE_BLAST_PHASE, false);
    }

    @Override
    public void tick() {
        syncFixedOriginFromEntityData();
        stabilizeFixedOrigin();
        super.tick();
        syncFixedOriginFromEntityData();
        stabilizeFixedOrigin();

        if (level().isClientSide) {
            tickClientEffects();
        } else {
            entityData.set(DATA_VISUAL_AGE, tickCount);
            logNuclearExplosionStarted();
            tickServerEffects();
            entityData.set(DATA_ACTIVE_BLAST_PHASE, isActiveBlastPhase());
        }

        if (tickCount > ENTITY_LIFETIME_TICKS && canReleaseImmediateChunks()) {
            unforceExplosionChunks(false);
            discard();
        }
    }

    private void tickServerEffects() {
        if (!chunksForced && !canReleaseImmediateChunks()) {
            forceExplosionChunks();
        }


        tickNuclearDestruction();
        if (chunksForced && canReleaseImmediateChunks()) {
            unforceExplosionChunks(false);
        }
        if (!appliedInitialThermalFlash) {
            appliedInitialThermalFlash = true;
            applyInitialThermalFlash();
        }

        tickNuclearExplosionEntityDamage();
        tickShockwaveServer();
        if (SkyentNuclearExplosionConfig.radiationCenterBurstEnabled()
                && centerRadiationTicks < getCenterRadiationDurationTicks()) {
            tickCenterRadiation();
            centerRadiationTicks++;
        }
    }

    private void logNuclearExplosionStarted() {
        if (nuclearExplosionStartedLogged) {
            return;
        }
        nuclearExplosionStartedLogged = true;
        SkyesNuclearTech.LOGGER.info(
                "Nuclear explosion started: radius={}",
                getRadius()
        );
    }

    private void logRayCalculationFinished(long rayCount) {
        if (rayCalculationLogged) {
            return;
        }
        rayCalculationLogged = true;

        long finishedNs = System.nanoTime();
        long elapsedNanos = finishedNs - rayCalculationStartNs;

        double elapsedMs = elapsedNanos / 1_000_000.0D;
        double mapDataMiB = rayCalculationMapDataBytes / (1024.0D * 1024.0D);

        SkyesNuclearTech.LOGGER.info(
                "Nuclear explosion rays calculated: radius={}, rays={}, elapsedMs={}, mapDataMiB={}",
                getRadius(),
                rayCount,
                String.format(Locale.ROOT, "%.2f", elapsedMs),
                String.format(Locale.ROOT, "%.2f", mapDataMiB)
        );
    }

    private void startDestructionMutationTimer() {
        if (destructionMutationStartNs < 0L) {
            destructionMutationStartNs = System.nanoTime();
        }
    }

    private void logDestructionMutationFinished() {
        if (destructionMutationLogged || destructionMutationStartNs < 0L) {
            return;
        }
        destructionMutationLogged = true;
        long elapsedNanos = System.nanoTime() - destructionMutationStartNs;
        SkyesNuclearTech.LOGGER.info(
                "Nuclear explosion destruction finished: radius={}, elapsedMs={}",
                getRadius(),
                elapsedNanos / 1_000_000.0D
        );
    }

    private void tickNuclearDestruction() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!ENABLE_NUCLEAR_BLOCK_DESTRUCTION || !destroyBlocks) {
            markPrimaryDestructionComplete();
            destructionPhase = DestructionPhase.COMPLETE;
            cleanupDestructionState("disabled");
            return;
        }

        if (destructionPhase != DestructionPhase.COMPLETE) {
            destructionTicks++;
            if (destructionPhase == DestructionPhase.ASYNC_PLANNING
                    && asyncRayPlanningHandle != null
                    && !asyncRayPlanningHandle.isDone()) {
                markDestructionProgress(serverLevel, "async_workers_running");
            }
            String timeoutReason = destructionTimeoutReason(serverLevel);
            if (timeoutReason != null) {
                logDestructionTimeout(serverLevel, timeoutReason);
                destructionPhase = DestructionPhase.COMPLETE;
                cleanupDestructionState(timeoutReason);
                return;
            }
        }

        if (destructionPhase == DestructionPhase.NOT_STARTED) {
            startNuclearDestructionPlanning(serverLevel);
        }

        if (SkyentNuclearExplosionConfig.waterEvaporationEnabled()
                && !waterClearStarted
                && fluidEvaporationPass == null
                && destructionTicks > NUKE_WATER_CLEAR_START_DELAY_TICKS
                && destructionPhase != DestructionPhase.COMPLETE) {
            startFluidEvaporation(serverLevel);
        }
        tickFluidEvaporation();

        if (destructionPhase == DestructionPhase.PLANNING && rayPlanner != null && destructionMask != null) {
            if (rayCalculationStartNs < 0L) {
                rayCalculationStartNs = System.nanoTime();
            }
            int rayBudget = NukePerformanceBudget.scaleInt(NUKE_RAY_PLANNER_MAX_RAYS_PER_TICK, 512, serverLevel.getServer());
            int stepBudget = NukePerformanceBudget.scaleInt(NUKE_RAY_PLANNER_MAX_STEPS_PER_TICK, 8_192, serverLevel.getServer());
            NuclearBlastRayPlanner.PlannerResult result = rayPlanner.tickBudget(
                    rayBudget,
                    stepBudget
            );
            if (result.raysProcessed() > 0 || result.stepsProcessed() > 0 || result.blocksMarked() > 0) {
                markDestructionProgress(serverLevel, "ray_planning");
            }
            NukePerformanceBudget.logIfEnabled(getId(), tickCount, destructionPhase.name(), 0, 0, rayBudget, stepBudget);

            if (rayPlanner.isComplete()) {
                logRayCalculationFinished(rayPlanner.totalRays());
                sectionCompletionTracker = new NuclearSectionCompletionTracker();
                sectionCompletionTracker.initializeFromDestructionMask(destructionMask);
                mutationQueue = new NuclearBlockMutationQueue(serverLevel, destructionMask, fixedOrigin(), sectionCompletionTracker, resistanceCache);
                destructionPhase = DestructionPhase.MUTATING;
                mutationStartTick = tickCount;
                markDestructionProgress(serverLevel, "sync_planning_complete");
            }
        }

        if (destructionPhase == DestructionPhase.ASYNC_PLANNING && rayPlanner != null && destructionMask != null && asyncRayPlanningHandle != null) {
            tickAsyncRayPlanning(serverLevel);
        }

        if (destructionPhase == DestructionPhase.MUTATING && mutationQueue != null) {
            if (NUKE_DESTRUCTION_PLAN_ONLY) {
                markPrimaryDestructionComplete();
                destructionPhase = DestructionPhase.COMPLETE;
                cleanupDestructionState("plan_only");
                return;
            }

            long mutationStartNs = System.nanoTime();
            int mutationSectionsBudget = NukePerformanceBudget.scaleInt(
                    SkyentNuclearExplosionConfig.mutationMaxSectionsPerTick(),
                    1,
                    serverLevel.getServer()
            );
            int mutationBlocksBudget = NukePerformanceBudget.scaleInt(
                    SkyentNuclearExplosionConfig.mutationMaxBlocksPerTick(),
                    64,
                    serverLevel.getServer()
            );
            double mutationMsBudget = NukePerformanceBudget.scaleMilliseconds(
                    SkyentNuclearExplosionConfig.mutationMaxMillisecondsPerTick(),
                    1.0D,
                    serverLevel.getServer()
            );
            startDestructionMutationTimer();
            NuclearBlockMutationQueue.MutationResult result = mutationQueue.tick(
                    mutationSectionsBudget,
                    mutationBlocksBudget,
                    mutationMsBudget
            );
            double mutationElapsedMs = elapsedMs(mutationStartNs);
            if (result.sectionsTouched() > 0 || result.blocksRemoved() > 0 || result.complete()) {
                markDestructionProgress(serverLevel, "mutation_applied_or_advanced");
            }
            NukePerformanceBudget.logIfEnabled(getId(), tickCount, destructionPhase.name(), mutationBlocksBudget, mutationSectionsBudget, 0, 0);
            if (!aftermathStarted && sectionCompletionTracker != null) {
                int readySections = sectionCompletionTracker.completedCount() + sectionCompletionTracker.skippedCount();
                if (readySections >= SkyentNuclearExplosionConfig.mutationMinCompletedSectionsBeforeAftermath()) {
                    startColumnCollapsePass(serverLevel, "completed_section_threshold");
                } else if (mutationStartTick >= 0 && tickCount - mutationStartTick >= SkyentNuclearExplosionConfig.mutationAftermathForceStartAfterTicks()) {
                    startColumnCollapsePass(serverLevel, "force_start_timeout");
                }
            }
            tickColumnCollapseTasks(serverLevel);

            if (mutationQueue.isComplete()) {
                logDestructionMutationFinished();
                markPrimaryDestructionComplete();
                destructionMask = null;
                resistanceCache = null;
                rayPlanner = null;
                mutationQueue = null;
                unforceExplosionChunks(false);
                if (!aftermathStarted) {
                    startColumnCollapsePass(serverLevel, "deletion_complete");
                    tickColumnCollapseTasks(serverLevel);
                }
                if (isGameplayWorkComplete()) {
                    destructionPhase = DestructionPhase.COMPLETE;
                    cleanupDestructionState("complete");
                } else {
                    destructionPhase = columnCollapsePass != null
                            ? DestructionPhase.COLUMN_COLLAPSE_PLANNING
                            : DestructionPhase.COLUMN_COLLAPSE_MUTATING;
                }
            }
        }

        if (destructionPhase == DestructionPhase.COLUMN_COLLAPSE_PLANNING) {
            tickColumnCollapseTasks(serverLevel);
            if (isGameplayWorkComplete()) {
                destructionPhase = DestructionPhase.COMPLETE;
                cleanupDestructionState("complete");
            } else if (columnCollapsePass == null) {
                destructionPhase = DestructionPhase.COLUMN_COLLAPSE_MUTATING;
            }
        }

        if (destructionPhase == DestructionPhase.COLUMN_COLLAPSE_MUTATING) {
            tickColumnCollapseTasks(serverLevel);
            if (isGameplayWorkComplete()) {
                destructionPhase = DestructionPhase.COMPLETE;
                cleanupDestructionState("complete");
            }
        }

        if (destructionPhase != DestructionPhase.COMPLETE && isGameplayWorkComplete()) {
            destructionPhase = DestructionPhase.COMPLETE;
            cleanupDestructionState("complete");
        }
    }

    private void startFluidEvaporation(ServerLevel serverLevel) {
        waterClearStarted = true;
        int evaporationRadius = Mth.ceil(getRadius() * SkyentNuclearExplosionConfig.waterEvaporationRadiusScale());
        fluidEvaporationPass = new NuclearWaterEvaporationPass(serverLevel, fixedOrigin(), evaporationRadius);
        markDestructionProgress(serverLevel, "water_evaporation_started");
    }

    private void tickFluidEvaporation() {
        if (fluidEvaporationPass == null) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level();
        int waterBudget = NukePerformanceBudget.scaleInt(
                SkyentNuclearExplosionConfig.waterEvaporationMaxBlocksPerTick(),
                1024,
                serverLevel.getServer()
        );
        NuclearWaterEvaporationPass.EvaporationResult result = fluidEvaporationPass.tick(
                SkyentNuclearExplosionConfig.waterEvaporationRadialLayersPerStep(),
                waterBudget,
                waterBudget
        );
        if (result.sectionsProcessed() > 0
                || result.columnsScanned() > 0
                || result.blockChecks() > 0
                || result.waterBlocksRemoved() > 0
                || result.lavaBlocksRemoved() > 0
                || result.waterloggedBlocksCleared() > 0
                || result.complete()) {
            markDestructionProgress(serverLevel, "water_evaporation");
        }
        if (fluidEvaporationPass.isComplete()) {
            fluidEvaporationPass = null;
        }
    }

    private void startColumnCollapsePass(ServerLevel serverLevel, String reason) {
        if (aftermathStarted) {
            return;
        }
        if (sectionCompletionTracker == null) {
            return;
        }

        double columnCollapseRadius = getRadius();
        columnCollapsePass = new NuclearColumnCollapsePass(
                serverLevel,
                fixedOrigin(),
                columnCollapseRadius,
                COLUMN_COLLAPSE_MAX_RESISTANCE,
                COLUMN_COLLAPSE_MAX_DROP_BLOCKS,
                getVisualSeed(),
                getAftermathChunkLoadingOwnerUuid(),
                sectionCompletionTracker
        );
        columnCollapseMutationQueue = null;
        aftermathStarted = true;
        aftermathStartTick = tickCount;
        aftermathStartReason = reason;
        markDestructionProgress(serverLevel, "aftermath_started_" + reason);
    }

    private void tickColumnCollapseTasks(ServerLevel serverLevel) {
        if (!aftermathStarted) {
            return;
        }

        if (columnCollapsePass != null) {
            AftermathThrottleBudget budget = computeAftermathThrottleBudget(serverLevel);
            NuclearColumnCollapsePass.CollapseResult result = columnCollapsePass.tick(
                    budget.workUnitsBudget(),
                    budget.columnsBudget(),
                    budget.timeBudgetMs()
            );
            if (result.columnsProcessed() > 0
                    || result.columnsDeferred() > 0
                    || result.unloadedWorkUnitsSkipped() > 0
                    || result.mutationsPlanned() > 0
                    || result.workUnitsProcessed() > 0
                    || result.sectionsMutated() > 0
                    || result.mutationsApplied() > 0
                    || result.complete()) {
                String reason = result.stoppedByTimeBudget() || budget.multiplier() < 1.0D
                        ? "aftermath_throttled_for_tps"
                        : result.unloadedWorkUnitsSkipped() > 0 ? "aftermath_skipped_unloaded_chunks" : "column_aftermath";
                markDestructionProgress(serverLevel, reason);
            }
            if (columnCollapsePass.isComplete()) {
                columnCollapsePass.clear();
                columnCollapsePass = null;
            }
        }
    }

    private boolean isAftermathComplete() {
        return aftermathStarted && columnCollapsePass == null && columnCollapseMutationQueue == null;
    }

    private boolean isGameplayWorkComplete() {
        return mutationQueue == null
                && rayPlanner == null
                && asyncRayPlanningHandle == null
                && isAftermathComplete()
                && fluidEvaporationPass == null;
    }

    private void cleanupDestructionState(String reason) {
        if (mutationQueue != null) {
            mutationQueue.clear();
        }
        if (columnCollapsePass != null) {
            columnCollapsePass.clear();
        }
        if (columnCollapseMutationQueue != null) {
            columnCollapseMutationQueue.clear();
        }
        if (fluidEvaporationPass != null) {
            fluidEvaporationPass.clear();
        }
        if (asyncRayPlanningHandle != null) {
            asyncRayPlanningHandle.cancel();
        }
        if (destructionMask != null) {
            destructionMask.clear();
        }
        if (sectionCompletionTracker != null) {
            sectionCompletionTracker.clear();
        }

        destructionMask = null;
        resistanceCache = null;
        rayPlanner = null;
        asyncRayPlanningHandle = null;
        mutationQueue = null;
        columnCollapsePass = null;
        columnCollapseMutationQueue = null;
        fluidEvaporationPass = null;
        sectionCompletionTracker = null;
        aftermathStarted = true;
        waterClearStarted = false;
        mutationStartTick = -1;
        aftermathStartReason = "cleaned_" + reason;
        lastDestructionProgressReason = "cleaned_" + reason;
        markPrimaryDestructionComplete();
        entityData.set(DATA_ACTIVE_BLAST_PHASE, isActiveBlastPhase());
    }

    private void startNuclearDestructionPlanning(ServerLevel serverLevel) {
        int destructionRadius = Mth.ceil(getRadius() * NUKE_DESTRUCTION_RADIUS_MULTIPLIER);
        double radiusScale = Math.max(0.01D, destructionRadius / NUKE_BASELINE_RADIUS);
        double energyScale = Math.pow(radiusScale, NUKE_RAY_STARTING_ENERGY_RADIUS_POWER);
        double baselineStartingEnergy = NUKE_RAY_BASE_STARTING_ENERGY
                + NUKE_BASELINE_RADIUS * NUKE_RAY_STARTING_ENERGY_PER_RADIUS;
        double destructionStrength = baselineStartingEnergy * energyScale;
        primaryDestructionComplete = false;
        primaryDestructionCompleteAge = -1;
        entityData.set(DATA_ACTIVE_BLAST_PHASE, true);
        destructionProgressCounter = 0L;
        lastDestructionProgressGameTime = -1L;
        lastDestructionProgressReason = "starting";
        markDestructionProgress(serverLevel, "planning_started");
        aftermathStarted = false;
        waterClearStarted = false;
        aftermathStartTick = -1;
        mutationStartTick = -1;
        aftermathStartReason = "not_started";
        sectionCompletionTracker = null;
        destructionMask = new NuclearDestructionMask();
        resistanceCache = new NuclearResistanceCache();
        rayPlanner = new NuclearBlastRayPlanner(
                serverLevel,
                fixedOrigin(),
                destructionRadius,
                destructionStrength,
                SkyentNuclearExplosionConfig.rayDensityMultiplier(),
                NUKE_RAY_INITIAL_ENERGY_MULTIPLIER,
                NUKE_CLOSE_RANGE_ARMOR_PIERCING_RADIUS_FRACTION,
                NUKE_CLOSE_RANGE_RESISTANCE_COST_MULTIPLIER,
                destructionMask,
                resistanceCache,
                getVisualSeed()
        );
        mutationQueue = null;
        asyncRayPlanningHandle = null;
        rayCalculationStartNs = System.nanoTime();
        rayCalculationMapDataBytes = 0L;
        rayCalculationLogged = false;
        destructionMutationStartNs = -1L;
        destructionMutationLogged = false;
        boolean asyncEnabled = SkyentNuclearExplosionConfig.asyncRayPlanning();
        int asyncMinRays = SkyentNuclearExplosionConfig.asyncMinRays();
        int asyncWorkerCount = SkyentNuclearExplosionConfig.asyncRayWorkers();
        boolean asyncEligible = rayPlanner.totalRays() >= asyncMinRays && asyncWorkerCount > 1;
        boolean useAsyncRayPlanning = asyncEnabled && asyncEligible;
        if (useAsyncRayPlanning) {
            NuclearBlockSnapshot snapshot = NuclearBlockSnapshot.build(serverLevel, fixedOrigin(), destructionRadius, resistanceCache);
            rayCalculationMapDataBytes = snapshot.mapDataBytes();
            asyncRayPlanningHandle = NuclearRayPlanningExecutor.submit(rayPlanner, snapshot, asyncWorkerCount);
            destructionPhase = DestructionPhase.ASYNC_PLANNING;
        } else {
            destructionPhase = DestructionPhase.PLANNING;
        }
    }

    private void tickAsyncRayPlanning(ServerLevel serverLevel) {
        if (asyncRayPlanningHandle == null || rayPlanner == null || destructionMask == null || resistanceCache == null) {
            destructionPhase = DestructionPhase.PLANNING;
            return;
        }
        if (!asyncRayPlanningHandle.isDone()) {
            return;
        }

        try {
            NuclearRayPlanningExecutor.AsyncPlanningResult asyncResult = asyncRayPlanningHandle.collect();
            if (asyncResult.canceled()) {
                SkyesNuclearTech.LOGGER.warn("Nuclear explosion async ray planning canceled; aborting destruction phase: id={}", getId());
                cleanupDestructionState("async_ray_canceled");
                destructionPhase = DestructionPhase.COMPLETE;
                return;
            }
            for (NuclearBlastRayPlanner.WorkerResult workerResult : asyncResult.workerResults()) {
                rayPlanner.mergeWorkerResult(workerResult);
            }
            rayPlanner.finishAsyncPlanning();
            logRayCalculationFinished(asyncResult.raysProcessed());
            sectionCompletionTracker = new NuclearSectionCompletionTracker();
            sectionCompletionTracker.initializeFromDestructionMask(destructionMask);
            mutationQueue = new NuclearBlockMutationQueue(serverLevel, destructionMask, fixedOrigin(), sectionCompletionTracker, resistanceCache);
            destructionPhase = DestructionPhase.MUTATING;
            mutationStartTick = tickCount;
            asyncRayPlanningHandle = null;
            markDestructionProgress(serverLevel, "async_planning_merged");
        } catch (RuntimeException exception) {
            asyncRayPlanningHandle = null;
            if (destructionMask != null) {
                destructionMask.clear();
            }
            rayCalculationMapDataBytes = 0L;
            rayPlanner = new NuclearBlastRayPlanner(
                    serverLevel,
                    fixedOrigin(),
                    Mth.ceil(getRadius() * NUKE_DESTRUCTION_RADIUS_MULTIPLIER),
                    rayPlanner == null ? 1.0D : rayPlanner.initialRayEnergy(),
                    SkyentNuclearExplosionConfig.rayDensityMultiplier(),
                    1.0D,
                    NUKE_CLOSE_RANGE_ARMOR_PIERCING_RADIUS_FRACTION,
                    NUKE_CLOSE_RANGE_RESISTANCE_COST_MULTIPLIER,
                    destructionMask,
                    resistanceCache,
                    getVisualSeed()
            );
            destructionPhase = DestructionPhase.PLANNING;
            markDestructionProgress(serverLevel, "async_failed_fallback_to_sync");
        }
    }

    private void markDestructionProgress(ServerLevel serverLevel, String reason) {
        destructionProgressCounter++;
        lastDestructionProgressGameTime = serverLevel.getGameTime();
        lastDestructionProgressReason = reason;
    }

    @Nullable
    private String destructionTimeoutReason(ServerLevel serverLevel) {
        if (destructionPhase == DestructionPhase.NOT_STARTED || destructionPhase == DestructionPhase.COMPLETE) {
            return null;
        }

        int maxTotalTicks = SkyentNuclearExplosionConfig.mutationMaxTotalDestructionTicks();
        if (maxTotalTicks > 0 && destructionTicks > maxTotalTicks) {
            return "max_total_timeout";
        }

        int noProgressTimeoutTicks = SkyentNuclearExplosionConfig.mutationNoProgressTimeoutTicks();
        if (noProgressTimeoutTicks <= 0) {
            return null;
        }
        if (lastDestructionProgressGameTime < 0L) {
            markDestructionProgress(serverLevel, "timeout_watch_initialized");
            return null;
        }
        long noProgressTicks = serverLevel.getGameTime() - lastDestructionProgressGameTime;
        return noProgressTicks > noProgressTimeoutTicks ? "no_progress_timeout" : null;
    }

    private void logDestructionTimeout(ServerLevel serverLevel, String timeoutReason) {
        SkyesNuclearTech.LOGGER.warn(
                "Nuclear explosion destruction timed out; aborting remaining gameplay work: id={}, reason={}, phase={}",
                getId(),
                timeoutReason,
                destructionPhase
        );
    }

    private static double elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000.0D;
    }

    private AftermathThrottleBudget computeAftermathThrottleBudget(ServerLevel serverLevel) {
        long nowNs = System.nanoTime();
        if (lastAftermathThrottleSampleNs > 0L) {
            double tickMs = Math.max(1.0D, (nowNs - lastAftermathThrottleSampleNs) / 1_000_000.0D);
            smoothedAftermathTickMs = Mth.lerp(0.15D, smoothedAftermathTickMs, tickMs);
        }
        lastAftermathThrottleSampleNs = nowNs;
        double avgTickMs = Math.max(1.0D, smoothedAftermathTickMs);
        double tpsEstimate = Math.min(SkyentNuclearExplosionConfig.aftermathTargetTps(), 1000.0D / avgTickMs);
        double multiplier = 1.0D;
        if (SkyentNuclearExplosionConfig.aftermathAdaptiveTpsThrottle()) {
            double softTps = SkyentNuclearExplosionConfig.aftermathSoftTps();
            double hardTps = Math.min(softTps, SkyentNuclearExplosionConfig.aftermathHardTps());
            if (tpsEstimate <= hardTps) {
                multiplier = 0.10D;
            } else if (tpsEstimate < softTps) {
                double t = (tpsEstimate - hardTps) / Math.max(0.001D, softTps - hardTps);
                multiplier = Mth.lerp(t, 0.10D, 1.0D);
            }
        }
        multiplier = Math.min(multiplier, NukePerformanceBudget.currentWorkScale(serverLevel.getServer()));

        int workUnitsBudget = Mth.clamp(
                Mth.ceil(SkyentNuclearExplosionConfig.aftermathBaseWorkUnitsPerTick() * multiplier),
                SkyentNuclearExplosionConfig.aftermathMinWorkUnitsPerTick(),
                SkyentNuclearExplosionConfig.aftermathMaxWorkUnitsPerTick()
        );
        int columnsBudget = Mth.clamp(
                Mth.ceil(SkyentNuclearExplosionConfig.aftermathBaseColumnsPerTick() * multiplier),
                SkyentNuclearExplosionConfig.aftermathMinColumnsPerTick(),
                SkyentNuclearExplosionConfig.aftermathMaxColumnsPerTick()
        );
        double timeBudgetMs = Mth.lerp(
                multiplier,
                SkyentNuclearExplosionConfig.aftermathLaggyMaxMillisecondsPerTick(),
                SkyentNuclearExplosionConfig.aftermathMaxMillisecondsPerTick()
        );
        return new AftermathThrottleBudget(avgTickMs, tpsEstimate, multiplier, workUnitsBudget, columnsBudget, timeBudgetMs);
    }

    private void tickCenterRadiation() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        int durationTicks = getCenterRadiationDurationTicks();
        double initialMsvPerSecond = getCenterRadiationInitialMsvPerSecond();
        double radiationRadius = getCenterRadiationRadius();
        double sourceMillisievertsPerSecond = centerRadiationSourceMsvPerSecond(
                centerRadiationTicks,
                durationTicks,
                initialMsvPerSecond
        );
        if (sourceMillisievertsPerSecond <= 0.0D) {
            return;
        }

        RadiationExposureSystem.tickPointSource(
                serverLevel,
                fixedOrigin(),
                sourceMillisievertsPerSecond,
                radiationRadius,
                1,
                false
        );
    }

    private double radiusScaleFromBaseline() {
        return Math.max(0.01D, getRadius() / NUKE_BASELINE_RADIUS);
    }

    private int getCenterRadiationDurationTicks() {
        double durationScale = Math.pow(radiusScaleFromBaseline(), CENTER_RADIATION_DURATION_RADIUS_EXPONENT);
        return Math.max(CENTER_RADIATION_MIN_DURATION_TICKS, Mth.ceil(SkyentNuclearExplosionConfig.radiationCenterBurstDurationTicks() * durationScale));
    }

    private double getCenterRadiationRadius() {
        double radiusScale = Math.pow(radiusScaleFromBaseline(), CENTER_RADIATION_RANGE_RADIUS_EXPONENT);
        return Math.max(CENTER_RADIATION_MIN_RADIUS, SkyentNuclearExplosionConfig.radiationCenterBurstRadius() * radiusScale);
    }

    private double getCenterRadiationInitialMsvPerSecond() {
        double sourceScale = Math.pow(radiusScaleFromBaseline(), CENTER_RADIATION_SOURCE_RADIUS_EXPONENT);
        return SkyentNuclearExplosionConfig.radiationCenterBurstInitialMsvPerSecond() * sourceScale;
    }

    private static double centerRadiationSourceMsvPerSecond(int ageTicks, int durationTicks, double initialMsvPerSecond) {
        if (ageTicks >= durationTicks) {
            return 0.0D;
        }

        double progress = Mth.clamp(ageTicks / (double) durationTicks, 0.0D, 1.0D);
        double curve = 1.0D - Math.log1p(progress * 9.0D) / Math.log1p(9.0D);
        return initialMsvPerSecond * Math.max(0.0D, curve);
    }

    private void forceExplosionChunks() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ChunkPos centerChunk = chunkPosition();
        retainedCenterChunk = centerChunk;
        int chunkRadius = NuclearExplosionChunkLoading.computeChunkRadius(getRadius());
        int added = NuclearExplosionChunkLoading.forceExplosionChunks(serverLevel, getChunkLoadingOwnerUuid(), centerChunk, chunkRadius, forcedExplosionChunks);
        if (!forcedExplosionChunks.contains(centerChunk)
                && NuclearExplosionChunkLoading.forceSingleChunk(
                serverLevel,
                getChunkLoadingOwnerUuid(),
                centerChunk,
                SkyentNuclearExplosionConfig.chunkLoadingTickingTickets()
        )) {
            forcedExplosionChunks.add(centerChunk);
            added++;
        }
        chunksForced = !forcedExplosionChunks.isEmpty();
    }

    private void unforceExplosionChunks() {
        unforceExplosionChunks(true);
    }

    private void unforceExplosionChunks(boolean releaseRetainedCenterChunk) {
        if (!chunksForced || forcedExplosionChunks.isEmpty()) {
            chunksForced = false;
            forcedExplosionChunks.clear();
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ChunkPos centerToKeep = releaseRetainedCenterChunk ? null : retainedCenterChunk;
        Set<ChunkPos> chunksToRelease = new HashSet<>(forcedExplosionChunks);
        if (centerToKeep != null) {
            chunksToRelease.remove(centerToKeep);
        }
        if (chunksToRelease.isEmpty()) {
            chunksForced = !forcedExplosionChunks.isEmpty();
            return;
        }
        Set<ChunkPos> releasedChunks = new HashSet<>(chunksToRelease);
        int released = NuclearExplosionChunkLoading.unforceExplosionChunks(serverLevel, getChunkLoadingOwnerUuid(), chunksToRelease);
        forcedExplosionChunks.removeAll(releasedChunks);
        chunksForced = !forcedExplosionChunks.isEmpty();
        if (releaseRetainedCenterChunk) {
            retainedCenterChunk = null;
        }
    }

    private boolean canReleaseImmediateChunks() {
        return tickCount >= SkyentNuclearExplosionConfig.chunkLoadingKeepImmediateChunksTicks()
                && (!ENABLE_NUCLEAR_BLOCK_DESTRUCTION
                || !destroyBlocks
                || (destructionPhase == DestructionPhase.COMPLETE && fluidEvaporationPass == null));
    }

    private void setFixedOrigin(double x, double y, double z) {
        originX = x;
        originY = y;
        originZ = z;
        if (!level().isClientSide) {
            explosionGroundY = computeExplosionGroundY(x, y, z);
            syncVisualOriginData();
        }
        setPos(x, y, z);
        setOldPosAndRot();
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0.0F;
    }

    private void syncVisualOriginData() {
        if (!Double.isFinite(originX) || !Double.isFinite(originY) || !Double.isFinite(originZ)) {
            return;
        }
        if (!Double.isFinite(explosionGroundY)) {
            explosionGroundY = computeExplosionGroundY(originX, originY, originZ);
        }
        entityData.set(DATA_ORIGIN_X, (float) originX);
        entityData.set(DATA_ORIGIN_Y, (float) originY);
        entityData.set(DATA_ORIGIN_Z, (float) originZ);
        entityData.set(DATA_GROUND_Y, (float) explosionGroundY);
        entityData.set(DATA_HAS_VISUAL_ORIGIN, true);
    }

    private double computeExplosionGroundY(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return Double.isFinite(y) ? y : getY();
        }
        if (!level().hasChunkAt(new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z)))) {
            return y;
        }
        int height = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        return Math.max(height, level().getMinBuildHeight());
    }

    private void syncFixedOriginFromEntityData() {
        if (!level().isClientSide || !hasSyncedExplosionOrigin()) {
            return;
        }
        originX = entityData.get(DATA_ORIGIN_X);
        originY = entityData.get(DATA_ORIGIN_Y);
        originZ = entityData.get(DATA_ORIGIN_Z);
        explosionGroundY = entityData.get(DATA_GROUND_Y);
    }

    private void stabilizeFixedOrigin() {
        if (Double.isNaN(originX) || Double.isNaN(originY) || Double.isNaN(originZ)) {
            setFixedOrigin(getX(), getY(), getZ());
            return;
        }

        noPhysics = true;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0.0F;
        setPos(originX, originY, originZ);
        setOldPosAndRot();
    }

    private Vec3 fixedOrigin() {
        if (Double.isNaN(originX) || Double.isNaN(originY) || Double.isNaN(originZ)) {
            return position();
        }
        return new Vec3(originX, originY, originZ);
    }

    private UUID getChunkLoadingOwnerUuid() {
        if (chunkLoadingOwnerUuid == null) {
            chunkLoadingOwnerUuid = getUUID();
        }
        return chunkLoadingOwnerUuid;
    }

    private UUID getAftermathChunkLoadingOwnerUuid() {
        if (aftermathChunkLoadingOwnerUuid == null) {
            UUID base = getChunkLoadingOwnerUuid();
            aftermathChunkLoadingOwnerUuid = new UUID(
                    base.getMostSignificantBits() ^ 0x5A7EAF7E2A71C9D3L,
                    base.getLeastSignificantBits() ^ 0x31C0B10C6D9E3779L
            );
        }
        return aftermathChunkLoadingOwnerUuid;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (destructionPhase != DestructionPhase.COMPLETE
                || mutationQueue != null
                || columnCollapsePass != null
                || columnCollapseMutationQueue != null
                || fluidEvaporationPass != null
                || sectionCompletionTracker != null) {
            destructionPhase = DestructionPhase.COMPLETE;
            cleanupDestructionState("entity_removed_" + reason.name());
        }
        unforceExplosionChunks();
        super.remove(reason);
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    private void applyInitialThermalFlash() {
        Vec3 center = fixedOrigin();
        double thermalFlashRadius = getThermalFlashRadius();
        double radiusSqr = thermalFlashRadius * thermalFlashRadius;
        AABB search = new AABB(center, center).inflate(thermalFlashRadius);
        int entitiesChecked = 0;
        int skippedNoLineOfSight = 0;
        int skippedCreativeSpectator = 0;
        int entitiesIgnited = 0;
        int entitiesDamaged = 0;
        float maxDamageApplied = 0.0F;

        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, search, entity -> entity.isAlive() && !entity.isRemoved())) {
            entitiesChecked++;
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
                skippedCreativeSpectator++;
                continue;
            }

            Vec3 offset = entity.position().subtract(center);
            double distanceSqr = offset.lengthSqr();
            if (distanceSqr > radiusSqr) {
                continue;
            }
            if (!hasThermalFlashLineOfSight(entity)) {
                skippedNoLineOfSight++;
                continue;
            }

            double distance = Math.max(0.0D, Math.sqrt(distanceSqr));
            double normalizedDistance = Mth.clamp(distance / thermalFlashRadius, 0.0D, 1.0D);
            double factor = Math.pow(1.0D - normalizedDistance, THERMAL_FLASH_DAMAGE_POWER);
            if (factor <= 0.0D) {
                continue;
            }

            float fireDamage = THERMAL_FLASH_MIN_FIRE_DAMAGE
                    + (THERMAL_FLASH_MAX_FIRE_DAMAGE - THERMAL_FLASH_MIN_FIRE_DAMAGE) * (float) factor;
            int fireSeconds = Mth.ceil(
                    THERMAL_FLASH_MIN_FIRE_SECONDS
                            + (THERMAL_FLASH_MAX_FIRE_SECONDS - THERMAL_FLASH_MIN_FIRE_SECONDS) * factor
            );
            if (entity.hurt(level().damageSources().onFire(), fireDamage)) {
                entitiesDamaged++;
                maxDamageApplied = Math.max(maxDamageApplied, fireDamage);
            }
            entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), fireSeconds * 20));
            entitiesIgnited++;
        }

    }

    private boolean hasThermalFlashLineOfSight(LivingEntity entity) {
        Vec3 origin = fixedOrigin();
        Vec3 target = entity.getEyePosition();
        ClipContext context = new ClipContext(
                origin,
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
        );
        BlockHitResult result = level().clip(context);
        return result.getType() == HitResult.Type.MISS;
    }

    private double getThermalFlashRadius() {
        return Math.max(THERMAL_FLASH_MIN_RADIUS, getRadius() * THERMAL_FLASH_RADIUS_MULTIPLIER);
    }

    private void tickClientEffects() {
        tickMushroomCloudSimulation();
        tickCloudlets();
        boolean shockwaveVisualTick = tickCount % SHOCKWAVE_VISUAL_INTERVAL_TICKS == 0;
        if (shockwaveVisualTick) {
            spawnShockwaveCloudlets();
        }
        if (isActiveBlastPhaseVisual()) {
            spawnActiveBlastSweepSmoke();
        }
        tickClientShockwaveArrivalSound();
    }

    private void tickMushroomCloudSimulation() {
        if (!shouldSpawnCloud()) {
            mushroomCloudSimulation = null;
            return;
        }
        if (level().isClientSide && !hasValidVisualSyncData()) {
            return;
        }
        Vec3 visualOrigin = getExplosionOrigin();
        if (mushroomCloudSimulation == null) {
            mushroomCloudSimulation = new NuclearMushroomCloudSimulation(getVisualSeed(), getRadius());
            mushroomCloudSimulation.setKnownGroundYRelative(getExplosionGroundY() - visualOrigin.y);
            int visualAge = getVisualAge();
            if (visualAge > 0 && level().isClientSide) {
                mushroomCloudSimulation.skipToAge(level(), visualOrigin, visualAge);
            }
        }
        mushroomCloudSimulation.tick(level(), visualOrigin);
    }

    private void tickCloudlets() {
        Vec3 visualOrigin = hasValidVisualSyncData() ? getExplosionOrigin() : fixedOrigin();
        Iterator<NuclearCloudlet> iterator = cloudlets.iterator();
        while (iterator.hasNext()) {
            NuclearCloudlet cloudlet = iterator.next();
            cloudlet.tick(level(), visualOrigin);
            if (cloudlet.isExpired()) {
                iterator.remove();
            }
        }
    }

    private void spawnShockwaveCloudlets() {
        shockwaveSpawnMethodCalls++;
        double shockwaveRadius = getShockwaveRadius();
        double maxRadius = getShockwaveMaxRadius();
        if (shockwaveRadius <= 0.0D || shockwaveRadius > maxRadius) {
            return;
        }
        shockwaveSpawnConditionPasses++;

        float distanceFactor = (float) Mth.clamp(shockwaveRadius / maxRadius, 0.0D, 1.0D);
        float baseSize = Mth.clamp(2.5F + (float) shockwaveRadius * 0.025F, 3.0F, 8.0F);
        double desiredSpacing = Math.max(2.5D, baseSize * 0.75D);
        int count = Mth.clamp((int) (Math.PI * 2.0D * shockwaveRadius / desiredSpacing), SHOCKWAVE_MIN_PUFFS, SHOCKWAVE_MAX_PUFFS);
        int visualAge = getVisualAge();
        RandomSource random = RandomSource.create(getVisualSeed() ^ 0x5DEECE66DL ^ visualAge * 104729L);
        int addedThisTick = 0;

        for (int index = 0; index < count; index++) {
            shockwaveCloudletsAttempted++;
            if (cloudlets.size() >= MAX_CLOUDLETS) {
                removeOldestCloudletForShockwave();
                if (cloudlets.size() >= MAX_CLOUDLETS) {
                    shockwaveCloudletsSkipped += count - index;
                    break;
                }
            }

            double angle = Math.PI * 2.0D * index / count + random.nextDouble() * 0.05D;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double puffRadius = shockwaveRadius - random.nextDouble() * SHOCKWAVE_BAND_BEHIND_BLOCKS + random.nextDouble() * SHOCKWAVE_BAND_AHEAD_BLOCKS;
            puffRadius = Mth.clamp(puffRadius, 0.0D, maxRadius);
            double worldX = getX() + cos * puffRadius;
            double worldZ = getZ() + sin * puffRadius;
            SurfaceSample surface = findShockwaveSurfaceTopY(worldX, worldZ, getY() + 1.0D);
            if (surface.chunkMissing()) {
                shockwaveChunkMissing++;
            } else if (surface.found()) {
                shockwaveSurfaceFound++;
            } else {
                shockwaveSurfaceNotFound++;
            }
            double surfaceY = surface.surfaceY();
            if (!Double.isFinite(surfaceY) || surfaceY <= level().getMinBuildHeight() || surfaceY >= level().getMaxBuildHeight() + 32.0D) {
                surfaceY = getY() + 1.0D;
                shockwaveInvalidY++;
            }
            double spawnY = surfaceY + 2.0D + random.nextDouble() * 1.5D;
            addShockwaveCloudlet(random, worldX, spawnY, worldZ, cos, sin, baseSize, distanceFactor);
            addedThisTick++;
        }

        if (addedThisTick == 0 && count > 0) {
            spawnFallbackShockwaveRing(count, shockwaveRadius, baseSize, distanceFactor, random);
        }
    }

    private void spawnActiveBlastSweepSmoke() {
        Vec3 origin = getExplosionOrigin();
        double activeRadius = activeNuclearDamageRadius();
        double speedScale = Mth.clamp(getRadius() / NUKE_BASELINE_RADIUS, 0.65D, 1.6D);
        int targetCount = Mth.clamp((int) (ACTIVE_BLAST_SWEEP_CLOUDLETS_PER_TICK * Math.sqrt(speedScale)), 8, 48);
        int added = 0;
        int visualAge = getVisualAge();
        RandomSource random = RandomSource.create(getVisualSeed() ^ 0x6A09E667F3BCC909L ^ visualAge * 130_363L);

        for (int attempt = 0; attempt < ACTIVE_BLAST_SWEEP_SPAWN_ATTEMPTS_PER_TICK && added < targetCount; attempt++) {
            activeBlastSweepAttempts++;
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = Math.pow(random.nextDouble(), 0.55D) * activeRadius;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double worldX = origin.x + cos * distance;
            double worldZ = origin.z + sin * distance;
            SurfaceSample surface = findActiveBlastSweepSurfaceTopY(worldX, worldZ);
            if (surface.chunkMissing()) {
                activeBlastSweepSkippedChunkMissing++;
                continue;
            }
            if (!surface.found() && !surface.usedHeightmapFallback()) {
                activeBlastSweepSkippedSurface++;
                continue;
            }

            if (cloudlets.size() >= MAX_CLOUDLETS) {
                removeOldestNonShockwaveCloudlet();
                if (cloudlets.size() >= MAX_CLOUDLETS) {
                    activeBlastSweepSkippedSurface++;
                    break;
                }
            }

            double spawnY = surface.surfaceY() + 0.8D + random.nextDouble() * 2.0D;
            addActiveBlastSweepCloudlet(random, worldX, spawnY, worldZ, cos, sin, speedScale);
            added++;
        }
    }

    private void addActiveBlastSweepCloudlet(
            RandomSource random,
            double worldX,
            double worldY,
            double worldZ,
            double cos,
            double sin,
            double speedScale
    ) {
        int lifetime = ACTIVE_BLAST_SWEEP_LIFETIME_MIN_TICKS + random.nextInt(ACTIVE_BLAST_SWEEP_LIFETIME_RANDOM_TICKS + 1);
        float baseSize = Mth.lerp(random.nextFloat(), ACTIVE_BLAST_SWEEP_MIN_SIZE, ACTIVE_BLAST_SWEEP_MAX_SIZE);
        float growSize = Mth.lerp(random.nextFloat(), ACTIVE_BLAST_SWEEP_MIN_GROW_SIZE, ACTIVE_BLAST_SWEEP_MAX_GROW_SIZE);
        double speed = Mth.lerp(random.nextDouble(), ACTIVE_BLAST_SWEEP_OUTWARD_SPEED_MIN, ACTIVE_BLAST_SWEEP_OUTWARD_SPEED_MAX) * speedScale;
        double side = (random.nextDouble() * 2.0D - 1.0D) * ACTIVE_BLAST_SWEEP_RANDOM_SIDE_SPEED * speedScale;
        double velocityX = cos * speed + -sin * side;
        double velocityZ = sin * speed + cos * side;
        double velocityY = Mth.lerp(random.nextDouble(), ACTIVE_BLAST_SWEEP_UPWARD_SPEED_MIN, ACTIVE_BLAST_SWEEP_UPWARD_SPEED_MAX) * speedScale;
        cloudlets.add(new NuclearCloudlet(
                NuclearCloudletType.ACTIVE_BLAST_SWEEP_SMOKE,
                worldX - getX(),
                worldY - getY(),
                worldZ - getZ(),
                lifetime,
                baseSize,
                growSize,
                random.nextLong(),
                velocityX,
                velocityY,
                velocityZ,
                0.0D,
                0.0D
        ));
        activeBlastSweepAdded++;
    }

    private SurfaceSample findActiveBlastSweepSurfaceTopY(double worldX, double worldZ) {
        int blockX = Mth.floor(worldX);
        int blockZ = Mth.floor(worldZ);
        if (!level().hasChunkAt(new BlockPos(blockX, Mth.floor(getY()), blockZ))) {
            return new SurfaceSample(Double.NaN, false, true, false, false, false, blockX, blockZ, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        }

        int minY = level().getMinBuildHeight();
        int maxY = level().getMaxBuildHeight() - 1;
        int heightmapY = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        boolean heightmapValid = heightmapY > minY + 1 && heightmapY <= maxY + 1;
        int startY = heightmapValid ? Mth.clamp(heightmapY + SHOCKWAVE_SURFACE_SCAN_PADDING, minY, maxY) : maxY;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos(blockX, startY, blockZ);
        CollisionContext context = CollisionContext.empty();

        for (int y = startY; y >= minY; y--) {
            mutablePos.set(blockX, y, blockZ);
            BlockState state = level().getBlockState(mutablePos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            if (!state.getCollisionShape(level(), mutablePos, context).isEmpty()) {
                return new SurfaceSample(y + 1.0D, true, false, heightmapValid, false, false, blockX, blockZ, heightmapY, startY, y);
            }
        }

        if (heightmapValid) {
            return new SurfaceSample(heightmapY, false, false, true, true, false, blockX, blockZ, heightmapY, startY, Integer.MIN_VALUE);
        }
        return new SurfaceSample(Double.NaN, false, false, false, false, false, blockX, blockZ, heightmapY, startY, Integer.MIN_VALUE);
    }

    private void spawnFallbackShockwaveRing(int expectedCount, double shockwaveRadius, float baseSize, float distanceFactor, RandomSource random) {
        int fallbackCount = Math.min(expectedCount, SHOCKWAVE_MIN_PUFFS);
        for (int index = 0; index < fallbackCount; index++) {
            if (cloudlets.size() >= MAX_CLOUDLETS) {
                removeOldestCloudletForShockwave();
                if (cloudlets.size() >= MAX_CLOUDLETS) {
                    shockwaveCloudletsSkipped += fallbackCount - index;
                    return;
                }
            }

            double angle = Math.PI * 2.0D * index / fallbackCount;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double worldX = getX() + cos * shockwaveRadius;
            double worldZ = getZ() + sin * shockwaveRadius;
            addShockwaveCloudlet(random, worldX, getY() + 2.0D, worldZ, cos, sin, baseSize, distanceFactor);
        }
    }

    private void addShockwaveCloudlet(
            RandomSource random,
            double worldX,
            double worldY,
            double worldZ,
            double cos,
            double sin,
            float baseSize,
            float distanceFactor
    ) {
        int lifetime = SHOCKWAVE_LIFETIME_MIN_TICKS + random.nextInt(SHOCKWAVE_LIFETIME_MAX_TICKS - SHOCKWAVE_LIFETIME_MIN_TICKS + 1);
        float size = baseSize * (0.82F + random.nextFloat() * 0.36F);
        double speed = 0.35D + random.nextDouble() * 0.40D + distanceFactor * 0.10D;
        double acceleration = 0.015D + random.nextDouble() * 0.015D;
        NuclearCloudlet puff = new NuclearCloudlet(
                NuclearCloudletType.SHOCKWAVE,
                worldX - getX(),
                worldY - getY(),
                worldZ - getZ(),
                lifetime,
                size,
                size + 2.0F + random.nextFloat() * 3.0F,
                random.nextLong(),
                cos * speed,
                0.03D + random.nextDouble() * 0.07D,
                sin * speed,
                cos * acceleration,
                sin * acceleration
        );
        cloudlets.add(puff);
        shockwaveCloudletsAdded++;
    }

    private SurfaceSample findShockwaveSurfaceTopY(double worldX, double worldZ, double fallbackY) {
        int blockX = Mth.floor(worldX);
        int blockZ = Mth.floor(worldZ);
        if (!level().hasChunkAt(new BlockPos(blockX, Mth.floor(getY()), blockZ))) {
            shockwaveFallbackEntityY++;
            return new SurfaceSample(fallbackY, false, true, true, false, true, blockX, blockZ, level().getMinBuildHeight(), level().getMaxBuildHeight() - 1, Integer.MIN_VALUE);
        }

        int minY = level().getMinBuildHeight();
        int maxY = level().getMaxBuildHeight() - 1;
        int heightmapY = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        boolean heightmapValid = heightmapY > minY + 1 && heightmapY <= maxY + 1;
        if (!heightmapValid) {
            shockwaveHeightmapInvalid++;
        }
        int startY = heightmapValid ? Mth.clamp(heightmapY + SHOCKWAVE_SURFACE_SCAN_PADDING, minY, maxY) : maxY;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos(blockX, startY, blockZ);
        CollisionContext context = CollisionContext.empty();

        for (int y = startY; y >= minY; y--) {
            mutablePos.set(blockX, y, blockZ);
            BlockState state = level().getBlockState(mutablePos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            if (!state.getCollisionShape(level(), mutablePos, context).isEmpty()) {
                shockwaveFoundSurfaceTopDown++;
                return new SurfaceSample(y + 1.0D, true, false, heightmapValid, false, false, blockX, blockZ, heightmapY, startY, y);
            }
        }

        if (heightmapY > minY + 1) {
            shockwaveFallbackHeightmap++;
            return new SurfaceSample(heightmapY, false, false, heightmapValid, true, false, blockX, blockZ, heightmapY, startY, Integer.MIN_VALUE);
        }

        shockwaveFallbackEntityY++;
        return new SurfaceSample(fallbackY, false, false, heightmapValid, false, true, blockX, blockZ, heightmapY, startY, Integer.MIN_VALUE);
    }

    private record SurfaceSample(
            double surfaceY,
            boolean found,
            boolean chunkMissing,
            boolean heightmapValid,
            boolean usedHeightmapFallback,
            boolean usedEntityFallback,
            int blockX,
            int blockZ,
            int heightmapY,
            int startY,
            int foundY
    ) {
    }

    private int nonShockwaveCloudletCount() {
        int count = 0;
        for (NuclearCloudlet cloudlet : cloudlets) {
            if (!cloudlet.isShockwaveVisual()) {
                count++;
            }
        }
        return count;
    }

    private void removeOldestNonShockwaveCloudlet() {
        for (Iterator<NuclearCloudlet> iterator = cloudlets.iterator(); iterator.hasNext(); ) {
            if (!iterator.next().isShockwaveVisual()) {
                iterator.remove();
                return;
            }
        }
    }

    private void removeOldestCloudletForShockwave() {
        int oldestShockwaveIndex = -1;
        int oldestShockwaveAge = -1;
        for (int index = 0; index < cloudlets.size(); index++) {
            NuclearCloudlet cloudlet = cloudlets.get(index);
            if (!cloudlet.isShockwaveVisual()) {
                cloudlets.remove(index);
                return;
            }
            if (cloudlet.type() == NuclearCloudletType.SHOCKWAVE && cloudlet.age() > oldestShockwaveAge) {
                oldestShockwaveAge = cloudlet.age();
                oldestShockwaveIndex = index;
            }
        }
        if (oldestShockwaveIndex >= 0) {
            cloudlets.remove(oldestShockwaveIndex);
        }
    }

    private int shockwaveCloudletCount() {
        int count = 0;
        for (NuclearCloudlet cloudlet : cloudlets) {
            if (cloudlet.type() == NuclearCloudletType.SHOCKWAVE) {
                count++;
            }
        }
        return count;
    }

    private int activeBlastSweepCloudletCount() {
        int count = 0;
        for (NuclearCloudlet cloudlet : cloudlets) {
            if (cloudlet.type() == NuclearCloudletType.ACTIVE_BLAST_SWEEP_SMOKE) {
                count++;
            }
        }
        return count;
    }

    private void tickClientShockwaveArrivalSound() {
        try {
            Class<?> soundClient = Class.forName("com.skyeshade.skyent.client.effect.NukeShockwaveSoundClient");
            soundClient.getMethod("tick", NuclearExplosionEntity.class).invoke(null, this);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to tick client nuke shockwave arrival sound", exception);
        }
    }

    private void tickShockwaveServer() {
        double currentRadius = getShockwaveRadius();
        double damageRadius = getShockwaveDamageRadius();
        if (currentRadius <= 0.0D || currentRadius > damageRadius + SHOCKWAVE_DAMAGE_BAND_WIDTH) {
            return;
        }

        double previousRadius = Math.max(0.0D, (tickCount - 1) * SHOCKWAVE_SPEED_BLOCKS_PER_TICK);
        damageEntitiesInWavefront(previousRadius, currentRadius);
    }

    private void damageEntitiesInWavefront(double previousRadius, double currentRadius) {
        double damageRadius = getShockwaveDamageRadius();
        double searchRadius = Math.min(currentRadius + SHOCKWAVE_DAMAGE_BAND_WIDTH, damageRadius + SHOCKWAVE_DAMAGE_BAND_WIDTH);
        AABB search = new AABB(
                getX() - searchRadius,
                getY() - 128.0D,
                getZ() - searchRadius,
                getX() + searchRadius,
                getY() + 256.0D,
                getZ() + searchRadius
        );

        ServerLevel serverLevel = (ServerLevel) level();
        int damagedThisTick = 0;
        int coveredThisTick = 0;
        float lastDamage = 0.0F;
        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, search, entity -> !entity.isRemoved())) {
            if (shockwaveDamagedEntities.contains(entity.getUUID())) {
                continue;
            }
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
                continue;
            }

            double distance = horizontalDistanceFromCenter(entity.position());
            if (distance <= previousRadius || distance > currentRadius + SHOCKWAVE_DAMAGE_BAND_WIDTH || distance > damageRadius + SHOCKWAVE_DAMAGE_BAND_WIDTH) {
                continue;
            }

            double normalized = Mth.clamp(distance / damageRadius, 0.0D, 1.0D);
            double damageFactor = Math.pow(1.0D - normalized, SHOCKWAVE_DAMAGE_FALLOFF_POWER);
            float damage = (float) (1000.0D * damageFactor * SHOCKWAVE_DAMAGE_MULTIPLIER);
            if (damage < SHOCKWAVE_MIN_DAMAGE) {
                continue;
            }
            if (isUnderShockwaveCover(entity)) {
                coveredThisTick++;
                shockwaveDamagedEntities.add(entity.getUUID());
                continue;
            }

            entity.hurt(ModDamageSources.shockwave(serverLevel), damage);
            Vec3 knockback = entity.position().subtract(position());
            Vec3 horizontal = new Vec3(knockback.x, 0.0D, knockback.z);
            if (horizontal.lengthSqr() > 1.0E-6D) {
                Vec3 direction = horizontal.normalize();
                double knockbackFactor = Math.pow(1.0D - normalized, SHOCKWAVE_KNOCKBACK_FALLOFF_POWER);
                double strength = 3.0D * knockbackFactor;
                entity.push(direction.x * strength, 0.65D * knockbackFactor, direction.z * strength);
                entity.hurtMarked = true;
            }
            shockwaveDamagedEntities.add(entity.getUUID());
            damagedThisTick++;
            lastDamage = damage;
        }

    }

    private void tickNuclearExplosionEntityDamage() {
        if (!(level() instanceof ServerLevel serverLevel)
                || tickCount % NUCLEAR_EXPLOSION_DAMAGE_INTERVAL_TICKS != 0
                || !isActiveBlastPhase()) {
            return;
        }

        Vec3 center = fixedOrigin();
        double damageRadius = getNuclearExplosionDamageRadius();
        double radiusSqr = damageRadius * damageRadius;
        AABB search = new AABB(
                center.x - damageRadius,
                center.y - damageRadius,
                center.z - damageRadius,
                center.x + damageRadius,
                center.y + damageRadius,
                center.z + damageRadius
        );

        int checked = 0;
        int covered = 0;
        int damaged = 0;
        int ignited = 0;
        float maxDamageApplied = 0.0F;
        float maxDamage = getRadius() * NUCLEAR_EXPLOSION_DAMAGE_PER_RADIUS;
        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, search, entity -> !entity.isRemoved())) {
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            Vec3 offset = entity.position().subtract(center);
            double distanceSqr = offset.lengthSqr();
            if (distanceSqr > radiusSqr) {
                continue;
            }

            checked++;
            if (hasEnoughBlastCover(entity, center, NUCLEAR_EXPLOSION_COVER_HARDNESS_THRESHOLD)) {
                covered++;
                continue;
            }

            double distance = Math.sqrt(distanceSqr);
            double normalized = Mth.clamp(distance / damageRadius, 0.0D, 1.0D);
            double damageFactor = 1.0D - normalized;
            float damage = Math.max(NUCLEAR_EXPLOSION_MIN_DAMAGE, maxDamage * (float) damageFactor);
            if (damage <= 0.0F) {
                continue;
            }
            if (entity.hurt(ModDamageSources.nuclearExplosion(serverLevel), damage)) {
                damaged++;
                maxDamageApplied = Math.max(maxDamageApplied, damage);
            }

            applyNuclearExplosionKnockback(entity, center, damageFactor);
            double fireRadius = Math.min(NUCLEAR_EXPLOSION_CLOSE_FIRE_RADIUS, damageRadius);
            if (distance <= fireRadius || damage >= maxDamage * 0.65F) {
                entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), NUCLEAR_EXPLOSION_FIRE_SECONDS * 20));
                ignited++;
            }
        }

    }

    private boolean isExplosionDestructionComplete() {
        return !ENABLE_NUCLEAR_BLOCK_DESTRUCTION || !destroyBlocks || destructionPhase == DestructionPhase.COMPLETE;
    }

    private boolean isActiveBlastPhase() {
        if (!ENABLE_NUCLEAR_BLOCK_DESTRUCTION || !destroyBlocks) {
            return false;
        }
        if (!primaryDestructionComplete) {
            return true;
        }
        return primaryDestructionCompleteAge >= 0
                && tickCount - primaryDestructionCompleteAge <= NUCLEAR_EXPLOSION_POST_DESTRUCTION_DAMAGE_TICKS;
    }

    private boolean isActiveBlastPhaseVisual() {
        return entityData.get(DATA_ACTIVE_BLAST_PHASE);
    }

    private void markPrimaryDestructionComplete() {
        if (!primaryDestructionComplete) {
            primaryDestructionComplete = true;
            primaryDestructionCompleteAge = tickCount;
        } else if (primaryDestructionCompleteAge < 0) {
            primaryDestructionCompleteAge = tickCount;
        }
    }

    private double getNuclearExplosionDamageRadius() {
        return activeNuclearDamageRadius();
    }

    private double activeNuclearDamageRadius() {
        return getRadius() * NUCLEAR_EXPLOSION_ACTIVE_DAMAGE_RADIUS_MULTIPLIER;
    }

    private void applyNuclearExplosionKnockback(LivingEntity entity, Vec3 center, double damageFactor) {
        Vec3 away = entity.position().subtract(center);
        Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
        if (horizontal.lengthSqr() <= 1.0E-6D) {
            horizontal = new Vec3(entity.getRandom().nextDouble() - 0.5D, 0.0D, entity.getRandom().nextDouble() - 0.5D);
        }
        if (horizontal.lengthSqr() <= 1.0E-6D) {
            return;
        }
        Vec3 direction = horizontal.normalize();
        double horizontalStrength = NUCLEAR_EXPLOSION_MAX_KNOCKBACK * damageFactor + 0.12D;
        double verticalStrength = NUCLEAR_EXPLOSION_MAX_VERTICAL_KNOCKBACK * damageFactor;
        entity.push(direction.x * horizontalStrength, verticalStrength, direction.z * horizontalStrength);
        entity.hurtMarked = true;
    }

    private boolean isUnderShockwaveCover(LivingEntity entity) {
        int x = Mth.floor(entity.getX());
        int z = Mth.floor(entity.getZ());
        int startY = Mth.floor(entity.getEyeY()) + 1;
        int endY = Math.min(level().getMaxBuildHeight() - 1, startY + SHOCKWAVE_COVER_SCAN_BLOCKS);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos(x, startY, z);
        CollisionContext context = CollisionContext.of(entity);
        for (int y = startY; y <= endY; y++) {
            mutablePos.set(x, y, z);
            BlockState state = level().getBlockState(mutablePos);
            if (!state.isAir()
                    && state.getBlock().getExplosionResistance() > 0.2F
                    && !state.getCollisionShape(level(), mutablePos, context).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEnoughBlastCover(LivingEntity entity, Vec3 center, double threshold) {
        Vec3 start = entity.getEyePosition();
        Vec3 delta = center.subtract(start);
        double length = delta.length();
        if (length <= 1.0E-6D) {
            return false;
        }
        Vec3 step = delta.normalize().scale(NUCLEAR_EXPLOSION_COVER_RAY_STEP_BLOCKS);
        int samples = Mth.ceil(length / NUCLEAR_EXPLOSION_COVER_RAY_STEP_BLOCKS);
        double accumulatedHardness = 0.0D;
        long previousBlock = Long.MIN_VALUE;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        CollisionContext context = CollisionContext.of(entity);
        for (int index = 1; index <= samples; index++) {
            Vec3 sample = start.add(step.scale(index));
            mutablePos.set(Mth.floor(sample.x), Mth.floor(sample.y), Mth.floor(sample.z));
            long blockKey = mutablePos.asLong();
            if (blockKey == previousBlock) {
                continue;
            }
            previousBlock = blockKey;
            if (!level().isInWorldBounds(mutablePos)) {
                continue;
            }

            BlockState state = level().getBlockState(mutablePos);
            if (state.isAir() || state.getCollisionShape(level(), mutablePos, context).isEmpty()) {
                continue;
            }

            float resistance = state.getBlock().getExplosionResistance();
            if (resistance < 0.0F || resistance >= threshold) {
                return true;
            }
            accumulatedHardness += Math.max(0.0F, resistance);
            if (accumulatedHardness >= threshold) {
                return true;
            }
        }
        return false;
    }

    private double horizontalDistanceFromCenter(Vec3 position) {
        double dx = position.x - getX();
        double dz = position.z - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double getShockwaveRadius() {
        return getVisualAge() * SHOCKWAVE_SPEED_BLOCKS_PER_TICK;
    }

    private double getShockwaveMaxRadius() {
        return getRadius() * SHOCKWAVE_MAX_RADIUS_MULTIPLIER;
    }

    private double getShockwaveDamageRadius() {
        return getRadius() * SHOCKWAVE_DAMAGE_RADIUS_MULTIPLIER;
    }

    public List<NuclearCloudlet> getCloudlets() {
        return cloudlets;
    }

    public List<NuclearMushroomCloudSimulation.MushroomCloudlet> getMushroomCloudlets() {
        return mushroomCloudSimulation == null ? List.of() : mushroomCloudSimulation.cloudlets();
    }

    public long getVisualSeed() {
        long seed = entityData.get(DATA_VISUAL_SEED);
        return seed == 0L ? getUUID().getLeastSignificantBits() : seed;
    }

    public int getVisualAge() {
        int syncedAge = entityData.get(DATA_VISUAL_AGE);
        return level().isClientSide ? Math.max(syncedAge, tickCount) : tickCount;
    }

    public float getVisualAge(float partialTick) {
        return getVisualAge() + partialTick;
    }

    public boolean hasSyncedExplosionOrigin() {
        return entityData.get(DATA_HAS_VISUAL_ORIGIN);
    }

    public boolean hasValidVisualSyncData() {
        return getRadius() > 0.0F
                && getVisualSeed() != 0L
                && hasSyncedExplosionOrigin()
                && Double.isFinite(getExplosionGroundY());
    }

    public Vec3 getExplosionOrigin() {
        if (hasSyncedExplosionOrigin()) {
            return new Vec3(
                    entityData.get(DATA_ORIGIN_X),
                    entityData.get(DATA_ORIGIN_Y),
                    entityData.get(DATA_ORIGIN_Z)
            );
        }
        return fixedOrigin();
    }

    public double getExplosionGroundY() {
        if (hasSyncedExplosionOrigin()) {
            return entityData.get(DATA_GROUND_Y);
        }
        if (!Double.isFinite(explosionGroundY)) {
            explosionGroundY = computeExplosionGroundY(originX, originY, originZ);
        }
        return explosionGroundY;
    }

    public boolean shouldSpawnCloud() {
        return entityData.get(DATA_SPAWN_CLOUD);
    }

    public boolean shouldFlashSky() {
        return entityData.get(DATA_FLASH_SKY);
    }

    public float getVisualRayScale() {
        double radiusScale = Math.max(0.05D, getRadius() / NUKE_BASELINE_RADIUS);
        return (float) (RAY_SCALE * Math.pow(radiusScale, NUKE_VISUAL_RAY_SCALE_POWER));
    }

    public float getRadius() {
        return entityData.get(DATA_RADIUS);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        strength = compound.getFloat("Strength");
        if (strength <= 0.0F) {
            strength = VANILLA_EXPLOSION_STRENGTH;
        }
        radius = compound.contains("Radius") ? compound.getFloat("Radius") : DEFAULT_NUKE_RADIUS;
        entityData.set(DATA_RADIUS, radius);
        destroyBlocks = !compound.contains("DestroyBlocks") || compound.getBoolean("DestroyBlocks");
        entityData.set(DATA_SPAWN_CLOUD, !compound.contains("SpawnCloud") || compound.getBoolean("SpawnCloud"));
        entityData.set(DATA_FLASH_SKY, !compound.contains("FlashSky") || compound.getBoolean("FlashSky"));
        playSounds = !compound.contains("PlaySounds") || compound.getBoolean("PlaySounds");
        explosionDone = compound.getBoolean("ExplosionDone");
        centerRadiationTicks = compound.contains("CenterRadiationTicks") ? compound.getInt("CenterRadiationTicks") : 0;
        appliedInitialThermalFlash = compound.contains("AppliedInitialThermalFlash")
                ? compound.getBoolean("AppliedInitialThermalFlash")
                : compound.getBoolean("AppliedEntityBlastImpulse");
        destructionPhase = readDestructionPhase(compound.getString("DestructionPhase"));
        destructionTicks = compound.contains("DestructionTicks") ? compound.getInt("DestructionTicks") : 0;
        primaryDestructionComplete = compound.contains("PrimaryDestructionComplete")
                ? compound.getBoolean("PrimaryDestructionComplete")
                : destructionPhase == DestructionPhase.COMPLETE;
        primaryDestructionCompleteAge = compound.contains("PrimaryDestructionCompleteAge")
                ? compound.getInt("PrimaryDestructionCompleteAge")
                : -1;
        if (compound.contains("VisualAge")) {
            tickCount = Math.max(0, compound.getInt("VisualAge"));
            entityData.set(DATA_VISUAL_AGE, tickCount);
        }
        if (!SAVE_NUKE_DESTRUCTION_PROGRESS && destructionPhase != DestructionPhase.COMPLETE) {
            // TODO: Persist planner ray index and compact section masks when large NBT writes are made configurable.
            destructionPhase = DestructionPhase.NOT_STARTED;
            destructionTicks = 0;
        }
        if (compound.hasUUID("ChunkLoadingOwnerUuid")) {
            chunkLoadingOwnerUuid = compound.getUUID("ChunkLoadingOwnerUuid");
        } else {
            chunkLoadingOwnerUuid = null;
        }
        originX = compound.contains("OriginX") ? compound.getDouble("OriginX") : getX();
        originY = compound.contains("OriginY") ? compound.getDouble("OriginY") : getY();
        originZ = compound.contains("OriginZ") ? compound.getDouble("OriginZ") : getZ();
        explosionGroundY = compound.contains("ExplosionGroundY") ? compound.getDouble("ExplosionGroundY") : computeExplosionGroundY(originX, originY, originZ);
        setFixedOrigin(originX, originY, originZ);
        syncVisualOriginData();
        entityData.set(DATA_ACTIVE_BLAST_PHASE, isActiveBlastPhase());
        entityData.set(DATA_VISUAL_SEED, compound.contains("VisualSeed") ? compound.getLong("VisualSeed") : level().random.nextLong());
        if (compound.hasUUID("SourceUuid")) {
            sourceUuid = compound.getUUID("SourceUuid");
        } else {
            sourceUuid = null;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putFloat("Strength", strength);
        compound.putFloat("Radius", getRadius());
        compound.putBoolean("DestroyBlocks", destroyBlocks);
        compound.putBoolean("SpawnCloud", shouldSpawnCloud());
        compound.putBoolean("FlashSky", shouldFlashSky());
        compound.putBoolean("PlaySounds", playSounds);
        compound.putBoolean("ExplosionDone", explosionDone);
        compound.putInt("CenterRadiationTicks", centerRadiationTicks);
        compound.putBoolean("AppliedInitialThermalFlash", appliedInitialThermalFlash);
        compound.putString("DestructionPhase", destructionPhase.name());
        compound.putInt("DestructionTicks", destructionTicks);
        compound.putBoolean("PrimaryDestructionComplete", primaryDestructionComplete);
        compound.putInt("PrimaryDestructionCompleteAge", primaryDestructionCompleteAge);
        compound.putInt("VisualAge", getVisualAge());
        compound.putUUID("ChunkLoadingOwnerUuid", getChunkLoadingOwnerUuid());
        Vec3 origin = fixedOrigin();
        compound.putDouble("OriginX", origin.x);
        compound.putDouble("OriginY", origin.y);
        compound.putDouble("OriginZ", origin.z);
        compound.putDouble("ExplosionGroundY", getExplosionGroundY());
        compound.putLong("VisualSeed", getVisualSeed());
        if (sourceUuid != null) {
            compound.putUUID("SourceUuid", sourceUuid);
        }
    }

    private static DestructionPhase readDestructionPhase(String name) {
        if (name == null || name.isBlank()) {
            return DestructionPhase.NOT_STARTED;
        }
        try {
            return DestructionPhase.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return DestructionPhase.NOT_STARTED;
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 2048.0D * 2048.0D;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        double horizontalRange = Math.max(getShockwaveMaxRadius() + 128.0D, 1024.0D);
        return new AABB(
                getX() - horizontalRange,
                getY() - 64.0D,
                getZ() - horizontalRange,
                getX() + horizontalRange,
                getY() + 512.0D,
                getZ() + horizontalRange
        );
    }

    public enum NuclearCloudletType {
        SHOCKWAVE,
        ACTIVE_BLAST_SWEEP_SMOKE
    }

    private enum DestructionPhase {
        NOT_STARTED,
        PLANNING,
        ASYNC_PLANNING,
        MUTATING,
        COLUMN_COLLAPSE_PLANNING,
        COLUMN_COLLAPSE_MUTATING,
        COMPLETE
    }

    private record AftermathThrottleBudget(
            double avgTickMs,
            double tpsEstimate,
            double multiplier,
            int workUnitsBudget,
            int columnsBudget,
            double timeBudgetMs
    ) {
    }

    public static final class NuclearCloudlet {
        private final NuclearCloudletType type;
        private final int lifetime;
        private final float startSize;
        private final float growSize;
        private final long seed;
        private double x;
        private double y;
        private double z;
        private double velocityX;
        private double velocityY;
        private double velocityZ;
        private double accelerationX;
        private double accelerationZ;
        private double prevX;
        private double prevY;
        private double prevZ;
        private int age;
        private boolean collided;

        private NuclearCloudlet(NuclearCloudletType type, double x, double y, double z, int lifetime, float startSize, float growSize, long seed) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.prevX = x;
            this.prevY = y;
            this.prevZ = z;
            this.lifetime = lifetime;
            this.startSize = startSize;
            this.growSize = growSize;
            this.seed = seed;
        }

        private NuclearCloudlet(
                NuclearCloudletType type,
                double x,
                double y,
                double z,
                int lifetime,
                float startSize,
                float growSize,
                long seed,
                double velocityX,
                double velocityY,
                double velocityZ,
                double accelerationX,
                double accelerationZ
        ) {
            this(type, x, y, z, lifetime, startSize, growSize, seed);
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.velocityZ = velocityZ;
            this.accelerationX = accelerationX;
            this.accelerationZ = accelerationZ;
        }

        private void tick(Level level, Vec3 origin) {
            prevX = x;
            prevY = y;
            prevZ = z;
            if (type == NuclearCloudletType.ACTIVE_BLAST_SWEEP_SMOKE) {
                tickActiveBlastSweep(level, origin);
                age++;
                return;
            }
            velocityX += accelerationX;
            velocityZ += accelerationZ;
            x += velocityX;
            y += velocityY;
            z += velocityZ;
            velocityX *= 0.96D;
            velocityY *= 0.96D;
            velocityZ *= 0.96D;

            age++;
        }

        private void tickActiveBlastSweep(Level level, Vec3 origin) {
            double remainingX = velocityX;
            double remainingY = velocityY;
            double remainingZ = velocityZ;
            double distance = Math.sqrt(remainingX * remainingX + remainingY * remainingY + remainingZ * remainingZ);
            int steps = Math.max(1, Mth.ceil(distance / 0.5D));
            double stepX = remainingX / steps;
            double stepY = remainingY / steps;
            double stepZ = remainingZ / steps;

            for (int step = 0; step < steps; step++) {
                double nextX = x + stepX;
                double nextY = y + stepY;
                double nextZ = z + stepZ;
                BlockPos nextPos = BlockPos.containing(origin.x + nextX, origin.y + nextY, origin.z + nextZ);
                if (level.hasChunkAt(nextPos)) {
                    BlockState state = level.getBlockState(nextPos);
                    if (!state.isAir() && !state.getCollisionShape(level, nextPos, CollisionContext.empty()).isEmpty()) {
                        collided = true;
                        velocityX *= 0.15D;
                        velocityZ *= 0.15D;
                        velocityY = Math.max(velocityY * 0.25D, 0.04D);
                        break;
                    }
                }
                x = nextX;
                y = nextY;
                z = nextZ;
            }

            velocityX *= collided ? 0.82D : 0.92D;
            velocityY *= collided ? 0.84D : 0.94D;
            velocityZ *= collided ? 0.82D : 0.92D;
        }

        public boolean isExpired() {
            return age >= lifetime;
        }

        public double x(float partialTick) {
            return Mth.lerp(partialTick, prevX, x);
        }

        public double y(float partialTick) {
            return Mth.lerp(partialTick, prevY, y);
        }

        public double z(float partialTick) {
            return Mth.lerp(partialTick, prevZ, z);
        }

        public float size(float partialTick) {
            float progress = Mth.clamp((age + partialTick) / (float) lifetime, 0.0F, 1.0F);
            return Mth.lerp(progress, startSize, growSize);
        }

        public float alpha(float partialTick) {
            float progress = Mth.clamp((age + partialTick) / (float) lifetime, 0.0F, 1.0F);
            if (type == NuclearCloudletType.SHOCKWAVE) {
                return progress > 0.72F ? Mth.lerp((progress - 0.72F) / 0.28F, 0.95F, 0.0F) : 0.95F;
            }
            if (type == NuclearCloudletType.ACTIVE_BLAST_SWEEP_SMOKE) {
                float alpha = progress > 0.55F ? Mth.lerp((progress - 0.55F) / 0.45F, 0.88F, 0.0F) : 0.88F;
                return collided ? alpha * 0.35F : alpha;
            }
            return progress > 0.68F ? Mth.lerp((progress - 0.68F) / 0.32F, 0.86F, 0.0F) : 0.86F;
        }

        public boolean isShockwaveVisual() {
            return type == NuclearCloudletType.SHOCKWAVE;
        }

        public NuclearCloudletType type() {
            return type;
        }

        public int age() {
            return age;
        }

        public int red(float partialTick) {
            return color(partialTick, 0);
        }

        public int green(float partialTick) {
            return color(partialTick, 1);
        }

        public int blue(float partialTick) {
            return color(partialTick, 2);
        }

        private int color(float partialTick, int component) {
            float progress = Mth.clamp((age + partialTick) / (float) lifetime, 0.0F, 1.0F);
            float red;
            float green;
            float blue;
            if (type == NuclearCloudletType.ACTIVE_BLAST_SWEEP_SMOKE) {
                red = Mth.lerp(progress, 0.26F, 0.10F);
                green = Mth.lerp(progress, 0.24F, 0.095F);
                blue = Mth.lerp(progress, 0.21F, 0.085F);
            } else {
                red = Mth.lerp(progress, 0.18F, 0.07F);
                green = Mth.lerp(progress, 0.16F, 0.065F);
                blue = Mth.lerp(progress, 0.14F, 0.06F);
            }
            return Math.round(Mth.clamp(component == 0 ? red : component == 1 ? green : blue, 0.0F, 1.0F) * 255.0F);
        }
    }
}

