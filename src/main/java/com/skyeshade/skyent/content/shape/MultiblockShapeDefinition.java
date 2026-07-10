package com.skyeshade.skyent.content.shape;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record MultiblockShapeDefinition(
        ResourceLocation id,
        ResourceLocation model,
        double scale,
        Vec3 origin,
        Vec3 translation,
        int sizeX,
        int sizeY,
        int sizeZ
) {
    public MultiblockShapeDefinition {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Multiblock shape size must be positive");
        }
    }
}
