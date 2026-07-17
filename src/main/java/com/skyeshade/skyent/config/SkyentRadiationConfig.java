package com.skyeshade.skyent.config;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.util.Mth;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class SkyentRadiationConfig {
    public static final String FILE_NAME = "skyent/radiation.toml";

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue EXPOSURE_PLAYER_UPDATE_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue EXPOSURE_ENTITY_UPDATE_INTERVAL_TICKS;
    private static final ModConfigSpec.DoubleValue EXPOSURE_RADIOACTIVE_BLOCK_SCAN_RADIUS;
    private static final ModConfigSpec.IntValue EXPOSURE_MAX_HOTTEST_SOURCES;
    private static final ModConfigSpec.IntValue EXPOSURE_HOTTEST_CANDIDATE_POOL_SIZE;
    private static final ModConfigSpec.IntValue EXPOSURE_MAX_CLOSEST_SOURCES;
    private static final ModConfigSpec.IntValue EXPOSURE_MAX_RANDOM_SOURCES;
    private static final ModConfigSpec.BooleanValue EXPOSURE_ENABLE_SOURCE_SAMPLING_CAP;
    private static final ModConfigSpec.BooleanValue EXPOSURE_USE_STREAMING_SOURCE_SELECTION;
    private static final ModConfigSpec.BooleanValue EXPOSURE_USE_LOCAL_SOURCE_CACHE;
    private static final ModConfigSpec.BooleanValue EXPOSURE_USE_PLAYER_LOCAL_SOURCE_CACHE;
    private static final ModConfigSpec.BooleanValue EXPOSURE_USE_ENTITY_LOCAL_SOURCE_CACHE;
    private static final ModConfigSpec.IntValue EXPOSURE_LOCAL_CACHE_MAX_SOURCES;
    private static final ModConfigSpec.IntValue EXPOSURE_LOCAL_CACHE_MIN_SOURCES_BEFORE_FULL_SCAN;
    private static final ModConfigSpec.IntValue EXPOSURE_LOCAL_CACHE_SOURCE_REFS_SAMPLED_PER_TICK;
    private static final ModConfigSpec.IntValue EXPOSURE_LOCAL_CACHE_SOURCE_REFS_SAMPLED_PER_PLAYER_UPDATE;
    private static final ModConfigSpec.IntValue EXPOSURE_LOCAL_CACHE_STALE_AFTER_TICKS;
    private static final ModConfigSpec.IntValue EXPOSURE_LOCAL_CACHE_FULL_RESCAN_INTERVAL_TICKS;
    private static final ModConfigSpec.DoubleValue EXPOSURE_LOCAL_CACHE_MAX_DISTANCE_MULTIPLIER;
    private static final ModConfigSpec.IntValue EXPOSURE_LOCAL_CACHE_EVICTION_BATCH_SIZE;
    private static final ModConfigSpec.BooleanValue EXPOSURE_LOCAL_CACHE_PREFER_EVICTION_OF_WEAK_SOURCES;

    private static final ModConfigSpec.BooleanValue HOT_BLOCK_RAYS_ENABLED;
    private static final ModConfigSpec.IntValue HOT_BLOCK_RAYS_MAX_EMITTERS_PER_TICK;
    private static final ModConfigSpec.IntValue HOT_BLOCK_RAYS_SOFT_EMITTERS_PER_TICK;
    private static final ModConfigSpec.IntValue HOT_BLOCK_RAYS_BASE_EXTRA_DELAY_TICKS;
    private static final ModConfigSpec.IntValue HOT_BLOCK_RAYS_MAX_EXTRA_DELAY_TICKS;
    private static final ModConfigSpec.IntValue HOT_BLOCK_RAYS_THROTTLE_RANDOM_JITTER_TICKS;

    private static final ModConfigSpec.BooleanValue DEBUG_EXPOSURE_SAMPLING;
    private static final ModConfigSpec.BooleanValue DEBUG_PLAYER_EXPOSURE_SAMPLING;
    private static final ModConfigSpec.BooleanValue DEBUG_ENTITY_EXPOSURE_SAMPLING;
    private static final ModConfigSpec.BooleanValue DEBUG_HOT_BLOCK_RAY_THROTTLE;

    static {
        BUILDER.comment(
                "Skyes Nuclear Tech radiation tuning.",
                "Most values are read at runtime; restart is still recommended after editing."
        );

        BUILDER.push("exposure");
        EXPOSURE_PLAYER_UPDATE_INTERVAL_TICKS = BUILDER
                .comment("Ticks between player environmental radiation exposure scans.")
                .defineInRange("player_update_interval_ticks", 5, 1, 1200);
        EXPOSURE_ENTITY_UPDATE_INTERVAL_TICKS = BUILDER
                .comment("Ticks between non-player living entity environmental radiation exposure scans.")
                .defineInRange("entity_update_interval_ticks", 20, 1, 1200);
        EXPOSURE_RADIOACTIVE_BLOCK_SCAN_RADIUS = BUILDER
                .comment("Radius in blocks used when collecting nearby radioactive block/entity sources for exposure.")
                .defineInRange("radioactive_block_scan_radius", 128.0D, 1.0D, 1024.0D);
        EXPOSURE_MAX_HOTTEST_SOURCES = BUILDER
                .comment("Maximum hot sources sampled before exposure raycasts. Strength wins first; distance only breaks near-equal strength ties.")
                .defineInRange("max_hottest_sources", 10, 0, 1024);
        EXPOSURE_HOTTEST_CANDIDATE_POOL_SIZE = BUILDER
                .comment("Number of strongest sources retained while scanning. Final hottest picks are still strength-first.")
                .defineInRange("hottest_candidate_pool_size", 64, 1, 4096);
        EXPOSURE_MAX_CLOSEST_SOURCES = BUILDER
                .comment("Maximum closest sources sampled before exposure raycasts.")
                .defineInRange("max_closest_sources", 10, 0, 1024);
        EXPOSURE_MAX_RANDOM_SOURCES = BUILDER
                .comment("Maximum random remaining sources sampled before exposure raycasts.")
                .defineInRange("max_random_sources", 10, 0, 1024);
        EXPOSURE_ENABLE_SOURCE_SAMPLING_CAP = BUILDER
                .comment("Caps exposure raycasts to hottest + closest + random sources. Keeps huge radioactive craters playable.")
                .define("enable_source_sampling_cap", true);
        EXPOSURE_USE_STREAMING_SOURCE_SELECTION = BUILDER
                .comment("Uses bounded top-k/reservoir source selection while scanning instead of sorting all nearby contributors.")
                .define("use_streaming_source_selection", true);
        EXPOSURE_USE_LOCAL_SOURCE_CACHE = BUILDER
                .comment(
                        "Legacy/global switch for the experimental per-target rolling source cache.",
                        "Local source caching is experimental and can be worse than chunk-bucket scans near dense nuke craters.",
                        "Leave disabled unless profiling. Player/entity cache switches below must also be enabled."
                )
                .define("use_local_source_cache", false);
        EXPOSURE_USE_PLAYER_LOCAL_SOURCE_CACHE = BUILDER
                .comment("Enables the experimental local source cache for players only. Requires use_local_source_cache=true.")
                .define("use_player_local_source_cache", false);
        EXPOSURE_USE_ENTITY_LOCAL_SOURCE_CACHE = BUILDER
                .comment("Enables the experimental local source cache for non-player living entities. Keep disabled unless profiling many mobs carefully.")
                .define("use_entity_local_source_cache", false);
        EXPOSURE_LOCAL_CACHE_MAX_SOURCES = BUILDER
                .comment("Maximum radioactive sources retained per player/entity/geiger local exposure cache.")
                .defineInRange("local_cache_max_sources", 512, 16, 16_384);
        EXPOSURE_LOCAL_CACHE_MIN_SOURCES_BEFORE_FULL_SCAN = BUILDER
                .comment("If a cache has fewer sources than this, one full scan may seed it. Set to 0 to avoid this fallback.")
                .defineInRange("local_cache_min_sources_before_full_scan", 32, 0, 16_384);
        EXPOSURE_LOCAL_CACHE_SOURCE_REFS_SAMPLED_PER_TICK = BUILDER
                .comment("Base source reference sample budget per exposure update for non-player/geiger targets.")
                .defineInRange("local_cache_source_refs_sampled_per_tick", 100, 1, 100_000);
        EXPOSURE_LOCAL_CACHE_SOURCE_REFS_SAMPLED_PER_PLAYER_UPDATE = BUILDER
                .comment("Source reference sample budget per player exposure update. Higher values discover crater sources faster.")
                .defineInRange("local_cache_source_refs_sampled_per_player_update", 300, 1, 100_000);
        EXPOSURE_LOCAL_CACHE_STALE_AFTER_TICKS = BUILDER
                .comment("Cached sources not refreshed within this many ticks become eviction candidates.")
                .defineInRange("local_cache_stale_after_ticks", 200, 1, 120_000);
        EXPOSURE_LOCAL_CACHE_FULL_RESCAN_INTERVAL_TICKS = BUILDER
                .comment("Optional full cache reseed interval. 0 disables periodic full rescans.")
                .defineInRange("local_cache_full_rescan_interval_ticks", 0, 0, 120_000);
        EXPOSURE_LOCAL_CACHE_MAX_DISTANCE_MULTIPLIER = BUILDER
                .comment("Cached sources beyond scan_radius * this multiplier are evicted or ignored.")
                .defineInRange("local_cache_max_distance_multiplier", 1.15D, 1.0D, 4.0D);
        EXPOSURE_LOCAL_CACHE_EVICTION_BATCH_SIZE = BUILDER
                .comment("Maximum cache entries removed in one eviction pass when over capacity.")
                .defineInRange("local_cache_eviction_batch_size", 64, 1, 4096);
        EXPOSURE_LOCAL_CACHE_PREFER_EVICTION_OF_WEAK_SOURCES = BUILDER
                .comment("When evicting, prefer removing stale/far/weak sources so hot nearby sources remain cached.")
                .define("local_cache_prefer_eviction_of_weak_sources", true);
        BUILDER.pop();

        BUILDER.push("hot_block_rays");
        HOT_BLOCK_RAYS_ENABLED = BUILDER
                .comment("Enables world-effect ray emissions from very hot radioactive blocks.")
                .define("enabled", true);
        HOT_BLOCK_RAYS_MAX_EMITTERS_PER_TICK = BUILDER
                .comment("Hard server-wide cap for hot radioactive block world-effect ray emitters per server tick.")
                .defineInRange("max_emitters_per_tick", 64, 1, 4096);
        HOT_BLOCK_RAYS_SOFT_EMITTERS_PER_TICK = BUILDER
                .comment("Soft cap where extra randomized delay begins increasing for denied emitters.")
                .defineInRange("soft_emitters_per_tick", 32, 1, 4096);
        HOT_BLOCK_RAYS_BASE_EXTRA_DELAY_TICKS = BUILDER
                .comment("Base delay assigned to throttled hot block ray emitters.")
                .defineInRange("base_extra_delay_ticks", 0, 0, 1200);
        HOT_BLOCK_RAYS_MAX_EXTRA_DELAY_TICKS = BUILDER
                .comment("Maximum load-based delay assigned to throttled hot block ray emitters.")
                .defineInRange("max_extra_delay_ticks", 200, 0, 12000);
        HOT_BLOCK_RAYS_THROTTLE_RANDOM_JITTER_TICKS = BUILDER
                .comment("Random jitter added to throttled hot block ray emitter delays so many sources spread out fairly.")
                .defineInRange("throttle_random_jitter_ticks", 80, 0, 12000);
        BUILDER.pop();

        BUILDER.push("debug");
        DEBUG_EXPOSURE_SAMPLING = BUILDER
                .comment("Legacy/global exposure sampling debug. If true, enables both player and entity exposure sampling logs.")
                .define("exposure_sampling", false);
        DEBUG_PLAYER_EXPOSURE_SAMPLING = BUILDER
                .comment("Logs player radiation exposure source sampling and ray timing.")
                .define("player_exposure_sampling", false);
        DEBUG_ENTITY_EXPOSURE_SAMPLING = BUILDER
                .comment("Logs non-player entity radiation exposure source sampling.")
                .define("entity_exposure_sampling", false);
        DEBUG_HOT_BLOCK_RAY_THROTTLE = BUILDER
                .comment("Logs periodic server-wide hot radioactive block ray throttle counters.")
                .define("hot_block_ray_throttle", false);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private SkyentRadiationConfig() {
    }

    public static int exposurePlayerUpdateIntervalTicks() {
        return Mth.clamp(EXPOSURE_PLAYER_UPDATE_INTERVAL_TICKS.get(), 1, 1200);
    }

    public static int exposureEntityUpdateIntervalTicks() {
        return Mth.clamp(EXPOSURE_ENTITY_UPDATE_INTERVAL_TICKS.get(), 1, 1200);
    }

    public static double exposureRadioactiveBlockScanRadius() {
        return Mth.clamp(EXPOSURE_RADIOACTIVE_BLOCK_SCAN_RADIUS.get(), 1.0D, 1024.0D);
    }

    public static int exposureMaxHottestSources() {
        return Mth.clamp(EXPOSURE_MAX_HOTTEST_SOURCES.get(), 0, 1024);
    }

    public static int exposureHottestCandidatePoolSize() {
        return Mth.clamp(EXPOSURE_HOTTEST_CANDIDATE_POOL_SIZE.get(), Math.max(1, exposureMaxHottestSources()), 4096);
    }

    public static int exposureMaxClosestSources() {
        return Mth.clamp(EXPOSURE_MAX_CLOSEST_SOURCES.get(), 0, 1024);
    }

    public static int exposureMaxRandomSources() {
        return Mth.clamp(EXPOSURE_MAX_RANDOM_SOURCES.get(), 0, 1024);
    }

    public static boolean exposureSourceSamplingCapEnabled() {
        return EXPOSURE_ENABLE_SOURCE_SAMPLING_CAP.get();
    }

    public static boolean exposureStreamingSourceSelectionEnabled() {
        return EXPOSURE_USE_STREAMING_SOURCE_SELECTION.get();
    }

    public static boolean exposureLocalSourceCacheEnabled() {
        return EXPOSURE_USE_LOCAL_SOURCE_CACHE.get();
    }

    public static boolean exposurePlayerLocalSourceCacheEnabled() {
        return EXPOSURE_USE_LOCAL_SOURCE_CACHE.get() && EXPOSURE_USE_PLAYER_LOCAL_SOURCE_CACHE.get();
    }

    public static boolean exposureEntityLocalSourceCacheEnabled() {
        return EXPOSURE_USE_LOCAL_SOURCE_CACHE.get() && EXPOSURE_USE_ENTITY_LOCAL_SOURCE_CACHE.get();
    }

    public static int exposureLocalCacheMaxSources() {
        return Mth.clamp(EXPOSURE_LOCAL_CACHE_MAX_SOURCES.get(), 16, 16_384);
    }

    public static int exposureLocalCacheMinSourcesBeforeFullScan() {
        return Mth.clamp(EXPOSURE_LOCAL_CACHE_MIN_SOURCES_BEFORE_FULL_SCAN.get(), 0, 16_384);
    }

    public static int exposureLocalCacheSourceRefsSampledPerTick() {
        return Mth.clamp(EXPOSURE_LOCAL_CACHE_SOURCE_REFS_SAMPLED_PER_TICK.get(), 1, 100_000);
    }

    public static int exposureLocalCacheSourceRefsSampledPerPlayerUpdate() {
        return Mth.clamp(EXPOSURE_LOCAL_CACHE_SOURCE_REFS_SAMPLED_PER_PLAYER_UPDATE.get(), 1, 100_000);
    }

    public static int exposureLocalCacheStaleAfterTicks() {
        return Mth.clamp(EXPOSURE_LOCAL_CACHE_STALE_AFTER_TICKS.get(), 1, 120_000);
    }

    public static int exposureLocalCacheFullRescanIntervalTicks() {
        return Mth.clamp(EXPOSURE_LOCAL_CACHE_FULL_RESCAN_INTERVAL_TICKS.get(), 0, 120_000);
    }

    public static double exposureLocalCacheMaxDistanceMultiplier() {
        return Mth.clamp(EXPOSURE_LOCAL_CACHE_MAX_DISTANCE_MULTIPLIER.get(), 1.0D, 4.0D);
    }

    public static int exposureLocalCacheEvictionBatchSize() {
        return Mth.clamp(EXPOSURE_LOCAL_CACHE_EVICTION_BATCH_SIZE.get(), 1, 4096);
    }

    public static boolean exposureLocalCachePreferEvictionOfWeakSources() {
        return EXPOSURE_LOCAL_CACHE_PREFER_EVICTION_OF_WEAK_SOURCES.get();
    }

    public static boolean hotBlockRaysEnabled() {
        return HOT_BLOCK_RAYS_ENABLED.get();
    }

    public static int hotBlockRaysMaxEmittersPerTick() {
        return Mth.clamp(HOT_BLOCK_RAYS_MAX_EMITTERS_PER_TICK.get(), 1, 4096);
    }

    public static int hotBlockRaysSoftEmittersPerTick() {
        return Mth.clamp(HOT_BLOCK_RAYS_SOFT_EMITTERS_PER_TICK.get(), 1, hotBlockRaysMaxEmittersPerTick());
    }

    public static int hotBlockRaysBaseExtraDelayTicks() {
        return Mth.clamp(HOT_BLOCK_RAYS_BASE_EXTRA_DELAY_TICKS.get(), 0, 1200);
    }

    public static int hotBlockRaysMaxExtraDelayTicks() {
        return Mth.clamp(HOT_BLOCK_RAYS_MAX_EXTRA_DELAY_TICKS.get(), 0, 12000);
    }

    public static int hotBlockRaysThrottleRandomJitterTicks() {
        return Mth.clamp(HOT_BLOCK_RAYS_THROTTLE_RANDOM_JITTER_TICKS.get(), 0, 12000);
    }

    public static boolean debugExposureSampling() {
        return DEBUG_EXPOSURE_SAMPLING.get();
    }

    public static boolean debugPlayerExposureSampling() {
        return DEBUG_EXPOSURE_SAMPLING.get() || DEBUG_PLAYER_EXPOSURE_SAMPLING.get();
    }

    public static boolean debugEntityExposureSampling() {
        return DEBUG_EXPOSURE_SAMPLING.get() || DEBUG_ENTITY_EXPOSURE_SAMPLING.get();
    }

    public static boolean debugHotBlockRayThrottle() {
        return DEBUG_HOT_BLOCK_RAY_THROTTLE.get();
    }

    public static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Skyent radiation config loaded: playerInterval={} entityInterval={} scanRadius={} samplingCap={} streamingSelection={} localCache={} playerLocalCache={} entityLocalCache={} localCacheMaxSources={} localCachePlayerSampleRefs={} hottest={} hottestPool={} closest={} random={} legacyExposureSamplingDebug={} playerExposureSamplingDebug={} entityExposureSamplingDebug={} hotBlockRays={} maxEmitters={} softEmitters={}",
                exposurePlayerUpdateIntervalTicks(),
                exposureEntityUpdateIntervalTicks(),
                exposureRadioactiveBlockScanRadius(),
                exposureSourceSamplingCapEnabled(),
                exposureStreamingSourceSelectionEnabled(),
                exposureLocalSourceCacheEnabled(),
                exposurePlayerLocalSourceCacheEnabled(),
                exposureEntityLocalSourceCacheEnabled(),
                exposureLocalCacheMaxSources(),
                exposureLocalCacheSourceRefsSampledPerPlayerUpdate(),
                exposureMaxHottestSources(),
                exposureHottestCandidatePoolSize(),
                exposureMaxClosestSources(),
                exposureMaxRandomSources(),
                debugExposureSampling(),
                debugPlayerExposureSampling(),
                debugEntityExposureSampling(),
                hotBlockRaysEnabled(),
                hotBlockRaysMaxEmittersPerTick(),
                hotBlockRaysSoftEmittersPerTick()
        );
    }
}
