package com.skyeshade.skyent.content.explosion.destruction;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

public final class NuclearResistanceCache {
    public static final float AIR_RESISTANCE = 0.0F;
    public static final float WATER_RESISTANCE = 0.1F;
    public static final float LAVA_RESISTANCE = 0.4F;
    public static final float OTHER_LIQUID_RESISTANCE = 0.2F;
    public static final float UNBREAKABLE_RESISTANCE = 3_600_000.0F;

    private final Map<BlockState, Float> cachedResistances = new HashMap<>();

    public float resistanceFor(BlockState state, Level level, BlockPos pos) {
        if (state.isAir()) {
            return AIR_RESISTANCE;
        }
        if (state.getFluidState().is(FluidTags.WATER)) {
            return WATER_RESISTANCE;
        }
        if (state.getFluidState().is(FluidTags.LAVA)) {
            return LAVA_RESISTANCE;
        }
        if (!state.getFluidState().isEmpty()) {
            return OTHER_LIQUID_RESISTANCE;
        }

        return cachedResistances.computeIfAbsent(state, this::computeResistance);
    }

    public boolean canMarkForDestruction(BlockState state) {
        return !state.isAir();
    }

    public boolean isRayBlocking(float resistance) {
        return resistance >= UNBREAKABLE_RESISTANCE;
    }

    private float computeResistance(BlockState state) {
        // TODO: Add skyent:nuke_resistant, skyent:nuke_fragile, and skyent:nuke_absorber block tags.
        float resistance = state.getBlock().getExplosionResistance();
        if (resistance < 0.0F) {
            return UNBREAKABLE_RESISTANCE;
        }
        return resistance;
    }
}
