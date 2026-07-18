package com.skyeshade.skyent.worldgen.structure;

import net.minecraft.nbt.CompoundTag;

public record VitrifiedCraterParams(int radius, int depth, int rimY, int bottomY, int topY, long seed) {
    private static final String RADIUS = "Radius";
    private static final String DEPTH = "Depth";
    private static final String RIM_Y = "RimY";
    private static final String LEGACY_SURFACE_Y = "SurfaceY";
    private static final String BOTTOM_Y = "BottomY";
    private static final String TOP_Y = "TopY";
    private static final String SEED = "Seed";

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(RADIUS, radius);
        tag.putInt(DEPTH, depth);
        tag.putInt(RIM_Y, rimY);
        tag.putInt(BOTTOM_Y, bottomY);
        tag.putInt(TOP_Y, topY);
        tag.putLong(SEED, seed);
        return tag;
    }

    public static VitrifiedCraterParams fromTag(CompoundTag tag) {
        int rimY = tag.contains(RIM_Y) ? tag.getInt(RIM_Y) : tag.getInt(LEGACY_SURFACE_Y);
        return new VitrifiedCraterParams(
                tag.getInt(RADIUS),
                tag.getInt(DEPTH),
                rimY,
                tag.getInt(BOTTOM_Y),
                tag.getInt(TOP_Y),
                tag.getLong(SEED)
        );
    }
}
