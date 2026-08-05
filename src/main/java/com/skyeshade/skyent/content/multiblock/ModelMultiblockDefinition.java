package com.skyeshade.skyent.content.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record ModelMultiblockDefinition(
        ResourceLocation id,
        int sizeX,
        int sizeY,
        int sizeZ,
        BlockPos controllerLocal,
        double modelScale,
        Vec3 modelOrigin,
        Vec3 modelTranslation,
        ModelMultiblockCollisionMode collisionMode,
        ModelMultiblockRenderMode renderMode,
        ModelMultiblockOrientation orientation
) {
    public ModelMultiblockDefinition(
            ResourceLocation id,
            int sizeX,
            int sizeY,
            int sizeZ,
            BlockPos controllerLocal,
            double modelScale,
            Vec3 modelOrigin,
            Vec3 modelTranslation,
            ModelMultiblockCollisionMode collisionMode,
            ModelMultiblockRenderMode renderMode
    ) {
        this(
                id,
                sizeX,
                sizeY,
                sizeZ,
                controllerLocal,
                modelScale,
                modelOrigin,
                modelTranslation,
                collisionMode,
                renderMode,
                ModelMultiblockOrientation.FACING_RIGHT_HANDED
        );
    }

    public ModelMultiblockDefinition {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Model multiblock dimensions must be positive");
        }
        if (controllerLocal.getX() < 0 || controllerLocal.getX() >= sizeX
                || controllerLocal.getY() < 0 || controllerLocal.getY() >= sizeY
                || controllerLocal.getZ() < 0 || controllerLocal.getZ() >= sizeZ) {
            throw new IllegalArgumentException("Controller local position must be inside the multiblock footprint");
        }
    }

    public boolean isControllerLocal(int x, int y, int z) {
        return controllerLocal.getX() == x && controllerLocal.getY() == y && controllerLocal.getZ() == z;
    }
}
