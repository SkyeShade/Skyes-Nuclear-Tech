package com.skyeshade.skyent.content.energy;

import java.util.Locale;

public enum LVWireType {
    COPPER("copper", ElectricalTier.LV, 4.0D, 128, 1.0D, 0.72F, 0.36F, 0.16F, 0.025F),
    STEEL("steel", ElectricalTier.LV, 8.0D, 256, 2.0D, 0.48F, 0.50F, 0.52F, 0.025F),
    MV_COPPER("mv_copper", ElectricalTier.MV, 4.0D, 512, 1.0D, 0.72F, 0.36F, 0.16F, 0.0375F);

    public static final float LV_CABLE_HALF_WIDTH = 0.025F;

    private final String serializedName;
    private final ElectricalTier tier;
    private final double maxCurrentAmps;
    private final int maxTransferRJPerTick;
    private final double voltageDropPerBlock;
    private final float red;
    private final float green;
    private final float blue;
    private final float cableHalfWidth;

    LVWireType(String serializedName, ElectricalTier tier, double maxCurrentAmps, int maxTransferRJPerTick, double voltageDropPerBlock, float red, float green, float blue, float cableHalfWidth) {
        this.serializedName = serializedName;
        this.tier = tier;
        this.maxCurrentAmps = maxCurrentAmps;
        this.maxTransferRJPerTick = maxTransferRJPerTick;
        this.voltageDropPerBlock = voltageDropPerBlock;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.cableHalfWidth = cableHalfWidth;
    }

    public String serializedName() {
        return serializedName;
    }

    public ElectricalTier tier() {
        return tier;
    }

    public boolean isTier(ElectricalTier tier) {
        return this.tier == tier;
    }

    public double maxCurrentAmps() {
        return maxCurrentAmps;
    }

    public int maxTransferRJPerTick() {
        return maxTransferRJPerTick;
    }

    public double voltageDropPerBlock() {
        return voltageDropPerBlock;
    }

    public float red() {
        return red;
    }

    public float green() {
        return green;
    }

    public float blue() {
        return blue;
    }

    public float cableHalfWidth() {
        return cableHalfWidth;
    }

    public static LVWireType byName(String name) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        for (LVWireType type : values()) {
            if (type.serializedName.equals(normalizedName)) {
                return type;
            }
        }

        return COPPER;
    }
}
