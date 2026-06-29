package com.skyeshade.skyent.content.block;

import com.skyeshade.skyent.content.radiation.EnvironmentalRadiationMode;
import com.skyeshade.skyent.content.radiation.RadiationConstants;

public class UraniumBlock extends RadioactiveBlock {
    public UraniumBlock(Properties properties) {
        super(
                properties,
                RadiationConstants.URANIUM_BLOCK_RADIATION_STRENGTH,
                RadiationConstants.URANIUM_BLOCK_RADIATION_RANGE,
                RadiationConstants.URANIUM_BLOCK_ENTITY_RADIATION_RANGE,
                EnvironmentalRadiationMode.FULL_RAY
        );
    }
}
