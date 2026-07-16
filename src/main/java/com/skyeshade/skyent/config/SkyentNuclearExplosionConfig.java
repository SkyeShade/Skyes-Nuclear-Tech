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

    private static final ModConfigSpec.BooleanValue CHUNK_LOADING_ENABLED;
    private static final ModConfigSpec.DoubleValue CHUNK_LOADING_MAX_GAMEPLAY_NUKE_RADIUS;
    private static final ModConfigSpec.DoubleValue CHUNK_LOADING_IMMEDIATE_RADIUS_MULTIPLIER;
    private static final ModConfigSpec.IntValue CHUNK_LOADING_IMMEDIATE_EXTRA_CHUNKS;
    private static final ModConfigSpec.IntValue CHUNK_LOADING_IMMEDIATE_MIN_CHUNK_RADIUS;
    private static final ModConfigSpec.IntValue CHUNK_LOADING_IMMEDIATE_MAX_CHUNK_RADIUS;
    private static final ModConfigSpec.IntValue CHUNK_LOADING_MAX_FORCED_CHUNKS;
    private static final ModConfigSpec.IntValue CHUNK_LOADING_KEEP_IMMEDIATE_CHUNKS_TICKS;
    private static final ModConfigSpec.BooleanValue CHUNK_LOADING_TICKING_TICKETS;
    private static final ModConfigSpec.IntValue CHUNK_LOADING_DEBUG_FORCE_CHUNK_RADIUS_OVERRIDE;

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
                .defineInRange("max_rays", 3_200_000, 1_024, 20_000_000);
        BUILDER.pop();

        BUILDER.push("mutation");
        MUTATION_MAX_BLOCKS_PER_TICK = BUILDER
                .comment("Maximum nuclear destruction block changes applied per server tick.")
                .defineInRange("max_blocks_per_tick", 8_192, 1, 100_000);
        MUTATION_MAX_MILLISECONDS_PER_TICK = BUILDER
                .comment("Approximate max milliseconds spent applying nuclear destruction mutations per tick.")
                .defineInRange("max_milliseconds_per_tick", 12.0D, 1.0D, 50.0D);
        BUILDER.pop();

        BUILDER.push("chunk_loading");
        CHUNK_LOADING_ENABLED = BUILDER
                .comment("Whether nuclear explosions force-load chunks for gameplay work.")
                .define("enabled", true);
        CHUNK_LOADING_MAX_GAMEPLAY_NUKE_RADIUS = BUILDER
                .comment("Largest nuke radius used when computing immediate forced chunk radius.")
                .defineInRange("max_gameplay_nuke_radius", 300.0D, 1.0D, 300.0D);
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
                .defineInRange("leaf_evaporation_inner_fraction", 0.5D, 0.0D, 1.0D);
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
        return Mth.clamp(RAY_PLANNING_MAX_RAYS.get(), 1_024, 2_000_000);
    }

    public static int mutationMaxBlocksPerTick() {
        return Mth.clamp(MUTATION_MAX_BLOCKS_PER_TICK.get(), 1, 100_000);
    }

    public static double mutationMaxMillisecondsPerTick() {
        return Mth.clamp(MUTATION_MAX_MILLISECONDS_PER_TICK.get(), 1.0D, 50.0D);
    }

    public static boolean chunkLoadingEnabled() {
        return CHUNK_LOADING_ENABLED.get();
    }

    public static double chunkLoadingMaxGameplayNukeRadius() {
        return Mth.clamp(CHUNK_LOADING_MAX_GAMEPLAY_NUKE_RADIUS.get(), 1.0D, 300.0D);
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
                "Skyent nuclear explosion config loaded: asyncRayPlanning={} asyncWorkers={} asyncMinRays={} rayDensityMultiplier={} maxRays={} mutationMaxBlocksPerTick={} mutationMaxMsPerTick={} maxGameplayNukeRadius={} waterRadiusScale={} fireRadiusMultiplier={} radiationBurstEnabled={}",
                asyncRayPlanning(),
                asyncRayWorkers(),
                asyncMinRays(),
                rayDensityMultiplier(),
                rayPlanningMaxRays(),
                mutationMaxBlocksPerTick(),
                mutationMaxMillisecondsPerTick(),
                chunkLoadingMaxGameplayNukeRadius(),
                waterEvaporationRadiusScale(),
                fireCharringRadiusMultiplier(),
                radiationCenterBurstEnabled()
        );
    }
}
