package com.skyeshade.skyent.content.explosion.destruction;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

public final class NuclearResistanceCache {
    public static final float AIR_RESISTANCE = 0.0F;
    public static final float WATER_RESISTANCE = 0.1F;
    public static final float LAVA_RESISTANCE = 0.4F;
    public static final float OTHER_LIQUID_RESISTANCE = 0.2F;
    public static final float FRAGILE_RESISTANCE = 0.05F;
    public static final float UNBREAKABLE_RESISTANCE = 3_600_000.0F;
    public static final TagKey<Block> NUKE_FRAGILE = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "nuke_fragile")
    );

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

    public boolean isFragile(BlockState state) {
        return state.is(NUKE_FRAGILE)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.LEAVES)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.VINE)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.COBWEB);
    }

    public boolean isRayBlocking(float resistance) {
        return resistance >= UNBREAKABLE_RESISTANCE;
    }

    private float computeResistance(BlockState state) {
        // TODO: Add skyent:nuke_resistant and skyent:nuke_absorber block tags.
        if (isFragile(state)) {
            return FRAGILE_RESISTANCE;
        }
        float resistance = state.getBlock().getExplosionResistance();
        if (resistance < 0.0F) {
            return UNBREAKABLE_RESISTANCE;
        }
        if (resistance == 0.0F) {
            return FRAGILE_RESISTANCE;
        }
        return resistance;
    }
}
