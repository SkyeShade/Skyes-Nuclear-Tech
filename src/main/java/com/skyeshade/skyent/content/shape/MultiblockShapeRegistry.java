package com.skyeshade.skyent.content.shape;

import com.skyeshade.skyent.SkyesNuclearTech;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MultiblockShapeRegistry {
    private static final ConcurrentMap<ResourceLocation, MultiblockShapeDefinition> DEFINITIONS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<ResourceLocation, MultiblockShapeData> CACHE = new ConcurrentHashMap<>();
    private static volatile ResourceManager activeResourceManager;

    private MultiblockShapeRegistry() {
    }

    public static void register(MultiblockShapeDefinition definition) {
        DEFINITIONS.put(definition.id(), definition);
        CACHE.remove(definition.id());
        SkyesNuclearTech.LOGGER.info("Registered multiblock shape definition {} -> {}", definition.id(), definition.model());
    }

    public static void reload(ResourceManager resourceManager) {
        activeResourceManager = resourceManager;
        CACHE.clear();
        for (ResourceLocation id : Map.copyOf(DEFINITIONS).keySet()) {
            MultiblockShapeData data = load(id);
            if (data != null) {
                CACHE.put(id, data);
            }
        }
    }

    public static VoxelShape getShape(
            ResourceLocation id,
            Direction facing,
            int partX,
            int partY,
            int partZ,
            ShapeFallback fallback
    ) {
        MultiblockShapeData data = CACHE.computeIfAbsent(id, MultiblockShapeRegistry::load);
        if (data == null) {
            return fallback.get(facing, partX, partY, partZ);
        }
        return data.shapeForLocal(partX, partY, partZ, facing);
    }

    private static MultiblockShapeData load(ResourceLocation id) {
        MultiblockShapeDefinition definition = DEFINITIONS.get(id);
        if (definition == null) {
            SkyesNuclearTech.LOGGER.warn("No multiblock shape definition registered for {}", id);
            return null;
        }

        if (activeResourceManager != null) {
            try {
                MultiblockShapeData data = MultiblockModelShapeLoader.load(definition, activeResourceManager);
                if (data != null) {
                    return data;
                }
            } catch (Exception exception) {
                SkyesNuclearTech.LOGGER.error(
                        "Failed to load multiblock shape {} from resource manager model {}; trying classpath fallback",
                        id,
                        definition.model(),
                        exception
                );
            }
        }

        try {
            return MultiblockModelShapeLoader.loadFromClasspath(definition);
        } catch (IOException exception) {
            SkyesNuclearTech.LOGGER.error("Failed to load multiblock shape {} from classpath model {}", id, definition.model(), exception);
            return null;
        }
    }

    @FunctionalInterface
    public interface ShapeFallback {
        VoxelShape get(Direction facing, int partX, int partY, int partZ);
    }
}
