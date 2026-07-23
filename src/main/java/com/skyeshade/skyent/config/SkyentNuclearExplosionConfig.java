package com.skyeshade.skyent.config;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.util.Mth;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class SkyentNuclearExplosionConfig {
    public static final String FILE_NAME = "skyent/nuclear_explosion.toml";

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue DEBUG_DETONATION_TIMING;
    private static final ModConfigSpec.BooleanValue DEBUG_CHUNK_LOADING;
    private static final ModConfigSpec.BooleanValue DEBUG_RAY_TIMING;
    private static final ModConfigSpec.BooleanValue DEBUG_RAY_PLANNER;
    private static final ModConfigSpec.BooleanValue DEBUG_WATER_CLEAR;
    private static final ModConfigSpec.BooleanValue DEBUG_CONTAMINATION;
    private static final ModConfigSpec.BooleanValue DEBUG_FIRE;
    private static final ModConfigSpec.BooleanValue DEBUG_CENTER_RADIATION;

    private static final ModConfigSpec.BooleanValue ASYNC_RAY_PLANNING;
    private static final ModConfigSpec.IntValue ASYNC_RAY_WORKERS;
    private static final ModConfigSpec.IntValue ASYNC_MIN_RAYS;
    private static final ModConfigSpec.DoubleValue RAY_DENSITY_MULTIPLIER;
    private static final ModConfigSpec.IntValue RAY_PLANNING_MAX_RAYS;

    private static final ModConfigSpec.IntValue MUTATION_MAX_BLOCKS_PER_TICK;
    private static final ModConfigSpec.DoubleValue MUTATION_MAX_MILLISECONDS_PER_TICK;
    private static final ModConfigSpec.IntValue MUTATION_MAX_SECTIONS_PER_TICK;
    private static final ModConfigSpec.IntValue MUTATION_MIN_COMPLETED_SECTIONS_BEFORE_AFTERMATH;
    private static final ModConfigSpec.IntValue MUTATION_AFTERMATH_FORCE_START_AFTER_TICKS;
    private static final ModConfigSpec.IntValue MUTATION_NO_PROGRESS_TIMEOUT_TICKS;
    private static final ModConfigSpec.IntValue MUTATION_MAX_TOTAL_DESTRUCTION_TICKS;

    private static final ModConfigSpec.BooleanValue CHUNK_LOADING_ENABLED;
    private static final ModConfigSpec.DoubleValue CHUNK_LOADING_MAX_RADIUS_FOR_IMMEDIATE_CHUNK_LOADING;
    private static final ModConfigSpec.DoubleValue CHUNK_LOADING_IMMEDIATE_RADIUS_MULTIPLIER;
    private static final ModConfigSpec.IntValue CHUNK_LOADING_IMMEDIATE_EXTRA_CHUNKS;
    private static final ModConfigSpec.IntValue CHUNK_LOADING_IMMEDIATE_MIN_CHUNK_RADIUS;
    private static final ModConfigSpec.IntValue CHUNK_LOADING_IMMEDIATE_MAX_CHUNK_RADIUS;
    private static final ModConfigSpec.IntValue CHUNK_LOADING_MAX_FORCED_CHUNKS;
    private static final ModConfigSpec.IntValue CHUNK_LOADING_KEEP_IMMEDIATE_CHUNKS_TICKS;
    private static final ModConfigSpec.BooleanValue CHUNK_LOADING_TICKING_TICKETS;
    private static final ModConfigSpec.IntValue CHUNK_LOADING_DEBUG_FORCE_CHUNK_RADIUS_OVERRIDE;

    private static final ModConfigSpec.BooleanValue AFTERMATH_ADAPTIVE_TPS_THROTTLE;
    private static final ModConfigSpec.DoubleValue AFTERMATH_TARGET_TPS;
    private static final ModConfigSpec.DoubleValue AFTERMATH_SOFT_TPS;
    private static final ModConfigSpec.DoubleValue AFTERMATH_HARD_TPS;
    private static final ModConfigSpec.IntValue AFTERMATH_MIN_WORK_UNITS_PER_TICK;
    private static final ModConfigSpec.IntValue AFTERMATH_MAX_WORK_UNITS_PER_TICK;
    private static final ModConfigSpec.IntValue AFTERMATH_BASE_WORK_UNITS_PER_TICK;
    private static final ModConfigSpec.IntValue AFTERMATH_MIN_COLUMNS_PER_TICK;
    private static final ModConfigSpec.IntValue AFTERMATH_MAX_COLUMNS_PER_TICK;
    private static final ModConfigSpec.IntValue AFTERMATH_BASE_COLUMNS_PER_TICK;
    private static final ModConfigSpec.DoubleValue AFTERMATH_MAX_MILLISECONDS_PER_TICK;
    private static final ModConfigSpec.DoubleValue AFTERMATH_LAGGY_MAX_MILLISECONDS_PER_TICK;
    private static final ModConfigSpec.IntValue AFTERMATH_UNLOADED_CHUNK_SKIP_COOLDOWN_TICKS;

    private static final ModConfigSpec.BooleanValue WATER_EVAPORATION_ENABLED;
    private static final ModConfigSpec.DoubleValue WATER_EVAPORATION_RADIUS_SCALE;
    private static final ModConfigSpec.IntValue WATER_EVAPORATION_RADIAL_LAYERS_PER_STEP;
    private static final ModConfigSpec.IntValue WATER_EVAPORATION_RECHECK_OVERLAP;
    private static final ModConfigSpec.IntValue WATER_EVAPORATION_MAX_BLOCKS_PER_TICK;
    private static final ModConfigSpec.IntValue WATER_EVAPORATION_VERTICAL_RANGE_DOWN;
    private static final ModConfigSpec.IntValue WATER_EVAPORATION_VERTICAL_RANGE_UP;

    private static final ModConfigSpec.DoubleValue FIRE_CHARRING_RADIUS_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue FIRE_CHARRING_LEAF_EVAPORATION_INNER_FRACTION;

    private static final ModConfigSpec.BooleanValue RADIATION_CENTER_BURST_ENABLED;
    private static final ModConfigSpec.IntValue RADIATION_CENTER_BURST_DURATION_TICKS;
    private static final ModConfigSpec.DoubleValue RADIATION_CENTER_BURST_INITIAL_MSV_PER_SECOND;
    private static final ModConfigSpec.DoubleValue RADIATION_CENTER_BURST_RADIUS;

    static {
        BUILDER.comment(
                "Skyes Nuclear Tech nuclear explosion tuning.",
                "Most values are read at runtime, but restart is recommended after editing."
        );

        BUILDER.push("debug");
        DEBUG_DETONATION_TIMING = BUILDER
                .comment("Logs the server-side detonation path and pre-spawn timing.")
                .define("detonation_timing", false);
        DEBUG_CHUNK_LOADING = BUILDER
                .comment("Logs nuclear chunk ticket registration and release timing.")
                .define("chunk_loading", false);
        DEBUG_RAY_TIMING = BUILDER
                .comment("Logs ray planning tick, async worker, snapshot, merge, and mutation timing.")
                .define("ray_timing", false);
        DEBUG_RAY_PLANNER = BUILDER
                .comment("Logs detailed nuclear ray planner counters.")
                .define("ray_planner", false);
        DEBUG_WATER_CLEAR = BUILDER
                .comment("Logs nuclear water evaporation pass progress.")
                .define("water_clear", false);
        DEBUG_CONTAMINATION = BUILDER
                .comment("Logs nuclear aftermath contamination/vitrification planning details.")
                .define("contamination", false);
        DEBUG_FIRE = BUILDER
                .comment("Reserved for nuclear fire/charring debug logging.")
                .define("fire", false);
        DEBUG_CENTER_RADIATION = BUILDER
                .comment("Logs nuclear center radiation burst exposure details.")
                .define("center_radiation", false);
        BUILDER.pop();

        BUILDER.push("ray_planning");
        ASYNC_RAY_PLANNING = BUILDER
                .comment("Enables experimental snapshot-backed async nuclear ray planning.")
                .define("async_ray_planning", true);
        ASYNC_RAY_WORKERS = BUILDER
                .comment("Worker threads used by async ray planning. Clamped to available processors.")
                .defineInRange("async_ray_workers", Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())), 1, 16);
        ASYNC_MIN_RAYS = BUILDER
                .comment("Minimum total rays before async ray planning is eligible.")
                .defineInRange("async_min_rays", 20_000, 0, 2_000_000);
        RAY_DENSITY_MULTIPLIER = BUILDER
                .comment(
                        "Controls destructive ray density.",
                        "Higher values improve crater coverage/surface stripping but increase planning cost.",
                        "Previous default was 8.0 * 4.0 = 32.0.",
                        "Examples: 16.0 = cheaper, 32.0 = default/current, 64.0 = very dense/expensive."
                )
                .defineInRange("ray_density_multiplier", 64.0D, 1.0D, 128.0D);
        RAY_PLANNING_MAX_RAYS = BUILDER
                .comment(
                        "Maximum destructive rays after ray_density_multiplier is applied.",
                        "Raising this can improve coverage at high densities but increases CPU and snapshot/mutation cost.",
                        "Increase max_rays if testing very high densities."
                )
                .defineInRange("max_rays", 200_000_000, 1_024, 2_000_000_000);
        BUILDER.pop();

        BUILDER.push("mutation");
        MUTATION_MAX_BLOCKS_PER_TICK = BUILDER
                .comment("Maximum nuclear destruction block changes applied per server tick.")
                .defineInRange("max_blocks_per_tick", 524_288, 1, 1_048_576);
        MUTATION_MAX_MILLISECONDS_PER_TICK = BUILDER
                .comment("Approximate max milliseconds spent applying nuclear destruction mutations per tick.")
                .defineInRange("max_milliseconds_per_tick", 100.0D, 1.0D, 250.0D);
        MUTATION_MAX_SECTIONS_PER_TICK = BUILDER
                .comment(
                        "Maximum destruction mask sections the mutation queue may complete per tick.",
                        "Higher values can finish large craters faster, but block and millisecond budgets still apply."
                )
                .defineInRange("max_sections_per_tick", 256, 1, 4096);
        MUTATION_MIN_COMPLETED_SECTIONS_BEFORE_AFTERMATH = BUILDER
                .comment("Completed or skipped destruction sections required before column aftermath can start.")
                .defineInRange("min_completed_sections_before_aftermath", 1, 0, 1_000_000);
        MUTATION_AFTERMATH_FORCE_START_AFTER_TICKS = BUILDER
                .comment("Ticks after mutation starts before column aftermath can start even if the section threshold is not met.")
                .defineInRange("aftermath_force_start_after_ticks", 40, 0, 20 * 60 * 60);
        MUTATION_NO_PROGRESS_TIMEOUT_TICKS = BUILDER
                .comment("Ticks before a nuclear destruction job is considered stuck if no phase makes progress. Set 0 to disable.")
                .defineInRange("no_progress_timeout_ticks", 400, 0, 20 * 60 * 60);
        MUTATION_MAX_TOTAL_DESTRUCTION_TICKS = BUILDER
                .comment("Emergency total lifetime cap for nuclear destruction work. Set 0 to disable the fixed total timeout.")
                .defineInRange("max_total_destruction_ticks", 0, 0, 20 * 60 * 60);
        BUILDER.pop();

        BUILDER.push("chunk_loading");
        CHUNK_LOADING_ENABLED = BUILDER
                .comment("Whether nuclear explosions force-load chunks for gameplay work.")
                .define("enabled", true);
        CHUNK_LOADING_MAX_RADIUS_FOR_IMMEDIATE_CHUNK_LOADING = BUILDER
                .comment(
                        "Largest nuke radius used as input for immediate forced chunk radius.",
                        "This does not cap explosion gameplay radius, ray destruction, visuals, shockwave, or radiation.",
                        "Keep immediate_max_chunk_radius and max_forced_chunks conservative unless testing huge nukes."
                )
                .defineInRange("max_radius_for_immediate_chunk_loading", 1000.0D, 1.0D, 10_000.0D);
        CHUNK_LOADING_IMMEDIATE_RADIUS_MULTIPLIER = BUILDER
                .comment("Multiplier applied to nuke radius before converting to immediate forced chunks.")
                .defineInRange("immediate_radius_multiplier", 1.10D, 0.0D, 10.0D);
        CHUNK_LOADING_IMMEDIATE_EXTRA_CHUNKS = BUILDER
                .comment("Extra chunk radius added after radius conversion.")
                .defineInRange("immediate_extra_chunks", 2, 0, 64);
        CHUNK_LOADING_IMMEDIATE_MIN_CHUNK_RADIUS = BUILDER
                .comment("Minimum immediate forced chunk radius.")
                .defineInRange("immediate_min_chunk_radius", 4, 0, 64);
        CHUNK_LOADING_IMMEDIATE_MAX_CHUNK_RADIUS = BUILDER
                .comment("Maximum immediate forced chunk radius.")
                .defineInRange("immediate_max_chunk_radius", 24, 0, 64);
        CHUNK_LOADING_MAX_FORCED_CHUNKS = BUILDER
                .comment("Safety cap for chunks forced by one nuclear explosion.")
                .defineInRange("max_forced_chunks", 2_048, 1, 10_000);
        CHUNK_LOADING_KEEP_IMMEDIATE_CHUNKS_TICKS = BUILDER
                .comment("Minimum ticks to keep immediate nuclear chunks forced after the explosion entity spawns.")
                .defineInRange("keep_immediate_chunks_ticks", 200, 0, 20 * 60 * 10);
        CHUNK_LOADING_TICKING_TICKETS = BUILDER
                .comment("Whether nuclear chunk tickets should tick forced chunks.")
                .define("ticking_tickets", true);
        CHUNK_LOADING_DEBUG_FORCE_CHUNK_RADIUS_OVERRIDE = BUILDER
                .comment("Debug override for immediate forced chunk radius. Use -1 to disable.")
                .defineInRange("debug_force_chunk_radius_override", -1, -1, 64);
        BUILDER.pop();

        BUILDER.push("aftermath");
        AFTERMATH_ADAPTIVE_TPS_THROTTLE = BUILDER
                .comment("Enables TPS-aware throttling for secondary nuclear column aftermath work.")
                .define("adaptive_tps_throttle", true);
        AFTERMATH_TARGET_TPS = BUILDER
                .comment("Nominal target TPS for aftermath throttle diagnostics.")
                .defineInRange("target_tps", 20.0D, 1.0D, 20.0D);
        AFTERMATH_SOFT_TPS = BUILDER
                .comment("TPS at or above this value uses full aftermath budgets.")
                .defineInRange("soft_tps", 18.0D, 1.0D, 20.0D);
        AFTERMATH_HARD_TPS = BUILDER
                .comment("TPS at or below this value uses minimum/laggy aftermath budgets.")
                .defineInRange("hard_tps", 15.0D, 1.0D, 20.0D);
        AFTERMATH_MIN_WORK_UNITS_PER_TICK = BUILDER
                .comment("Minimum column aftermath chunk work units processed per tick when work is available.")
                .defineInRange("min_work_units_per_tick", 1, 1, 4096);
        AFTERMATH_MAX_WORK_UNITS_PER_TICK = BUILDER
                .comment("Maximum column aftermath chunk work units processed per tick.")
                .defineInRange("max_work_units_per_tick", 128, 1, 4096);
        AFTERMATH_BASE_WORK_UNITS_PER_TICK = BUILDER
                .comment("Base column aftermath chunk work units per tick before TPS throttle scaling.")
                .defineInRange("base_work_units_per_tick", 32, 1, 4096);
        AFTERMATH_MIN_COLUMNS_PER_TICK = BUILDER
                .comment("Minimum column aftermath columns scanned per tick when work is available.")
                .defineInRange("min_columns_per_tick", 16, 1, 65_536);
        AFTERMATH_MAX_COLUMNS_PER_TICK = BUILDER
                .comment("Maximum column aftermath columns scanned per tick.")
                .defineInRange("max_columns_per_tick", 4096, 1, 65_536);
        AFTERMATH_BASE_COLUMNS_PER_TICK = BUILDER
                .comment("Base column aftermath columns scanned per tick before TPS throttle scaling.")
                .defineInRange("base_columns_per_tick", 1024, 1, 65_536);
        AFTERMATH_MAX_MILLISECONDS_PER_TICK = BUILDER
                .comment("Maximum milliseconds per tick spent in column aftermath on healthy servers.")
                .defineInRange("max_milliseconds_per_tick", 8.0D, 0.1D, 50.0D);
        AFTERMATH_LAGGY_MAX_MILLISECONDS_PER_TICK = BUILDER
                .comment("Maximum milliseconds per tick spent in column aftermath when TPS is at or below hard_tps.")
                .defineInRange("laggy_max_milliseconds_per_tick", 2.0D, 0.1D, 50.0D);
        AFTERMATH_UNLOADED_CHUNK_SKIP_COOLDOWN_TICKS = BUILDER
                .comment("Reserved cooldown for deferred unloaded aftermath chunks. Column aftermath now force-loads only the active mutation chunk.")
                .defineInRange("unloaded_chunk_skip_cooldown_ticks", 40, 0, 20 * 60 * 60);
        BUILDER.pop();

        BUILDER.push("water_evaporation");
        WATER_EVAPORATION_ENABLED = BUILDER
                .comment("Whether nuclear explosions run the water/lava evaporation pass.")
                .define("enabled", true);
        WATER_EVAPORATION_RADIUS_SCALE = BUILDER
                .comment("Water evaporation radius relative to nuke radius.")
                .defineInRange("radius_scale", 0.75D, 0.0D, 10.0D);
        WATER_EVAPORATION_RADIAL_LAYERS_PER_STEP = BUILDER
                .comment("Outward radial band thickness processed per water evaporation step.")
                .defineInRange("radial_layers_per_step", 2, 1, 64);
        WATER_EVAPORATION_RECHECK_OVERLAP = BUILDER
                .comment("Previous radial layers rechecked to catch inward-flowing water.")
                .defineInRange("recheck_overlap", 1, 0, 16);
        WATER_EVAPORATION_MAX_BLOCKS_PER_TICK = BUILDER
                .comment("Maximum water evaporation block checks and changes per tick.")
                .defineInRange("max_blocks_per_tick", 200_000, 1, 1_000_000);
        WATER_EVAPORATION_VERTICAL_RANGE_DOWN = BUILDER
                .comment("Blocks scanned downward from the explosion center during water evaporation.")
                .defineInRange("vertical_range_down", 96, 0, 384);
        WATER_EVAPORATION_VERTICAL_RANGE_UP = BUILDER
                .comment("Blocks scanned upward from the explosion center during water evaporation.")
                .defineInRange("vertical_range_up", 32, 0, 384);
        BUILDER.pop();

        BUILDER.push("fire_and_charring");
        FIRE_CHARRING_RADIUS_MULTIPLIER = BUILDER
                .comment("Multiplier for nuclear aftermath fire ignition and wood charring radius.")
                .defineInRange("radius_multiplier", 2.0D, 0.0D, 10.0D);
        FIRE_CHARRING_LEAF_EVAPORATION_INNER_FRACTION = BUILDER
                .comment("Inner fraction of the fire/charring radius where leaves evaporate instead of burning.")
                .defineInRange("leaf_evaporation_inner_fraction", 0.8D, 0.0D, 1.0D);
        BUILDER.pop();

        BUILDER.push("radiation");
        RADIATION_CENTER_BURST_ENABLED = BUILDER
                .comment("Whether nuclear explosions emit the initial center radiation burst.")
                .define("center_burst_enabled", true);
        RADIATION_CENTER_BURST_DURATION_TICKS = BUILDER
                .comment("Radius-200 baseline duration of the center radiation burst.")
                .defineInRange("center_burst_duration_ticks", 40, 0, 20 * 60);
        RADIATION_CENTER_BURST_INITIAL_MSV_PER_SECOND = BUILDER
                .comment("Radius-200 baseline initial source strength for the center radiation burst.")
                .defineInRange("center_burst_initial_msv_per_second", 504_250_000.0D, 0.0D, Double.MAX_VALUE);
        RADIATION_CENTER_BURST_RADIUS = BUILDER
                .comment("Radius-200 baseline range of the center radiation burst.")
                .defineInRange("center_burst_radius", 512.0D, 0.0D, 4096.0D);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private SkyentNuclearExplosionConfig() {
    }

    public static boolean debugDetonationTiming() {
        return DEBUG_DETONATION_TIMING.get();
    }

    public static boolean debugChunkLoading() {
        return DEBUG_CHUNK_LOADING.get();
    }

    public static boolean debugRayTiming() {
        return DEBUG_RAY_TIMING.get();
    }

    public static boolean debugRayPlanner() {
        return DEBUG_RAY_PLANNER.get();
    }

    public static boolean debugWaterClear() {
        return DEBUG_WATER_CLEAR.get();
    }

    public static boolean debugContamination() {
        return DEBUG_CONTAMINATION.get();
    }

    public static boolean debugFire() {
        return DEBUG_FIRE.get();
    }

    public static boolean debugCenterRadiation() {
        return DEBUG_CENTER_RADIATION.get();
    }

    public static boolean asyncRayPlanning() {
        return ASYNC_RAY_PLANNING.get();
    }

    public static int asyncRayWorkers() {
        return Mth.clamp(ASYNC_RAY_WORKERS.get(), 1, Math.max(1, Math.min(16, Runtime.getRuntime().availableProcessors())));
    }

    public static int asyncMinRays() {
        return Mth.clamp(ASYNC_MIN_RAYS.get(), 0, 2_000_000);
    }

    public static double rayDensityMultiplier() {
        return Mth.clamp(RAY_DENSITY_MULTIPLIER.get(), 1.0D, 128.0D);
    }

    public static int rayPlanningMaxRays() {
        return Mth.clamp(RAY_PLANNING_MAX_RAYS.get(), 1_024, Integer.MAX_VALUE);
    }

    public static int mutationMaxBlocksPerTick() {
        return Mth.clamp(MUTATION_MAX_BLOCKS_PER_TICK.get(), 1, Integer.MAX_VALUE);
    }

    public static double mutationMaxMillisecondsPerTick() {
        return Mth.clamp(MUTATION_MAX_MILLISECONDS_PER_TICK.get(), 1.0D, 50.0D);
    }

    public static int mutationMaxSectionsPerTick() {
        return Mth.clamp(MUTATION_MAX_SECTIONS_PER_TICK.get(), 1, 4096);
    }

    public static int mutationMinCompletedSectionsBeforeAftermath() {
        return Mth.clamp(MUTATION_MIN_COMPLETED_SECTIONS_BEFORE_AFTERMATH.get(), 0, 1_000_000);
    }

    public static int mutationAftermathForceStartAfterTicks() {
        return Mth.clamp(MUTATION_AFTERMATH_FORCE_START_AFTER_TICKS.get(), 0, 20 * 60 * 60);
    }

    public static int mutationNoProgressTimeoutTicks() {
        return Mth.clamp(MUTATION_NO_PROGRESS_TIMEOUT_TICKS.get(), 0, 20 * 60 * 60);
    }

    public static int mutationMaxTotalDestructionTicks() {
        return Mth.clamp(MUTATION_MAX_TOTAL_DESTRUCTION_TICKS.get(), 0, 20 * 60 * 60);
    }

    public static boolean chunkLoadingEnabled() {
        return CHUNK_LOADING_ENABLED.get();
    }

    public static double chunkLoadingMaxRadiusForImmediateChunkLoading() {
        return Mth.clamp(CHUNK_LOADING_MAX_RADIUS_FOR_IMMEDIATE_CHUNK_LOADING.get(), 1.0D, 10_000.0D);
    }

    public static double chunkLoadingImmediateRadiusMultiplier() {
        return Mth.clamp(CHUNK_LOADING_IMMEDIATE_RADIUS_MULTIPLIER.get(), 0.0D, 10.0D);
    }

    public static int chunkLoadingImmediateExtraChunks() {
        return Mth.clamp(CHUNK_LOADING_IMMEDIATE_EXTRA_CHUNKS.get(), 0, 64);
    }

    public static int chunkLoadingImmediateMinChunkRadius() {
        return Mth.clamp(CHUNK_LOADING_IMMEDIATE_MIN_CHUNK_RADIUS.get(), 0, 64);
    }

    public static int chunkLoadingImmediateMaxChunkRadius() {
        return Mth.clamp(CHUNK_LOADING_IMMEDIATE_MAX_CHUNK_RADIUS.get(), 0, 64);
    }

    public static int chunkLoadingMaxForcedChunks() {
        return Mth.clamp(CHUNK_LOADING_MAX_FORCED_CHUNKS.get(), 1, 10_000);
    }

    public static int chunkLoadingKeepImmediateChunksTicks() {
        return Mth.clamp(CHUNK_LOADING_KEEP_IMMEDIATE_CHUNKS_TICKS.get(), 0, 20 * 60 * 10);
    }

    public static boolean chunkLoadingTickingTickets() {
        return CHUNK_LOADING_TICKING_TICKETS.get();
    }

    public static int chunkLoadingDebugForceChunkRadiusOverride() {
        return Mth.clamp(CHUNK_LOADING_DEBUG_FORCE_CHUNK_RADIUS_OVERRIDE.get(), -1, 64);
    }

    public static boolean aftermathAdaptiveTpsThrottle() {
        return AFTERMATH_ADAPTIVE_TPS_THROTTLE.get();
    }

    public static double aftermathTargetTps() {
        return Mth.clamp(AFTERMATH_TARGET_TPS.get(), 1.0D, 20.0D);
    }

    public static double aftermathSoftTps() {
        return Mth.clamp(AFTERMATH_SOFT_TPS.get(), 1.0D, 20.0D);
    }

    public static double aftermathHardTps() {
        return Mth.clamp(AFTERMATH_HARD_TPS.get(), 1.0D, 20.0D);
    }

    public static int aftermathMinWorkUnitsPerTick() {
        return Mth.clamp(AFTERMATH_MIN_WORK_UNITS_PER_TICK.get(), 1, 4096);
    }

    public static int aftermathMaxWorkUnitsPerTick() {
        return Mth.clamp(AFTERMATH_MAX_WORK_UNITS_PER_TICK.get(), aftermathMinWorkUnitsPerTick(), 4096);
    }

    public static int aftermathBaseWorkUnitsPerTick() {
        return Mth.clamp(AFTERMATH_BASE_WORK_UNITS_PER_TICK.get(), aftermathMinWorkUnitsPerTick(), aftermathMaxWorkUnitsPerTick());
    }

    public static int aftermathMinColumnsPerTick() {
        return Mth.clamp(AFTERMATH_MIN_COLUMNS_PER_TICK.get(), 1, 65_536);
    }

    public static int aftermathMaxColumnsPerTick() {
        return Mth.clamp(AFTERMATH_MAX_COLUMNS_PER_TICK.get(), aftermathMinColumnsPerTick(), 65_536);
    }

    public static int aftermathBaseColumnsPerTick() {
        return Mth.clamp(AFTERMATH_BASE_COLUMNS_PER_TICK.get(), aftermathMinColumnsPerTick(), aftermathMaxColumnsPerTick());
    }

    public static double aftermathMaxMillisecondsPerTick() {
        return Mth.clamp(AFTERMATH_MAX_MILLISECONDS_PER_TICK.get(), 0.1D, 50.0D);
    }

    public static double aftermathLaggyMaxMillisecondsPerTick() {
        return Mth.clamp(AFTERMATH_LAGGY_MAX_MILLISECONDS_PER_TICK.get(), 0.1D, aftermathMaxMillisecondsPerTick());
    }

    public static int aftermathUnloadedChunkSkipCooldownTicks() {
        return Mth.clamp(AFTERMATH_UNLOADED_CHUNK_SKIP_COOLDOWN_TICKS.get(), 0, 20 * 60 * 60);
    }

    public static boolean waterEvaporationEnabled() {
        return WATER_EVAPORATION_ENABLED.get();
    }

    public static double waterEvaporationRadiusScale() {
        return Mth.clamp(WATER_EVAPORATION_RADIUS_SCALE.get(), 0.0D, 10.0D);
    }

    public static int waterEvaporationRadialLayersPerStep() {
        return Mth.clamp(WATER_EVAPORATION_RADIAL_LAYERS_PER_STEP.get(), 1, 64);
    }

    public static int waterEvaporationRecheckOverlap() {
        return Mth.clamp(WATER_EVAPORATION_RECHECK_OVERLAP.get(), 0, 16);
    }

    public static int waterEvaporationMaxBlocksPerTick() {
        return Mth.clamp(WATER_EVAPORATION_MAX_BLOCKS_PER_TICK.get(), 1, 1_000_000);
    }

    public static int waterEvaporationVerticalRangeDown() {
        return Mth.clamp(WATER_EVAPORATION_VERTICAL_RANGE_DOWN.get(), 0, 384);
    }

    public static int waterEvaporationVerticalRangeUp() {
        return Mth.clamp(WATER_EVAPORATION_VERTICAL_RANGE_UP.get(), 0, 384);
    }

    public static double fireCharringRadiusMultiplier() {
        return Mth.clamp(FIRE_CHARRING_RADIUS_MULTIPLIER.get(), 0.0D, 10.0D);
    }

    public static double fireCharringLeafEvaporationInnerFraction() {
        return Mth.clamp(FIRE_CHARRING_LEAF_EVAPORATION_INNER_FRACTION.get(), 0.0D, 1.0D);
    }

    public static boolean radiationCenterBurstEnabled() {
        return RADIATION_CENTER_BURST_ENABLED.get();
    }

    public static int radiationCenterBurstDurationTicks() {
        return Mth.clamp(RADIATION_CENTER_BURST_DURATION_TICKS.get(), 0, 20 * 60);
    }

    public static double radiationCenterBurstInitialMsvPerSecond() {
        return Math.max(0.0D, RADIATION_CENTER_BURST_INITIAL_MSV_PER_SECOND.get());
    }

    public static double radiationCenterBurstRadius() {
        return Mth.clamp(RADIATION_CENTER_BURST_RADIUS.get(), 0.0D, 4096.0D);
    }

    public static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Skyent nuclear explosion config loaded: asyncRayPlanning={} asyncWorkers={} asyncMinRays={} rayDensityMultiplier={} maxRays={} mutationMaxBlocksPerTick={} mutationMaxMsPerTick={} mutationMaxSectionsPerTick={} aftermathMinCompletedSections={} aftermathForceStartAfterTicks={} noProgressTimeoutTicks={} maxTotalDestructionTicks={} maxRadiusForImmediateChunkLoading={} immediateMaxChunkRadius={} maxForcedChunks={} aftermathAdaptiveThrottle={} aftermathBaseWorkUnits={} aftermathBaseColumns={} aftermathMaxMs={} aftermathLaggyMaxMs={} waterRadiusScale={} fireRadiusMultiplier={} radiationBurstEnabled={}",
                asyncRayPlanning(),
                asyncRayWorkers(),
                asyncMinRays(),
                rayDensityMultiplier(),
                rayPlanningMaxRays(),
                mutationMaxBlocksPerTick(),
                mutationMaxMillisecondsPerTick(),
                mutationMaxSectionsPerTick(),
                mutationMinCompletedSectionsBeforeAftermath(),
                mutationAftermathForceStartAfterTicks(),
                mutationNoProgressTimeoutTicks(),
                mutationMaxTotalDestructionTicks(),
                chunkLoadingMaxRadiusForImmediateChunkLoading(),
                chunkLoadingImmediateMaxChunkRadius(),
                chunkLoadingMaxForcedChunks(),
                aftermathAdaptiveTpsThrottle(),
                aftermathBaseWorkUnitsPerTick(),
                aftermathBaseColumnsPerTick(),
                aftermathMaxMillisecondsPerTick(),
                aftermathLaggyMaxMillisecondsPerTick(),
                waterEvaporationRadiusScale(),
                fireCharringRadiusMultiplier(),
                radiationCenterBurstEnabled()
        );
    }
}
