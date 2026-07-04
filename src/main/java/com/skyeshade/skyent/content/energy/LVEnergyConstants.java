package com.skyeshade.skyent.content.energy;

public final class LVEnergyConstants {
    public static final int LV_VOLTAGE = ElectricalTier.LV.voltage();
    public static final double LV_MACHINE_MAX_INPUT_CURRENT_AMPS = 2.0D;
    public static final int LV_MACHINE_MAX_INPUT_RJ_PER_TICK =
            (int) Math.round(LV_VOLTAGE * LV_MACHINE_MAX_INPUT_CURRENT_AMPS);

    private LVEnergyConstants() {
    }
}
