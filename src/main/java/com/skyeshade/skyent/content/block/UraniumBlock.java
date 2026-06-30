package com.skyeshade.skyent.content.block;

import com.skyeshade.skyent.content.radiation.EnvironmentalRadiationMode;

public class UraniumBlock extends RadioactiveBlock {
    public UraniumBlock(Properties properties) {
        super(properties, EnvironmentalRadiationMode.FULL_RAY);
    }
}
