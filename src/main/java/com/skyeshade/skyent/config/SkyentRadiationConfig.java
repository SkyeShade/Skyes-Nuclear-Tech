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

    private static final ModConfigSpec.BooleanValue DEBUG_EXPOSURE_SAMPLING;
    private static final ModConfigSpec.BooleanValue DEBUG_PLAYER_EXPOSURE_SAMPLING;
    private static final ModConfigSpec.BooleanValue DEBUG_ENTITY_EXPOSURE_SAMPLING;

    private static final ModConfigSpec.BooleanValue SPATIAL_INDEX_ENABLED;
    private static final ModConfigSpec.IntValue SPATIAL_INDEX_CELL_SIZE;
    private static final ModConfigSpec.BooleanValue SPATIAL_INDEX_DEBUG;

    private static final ModConfigSpec.BooleanValue EXPOSURE_CLUSTERING_ENABLED;
    private static final ModConfigSpec.BooleanValue EXPOSURE_CLUSTER_STATIC_WEAK_SOURCES;
    private static final ModConfigSpec.IntValue EXPOSURE_MIN_SOURCES_PER_AGGREGATE_CELL;
    private static final ModConfigSpec.DoubleValue EXPOSURE_CLUSTER_MAX_INDIVIDUAL_STRENGTH;
    private static final ModConfigSpec.DoubleValue EXPOSURE_ALWAYS_INDIVIDUAL_STRENGTH;
    private static final ModConfigSpec.DoubleValue EXPOSURE_DOMINANT_SOURCE_FRACTION_FOR_INDIVIDUAL;
    private static final ModConfigSpec.BooleanValue EXPOSURE_DISABLE_AGGREGATION_NEAR_SHIELDING;
    private static final ModConfigSpec.IntValue EXPOSURE_SHIELDING_NEIGHBOR_RADIUS;
    private static final ModConfigSpec.ConfigValue<String> EXPOSURE_AGGREGATE_STRENGTH_MODE;
    private static final ModConfigSpec.DoubleValue EXPOSURE_AGGREGATE_MAX_STRENGTH;
    private static final ModConfigSpec.ConfigValue<String> EXPOSURE_AGGREGATE_POSITION_MODE;

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
        BUILDER.pop();

        BUILDER.push("spatial_index");
        SPATIAL_INDEX_ENABLED = BUILDER
                .comment("Uses a dimension -> chunk -> 3D cell source index for radiation source collection.")
                .define("enabled", true);
        SPATIAL_INDEX_CELL_SIZE = BUILDER
                .comment("Cell size in blocks for the radiation spatial index. Must divide 16; invalid values are clamped to 8.")
                .defineInRange("cell_size", 8, 1, 16);
        SPATIAL_INDEX_DEBUG = BUILDER
                .comment("Logs radiation spatial index rebuild/query details.")
                .define("debug", false);
        BUILDER.pop();

        BUILDER.push("exposure_clustering");
        EXPOSURE_CLUSTERING_ENABLED = BUILDER
                .comment("Allows weak static radioactive block sources in the same spatial cell to be represented by one aggregate source.")
                .define("enabled", true);
        EXPOSURE_CLUSTER_STATIC_WEAK_SOURCES = BUILDER
                .comment("Clusters static weak/medium block sources. Hot/special sources remain individual.")
                .define("cluster_static_weak_sources", true);
        EXPOSURE_MIN_SOURCES_PER_AGGREGATE_CELL = BUILDER
                .comment("Minimum number of clusterable radioactive block sources required before a cell may aggregate.")
                .defineInRange("min_sources_per_aggregate_cell", 8, 1, 4096);
        EXPOSURE_CLUSTER_MAX_INDIVIDUAL_STRENGTH = BUILDER
                .comment("Sources at or below this strength may be clustered when otherwise safe.")
                .defineInRange("cluster_max_individual_strength", 999.0D, 0.0D, 1_000_000.0D);
        EXPOSURE_ALWAYS_INDIVIDUAL_STRENGTH = BUILDER
                .comment("Sources at or above this strength are always kept as individual candidates.")
                .defineInRange("always_individual_strength", 1000.0D, 0.0D, 1_000_000.0D);
        EXPOSURE_DOMINANT_SOURCE_FRACTION_FOR_INDIVIDUAL = BUILDER
                .comment("If maxStrength / sumStrength is above this, one source dominates the cell, so keep sources individual.")
                .defineInRange("dominant_source_fraction_for_individual", 0.50D, 0.0D, 1.0D);
        EXPOSURE_DISABLE_AGGREGATION_NEAR_SHIELDING = BUILDER
                .comment("If true, cells touching radiation shielding blocks keep sources individual for accurate shielding rays.")
                .define("disable_aggregation_near_shielding", true);
        EXPOSURE_SHIELDING_NEIGHBOR_RADIUS = BUILDER
                .comment("Shielding search radius around each radiation cell, in blocks.")
                .defineInRange("shielding_neighbor_radius", 1, 0, 16);
        EXPOSURE_AGGREGATE_STRENGTH_MODE = BUILDER
                .comment("Aggregate strength mode. Currently supports sum_capped.")
                .define("aggregate_strength_mode", "sum_capped");
        EXPOSURE_AGGREGATE_MAX_STRENGTH = BUILDER
                .comment("Maximum strength of one aggregate cell source when using sum_capped.")
                .defineInRange("aggregate_max_strength", 750.0D, 0.0D, 1_000_000.0D);
        EXPOSURE_AGGREGATE_POSITION_MODE = BUILDER
                .comment("Aggregate position mode. Currently supports weighted_center.")
                .define("aggregate_position_mode", "weighted_center");
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

    public static boolean debugExposureSampling() {
        return DEBUG_EXPOSURE_SAMPLING.get();
    }

    public static boolean debugPlayerExposureSampling() {
        return DEBUG_EXPOSURE_SAMPLING.get() || DEBUG_PLAYER_EXPOSURE_SAMPLING.get();
    }

    public static boolean debugEntityExposureSampling() {
        return DEBUG_EXPOSURE_SAMPLING.get() || DEBUG_ENTITY_EXPOSURE_SAMPLING.get();
    }

    public static boolean radiationSpatialIndexEnabled() {
        return SPATIAL_INDEX_ENABLED.get();
    }

    public static int radiationSpatialIndexCellSize() {
        int configured = Mth.clamp(SPATIAL_INDEX_CELL_SIZE.get(), 1, 16);
        return 16 % configured == 0 ? configured : 8;
    }

    public static boolean debugRadiationSpatialIndex() {
        return SPATIAL_INDEX_DEBUG.get();
    }

    public static boolean exposureClusteringEnabled() {
        return EXPOSURE_CLUSTERING_ENABLED.get();
    }

    public static boolean exposureClusterStaticWeakSources() {
        return EXPOSURE_CLUSTER_STATIC_WEAK_SOURCES.get();
    }

    public static int exposureMinSourcesPerAggregateCell() {
        return Mth.clamp(EXPOSURE_MIN_SOURCES_PER_AGGREGATE_CELL.get(), 1, 4096);
    }

    public static double exposureClusterMaxIndividualStrength() {
        return Mth.clamp(EXPOSURE_CLUSTER_MAX_INDIVIDUAL_STRENGTH.get(), 0.0D, 1_000_000.0D);
    }

    public static double exposureAlwaysIndividualStrength() {
        return Mth.clamp(EXPOSURE_ALWAYS_INDIVIDUAL_STRENGTH.get(), 0.0D, 1_000_000.0D);
    }

    public static double exposureDominantSourceFractionForIndividual() {
        return Mth.clamp(EXPOSURE_DOMINANT_SOURCE_FRACTION_FOR_INDIVIDUAL.get(), 0.0D, 1.0D);
    }

    public static boolean exposureDisableAggregationNearShielding() {
        return EXPOSURE_DISABLE_AGGREGATION_NEAR_SHIELDING.get();
    }

    public static int exposureShieldingNeighborRadius() {
        return Mth.clamp(EXPOSURE_SHIELDING_NEIGHBOR_RADIUS.get(), 0, 16);
    }

    public static String exposureAggregateStrengthMode() {
        return EXPOSURE_AGGREGATE_STRENGTH_MODE.get();
    }

    public static double exposureAggregateMaxStrength() {
        return Mth.clamp(EXPOSURE_AGGREGATE_MAX_STRENGTH.get(), 0.0D, 1_000_000.0D);
    }

    public static String exposureAggregatePositionMode() {
        return EXPOSURE_AGGREGATE_POSITION_MODE.get();
    }

    public static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Skyent radiation config loaded: playerInterval={} entityInterval={} scanRadius={} samplingCap={} streamingSelection={} spatialIndex={} cellSize={} clustering={} minAggregateSources={} individualStrength={} aggregateMaxStrength={} hottest={} hottestPool={} closest={} random={} legacyExposureSamplingDebug={} playerExposureSamplingDebug={} entityExposureSamplingDebug={}",
                exposurePlayerUpdateIntervalTicks(),
                exposureEntityUpdateIntervalTicks(),
                exposureRadioactiveBlockScanRadius(),
                exposureSourceSamplingCapEnabled(),
                exposureStreamingSourceSelectionEnabled(),
                radiationSpatialIndexEnabled(),
                radiationSpatialIndexCellSize(),
                exposureClusteringEnabled(),
                exposureMinSourcesPerAggregateCell(),
                exposureAlwaysIndividualStrength(),
                exposureAggregateMaxStrength(),
                exposureMaxHottestSources(),
                exposureHottestCandidatePoolSize(),
                exposureMaxClosestSources(),
                exposureMaxRandomSources(),
                debugExposureSampling(),
                debugPlayerExposureSampling(),
                debugEntityExposureSampling()
        );
    }
}
