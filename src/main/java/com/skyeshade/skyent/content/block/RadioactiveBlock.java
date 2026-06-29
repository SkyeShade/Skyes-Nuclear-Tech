package com.skyeshade.skyent.content.block;

import com.skyeshade.skyent.content.radiation.EnvironmentalRadiationMode;
import com.skyeshade.skyent.content.radiation.RadiationUtil;
import com.skyeshade.skyent.content.radiation.RadioactiveSource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class RadioactiveBlock extends Block implements RadioactiveSource {
    private final double radiationStrength;
    private final int radiationRange;
    private final EnvironmentalRadiationMode environmentalMode;

    public RadioactiveBlock(Properties properties, double radiationStrength, int radiationRange) {
        this(properties, radiationStrength, radiationRange, EnvironmentalRadiationMode.CHEAP);
    }

    public RadioactiveBlock(Properties properties, double radiationStrength, int radiationRange, EnvironmentalRadiationMode environmentalMode) {
        super(properties.randomTicks());
        this.radiationStrength = radiationStrength;
        this.radiationRange = radiationRange;
        this.environmentalMode = environmentalMode;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (environmentalMode == EnvironmentalRadiationMode.FULL_RAY) {
            RadiationUtil.applyFullEnvironmentalRadiation(level, pos, radiationStrength, radiationRange, random);
        } else {
            RadiationUtil.applyCheapEnvironmentalRadiation(level, pos, radiationStrength, radiationRange, random);
        }
    }

    @Override
    public double getRadiationStrength() {
        return radiationStrength;
    }

    @Override
    public int getRadiationRange() {
        return radiationRange;
    }
}
