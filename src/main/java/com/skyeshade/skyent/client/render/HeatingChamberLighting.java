package com.skyeshade.skyent.client.render;

import com.skyeshade.skyent.content.block.HeatingChamberBlock;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;

public final class HeatingChamberLighting {
    private static final int SIZE_X = 2;
    private static final int SIZE_Y = 3;
    private static final int SIZE_Z = 2;
    public static final int SHARED_LIGHT_REDUCTION = 0;
    public static final float RENDER_BRIGHTNESS_MULTIPLIER = 0.5F;
    public static final float RENDER_BRIGHTNESS_FLOOR = 0.3F;

    private HeatingChamberLighting() {
    }

    @FunctionalInterface
    public interface LocalToWorld {
        BlockPos map(BlockPos controllerPos, Direction facing, int x, int y, int z);
    }

    public static int computeMaxPackedLight(BlockAndTintGetter level, BlockPos controllerPos, Direction facing) {
        return computeMaxPackedLight(level, controllerPos, facing, SIZE_X, SIZE_Y, SIZE_Z, HeatingChamberBlock::localToWorld);
    }

    public static int computeMaxPackedLight(BlockAndTintGetter level, BlockPos controllerPos, Direction facing, int sizeX, int sizeY, int sizeZ, LocalToWorld localToWorld) {
        int sky = 0;
        int block = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    BlockPos cellPos = localToWorld.map(controllerPos, facing, x, y, z);
                    int light = sampleCellAndAdjacentLight(level, cellPos);
                    sky = Math.max(sky, LightTexture.sky(light));
                    block = Math.max(block, LightTexture.block(light));
                }
            }
        }
        return LightTexture.pack(block, sky);
    }

    public static int computeControllerPackedLight(BlockAndTintGetter level, BlockPos controllerPos) {
        int block = level.getBrightness(LightLayer.BLOCK, controllerPos);
        int sky = level.getBrightness(LightLayer.SKY, controllerPos);
        return LightTexture.pack(block, sky);
    }

    public static int reducePackedLight(int packedLight, int reduction) {
        int safeReduction = Math.max(0, reduction);
        if (safeReduction == 0) {
            return packedLight;
        }
        int block = Mth.clamp(Mth.clamp(LightTexture.block(packedLight), 0, 15) - safeReduction, 0, 15);
        int sky = Mth.clamp(Mth.clamp(LightTexture.sky(packedLight), 0, 15) - safeReduction, 0, 15);
        return LightTexture.pack(block, sky);
    }

    private static int sampleCellAndAdjacentLight(BlockAndTintGetter level, BlockPos pos) {
        int centerLight = sampleLight(level, pos);
        int sky = LightTexture.sky(centerLight);
        int block = LightTexture.block(centerLight);
        for (Direction direction : Direction.values()) {
            int light = sampleLight(level, pos.relative(direction));
            sky = Math.max(sky, LightTexture.sky(light));
            block = Math.max(block, LightTexture.block(light));
        }
        return LightTexture.pack(block, sky);
    }

    private static int sampleLight(BlockAndTintGetter level, BlockPos pos) {
        return LevelRenderer.getLightColor(level, level.getBlockState(pos), pos);
    }
}
