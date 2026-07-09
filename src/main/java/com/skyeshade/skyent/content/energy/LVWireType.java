package com.skyeshade.skyent.content.energy;

import java.util.Locale;

public enum LVWireType {
    COPPER("copper", 4.0D, 1.0D, 16, 0.72F, 0.36F, 0.16F, 0.025F),
    STEEL("steel", 8.0D, 3.0D, 12, 0.48F, 0.50F, 0.52F, 0.025F),
    COBALT_BRONZE("cobalt_bronze", 8.0D, 1.5D, 32, 151.0F / 255.0F, 116.0F / 255.0F, 111.0F / 255.0F, 0.0375F);

    public static final float LV_CABLE_HALF_WIDTH = 0.025F;

    private final String serializedName;
    private final double maxCurrentAmps;
    private final double resistance;
    private final int maxLengthBlocks;
    private final float red;
    private final float green;
    private final float blue;
    private final float cableHalfWidth;

    LVWireType(String serializedName, double maxCurrentAmps, double resistance, int maxLengthBlocks, float red, float green, float blue, float cableHalfWidth) {
        this.serializedName = serializedName;
        this.maxCurrentAmps = maxCurrentAmps;
        this.resistance = resistance;
        this.maxLengthBlocks = maxLengthBlocks;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.cableHalfWidth = cableHalfWidth;
    }

    public String serializedName() {
        return serializedName;
    }

    public ElectricalTier tier() {
        return ElectricalTier.LV;
    }

    public boolean isTier(ElectricalTier tier) {
        return canConnectToTier(tier);
    }

    public boolean canConnectToTier(ElectricalTier tier) {
        return true;
    }

    public double maxCurrentAmps() {
        return maxCurrentAmps;
    }

    public int maxTransferRJPerTick() {
        return maxTransferRJPerTick(tier());
    }

    public int maxTransferRJPerTick(ElectricalTier networkTier) {
        return (int) Math.floor(networkTier.voltage() * maxCurrentAmps);
    }

    public double resistance() {
        return resistance;
    }

    public double resistancePerBlock() {
        return resistance;
    }

    public int maxLengthBlocks() {
        return maxLengthBlocks;
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
        if ("mv_copper".equals(normalizedName)) {
            return COBALT_BRONZE;
        }
        for (LVWireType type : values()) {
            if (type.serializedName.equals(normalizedName)) {
                return type;
            }
        }

        return COPPER;
    }
}
