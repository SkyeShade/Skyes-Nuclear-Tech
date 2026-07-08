package com.skyeshade.skyent.client.render;

import com.skyeshade.skyent.content.block.LVMVTransformerBlock;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;

public final class LVMVTransformerLighting {
    private static final int SIZE_X = 1;
    private static final int SIZE_Y = 2;
    private static final int SIZE_Z = 2;
    public static final int SHARED_LIGHT_REDUCTION = 0;

    private LVMVTransformerLighting() {
    }

    public static int computeMaxPackedLight(BlockAndTintGetter level, BlockPos controllerPos, Direction facing) {
        int sky = 0;
        int block = 0;
        for (int y = 0; y < SIZE_Y; y++) {
            for (int x = 0; x < SIZE_X; x++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    BlockPos cellPos = LVMVTransformerBlock.localToWorld(controllerPos, facing, x, y, z);
                    int light = sampleCellAndAdjacentLight(level, cellPos);
                    sky = Math.max(sky, LightTexture.sky(light));
                    block = Math.max(block, LightTexture.block(light));
                }
            }
        }
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
