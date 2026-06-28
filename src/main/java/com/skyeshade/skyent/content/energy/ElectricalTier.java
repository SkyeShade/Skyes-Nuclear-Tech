package com.skyeshade.skyent.content.energy;

public enum ElectricalTier {
    LV(32),
    MV(128),
    HV(512),
    EV(2048);

    private final int voltage;

    ElectricalTier(int voltage) {
        this.voltage = voltage;
    }

    public int voltage() {
        return voltage;
    }

    public String displayName() {
        return name() + " (" + voltage + " V)";
    }
}
