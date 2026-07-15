package com.skyeshade.skyent.content.entity;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.explosion.destruction.NuclearBlastRayPlanner;
import com.skyeshade.skyent.content.explosion.destruction.NuclearBlockMutationQueue;
import com.skyeshade.skyent.content.explosion.destruction.NuclearColumnCollapsePass;
import com.skyeshade.skyent.content.explosion.destruction.NuclearDestructionMask;
import com.skyeshade.skyent.content.explosion.destruction.NuclearPlannedBlockMutationQueue;
import com.skyeshade.skyent.content.explosion.destruction.NuclearResistanceCache;
import com.skyeshade.skyent.content.explosion.destruction.NuclearSectionCompletionTracker;
import com.skyeshade.skyent.content.explosion.destruction.NuclearWaterEvaporationPass;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    public static final float VANILLA_EXPLOSION_STRENGTH = 16.0F;
    public static final int ENTITY_LIFETIME_TICKS = 20 * 60 * 4;
    public static final float DEFAULT_NUKE_RADIUS = 200.0F;
    public static final double SHOCKWAVE_MAX_RADIUS_MULTIPLIER = 4.0D;
    public static final double SHOCKWAVE_SPEED_BLOCKS_PER_TICK = 2.0D;
    public static final int SHOCKWAVE_VISUAL_INTERVAL_TICKS = 1;
    public static final int SHOCKWAVE_SOUND_TICKS = 5;
    public static final float SHOCKWAVE_SOUND_VOLUME = 2000.0F;
    private static final double SHOCKWAVE_DAMAGE_BAND_WIDTH = 5.0D;
    private static final int SHOCKWAVE_MIN_PUFFS = 32;
    private static final int SHOCKWAVE_MAX_PUFFS = 260;
    private static final int SHOCKWAVE_LIFETIME_MIN_TICKS = 18;
    private static final int SHOCKWAVE_LIFETIME_MAX_TICKS = 34;
    private static final double SHOCKWAVE_BAND_BEHIND_BLOCKS = 4.0D;
    private static final double SHOCKWAVE_BAND_AHEAD_BLOCKS = 1.0D;
    private static final int SHOCKWAVE_SURFACE_SCAN_PADDING = 8;
    private static final int CENTER_RADIATION_DURATION_TICKS = 40;
    private static final double CENTER_RADIATION_INITIAL_MSV_PER_SECOND = 504_250_000.0D;
    private static final double CENTER_RADIATION_RADIUS = 512.0D;
    private static final double BLAST_KNOCKBACK_RADIUS = 256.0D;
    private static final double BLAST_CLOSE_FIRE_RADIUS = 64.0D;
    private static final double BLAST_MAX_HORIZONTAL_KNOCKBACK = 6.0D;
    private static final double BLAST_MAX_VERTICAL_KNOCKBACK = 1.8D;
    private static final double BLAST_MIN_KNOCKBACK = 0.15D;
    private static final int BLAST_CLOSE_FIRE_SECONDS = 12;
    private static final int BLAST_FAR_FIRE_SECONDS = 4;
    public static final int MAX_CLOUDLETS = 5200;
    public static final int RAY_GROW_TICKS = 10;
    public static final int RAY_FADE_TICKS = 40;
    public static final int RAY_TOTAL_TICKS = RAY_GROW_TICKS + RAY_FADE_TICKS;
    public static final float RAY_SCALE = 56.0F;
    private static final boolean DEBUG_SHOCKWAVE_VISUALS = Boolean.getBoolean("skyent.debugNukeShockwave");
    private static final boolean DEBUG_FORCE_SHOCKWAVE_TEST_CLOUDLET = Boolean.getBoolean("skyent.debugNukeShockwaveTestCloudlet");
    private static final boolean DEBUG_CENTER_RADIATION = Boolean.getBoolean("skyent.debugNukeRadiation");
    private static final boolean ENABLE_NUCLEAR_BLOCK_DESTRUCTION = true;
    private static final boolean NUKE_DESTRUCTION_PLAN_ONLY = false;
    private static final boolean SAVE_NUKE_DESTRUCTION_PROGRESS = false;
    private static final boolean DEBUG_NUKE_DESTRUCTION = Boolean.getBoolean("skyent.debugNukeDestruction");
    private static final boolean DEBUG_NUKE_RAY_PLANNER = Boolean.getBoolean("skyent.debugNukeRayPlanner");
    private static final boolean DEBUG_NUKE_COLUMN_COLLAPSE = Boolean.getBoolean("skyent.debugNukeColumnCollapse");
    private static final boolean DEBUG_NUKE_LIFECYCLE = Boolean.getBoolean("skyent.debugNukeLifecycle");
    private static final boolean DEBUG_NUKE_ITEM_DROPS = Boolean.getBoolean("skyent.debugNukeItemDrops");
    private static final boolean DEBUG_NUKE_FLUID_EVAPORATION = Boolean.getBoolean("skyent.debugNukeFluidEvaporation")
            || Boolean.getBoolean("skyent.debugNukeWaterEvaporation");
    private static final int NUKE_RAY_PLANNER_MAX_RAYS_PER_TICK = 8_128;
    private static final int NUKE_RAY_PLANNER_MAX_STEPS_PER_TICK = 128_000;
    private static final int NUKE_BLOCK_MUTATION_MAX_BLOCKS_PER_TICK = 20_480;
    private static final int NUKE_BLOCK_MUTATION_MAX_SECTIONS_PER_TICK = 256;
    private static final int NUKE_DESTRUCTION_MAX_TICKS = 20 * 60 * 5;
    private static final int FLUID_EVAPORATION_MAX_SECTIONS_PER_TICK = 32;
    private static final int FLUID_EVAPORATION_MAX_BLOCK_CHECKS_PER_TICK = 250_000;
    private static final int FLUID_EVAPORATION_MAX_BLOCK_CHANGES_PER_TICK = 16_384;
    private static final int COLUMN_COLLAPSE_COLUMNS_PER_TICK = 1_024;
    private static final int COLUMN_COLLAPSE_MAX_BLOCK_WRITES_PER_TICK = 8_128;
    private static final int COLUMN_COLLAPSE_MAX_DROP_BLOCKS = 10;
    private static final int AFTERMATH_MIN_COMPLETED_SECTIONS_TO_START = 1;
    private static final int AFTERMATH_FORCE_START_AFTER_TICKS = 40;
    private static final double FLUID_EVAPORATION_RADIUS_SCALE = 0.60D;
    private static final boolean NUKE_CLEANUP_DROPPED_ITEMS_IN_AFTERMATH = false;
    private static final double NUKE_ITEM_CLEANUP_RADIUS_SCALE = 3.0D;
    private static final double NUKE_DESTRUCTION_RADIUS_MULTIPLIER = 1.0D;
    private static final double NUKE_RAY_BASE_STARTING_ENERGY = 13_000_900_000.0D;
    private static final double NUKE_RAY_STARTING_ENERGY_PER_RADIUS = 100_250_500.0D;
    private static final double NUKE_RAY_COUNT_MULTIPLIER = 4.0D;
    private static final double NUKE_RAY_COUNT_EXTRA_MULTIPLIER = 4.0D;
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
    private int centerRadiationTicks;
    private boolean appliedEntityBlastImpulse;
    private boolean chunksForced;
    private boolean loggedInitialChunkLoadingState;
    private boolean debugShockwaveTestCloudletSpawned;
    @Nullable
    private NuclearDestructionMask destructionMask;
    @Nullable
    private NuclearResistanceCache resistanceCache;
    @Nullable
    private NuclearBlastRayPlanner rayPlanner;
    @Nullable
    private NuclearBlockMutationQueue mutationQueue;
    @Nullable
    private NuclearWaterEvaporationPass fluidEvaporationPass;
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
    private boolean destructionCleanupLogged;
    private DestructionPhase destructionPhase = DestructionPhase.NOT_STARTED;
    private int destructionTicks;
    private double originX = Double.NaN;
    private double originY = Double.NaN;
    private double originZ = Double.NaN;

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
        this.strength = strength;
        this.radius = DEFAULT_NUKE_RADIUS;
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
        chunksForced = !forcedExplosionChunks.isEmpty();
        NuclearExplosionChunkLoading.debugAdopted(getChunkLoadingOwnerUuid(), getId(), forcedExplosionChunks.size());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SPAWN_CLOUD, true);
        builder.define(DATA_FLASH_SKY, true);
        builder.define(DATA_VISUAL_SEED, 0L);
        builder.define(DATA_RADIUS, DEFAULT_NUKE_RADIUS);
    }

    @Override
    public void tick() {
        stabilizeFixedOrigin();
        super.tick();
        stabilizeFixedOrigin();

        if (level().isClientSide) {
            tickClientEffects();
        } else {
            tickServerEffects();
        }

        if (tickCount > ENTITY_LIFETIME_TICKS && canReleaseImmediateChunks()) {
            unforceExplosionChunks();
            discard();
        }
    }

    private void tickServerEffects() {
        if (!chunksForced && !canReleaseImmediateChunks()) {
            NuclearExplosionChunkLoading.debugFallbackForce(getChunkLoadingOwnerUuid(), getId());
            forceExplosionChunks();
        } else if (!loggedInitialChunkLoadingState) {
            NuclearExplosionChunkLoading.debugAlreadyForced(getChunkLoadingOwnerUuid(), getId(), forcedExplosionChunks.size());
        }
        loggedInitialChunkLoadingState = true;

        if (!explosionDone) {
            explosionDone = true;
            Entity source = sourceUuid == null || level().getServer() == null
                    ? null
                    : level().getServer().getPlayerList().getPlayer(sourceUuid);
            level().explode(
                    source,
                    getX(),
                    getY(),
                    getZ(),
                    strength,
                    Level.ExplosionInteraction.NONE
            );
        }
        tickNuclearDestruction();
        if (chunksForced && canReleaseImmediateChunks()) {
            unforceExplosionChunks();
        }
        if (!appliedEntityBlastImpulse) {
            appliedEntityBlastImpulse = true;
            applyEntityBlastImpulse();
        }

        tickShockwaveServer();
        if (centerRadiationTicks < CENTER_RADIATION_DURATION_TICKS) {
            tickCenterRadiation();
            centerRadiationTicks++;
        }
        logLifecycleDebug();
    }

    private void tickNuclearDestruction() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!ENABLE_NUCLEAR_BLOCK_DESTRUCTION || !destroyBlocks) {
            destructionPhase = DestructionPhase.COMPLETE;
            cleanupDestructionState("disabled");
            return;
        }

        if (destructionPhase != DestructionPhase.COMPLETE) {
            destructionTicks++;
            if (destructionTicks > NUKE_DESTRUCTION_MAX_TICKS) {
                SkyesNuclearTech.LOGGER.warn(
                        "Nuke destruction timed out safely: id={} phase={} ticks={} maskSections={} estimatedBlocks={}",
                        getId(),
                        destructionPhase,
                        destructionTicks,
                        destructionMask == null ? 0 : destructionMask.sectionCount(),
                        destructionMask == null ? 0 : destructionMask.estimatedBlockCount()
                );
                destructionPhase = DestructionPhase.COMPLETE;
                cleanupDestructionState("timeout");
                return;
            }
        }

        if (destructionPhase == DestructionPhase.NOT_STARTED) {
            startFluidEvaporation(serverLevel);
            startNuclearDestructionPlanning(serverLevel);
        }

        tickFluidEvaporation();

        if (destructionPhase == DestructionPhase.PLANNING && rayPlanner != null && destructionMask != null) {
            NuclearBlastRayPlanner.PlannerResult result = rayPlanner.tickBudget(
                    NUKE_RAY_PLANNER_MAX_RAYS_PER_TICK,
                    NUKE_RAY_PLANNER_MAX_STEPS_PER_TICK
            );
            logRayPlannerDebug(result);

            if (rayPlanner.isComplete()) {
                sectionCompletionTracker = new NuclearSectionCompletionTracker();
                sectionCompletionTracker.initializeFromDestructionMask(destructionMask);
                mutationQueue = new NuclearBlockMutationQueue(serverLevel, destructionMask, fixedOrigin(), sectionCompletionTracker);
                destructionPhase = DestructionPhase.MUTATING;
                mutationStartTick = tickCount;
                SkyesNuclearTech.LOGGER.info(
                        "Nuke destruction planning complete: id={} rays={}/{} baseRays={} rayMultiplier={} extraRayMultiplier={} initialRayEnergy={} distanceDecayPerBlock={} resistanceCostMultiplier={} resistanceCostOffset={} resistancePowerNear={} resistancePowerFar={} resistancePowerCurve={} distanceResistanceGrowth={} materialStackingGrowth={} closePierceFraction={} closeResistanceMultiplier={} resistanceSamples={} steps={} maskSections={} estimatedBlocks={} trackerPendingSections={} initiallyPrunedEmptySections={} initiallyPrunedAirSections={} mutationUpdateFlags={} suppressDrops=true unloadedStops={} energyStops={} blockedStops={} unbreakableStops={} outOfWorldStops={} fragileMarked={} nonSolidMarked={} fluidMarked={} airSkipped={} blockEntitySkips={} highResHit={} highResMarked={} highResBlocked={} highResEnergyStops={} obsidianHit={} obsidianMarked={} obsidianBlocked={} obsidianEnergyStops={} maxObsidianDepthMarkedOnSingleRay={}",
                        getId(),
                        rayPlanner.rayIndex(),
                        rayPlanner.totalRays(),
                        rayPlanner.baseRayCount(),
                        rayPlanner.rayCountMultiplier(),
                        rayPlanner.extraRayCountMultiplier(),
                        rayPlanner.initialRayEnergy(),
                        rayPlanner.distanceDecayPerBlock(),
                        rayPlanner.resistanceCostMultiplier(),
                        rayPlanner.resistanceCostOffset(),
                        rayPlanner.resistancePowerNear(),
                        rayPlanner.resistancePowerFar(),
                        rayPlanner.resistancePowerDistanceCurve(),
                        rayPlanner.distanceResistanceGrowth(),
                        rayPlanner.materialPenetrationStackingGrowth(),
                        rayPlanner.closeRangeArmorPiercingRadiusFraction(),
                        rayPlanner.closeRangeResistanceCostMultiplier(),
                        rayPlanner.resistanceCostSamples(),
                        rayPlanner.stepsProcessedTotal(),
                        destructionMask.sectionCount(),
                        destructionMask.estimatedBlockCount(),
                        sectionCompletionTracker.pendingCount(),
                        mutationQueue.initiallyPrunedEmptySections(),
                        mutationQueue.initiallyPrunedAirSections(),
                        NuclearBlockMutationQueue.NUKE_BLOCK_UPDATE_FLAGS,
                        rayPlanner.unloadedChunkStops(),
                        rayPlanner.energyStops(),
                        rayPlanner.blockedRayStops(),
                        rayPlanner.unbreakableStops(),
                        rayPlanner.outOfWorldStops(),
                        rayPlanner.fragileBlocksMarked(),
                        rayPlanner.nonSolidBlocksMarked(),
                        rayPlanner.fluidBlocksMarked(),
                        rayPlanner.airBlocksSkipped(),
                        rayPlanner.blockEntitySkips(),
                        rayPlanner.highResistanceBlocksHit(),
                        rayPlanner.highResistanceBlocksMarked(),
                        rayPlanner.highResistanceBlocksBlocked(),
                        rayPlanner.highResistanceBlocksStoppedByEnergy(),
                        rayPlanner.obsidianBlocksHit(),
                        rayPlanner.obsidianBlocksMarked(),
                        rayPlanner.obsidianBlocksBlocked(),
                        rayPlanner.obsidianBlocksStoppedByEnergy(),
                        rayPlanner.maxObsidianDepthMarkedOnSingleRay()
                );
            }
        }

        if (destructionPhase == DestructionPhase.MUTATING && mutationQueue != null) {
            if (NUKE_DESTRUCTION_PLAN_ONLY) {
                SkyesNuclearTech.LOGGER.info(
                        "Nuke destruction plan-only complete: id={} plannedSections={} plannedBlocks={}",
                        getId(),
                        destructionMask == null ? 0 : destructionMask.sectionCount(),
                        destructionMask == null ? 0 : destructionMask.estimatedBlockCount()
                );
                destructionPhase = DestructionPhase.COMPLETE;
                cleanupDestructionState("plan_only");
                return;
            }

            NuclearBlockMutationQueue.MutationResult result = mutationQueue.tick(
                    NUKE_BLOCK_MUTATION_MAX_SECTIONS_PER_TICK,
                    NUKE_BLOCK_MUTATION_MAX_BLOCKS_PER_TICK
            );
            logMutationDebug(result);
            if (!aftermathStarted && sectionCompletionTracker != null) {
                int readySections = sectionCompletionTracker.completedCount() + sectionCompletionTracker.skippedCount();
                if (readySections >= AFTERMATH_MIN_COMPLETED_SECTIONS_TO_START) {
                    startColumnCollapsePass(serverLevel, "completed_section_threshold");
                } else if (mutationStartTick >= 0 && tickCount - mutationStartTick >= AFTERMATH_FORCE_START_AFTER_TICKS) {
                    startColumnCollapsePass(serverLevel, "force_start_timeout");
                }
            }
            tickColumnCollapseTasks(serverLevel);

            if (mutationQueue.isComplete()) {
                SkyesNuclearTech.LOGGER.info(
                        "Nuke destruction mutation complete: id={} removedBlocks={} touchedSections={} obsidianRemoved={} highResistanceRemoved={} unloadedSkips={} prunedEmptySections={} prunedAirSections={} processedRadius={} processedFraction={} trackerCompleted={} trackerSkipped={} aftermathStarted={} aftermathStartTick={}",
                        getId(),
                        mutationQueue.totalBlocksRemoved(),
                        mutationQueue.totalSectionsTouched(),
                        mutationQueue.obsidianBlocksRemoved(),
                        mutationQueue.highResistanceBlocksRemoved(),
                        mutationQueue.unloadedSectionSkips(),
                        mutationQueue.prunedEmptySections(),
                        mutationQueue.prunedAirSections(),
                        mutationQueue.processedRadius(),
                        mutationQueue.processedRadiusFraction(),
                        sectionCompletionTracker == null ? 0 : sectionCompletionTracker.completedCount(),
                        sectionCompletionTracker == null ? 0 : sectionCompletionTracker.skippedCount(),
                        aftermathStarted,
                        aftermathStartTick
                );
                destructionMask = null;
                resistanceCache = null;
                rayPlanner = null;
                mutationQueue = null;
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
        int evaporationRadius = Mth.ceil(getRadius() * FLUID_EVAPORATION_RADIUS_SCALE);
        fluidEvaporationPass = new NuclearWaterEvaporationPass(serverLevel, fixedOrigin(), evaporationRadius);
        if (DEBUG_NUKE_FLUID_EVAPORATION) {
            SkyesNuclearTech.LOGGER.info(
                    "Nuke fluid evaporation started: id={} center={} entityRadius={} evaporationRadius={} radiusScale={} sections={} chunkRadius={}",
                    getId(),
                    fixedOrigin(),
                    getRadius(),
                    evaporationRadius,
                    FLUID_EVAPORATION_RADIUS_SCALE,
                    fluidEvaporationPass.sectionCount(),
                    NuclearExplosionChunkLoading.computeChunkRadius(getRadius())
            );
        }
    }

    private void tickFluidEvaporation() {
        if (fluidEvaporationPass == null) {
            return;
        }

        NuclearWaterEvaporationPass.EvaporationResult result = fluidEvaporationPass.tick(
                FLUID_EVAPORATION_MAX_SECTIONS_PER_TICK,
                FLUID_EVAPORATION_MAX_BLOCK_CHECKS_PER_TICK,
                FLUID_EVAPORATION_MAX_BLOCK_CHANGES_PER_TICK
        );
        logFluidEvaporationDebug(result);
        if (fluidEvaporationPass.isComplete()) {
            SkyesNuclearTech.LOGGER.info(
                    "Nuke fluid evaporation complete: id={} radius={} sectionsProcessed={} blockChecks={} waterRemoved={} lavaRemoved={} waterloggedCleared={} skippedUnloadedSections={} skippedBlockEntities={}",
                    getId(),
                    fluidEvaporationPass.radius(),
                    fluidEvaporationPass.totalSectionsProcessed(),
                    fluidEvaporationPass.totalBlocksChecked(),
                    fluidEvaporationPass.totalWaterBlocksRemoved(),
                    fluidEvaporationPass.totalLavaBlocksRemoved(),
                    fluidEvaporationPass.totalWaterloggedBlocksCleared(),
                    fluidEvaporationPass.skippedUnloadedSections(),
                    fluidEvaporationPass.skippedBlockEntities()
            );
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
                sectionCompletionTracker
        );
        columnCollapseMutationQueue = null;
        aftermathStarted = true;
        aftermathStartTick = tickCount;
        aftermathStartReason = reason;
        SkyesNuclearTech.LOGGER.info(
                "Nuke column aftermath work started: id={} tick={} phase={} reason={} mutationTicks={} workUnits={} currentRing={} maxRing={} columnsApprox={} collapseRadius={} charredLogRadius={} deadVegetationRadius={} fireRadius={} trackerPending={} trackerCompleted={} trackerSkipped={} minCompletedSections={} forceStartAfterTicks={} maxResistance={} maxDropBlocks={} maxRunsPerColumn={} scanDepth={}",
                getId(),
                tickCount,
                destructionPhase,
                aftermathStartReason,
                mutationStartTick < 0 ? 0 : tickCount - mutationStartTick,
                columnCollapsePass.workUnitsTotal(),
                columnCollapsePass.currentRing(),
                columnCollapsePass.maxRing(),
                columnCollapsePass.columnsRemaining(),
                columnCollapsePass.collapseRadius(),
                columnCollapsePass.charredLogRadius(),
                columnCollapsePass.deadVegetationRadius(),
                columnCollapsePass.fireRadius(),
                sectionCompletionTracker.pendingCount(),
                sectionCompletionTracker.completedCount(),
                sectionCompletionTracker.skippedCount(),
                AFTERMATH_MIN_COMPLETED_SECTIONS_TO_START,
                AFTERMATH_FORCE_START_AFTER_TICKS,
                COLUMN_COLLAPSE_MAX_RESISTANCE,
                COLUMN_COLLAPSE_MAX_DROP_BLOCKS,
                columnCollapsePass.maxRunsPerColumn(),
                columnCollapsePass.scanDepthBelowSurface()
        );
    }

    private void tickColumnCollapseTasks(ServerLevel serverLevel) {
        if (!aftermathStarted) {
            return;
        }

        if (columnCollapsePass != null) {
            NuclearColumnCollapsePass.CollapseResult result = columnCollapsePass.tick(
                    COLUMN_COLLAPSE_COLUMNS_PER_TICK
            );
            logColumnCollapseDebug(result);
            if (DEBUG_NUKE_COLUMN_COLLAPSE
                    && sectionCompletionTracker != null
                    && sectionCompletionTracker.isExplosionMutationComplete()
                    && columnCollapsePass.blockedByPendingSections()
                    && columnCollapsePass.blockedTicks() > 200
                    && tickCount % 100 == 0) {
                SkyesNuclearTech.LOGGER.warn(
                        "Nuke column aftermath still blocked after explosion mutation complete: id={} ring={} chunk={} blockedTicks={} trackerPending={} trackerCompleted={} trackerSkipped={}",
                        getId(),
                        columnCollapsePass.blockedRing(),
                        columnCollapsePass.blockedWorkUnitDebug(),
                        columnCollapsePass.blockedTicks(),
                        sectionCompletionTracker.pendingCount(),
                        sectionCompletionTracker.completedCount(),
                        sectionCompletionTracker.skippedCount()
                );
            }

            if (columnCollapsePass.isComplete()) {
                SkyesNuclearTech.LOGGER.info(
                        "Nuke column aftermath complete: id={} processedColumns={} workUnitsCompleted={}/{} workUnitsPlanned={} noOpWorkUnits={} startReason={} currentRing={} maxRing={} deferredRemaining={} deferredChecks={} deferredQueued={} collapseRadius={} charredLogRadius={} deadVegetationRadius={} fireRadius={} totalMutationsApplied={} totalSectionsMutated={} movementMutations={} charredLogs={} deadGrass={} deadLeaves={} plannedPlantRemovals={} plannedFires={} placedFires={} mutationUnloadedSkips={} mutationBlockEntitySkips={} skippedUnloadedColumns={} barriers={} movableBlocks={} maxDropBlocks={} maxRunsPerColumn={} scanDepth={} runsFound={} runsMoved={} averageDrop={} maxDropSeen={} skippedBarrier={} skippedFluid={} skippedBlockEntity={} updateFlags={} suppressDrops=true",
                        getId(),
                        columnCollapsePass.totalColumnsProcessed(),
                        columnCollapsePass.workUnitsCompleted(),
                        columnCollapsePass.workUnitsTotal(),
                        columnCollapsePass.workUnitsPlanned(),
                        columnCollapsePass.noOpWorkUnitsSkipped(),
                        aftermathStartReason,
                        columnCollapsePass.currentRing(),
                        columnCollapsePass.maxRing(),
                        columnCollapsePass.deferredWorkUnitsRemaining(),
                        columnCollapsePass.deferredColumnChecks(),
                        columnCollapsePass.deferredColumnsQueued(),
                        columnCollapsePass.collapseRadius(),
                        columnCollapsePass.charredLogRadius(),
                        columnCollapsePass.deadVegetationRadius(),
                        columnCollapsePass.fireRadius(),
                        columnCollapsePass.totalMutationsApplied(),
                        columnCollapsePass.totalSectionsMutated(),
                        columnCollapsePass.plannedMovementMutations(),
                        columnCollapsePass.plannedCharredLogReplacements(),
                        columnCollapsePass.plannedDeadGrassReplacements(),
                        columnCollapsePass.plannedDeadLeafReplacements(),
                        columnCollapsePass.plannedPlantRemovals(),
                        columnCollapsePass.plannedFireBlocks(),
                        columnCollapsePass.placedFireBlocks(),
                        columnCollapsePass.mutationUnloadedSectionSkips(),
                        columnCollapsePass.mutationBlockEntitySkips(),
                        columnCollapsePass.skippedUnloadedColumns(),
                        columnCollapsePass.barriersEncountered(),
                        columnCollapsePass.movableBlocksCollected(),
                        columnCollapsePass.maxDropBlocks(),
                        columnCollapsePass.maxRunsPerColumn(),
                        columnCollapsePass.scanDepthBelowSurface(),
                        columnCollapsePass.surfaceRunsFound(),
                        columnCollapsePass.surfaceRunsMoved(),
                        columnCollapsePass.averageDropDistance(),
                        columnCollapsePass.maxDropDistanceSeen(),
                        columnCollapsePass.skippedBarriersBeforeSurface(),
                        columnCollapsePass.skippedFluidsBeforeSurface(),
                        columnCollapsePass.skippedBlockEntitiesBeforeSurface(),
                        NuclearBlockMutationQueue.NUKE_BLOCK_UPDATE_FLAGS
                );
                logAftermathItemEntityCount(serverLevel);
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
                && isAftermathComplete()
                && fluidEvaporationPass == null;
    }

    private void cleanupDestructionState(String reason) {
        int forcedBefore = forcedExplosionChunks.size();
        int maskSections = destructionMask == null ? 0 : destructionMask.sectionCount();
        int mutationSections = mutationQueue == null ? 0 : mutationQueue.sectionsRemaining();
        int columnColumns = columnCollapsePass == null ? 0 : columnCollapsePass.columnsRemaining();
        int columnDeferred = columnCollapsePass == null ? 0 : columnCollapsePass.deferredColumnsRemaining();
        int plannedSections = columnCollapsePass == null ? 0 : columnCollapsePass.currentLocalPlannedSections();
        int trackerPending = sectionCompletionTracker == null ? 0 : sectionCompletionTracker.pendingCount();
        int trackerCompleted = sectionCompletionTracker == null ? 0 : sectionCompletionTracker.completedCount();
        int trackerSkipped = sectionCompletionTracker == null ? 0 : sectionCompletionTracker.skippedCount();

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
        if (destructionMask != null) {
            destructionMask.clear();
        }
        if (sectionCompletionTracker != null) {
            sectionCompletionTracker.clear();
        }

        destructionMask = null;
        resistanceCache = null;
        rayPlanner = null;
        mutationQueue = null;
        columnCollapsePass = null;
        columnCollapseMutationQueue = null;
        fluidEvaporationPass = null;
        sectionCompletionTracker = null;
        aftermathStarted = true;
        mutationStartTick = -1;
        aftermathStartReason = "cleaned_" + reason;

        if (!destructionCleanupLogged) {
            destructionCleanupLogged = true;
            SkyesNuclearTech.LOGGER.info(
                    "Nuke destruction cleanup: id={} reason={} tick={} phase={} maskSections={} mutationSections={} columnColumns={} columnDeferred={} localPlannedSections={} trackerPending={} trackerCompleted={} trackerSkipped={} chunksForced={} canReleaseChunks={} queuesCleared=true",
                    getId(),
                    reason,
                    tickCount,
                    destructionPhase,
                    maskSections,
                    mutationSections,
                    columnColumns,
                    columnDeferred,
                    plannedSections,
                    trackerPending,
                    trackerCompleted,
                    trackerSkipped,
                    forcedBefore,
                    canReleaseImmediateChunks()
            );
        }
    }

    private void startNuclearDestructionPlanning(ServerLevel serverLevel) {
        int destructionRadius = Mth.ceil(getRadius() * NUKE_DESTRUCTION_RADIUS_MULTIPLIER);
        double destructionStrength = NUKE_RAY_BASE_STARTING_ENERGY
                + getRadius() * NUKE_RAY_STARTING_ENERGY_PER_RADIUS;
        destructionCleanupLogged = false;
        aftermathStarted = false;
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
                NUKE_RAY_COUNT_MULTIPLIER,
                NUKE_RAY_COUNT_EXTRA_MULTIPLIER,
                NUKE_RAY_INITIAL_ENERGY_MULTIPLIER,
                NUKE_CLOSE_RANGE_ARMOR_PIERCING_RADIUS_FRACTION,
                NUKE_CLOSE_RANGE_RESISTANCE_COST_MULTIPLIER,
                destructionMask,
                resistanceCache,
                getVisualSeed()
        );
        mutationQueue = null;
        destructionPhase = DestructionPhase.PLANNING;
        SkyesNuclearTech.LOGGER.info(
                "Nuke destruction planning started: id={} entityRadius={} destructionRadius={} chunkRadius={} strength={} rayBaseEnergy={} rayEnergyPerRadius={} baseRays={} totalRays={} rayMultiplier={} extraRayMultiplier={} initialRayEnergy={} distanceDecayPerBlock={} resistanceCostMultiplier={} resistanceCostOffset={} resistancePowerNear={} resistancePowerFar={} resistancePowerCurve={} distanceResistanceGrowth={} materialStackingGrowth={} closePierceFraction={} closeResistanceMultiplier={} resistanceSamples={} planOnly={}",
                getId(),
                getRadius(),
                destructionRadius,
                NuclearExplosionChunkLoading.computeChunkRadius(getRadius()),
                destructionStrength,
                NUKE_RAY_BASE_STARTING_ENERGY,
                NUKE_RAY_STARTING_ENERGY_PER_RADIUS,
                rayPlanner.baseRayCount(),
                rayPlanner.totalRays(),
                rayPlanner.rayCountMultiplier(),
                rayPlanner.extraRayCountMultiplier(),
                rayPlanner.initialRayEnergy(),
                rayPlanner.distanceDecayPerBlock(),
                rayPlanner.resistanceCostMultiplier(),
                rayPlanner.resistanceCostOffset(),
                rayPlanner.resistancePowerNear(),
                rayPlanner.resistancePowerFar(),
                rayPlanner.resistancePowerDistanceCurve(),
                rayPlanner.distanceResistanceGrowth(),
                rayPlanner.materialPenetrationStackingGrowth(),
                rayPlanner.closeRangeArmorPiercingRadiusFraction(),
                rayPlanner.closeRangeResistanceCostMultiplier(),
                rayPlanner.resistanceCostSamples(),
                NUKE_DESTRUCTION_PLAN_ONLY
        );
    }

    private void logRayPlannerDebug(NuclearBlastRayPlanner.PlannerResult result) {
        if (!DEBUG_NUKE_RAY_PLANNER || tickCount % 20 != 0 || rayPlanner == null || destructionMask == null) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke ray planner debug: id={} tick={} phase={} rayIndex={}/{} baseRays={} rayMultiplier={} extraRayMultiplier={} initialRayEnergy={} distanceDecayPerBlock={} resistanceCostMultiplier={} resistanceCostOffset={} resistancePowerNear={} resistancePowerFar={} resistancePowerCurve={} distanceResistanceGrowth={} materialStackingGrowth={} closePierceFraction={} closeResistanceMultiplier={} resistanceSamples={} tickRays={} tickSteps={} totalRays={} totalSteps={} tickMarked={} totalMarked={} maskSections={} estimatedBlocks={} unloadedStops={} energyStops={} blockedStops={} unbreakableStops={} outOfWorldStops={} fragileMarked={} nonSolidMarked={} fluidMarked={} airSkipped={} blockEntitySkips={} highResHit={} highResMarked={} highResBlocked={} highResEnergyStops={} obsidianHit={} obsidianMarked={} obsidianBlocked={} obsidianEnergyStops={} maxObsidianDepthMarkedOnSingleRay={}",
                getId(),
                tickCount,
                destructionPhase,
                rayPlanner.rayIndex(),
                rayPlanner.totalRays(),
                rayPlanner.baseRayCount(),
                rayPlanner.rayCountMultiplier(),
                rayPlanner.extraRayCountMultiplier(),
                rayPlanner.initialRayEnergy(),
                rayPlanner.distanceDecayPerBlock(),
                rayPlanner.resistanceCostMultiplier(),
                rayPlanner.resistanceCostOffset(),
                rayPlanner.resistancePowerNear(),
                rayPlanner.resistancePowerFar(),
                rayPlanner.resistancePowerDistanceCurve(),
                rayPlanner.distanceResistanceGrowth(),
                rayPlanner.materialPenetrationStackingGrowth(),
                rayPlanner.closeRangeArmorPiercingRadiusFraction(),
                rayPlanner.closeRangeResistanceCostMultiplier(),
                rayPlanner.resistanceCostSamples(),
                result.raysProcessed(),
                result.stepsProcessed(),
                rayPlanner.raysProcessedTotal(),
                rayPlanner.stepsProcessedTotal(),
                result.blocksMarked(),
                rayPlanner.blocksMarkedTotal(),
                destructionMask.sectionCount(),
                destructionMask.estimatedBlockCount(),
                rayPlanner.unloadedChunkStops(),
                rayPlanner.energyStops(),
                rayPlanner.blockedRayStops(),
                rayPlanner.unbreakableStops(),
                rayPlanner.outOfWorldStops(),
                rayPlanner.fragileBlocksMarked(),
                rayPlanner.nonSolidBlocksMarked(),
                rayPlanner.fluidBlocksMarked(),
                rayPlanner.airBlocksSkipped(),
                rayPlanner.blockEntitySkips(),
                rayPlanner.highResistanceBlocksHit(),
                rayPlanner.highResistanceBlocksMarked(),
                rayPlanner.highResistanceBlocksBlocked(),
                rayPlanner.highResistanceBlocksStoppedByEnergy(),
                rayPlanner.obsidianBlocksHit(),
                rayPlanner.obsidianBlocksMarked(),
                rayPlanner.obsidianBlocksBlocked(),
                rayPlanner.obsidianBlocksStoppedByEnergy(),
                rayPlanner.maxObsidianDepthMarkedOnSingleRay()
        );
    }

    private void logMutationDebug(NuclearBlockMutationQueue.MutationResult result) {
        if (!DEBUG_NUKE_DESTRUCTION || tickCount % 20 != 0 || mutationQueue == null) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke destruction mutation debug: id={} tick={} phase={} sectionsRemaining={} tickSections={} tickBlocksRemoved={} totalBlocksRemoved={} totalSectionsTouched={} obsidianRemoved={} highResistanceRemoved={} unloadedSkips={} prunedEmptySections={} prunedAirSections={} initiallyPrunedEmptySections={} initiallyPrunedAirSections={} processedRadius={} maxPlannedRadius={} processedFraction={} trackerPending={} trackerCompleted={} trackerSkipped={} aftermathStarted={} aftermathStartTick={} aftermathStartReason={} minCompletedSectionsToStart={} forceStartAfterTicks={}",
                getId(),
                tickCount,
                destructionPhase,
                mutationQueue.sectionsRemaining(),
                result.sectionsTouched(),
                result.blocksRemoved(),
                mutationQueue.totalBlocksRemoved(),
                mutationQueue.totalSectionsTouched(),
                mutationQueue.obsidianBlocksRemoved(),
                mutationQueue.highResistanceBlocksRemoved(),
                mutationQueue.unloadedSectionSkips(),
                mutationQueue.prunedEmptySections(),
                mutationQueue.prunedAirSections(),
                mutationQueue.initiallyPrunedEmptySections(),
                mutationQueue.initiallyPrunedAirSections(),
                mutationQueue.processedRadius(),
                mutationQueue.maxPlannedHorizontalDistance(),
                mutationQueue.processedRadiusFraction(),
                sectionCompletionTracker == null ? 0 : sectionCompletionTracker.pendingCount(),
                sectionCompletionTracker == null ? 0 : sectionCompletionTracker.completedCount(),
                sectionCompletionTracker == null ? 0 : sectionCompletionTracker.skippedCount(),
                aftermathStarted,
                aftermathStartTick,
                aftermathStartReason,
                AFTERMATH_MIN_COMPLETED_SECTIONS_TO_START,
                AFTERMATH_FORCE_START_AFTER_TICKS
        );
    }

    private void logColumnCollapseDebug(NuclearColumnCollapsePass.CollapseResult result) {
        if (!DEBUG_NUKE_COLUMN_COLLAPSE || tickCount % 20 != 0 || columnCollapsePass == null) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke column aftermath debug: id={} tick={} phase={} aftermathStarted={} aftermathStartTick={} startReason={} trackerPending={} trackerCompleted={} trackerSkipped={} collapseRadius={} charredLogRadius={} deadVegetationRadius={} fireRadius={} currentRing={} maxRing={} workUnitsCompleted={}/{} readyWorkUnits={} deferredWorkUnits={} outerWorkUnitsNotStarted={} currentChunk={} blockedByPending={} blockedChunk={} blockedRing={} blockedTicks={} longestBlockedTicks={} tickChunksPlanned={} tickColumns={} tickDeferred={} tickPlannedMutations={} tickSectionsMutated={} tickMutationsApplied={} totalMutationsApplied={} totalSectionsMutated={} currentLocalMutations={} currentLocalSections={} noOpWorkUnits={} deferredChecks={} deferredQueued={} deferredRequeued={} movementMutations={} charredLogs={} deadGrass={} deadLeaves={} plannedPlantRemovals={} plannedFires={} placedFires={} totalColumnsProcessed={} skippedUnloadedColumns={} mutationUnloadedSkips={} mutationBlockEntitySkips={} barriers={} movableBlocks={} maxDropBlocks={} maxRunsPerColumn={} scanDepth={} runsFound={} runsMoved={} averageDrop={} maxDropSeen={} skippedBarrier={} skippedFluid={} skippedBlockEntity={}",
                getId(),
                tickCount,
                destructionPhase,
                aftermathStarted,
                aftermathStartTick,
                aftermathStartReason,
                sectionCompletionTracker == null ? 0 : sectionCompletionTracker.pendingCount(),
                sectionCompletionTracker == null ? 0 : sectionCompletionTracker.completedCount(),
                sectionCompletionTracker == null ? 0 : sectionCompletionTracker.skippedCount(),
                columnCollapsePass.collapseRadius(),
                columnCollapsePass.charredLogRadius(),
                columnCollapsePass.deadVegetationRadius(),
                columnCollapsePass.fireRadius(),
                columnCollapsePass.currentRing(),
                columnCollapsePass.maxRing(),
                columnCollapsePass.workUnitsCompleted(),
                columnCollapsePass.workUnitsTotal(),
                columnCollapsePass.readyWorkUnitsRemaining(),
                columnCollapsePass.deferredWorkUnitsRemaining(),
                columnCollapsePass.outerWorkUnitsNotStarted(),
                columnCollapsePass.currentWorkUnitDebug(),
                columnCollapsePass.blockedByPendingSections(),
                columnCollapsePass.blockedWorkUnitDebug(),
                columnCollapsePass.blockedRing(),
                columnCollapsePass.blockedTicks(),
                columnCollapsePass.longestBlockedTicks(),
                result.workUnitsPlanned(),
                result.columnsProcessed(),
                result.columnsDeferred(),
                result.mutationsPlanned(),
                result.sectionsMutated(),
                result.mutationsApplied(),
                columnCollapsePass.totalMutationsApplied(),
                columnCollapsePass.totalSectionsMutated(),
                columnCollapsePass.currentLocalPlannedMutations(),
                columnCollapsePass.currentLocalPlannedSections(),
                columnCollapsePass.noOpWorkUnitsSkipped(),
                columnCollapsePass.deferredColumnChecks(),
                columnCollapsePass.deferredColumnsQueued(),
                columnCollapsePass.deferredWorkUnitsRequeued(),
                columnCollapsePass.plannedMovementMutations(),
                columnCollapsePass.plannedCharredLogReplacements(),
                columnCollapsePass.plannedDeadGrassReplacements(),
                columnCollapsePass.plannedDeadLeafReplacements(),
                columnCollapsePass.plannedPlantRemovals(),
                columnCollapsePass.plannedFireBlocks(),
                columnCollapsePass.placedFireBlocks(),
                columnCollapsePass.totalColumnsProcessed(),
                columnCollapsePass.skippedUnloadedColumns(),
                columnCollapsePass.mutationUnloadedSectionSkips(),
                columnCollapsePass.mutationBlockEntitySkips(),
                columnCollapsePass.barriersEncountered(),
                columnCollapsePass.movableBlocksCollected(),
                columnCollapsePass.maxDropBlocks(),
                columnCollapsePass.maxRunsPerColumn(),
                columnCollapsePass.scanDepthBelowSurface(),
                columnCollapsePass.surfaceRunsFound(),
                columnCollapsePass.surfaceRunsMoved(),
                columnCollapsePass.averageDropDistance(),
                columnCollapsePass.maxDropDistanceSeen(),
                columnCollapsePass.skippedBarriersBeforeSurface(),
                columnCollapsePass.skippedFluidsBeforeSurface(),
                columnCollapsePass.skippedBlockEntitiesBeforeSurface()
        );
    }

    private void logColumnCollapseMutationDebug(NuclearPlannedBlockMutationQueue.MutationResult result) {
        if (!DEBUG_NUKE_COLUMN_COLLAPSE || tickCount % 20 != 0 || columnCollapseMutationQueue == null) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke column collapse mutation debug: id={} tick={} phase={} aftermathStarted={} aftermathStartTick={} sectionsRemaining={} tickSections={} tickBlocksChanged={} totalBlocksChanged={} totalSectionsTouched={} unloadedSkips={} blockEntitySkips={} placedFires={}",
                getId(),
                tickCount,
                destructionPhase,
                aftermathStarted,
                aftermathStartTick,
                columnCollapseMutationQueue.sectionsRemaining(),
                result.sectionsTouched(),
                result.blocksChanged(),
                columnCollapseMutationQueue.totalBlocksChanged(),
                columnCollapseMutationQueue.totalSectionsTouched(),
                columnCollapseMutationQueue.unloadedSectionSkips(),
                columnCollapseMutationQueue.blockEntitySkips(),
                columnCollapseMutationQueue.totalFireBlocksPlaced()
        );
    }

    private void logFluidEvaporationDebug(NuclearWaterEvaporationPass.EvaporationResult result) {
        if (!DEBUG_NUKE_FLUID_EVAPORATION || (tickCount % 20 != 0 && !result.complete()) || fluidEvaporationPass == null) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke fluid evaporation debug: id={} tick={} phase={} radius={} sectionsRemaining={} tickSections={} tickBlockChecks={} tickWaterRemoved={} tickLavaRemoved={} tickWaterloggedCleared={} tickSkippedUnloadedSections={} tickSkippedBlockEntities={} totalSectionsProcessed={} totalBlockChecks={} totalWaterRemoved={} totalLavaRemoved={} totalWaterloggedCleared={} totalSkippedUnloadedSections={} totalSkippedBlockEntities={} complete={}",
                getId(),
                tickCount,
                destructionPhase,
                fluidEvaporationPass.radius(),
                fluidEvaporationPass.sectionsRemaining(),
                result.sectionsProcessed(),
                result.blockChecks(),
                result.waterBlocksRemoved(),
                result.lavaBlocksRemoved(),
                result.waterloggedBlocksCleared(),
                result.skippedUnloadedSections(),
                result.skippedBlockEntities(),
                fluidEvaporationPass.totalSectionsProcessed(),
                fluidEvaporationPass.totalBlocksChecked(),
                fluidEvaporationPass.totalWaterBlocksRemoved(),
                fluidEvaporationPass.totalLavaBlocksRemoved(),
                fluidEvaporationPass.totalWaterloggedBlocksCleared(),
                fluidEvaporationPass.skippedUnloadedSections(),
                fluidEvaporationPass.skippedBlockEntities(),
                result.complete()
        );
    }

    private void logLifecycleDebug() {
        if (!DEBUG_NUKE_LIFECYCLE || tickCount % 100 != 0) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke lifecycle debug: id={} tick={} phase={} fluidActive={} rayPlannerActive={} mutationQueueActive={} columnPassActive={} columnMutationActive={} sectionTrackerActive={} maskSections={} mutationSectionsRemaining={} columnColumnsRemaining={} columnDeferredRemaining={} localPlannedSectionsRemaining={} columnWorkUnitsCompleted={} columnWorkUnitsTotal={} trackerPending={} trackerCompleted={} trackerSkipped={} forcedChunkCount={} chunksForced={} canReleaseChunks={} cloudlets={} mushroomCloudlets={} alive={}",
                getId(),
                tickCount,
                destructionPhase,
                fluidEvaporationPass != null,
                rayPlanner != null,
                mutationQueue != null,
                columnCollapsePass != null,
                columnCollapseMutationQueue != null,
                sectionCompletionTracker != null,
                destructionMask == null ? 0 : destructionMask.sectionCount(),
                mutationQueue == null ? 0 : mutationQueue.sectionsRemaining(),
                columnCollapsePass == null ? 0 : columnCollapsePass.columnsRemaining(),
                columnCollapsePass == null ? 0 : columnCollapsePass.deferredColumnsRemaining(),
                columnCollapsePass == null ? 0 : columnCollapsePass.currentLocalPlannedSections(),
                columnCollapsePass == null ? 0 : columnCollapsePass.workUnitsCompleted(),
                columnCollapsePass == null ? 0 : columnCollapsePass.workUnitsTotal(),
                sectionCompletionTracker == null ? 0 : sectionCompletionTracker.pendingCount(),
                sectionCompletionTracker == null ? 0 : sectionCompletionTracker.completedCount(),
                sectionCompletionTracker == null ? 0 : sectionCompletionTracker.skippedCount(),
                forcedExplosionChunks.size(),
                chunksForced,
                canReleaseImmediateChunks(),
                cloudlets.size(),
                mushroomCloudSimulation == null ? 0 : mushroomCloudSimulation.cloudlets().size(),
                !isRemoved()
        );
    }

    private void logAftermathItemEntityCount(ServerLevel serverLevel) {
        if (!DEBUG_NUKE_ITEM_DROPS && !NUKE_CLEANUP_DROPPED_ITEMS_IN_AFTERMATH) {
            return;
        }

        Vec3 origin = fixedOrigin();
        double radius = getRadius() * NUKE_ITEM_CLEANUP_RADIUS_SCALE;
        double radiusSqr = radius * radius;
        AABB bounds = new AABB(
                origin.x - radius,
                origin.y - radius,
                origin.z - radius,
                origin.x + radius,
                origin.y + radius,
                origin.z + radius
        );
        List<ItemEntity> itemEntities = serverLevel.getEntitiesOfClass(ItemEntity.class, bounds, item -> {
            double dx = item.getX() - origin.x;
            double dz = item.getZ() - origin.z;
            return dx * dx + dz * dz <= radiusSqr;
        });
        int removed = 0;
        if (NUKE_CLEANUP_DROPPED_ITEMS_IN_AFTERMATH) {
            for (ItemEntity itemEntity : itemEntities) {
                itemEntity.discard();
                removed++;
            }
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke aftermath item entity check: id={} radius={} itemEntities={} cleanupEnabled={} removed={}",
                getId(),
                radius,
                itemEntities.size(),
                NUKE_CLEANUP_DROPPED_ITEMS_IN_AFTERMATH,
                removed
        );
    }

    private void tickCenterRadiation() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        double sourceMillisievertsPerSecond = centerRadiationSourceMsvPerSecond(centerRadiationTicks);
        if (sourceMillisievertsPerSecond <= 0.0D) {
            return;
        }

        RadiationExposureSystem.PointSourceTickResult result = RadiationExposureSystem.tickPointSource(
                serverLevel,
                position(),
                sourceMillisievertsPerSecond,
                CENTER_RADIATION_RADIUS,
                1,
                DEBUG_CENTER_RADIATION
        );

        if (DEBUG_CENTER_RADIATION && centerRadiationTicks % 10 == 0) {
            SkyesNuclearTech.LOGGER.info(
                    "Nuke center radiation point source: id={} age={} sourceMsvPerSecond={} checked={} exposed={} playersChecked={} playersExposed={} immunePlayersSkippedDamage={} maxEntityExposureMsvPerSecond={} maxPlayerExposureMsvPerSecond={} nearestPlayer={} nearestDistance={} nearestExposureMsvPerSecond={} nearestDoseMsvThisTick={} nearestTransmission={} nearestImmune={} players=[{}]",
                    getId(),
                    centerRadiationTicks,
                    sourceMillisievertsPerSecond,
                    result.checkedEntities(),
                    result.exposedEntities(),
                    result.checkedPlayers(),
                    result.exposedPlayers(),
                    result.immunePlayersSkippedDamage(),
                    result.maxEntityExposureMillisievertsPerSecond(),
                    result.maxPlayerExposureMillisievertsPerSecond(),
                    result.nearestPlayerName(),
                    result.nearestPlayerDistance(),
                    result.nearestPlayerExposureMillisievertsPerSecond(),
                    result.nearestPlayerDoseMillisievertsThisTick(),
                    result.nearestPlayerTransmission(),
                    result.nearestPlayerImmune(),
                    result.playerDetails()
            );
        }
    }

    private static double centerRadiationSourceMsvPerSecond(int ageTicks) {
        if (ageTicks >= CENTER_RADIATION_DURATION_TICKS) {
            return 0.0D;
        }

        double progress = Mth.clamp(ageTicks / (double) CENTER_RADIATION_DURATION_TICKS, 0.0D, 1.0D);
        double curve = 1.0D - Math.log1p(progress * 9.0D) / Math.log1p(9.0D);
        return CENTER_RADIATION_INITIAL_MSV_PER_SECOND * Math.max(0.0D, curve);
    }

    private void forceExplosionChunks() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ChunkPos centerChunk = chunkPosition();
        int chunkRadius = NuclearExplosionChunkLoading.computeChunkRadius(getRadius());
        int added = NuclearExplosionChunkLoading.forceExplosionChunks(serverLevel, getChunkLoadingOwnerUuid(), centerChunk, chunkRadius, forcedExplosionChunks);
        chunksForced = !forcedExplosionChunks.isEmpty();
        NuclearExplosionChunkLoading.debugForced(getChunkLoadingOwnerUuid(), getId(), centerChunk, chunkRadius, forcedExplosionChunks.size(), added);
    }

    private void unforceExplosionChunks() {
        if (!chunksForced || forcedExplosionChunks.isEmpty()) {
            chunksForced = false;
            forcedExplosionChunks.clear();
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        int released = NuclearExplosionChunkLoading.unforceExplosionChunks(serverLevel, getChunkLoadingOwnerUuid(), forcedExplosionChunks);
        chunksForced = false;
        NuclearExplosionChunkLoading.debugUnforced(getChunkLoadingOwnerUuid(), getId(), released);
    }

    private boolean canReleaseImmediateChunks() {
        return !ENABLE_NUCLEAR_BLOCK_DESTRUCTION
                || !destroyBlocks
                || (destructionPhase == DestructionPhase.COMPLETE && fluidEvaporationPass == null);
    }

    private void setFixedOrigin(double x, double y, double z) {
        originX = x;
        originY = y;
        originZ = z;
        setPos(x, y, z);
        setOldPosAndRot();
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0.0F;
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

    private void applyEntityBlastImpulse() {
        Vec3 center = position();
        double radiusSqr = BLAST_KNOCKBACK_RADIUS * BLAST_KNOCKBACK_RADIUS;
        AABB search = new AABB(center, center).inflate(BLAST_KNOCKBACK_RADIUS);

        // TODO: Replace naive entity blast with a cover-aware blast pass.
        // TODO: Hard blocks between explosion and entity should block or reduce damage and knockback.
        // TODO: Add structural blast wave pass for weak and surface blocks.
        // TODO: Add contamination pass for irradiated and charred crater blocks.
        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, search, entity -> entity.isAlive() && !entity.isRemoved())) {
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
                continue;
            }

            Vec3 offset = entity.position().subtract(center);
            double distanceSqr = offset.lengthSqr();
            if (distanceSqr > radiusSqr) {
                continue;
            }

            double distance = Math.max(1.0D, Math.sqrt(distanceSqr));
            double normalizedDistance = Mth.clamp(distance / BLAST_KNOCKBACK_RADIUS, 0.0D, 1.0D);
            double falloff = Math.pow(1.0D - normalizedDistance, 1.5D);
            double horizontalStrength = BLAST_MIN_KNOCKBACK
                    + (BLAST_MAX_HORIZONTAL_KNOCKBACK - BLAST_MIN_KNOCKBACK) * falloff;
            double verticalFalloff = (1.0D - normalizedDistance) * (1.0D - normalizedDistance);
            double verticalStrength = 0.15D + BLAST_MAX_VERTICAL_KNOCKBACK * verticalFalloff;

            Vec3 horizontal = new Vec3(offset.x, 0.0D, offset.z);
            Vec3 direction = horizontal.lengthSqr() > 1.0E-6D ? horizontal.normalize() : randomHorizontalDirection(entity);
            entity.push(direction.x * horizontalStrength, verticalStrength, direction.z * horizontalStrength);
            entity.hurtMarked = true;

            if (distance <= BLAST_CLOSE_FIRE_RADIUS) {
                int fireSeconds = distance <= BLAST_CLOSE_FIRE_RADIUS * 0.5D
                        ? BLAST_CLOSE_FIRE_SECONDS + BLAST_FAR_FIRE_SECONDS
                        : BLAST_CLOSE_FIRE_SECONDS;
                entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), fireSeconds * 20));
            }
        }
    }

    private Vec3 randomHorizontalDirection(Entity entity) {
        RandomSource random = entity.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        return new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
    }

    private void tickClientEffects() {
        tickMushroomCloudSimulation();
        tickCloudlets();
        if (DEBUG_FORCE_SHOCKWAVE_TEST_CLOUDLET && !debugShockwaveTestCloudletSpawned && tickCount >= 5) {
            debugShockwaveTestCloudletSpawned = true;
            cloudlets.add(new NuclearCloudlet(NuclearCloudletType.SHOCKWAVE, 5.0D, 2.0D, 0.0D, 200, 20.0F, 20.0F, getVisualSeed()));
        }
        boolean shockwaveVisualTick = tickCount % SHOCKWAVE_VISUAL_INTERVAL_TICKS == 0;
        if (shockwaveVisualTick) {
            spawnShockwaveCloudlets();
        }
        logShockwaveVisualDebug(shockwaveVisualTick);
        tickClientShockwaveArrivalSound();
    }

    private void tickMushroomCloudSimulation() {
        if (!shouldSpawnCloud()) {
            mushroomCloudSimulation = null;
            return;
        }
        if (mushroomCloudSimulation == null) {
            mushroomCloudSimulation = new NuclearMushroomCloudSimulation(getVisualSeed(), getRadius());
        }
        mushroomCloudSimulation.tick(level(), position());
    }

    private void tickCloudlets() {
        Iterator<NuclearCloudlet> iterator = cloudlets.iterator();
        while (iterator.hasNext()) {
            NuclearCloudlet cloudlet = iterator.next();
            cloudlet.tick();
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
        RandomSource random = RandomSource.create(getVisualSeed() ^ 0x5DEECE66DL ^ tickCount * 104729L);
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
            maybeLogShockwaveSurfaceSample(index, count, worldX, worldZ, surface, spawnY);
            addShockwaveCloudlet(random, worldX, spawnY, worldZ, cos, sin, baseSize, distanceFactor);
            addedThisTick++;
        }

        if (addedThisTick == 0 && count > 0) {
            spawnFallbackShockwaveRing(count, shockwaveRadius, baseSize, distanceFactor, random);
        }
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

    private void maybeLogShockwaveSurfaceSample(int index, int count, double worldX, double worldZ, SurfaceSample surface, double spawnY) {
        if (!DEBUG_SHOCKWAVE_VISUALS || tickCount % 20 != 0 || index != 0) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke shockwave surface sample: id={} tick={} sample=1/{} bx={} bz={} worldX={} worldZ={} heightmapY={} startY={} foundY={} surfaceTopY={} spawnY={} heightmapValid={} fallbackHeightmap={} fallbackEntityY={} chunkMissing={}",
                getId(),
                tickCount,
                count,
                surface.blockX(),
                surface.blockZ(),
                worldX,
                worldZ,
                surface.heightmapY(),
                surface.startY(),
                surface.foundY(),
                surface.surfaceY(),
                spawnY,
                surface.heightmapValid(),
                surface.usedHeightmapFallback(),
                surface.usedEntityFallback(),
                surface.chunkMissing()
        );
    }

    private void logShockwaveVisualDebug(boolean shockwaveVisualTick) {
        if (!DEBUG_SHOCKWAVE_VISUALS || tickCount % 20 != 0) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke shockwave client debug: id={} tick={} client={} radius={} max={} intervalTick={} methodCalls={} conditionPasses={} attempted={} added={} skipped={} surfaceFound={} surfaceNotFound={} chunkMissing={} invalidY={} heightmapInvalid={} foundTopDown={} fallbackHeightmap={} fallbackEntityY={} shockwaveCloudlets={} totalCloudlets={} spawnCloud={}",
                getId(),
                tickCount,
                level().isClientSide,
                getShockwaveRadius(),
                getShockwaveMaxRadius(),
                shockwaveVisualTick,
                shockwaveSpawnMethodCalls,
                shockwaveSpawnConditionPasses,
                shockwaveCloudletsAttempted,
                shockwaveCloudletsAdded,
                shockwaveCloudletsSkipped,
                shockwaveSurfaceFound,
                shockwaveSurfaceNotFound,
                shockwaveChunkMissing,
                shockwaveInvalidY,
                shockwaveHeightmapInvalid,
                shockwaveFoundSurfaceTopDown,
                shockwaveFallbackHeightmap,
                shockwaveFallbackEntityY,
                shockwaveCloudletCount(),
                cloudlets.size(),
                shouldSpawnCloud()
        );
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
        double maxRadius = getShockwaveMaxRadius();
        if (currentRadius <= 0.0D || currentRadius > maxRadius + SHOCKWAVE_DAMAGE_BAND_WIDTH) {
            return;
        }

        double previousRadius = Math.max(0.0D, (tickCount - 1) * SHOCKWAVE_SPEED_BLOCKS_PER_TICK);
        damageEntitiesInWavefront(previousRadius, currentRadius);
    }

    private void damageEntitiesInWavefront(double previousRadius, double currentRadius) {
        double searchRadius = Math.min(currentRadius + SHOCKWAVE_DAMAGE_BAND_WIDTH, getShockwaveMaxRadius() + SHOCKWAVE_DAMAGE_BAND_WIDTH);
        AABB search = new AABB(
                getX() - searchRadius,
                getY() - 128.0D,
                getZ() - searchRadius,
                getX() + searchRadius,
                getY() + 256.0D,
                getZ() + searchRadius
        );

        Entity source = sourceUuid == null || level().getServer() == null
                ? null
                : level().getServer().getPlayerList().getPlayer(sourceUuid);
        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, search, entity -> !entity.isRemoved())) {
            if (shockwaveDamagedEntities.contains(entity.getUUID())) {
                continue;
            }
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
                continue;
            }

            double distance = horizontalDistanceFromCenter(entity.position());
            if (distance <= previousRadius || distance > currentRadius + SHOCKWAVE_DAMAGE_BAND_WIDTH || distance > getShockwaveMaxRadius()) {
                continue;
            }

            float falloff = 1.0F - (float) Mth.clamp(distance / getShockwaveMaxRadius(), 0.0D, 1.0D) * 0.5F;
            entity.hurt(level().damageSources().explosion(this, source), 1000.0F * falloff);
            Vec3 knockback = entity.position().subtract(position());
            Vec3 horizontal = new Vec3(knockback.x, 0.0D, knockback.z);
            if (horizontal.lengthSqr() > 1.0E-6D) {
                Vec3 direction = horizontal.normalize();
                double strength = 3.0D * falloff;
                entity.push(direction.x * strength, 0.65D * falloff, direction.z * strength);
                entity.hurtMarked = true;
            }
            shockwaveDamagedEntities.add(entity.getUUID());
        }
    }

    private double horizontalDistanceFromCenter(Vec3 position) {
        double dx = position.x - getX();
        double dz = position.z - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double getShockwaveRadius() {
        return tickCount * SHOCKWAVE_SPEED_BLOCKS_PER_TICK;
    }

    private double getShockwaveMaxRadius() {
        return getRadius() * SHOCKWAVE_MAX_RADIUS_MULTIPLIER;
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

    public boolean shouldSpawnCloud() {
        return entityData.get(DATA_SPAWN_CLOUD);
    }

    public boolean shouldFlashSky() {
        return entityData.get(DATA_FLASH_SKY);
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
        appliedEntityBlastImpulse = compound.getBoolean("AppliedEntityBlastImpulse");
        destructionPhase = readDestructionPhase(compound.getString("DestructionPhase"));
        destructionTicks = compound.contains("DestructionTicks") ? compound.getInt("DestructionTicks") : 0;
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
        setFixedOrigin(originX, originY, originZ);
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
        compound.putBoolean("AppliedEntityBlastImpulse", appliedEntityBlastImpulse);
        compound.putString("DestructionPhase", destructionPhase.name());
        compound.putInt("DestructionTicks", destructionTicks);
        compound.putUUID("ChunkLoadingOwnerUuid", getChunkLoadingOwnerUuid());
        Vec3 origin = fixedOrigin();
        compound.putDouble("OriginX", origin.x);
        compound.putDouble("OriginY", origin.y);
        compound.putDouble("OriginZ", origin.z);
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
        SHOCKWAVE
    }

    private enum DestructionPhase {
        NOT_STARTED,
        PLANNING,
        MUTATING,
        COLUMN_COLLAPSE_PLANNING,
        COLUMN_COLLAPSE_MUTATING,
        COMPLETE
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

        private void tick() {
            prevX = x;
            prevY = y;
            prevZ = z;
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
            float red = Mth.lerp(progress, 0.18F, 0.07F);
            float green = Mth.lerp(progress, 0.16F, 0.065F);
            float blue = Mth.lerp(progress, 0.14F, 0.06F);
            return Math.round(Mth.clamp(component == 0 ? red : component == 1 ? green : blue, 0.0F, 1.0F) * 255.0F);
        }
    }
}
