package com.skyeshade.skyent.content.radiation;

public record EnvironmentalRadiationRayProfile(
        double strength,
        int range,
        int rayCount,
        int maxConversions,
        int baseTickInterval,
        int maxTickInterval,
        int priority
) {
}
