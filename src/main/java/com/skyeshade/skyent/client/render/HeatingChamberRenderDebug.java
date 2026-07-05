package com.skyeshade.skyent.client.render;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class HeatingChamberRenderDebug {
    public static final boolean ENABLED = Boolean.getBoolean("skyent.debugHeatingChamberLighting");
    public static final int FULLBRIGHT_MAGENTA = 0xF0F0F0;

    private HeatingChamberRenderDebug() {
    }

    public static void log(String message, Object... args) {
        if (ENABLED) {
            SkyesNuclearTech.LOGGER.info("[HeatingChamberLightingDebug] " + message, args);
        }
    }

    public static void logBlock(BlockPos pos, BlockState state, String message, Object... args) {
        if (ENABLED) {
            Object[] prefixed = new Object[args.length + 2];
            prefixed[0] = pos;
            prefixed[1] = state;
            System.arraycopy(args, 0, prefixed, 2, args.length);
            SkyesNuclearTech.LOGGER.info("[HeatingChamberLightingDebug] pos={} state={} " + message, prefixed);
        }
    }
}
