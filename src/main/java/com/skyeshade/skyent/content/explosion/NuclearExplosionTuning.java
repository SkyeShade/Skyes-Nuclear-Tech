package com.skyeshade.skyent.content.explosion;

public final class NuclearExplosionTuning {
    public static final double DEFAULT_NUCLEAR_CHARGE_RADIUS = 100.0D;
    public static final double MIN_NUCLEAR_CHARGE_RADIUS = 1.0D;
    public static final double MAX_NUCLEAR_CHARGE_RADIUS = 2_000.0D;

    public static double nuclearChargeRadius = DEFAULT_NUCLEAR_CHARGE_RADIUS;

    private NuclearExplosionTuning() {
    }

    public static double setNuclearChargeRadius(double radius) {
        nuclearChargeRadius = Math.max(MIN_NUCLEAR_CHARGE_RADIUS, Math.min(MAX_NUCLEAR_CHARGE_RADIUS, radius));
        return nuclearChargeRadius;
    }
}
