package com.skyeshade.skyent.content.energy;

import java.util.Locale;

public enum LVWireType {
    COPPER("copper", 4.0D, 128, 1.0D, 0.72F, 0.36F, 0.16F),
    STEEL("steel", 8.0D, 256, 2.0D, 0.48F, 0.50F, 0.52F);

    private final String serializedName;
    private final double maxCurrentAmps;
    private final int maxTransferRJPerTick;
    private final double voltageDropPerBlock;
    private final float red;
    private final float green;
    private final float blue;

    LVWireType(String serializedName, double maxCurrentAmps, int maxTransferRJPerTick, double voltageDropPerBlock, float red, float green, float blue) {
        this.serializedName = serializedName;
        this.maxCurrentAmps = maxCurrentAmps;
        this.maxTransferRJPerTick = maxTransferRJPerTick;
        this.voltageDropPerBlock = voltageDropPerBlock;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public String serializedName() {
        return serializedName;
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
