package com.skyeshade.skyent.content.energy;

public interface RJEnergyInfo {
    int getEnergyStoredRJ();

    int getEnergyCapacityRJ();

    default int getCurrentGenerationRJPerTick() {
        return 0;
    }

    default int getCurrentUsageRJPerTick() {
        return 0;
    }

    default int getMaxOutputRJPerTick() {
        return 0;
    }

    default String getVoltageTierName() {
        return "";
    }
}
