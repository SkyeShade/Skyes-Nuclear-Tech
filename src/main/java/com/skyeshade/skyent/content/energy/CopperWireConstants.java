package com.skyeshade.skyent.content.energy;

public final class CopperWireConstants {
    public static final ElectricalTier VOLTAGE_TIER = ElectricalTier.LV;
    public static final int VOLTAGE = ElectricalTier.LV.voltage();
    public static final double COPPER_MAX_CURRENT_A = 4.0D;
    public static final double MAX_CURRENT_AMPS = COPPER_MAX_CURRENT_A;
    public static final int MAX_SAFE_TRANSFER_RJ_PER_TICK = 128;
    public static final double RESISTANCE_RJ_PER_BLOCK_PER_AMP = 0.05D;
    public static final double COPPER_HEAT_PER_AMP_OVER = 0.5D;
    public static final double COPPER_COOLING_PER_TICK = 0.25D;
    public static final double COPPER_SMOKE_HEAT = 35.0D;
    public static final double COPPER_GLOW_RED_HEAT = 35.0D;
    public static final double COPPER_FULLBRIGHT_HEAT = 125.0D;
    public static final double COPPER_GLOW_ORANGE_HEAT = 80.0D;
    public static final double COPPER_GLOW_WHITE_HEAT = 105.0D;
    public static final double COPPER_BURNOUT_HEAT = 125.0D;

    private CopperWireConstants() {
    }
}
