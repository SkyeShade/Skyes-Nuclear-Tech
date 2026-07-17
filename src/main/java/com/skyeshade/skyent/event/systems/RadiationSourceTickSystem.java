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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class RadiationSourceTickSystem {
    public static final int MOLTEN_CORIUM_REGISTRY_TICK_INTERVAL = 20;
    private static final int RADIOACTIVE_SCRAP_METAL_RADIATION_INTERVAL_TICKS = 40;
    private static final int RADIOACTIVE_SCRAP_METAL_RADIATION_ATTEMPTS_PER_RUN = 8;
    private static final int RADIOACTIVE_SCRAP_METAL_MAX_CONVERSIONS_PER_RUN = 4;

    private static final boolean DEBUG_RADIATION_SOURCES = false;
    private static final boolean DEBUG_SOURCE_TICK = false;
    private static final boolean DEBUG_MOLTEN_CORIUM_ENVIRONMENT = false;

    private RadiationSourceTickSystem() {
    }

    public static void tick(ServerLevel level) {
        RadioactiveSourceRegistry registry = RadioactiveSourceRegistry.get(level);
        List<BlockPos> sources = registry.copyAllSources();
        Collections.shuffle(sources, new Random(level.getSeed() ^ (level.getGameTime() * 0x9E3779B97F4A7C15L)));
        int moltenCoriumSources = 0;
        int radioactiveScrapMetalSources = 0;
        int staleEntries = 0;
        int serverTick = level.getServer().getTickCount();
        boolean scrapMetalRadiationTick = serverTick % RADIOACTIVE_SCRAP_METAL_RADIATION_INTERVAL_TICKS == 0;

        for (BlockPos pos : sources) {
            if (!level.hasChunkAt(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof RadioactiveSource)) {
                RadioactiveSourceRegistry.unregister(level, pos);
                staleEntries++;
                continue;
            }

            if (state.is(ModBlocks.MOLTEN_CORIUM_BLOCK.get())) {
                moltenCoriumSources++;
                MoltenCoriumBlock.tickFromSourceRegistry(level, pos, state, level.random);
            } else if (isActiveEnvironmentalRadiationSource(state)) {
                radioactiveScrapMetalSources++;
                if (scrapMetalRadiationTick) {
                    tickActiveEnvironmentalRadiationSource(level, pos, state, serverTick);
                }
            }
        }

        if (DEBUG_SOURCE_TICK) {
            SkyesNuclearTech.LOGGER.info(
                    "Radiation source registry tick in {}: sources={}, moltenCorium={}, radioactiveScrapMetal={}, staleRemoved={}",
                    level.dimension().location(),
                    sources.size(),
                    moltenCoriumSources,
                    radioactiveScrapMetalSources,
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
    }

    private static boolean isActiveEnvironmentalRadiationSource(BlockState state) {
        return state.is(ModBlocks.RADIOACTIVE_SCRAP_METAL.get())
                || state.is(ModBlocks.HOT_VITRIFIED_STONE.get())
                || state.is(ModBlocks.RADIANT_VITRIFIED_STONE.get())
                || state.is(ModBlocks.INFERNAL_VITRIFIED_STONE.get());
    }

    private static void tickActiveEnvironmentalRadiationSource(ServerLevel level, BlockPos pos, BlockState state, int serverTick) {
        RadioactiveSourceRegistry.register(level, pos);
        debugRadioactiveScrapMetalActiveTick(level, pos, serverTick);
        if (!RadiationHotBlockRayThrottle.request(level, pos).allowed()) {
            return;
        }

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
}
