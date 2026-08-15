package com.skyeshade.skyent.content.energy;

public final class InsulatedCopperCableConstants {
    public static final double MAX_CURRENT_AMPS = 16.0D;
    public static final double RESISTANCE_PER_BLOCK = 0.25D;

    private InsulatedCopperCableConstants() {
    }

    public static int maxTransferRJPerTick(ElectricalTier networkTier) {
        return (int) Math.floor(networkTier.voltage() * MAX_CURRENT_AMPS);
    }
}
