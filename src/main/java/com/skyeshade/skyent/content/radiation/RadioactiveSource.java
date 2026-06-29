package com.skyeshade.skyent.content.radiation;

public interface RadioactiveSource {
    double getRadiationStrength();

    int getEnvironmentalRadiationRange();

    int getEntityRadiationRange();

    default int getRadiationRange() {
        return getEnvironmentalRadiationRange();
    }
}
