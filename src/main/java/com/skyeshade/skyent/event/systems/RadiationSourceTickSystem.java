package com.skyeshade.skyent.event.systems;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.MoltenCoriumBlock;
import com.skyeshade.skyent.content.radiation.RadioactiveSource;
import com.skyeshade.skyent.content.radiation.RadioactiveSourceRegistry;
import com.skyeshade.skyent.content.radiation.RadiationBlockProfiles;
import com.skyeshade.skyent.content.radiation.RadiationHotBlockRayThrottle;
import com.skyeshade.skyent.content.radiation.RadiationUtil;
import com.skyeshade.skyent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class RadiationSourceTickSystem {
    public static final int MOLTEN_CORIUM_REGISTRY_TICK_INTERVAL = 20;
    private static final int RADIOACTIVE_SCRAP_METAL_RADIATION_INTERVAL_TICKS = 40;
    private static final int RADIOACTIVE_SCRAP_METAL_RADIATION_ATTEMPTS_PER_RUN = 8;
    private static final int RADIOACTIVE_SCRAP_METAL_MAX_CONVERSIONS_PER_RUN = 4;

    private static final boolean DEBUG_RADIATION_SOURCES = false;
    private static final boolean DEBUG_SOURCE_TICK = false;
    private static final boolean DEBUG_MOLTEN_CORIUM_ENVIRONMENT = false;
    private static final boolean DEBUG_RADIOACTIVE_BLOCK_TICKS = Boolean.getBoolean("skyent.debugRadioactiveBlockTicks");
    private static final int DEBUG_RADIOACTIVE_BLOCK_TICK_LOG_INTERVAL = 100;

    private static final Map<ResourceKey<Level>, Set<BlockPos>> ACTIVE_SOURCES_BY_DIMENSION = new HashMap<>();
    private static final BlockTickDebugCounters DEBUG_COUNTERS = new BlockTickDebugCounters();
    private static boolean debugBehaviorLogged;

    private RadiationSourceTickSystem() {
    }

    public static void tick(ServerLevel level) {
        long startNs = DEBUG_RADIOACTIVE_BLOCK_TICKS ? System.nanoTime() : 0L;
        Set<BlockPos> activeSourceSet = ACTIVE_SOURCES_BY_DIMENSION.getOrDefault(level.dimension(), Set.of());
        List<BlockPos> sources = new ArrayList<>(activeSourceSet);
        Collections.shuffle(sources, new Random(level.getSeed() ^ (level.getGameTime() * 0x9E3779B97F4A7C15L)));
        int moltenCoriumSources = 0;
        int activeEnvironmentalSources = 0;
        int staleEntries = 0;
        int serverTick = level.getServer().getTickCount();
        boolean scrapMetalRadiationTick = serverTick % RADIOACTIVE_SCRAP_METAL_RADIATION_INTERVAL_TICKS == 0;

        for (BlockPos pos : sources) {
            if (!level.hasChunkAt(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof RadioactiveSource)) {
                unregisterActiveSource(level, pos);
                staleEntries++;
                continue;
            }

            if (state.is(ModBlocks.MOLTEN_CORIUM_BLOCK.get())) {
                moltenCoriumSources++;
                MoltenCoriumBlock.tickFromSourceRegistry(level, pos, state, level.random);
            } else if (shouldRunEnvironmentalSpread(state)) {
                activeEnvironmentalSources++;
                if (scrapMetalRadiationTick) {
                    tickActiveEnvironmentalRadiationSource(level, pos, state, serverTick);
                }
            } else {
                unregisterActiveSource(level, pos);
                recordPassiveActiveSourceSkipped(state);
            }
        }

        if (DEBUG_SOURCE_TICK) {
            SkyesNuclearTech.LOGGER.info(
                    "Radiation source registry tick in {}: activeSources={}, moltenCorium={}, activeEnvironmental={}, staleRemoved={}",
                    level.dimension().location(),
                    sources.size(),
                    moltenCoriumSources,
                    activeEnvironmentalSources,
                    staleEntries
            );
        }
        if (DEBUG_MOLTEN_CORIUM_ENVIRONMENT && moltenCoriumSources > 0) {
            SkyesNuclearTech.LOGGER.info(
                    "Molten corium registry maintenance emitted environmental radiation for {} positions in {}",
                    moltenCoriumSources,
                    level.dimension().location()
            );
        }
        RadiationHotBlockRayThrottle.logTickSummary(level);
        if (DEBUG_RADIOACTIVE_BLOCK_TICKS) {
            DEBUG_COUNTERS.addSpreadTime(System.nanoTime() - startNs);
            logRadioactiveBlockTickSummary(level);
        }
    }

    public static boolean shouldRunEnvironmentalSpread(BlockState state) {
        return state.is(ModBlocks.RADIOACTIVE_SCRAP_METAL.get())
                || isActiveVitrifiedStone(state)
                || state.is(ModBlocks.MOLTEN_CORIUM_BLOCK.get());
    }

    public static void registerActiveSourceIfNeeded(ServerLevel level, BlockPos pos, BlockState state) {
        if (shouldRunEnvironmentalSpread(state)) {
            ACTIVE_SOURCES_BY_DIMENSION
                    .computeIfAbsent(level.dimension(), ignored -> new HashSet<>())
                    .add(pos.immutable());
        } else {
            recordPassiveActiveSourceSkipped(state);
        }
    }

    public static void unregisterActiveSource(ServerLevel level, BlockPos pos) {
        Set<BlockPos> positions = ACTIVE_SOURCES_BY_DIMENSION.get(level.dimension());
        if (positions == null) {
            return;
        }

        positions.remove(pos);
        if (positions.isEmpty()) {
            ACTIVE_SOURCES_BY_DIMENSION.remove(level.dimension());
        }
    }

    public static void clearActiveSources() {
        ACTIVE_SOURCES_BY_DIMENSION.clear();
        DEBUG_COUNTERS.reset();
        debugBehaviorLogged = false;
    }

    public static void recordRadioactiveBlockRandomTick(BlockState state) {
        if (!DEBUG_RADIOACTIVE_BLOCK_TICKS) {
            return;
        }

        DEBUG_COUNTERS.randomTicks++;
        if (isContaminatedTerrain(state)) {
            DEBUG_COUNTERS.contaminatedTerrainRandomTicks++;
        }
    }

    public static void recordEnvironmentalSpreadAttempt(BlockState state, boolean fullRay) {
        if (!DEBUG_RADIOACTIVE_BLOCK_TICKS) {
            return;
        }

        DEBUG_COUNTERS.environmentalSpreadAttempts++;
        if (fullRay) {
            DEBUG_COUNTERS.blockRadiationRayCasts++;
        }
        if (isContaminatedTerrain(state)) {
            DEBUG_COUNTERS.contaminatedTerrainSpreadAttempts++;
        }
    }

    public static void debugActiveVitrifiedRandomTick(ServerLevel level, BlockPos pos, BlockState state, boolean environmentalRaysRan) {
        if (!DEBUG_RADIOACTIVE_BLOCK_TICKS || !isActiveVitrifiedStone(state)) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Active vitrified radiation random tick: block={} pos={} dimension={} strength={} environmentalRaysRan={}",
                BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                pos,
                level.dimension().location(),
                RadiationBlockProfiles.getRadiationStrength(state),
                environmentalRaysRan
        );
    }

    private static void recordPassiveActiveSourceSkipped(BlockState state) {
        if (!DEBUG_RADIOACTIVE_BLOCK_TICKS) {
            return;
        }

        DEBUG_COUNTERS.passiveSourceSkips++;
        if (isContaminatedTerrain(state)) {
            DEBUG_COUNTERS.contaminatedTerrainPassiveSkips++;
        }
    }

    private static void tickActiveEnvironmentalRadiationSource(ServerLevel level, BlockPos pos, BlockState state, int serverTick) {
        RadioactiveSourceRegistry.register(level, pos);
        debugRadioactiveScrapMetalActiveTick(level, pos, serverTick);
        if (!RadiationHotBlockRayThrottle.request(level, pos).allowed()) {
            return;
        }

        recordEnvironmentalSpreadAttempt(state, true);
        RadiationUtil.applyFullEnvironmentalRadiation(
                level,
                pos,
                RadiationBlockProfiles.getRadiationStrength(state),
                RadiationBlockProfiles.getEnvironmentalRange(state),
                RADIOACTIVE_SCRAP_METAL_RADIATION_ATTEMPTS_PER_RUN,
                RADIOACTIVE_SCRAP_METAL_MAX_CONVERSIONS_PER_RUN,
                level.random
        );
    }

    public static void discoverSourcesInChunk(ServerLevel level, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int discoveredSources = 0;
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
                            if (state.is(ModBlocks.RADIOACTIVE_SCRAP_METAL.get())) {
                                debugRadioactiveScrapMetalRegistered(level, mutablePos, "chunk discovery");
                            }
                            discoveredSources++;
                        }
                    }
                }
            }
        }

        if (DEBUG_SOURCE_TICK && discoveredSources > 0) {
            SkyesNuclearTech.LOGGER.info(
                    "Discovered {} radioactive source blocks in chunk {} {} in {}",
                    discoveredSources,
                    chunkPos.x,
                    chunkPos.z,
                    level.dimension().location()
            );
        }
    }

    private static boolean isContaminatedTerrain(BlockState state) {
        return state.is(ModBlocks.CONTAMINATED_GRASS_BLOCK.get());
    }

    private static boolean isActiveVitrifiedStone(BlockState state) {
        return state.is(ModBlocks.BAKED_VITRIFIED_STONE.get())
                || state.is(ModBlocks.SCORCHED_VITRIFIED_STONE.get())
                || state.is(ModBlocks.IRRADIATED_VITRIFIED_STONE.get())
                || state.is(ModBlocks.HOT_VITRIFIED_STONE.get())
                || state.is(ModBlocks.RADIANT_VITRIFIED_STONE.get())
                || state.is(ModBlocks.INFERNAL_VITRIFIED_STONE.get());
    }

    private static void logRadioactiveBlockTickSummary(ServerLevel level) {
        if (!debugBehaviorLogged) {
            debugBehaviorLogged = true;
            SkyesNuclearTech.LOGGER.info(
                    "Radioactive block behavior: contaminated_grass_block=PASSIVE_SOURCE_ONLY, vitrified_stone=PASSIVE_SOURCE_ONLY, radioactive_scrap_metal/baked_vitrified/scorched_vitrified/irradiated_vitrified/hot_vitrified/radiant_vitrified/infernal_vitrified/molten_corium=ACTIVE_ENVIRONMENTAL_SPREADER"
            );
        }

        long gameTime = level.getGameTime();
        if (gameTime % DEBUG_RADIOACTIVE_BLOCK_TICK_LOG_INTERVAL != 0L) {
            return;
        }

        int activeSources = ACTIVE_SOURCES_BY_DIMENSION.getOrDefault(level.dimension(), Set.of()).size();
        SkyesNuclearTech.LOGGER.info(
                "Radioactive block tick summary: dimension={} tick={} activeSources={} randomTicks={} contaminatedTerrainRandomTicks={} environmentalSpreadAttempts={} blockRadiationRayCasts={} contaminatedTerrainSpreadAttempts={} passiveSourceSkips={} contaminatedTerrainPassiveSkips={} spreadMs={}",
                level.dimension().location(),
                gameTime,
                activeSources,
                DEBUG_COUNTERS.randomTicks,
                DEBUG_COUNTERS.contaminatedTerrainRandomTicks,
                DEBUG_COUNTERS.environmentalSpreadAttempts,
                DEBUG_COUNTERS.blockRadiationRayCasts,
                DEBUG_COUNTERS.contaminatedTerrainSpreadAttempts,
                DEBUG_COUNTERS.passiveSourceSkips,
                DEBUG_COUNTERS.contaminatedTerrainPassiveSkips,
                String.format("%.3f", DEBUG_COUNTERS.spreadNanos / 1_000_000.0D)
        );
        DEBUG_COUNTERS.reset();
    }

    public static void debugRadioactiveScrapMetalRegistered(ServerLevel level, BlockPos pos, String reason) {
        if (DEBUG_RADIATION_SOURCES) {
            SkyesNuclearTech.LOGGER.info(
                    "Radioactive scrap metal active source registered: reason={} pos={} dimension={} interval={} attempts={} maxConversions={}",
                    reason,
                    pos,
                    level.dimension().location(),
                    RADIOACTIVE_SCRAP_METAL_RADIATION_INTERVAL_TICKS,
                    RADIOACTIVE_SCRAP_METAL_RADIATION_ATTEMPTS_PER_RUN,
                    RADIOACTIVE_SCRAP_METAL_MAX_CONVERSIONS_PER_RUN
            );
        }
    }

    public static void debugRadioactiveScrapMetalRemoved(ServerLevel level, BlockPos pos, String reason) {
        if (DEBUG_RADIATION_SOURCES) {
            SkyesNuclearTech.LOGGER.info(
                    "Radioactive scrap metal active source removed: reason={} pos={} dimension={}",
                    reason,
                    pos,
                    level.dimension().location()
            );
        }
    }

    private static void debugRadioactiveScrapMetalActiveTick(ServerLevel level, BlockPos pos, int serverTick) {
        if (DEBUG_RADIATION_SOURCES) {
            SkyesNuclearTech.LOGGER.info(
                    "Radioactive scrap metal active radiation tick: pos={} dimension={} serverTick={} interval={} strength={} attempts={} maxConversions={}",
                    pos,
                    level.dimension().location(),
                    serverTick,
                    RADIOACTIVE_SCRAP_METAL_RADIATION_INTERVAL_TICKS,
                    RadiationBlockProfiles.getRadiationStrength(ModBlocks.RADIOACTIVE_SCRAP_METAL.get()),
                    RADIOACTIVE_SCRAP_METAL_RADIATION_ATTEMPTS_PER_RUN,
                    RADIOACTIVE_SCRAP_METAL_MAX_CONVERSIONS_PER_RUN
            );
        }
    }

    private static final class BlockTickDebugCounters {
        private int randomTicks;
        private int contaminatedTerrainRandomTicks;
        private int environmentalSpreadAttempts;
        private int blockRadiationRayCasts;
        private int contaminatedTerrainSpreadAttempts;
        private int passiveSourceSkips;
        private int contaminatedTerrainPassiveSkips;
        private long spreadNanos;

        private void addSpreadTime(long nanos) {
            spreadNanos += Math.max(0L, nanos);
        }

        private void reset() {
            randomTicks = 0;
            contaminatedTerrainRandomTicks = 0;
            environmentalSpreadAttempts = 0;
            blockRadiationRayCasts = 0;
            contaminatedTerrainSpreadAttempts = 0;
            passiveSourceSkips = 0;
            contaminatedTerrainPassiveSkips = 0;
            spreadNanos = 0L;
        }
    }
}
