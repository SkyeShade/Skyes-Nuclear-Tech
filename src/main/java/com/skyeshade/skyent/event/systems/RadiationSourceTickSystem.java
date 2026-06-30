package com.skyeshade.skyent.event.systems;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.MoltenCoriumBlock;
import com.skyeshade.skyent.content.radiation.RadioactiveSource;
import com.skyeshade.skyent.content.radiation.RadioactiveSourceRegistry;
import com.skyeshade.skyent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class RadiationSourceTickSystem {
    public static final int MOLTEN_CORIUM_REGISTRY_TICK_INTERVAL = 20;

    private static final boolean DEBUG_SOURCE_TICK = false;
    private static final boolean DEBUG_MOLTEN_CORIUM_ENVIRONMENT = false;

    private RadiationSourceTickSystem() {
    }

    public static void tick(ServerLevel level) {
        RadioactiveSourceRegistry registry = RadioactiveSourceRegistry.get(level);
        List<BlockPos> sources = registry.copyAllSources();
        int moltenCoriumSources = 0;
        int staleEntries = 0;

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
            }
        }

        if (DEBUG_SOURCE_TICK) {
            SkyesNuclearTech.LOGGER.info(
                    "Radiation source registry tick in {}: sources={}, moltenCorium={}, staleRemoved={}",
                    level.dimension().location(),
                    sources.size(),
                    moltenCoriumSources,
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
    }
}
