package com.skyeshade.skyent.content.block;

import com.skyeshade.skyent.content.radiation.EnvironmentalRadiationMode;
import com.skyeshade.skyent.content.radiation.RadioactiveSourceRegistry;
import com.skyeshade.skyent.content.radiation.RadiationUtil;
import com.skyeshade.skyent.content.radiation.RadioactiveSource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class RadioactiveBlock extends Block implements RadioactiveSource {
    private final double radiationStrength;
    private final int environmentalRadiationRange;
    private final int entityRadiationRange;
    private final EnvironmentalRadiationMode environmentalMode;

    public RadioactiveBlock(Properties properties, double radiationStrength, int environmentalRadiationRange) {
        this(properties, radiationStrength, environmentalRadiationRange, environmentalRadiationRange, EnvironmentalRadiationMode.CHEAP);
    }

    public RadioactiveBlock(Properties properties, double radiationStrength, int environmentalRadiationRange, EnvironmentalRadiationMode environmentalMode) {
        this(properties, radiationStrength, environmentalRadiationRange, environmentalRadiationRange, environmentalMode);
    }

    public RadioactiveBlock(Properties properties, double radiationStrength, int environmentalRadiationRange, int entityRadiationRange, EnvironmentalRadiationMode environmentalMode) {
        super(properties.randomTicks());
        this.radiationStrength = radiationStrength;
        this.environmentalRadiationRange = environmentalRadiationRange;
        this.entityRadiationRange = entityRadiationRange;
        this.environmentalMode = environmentalMode;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) {
            RadioactiveSourceRegistry.register(serverLevel, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !newState.is(state.getBlock())) {
            RadioactiveSourceRegistry.unregister(serverLevel, pos);
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        RadioactiveSourceRegistry.register(level, pos);
        if (environmentalMode == EnvironmentalRadiationMode.FULL_RAY) {
            RadiationUtil.applyFullEnvironmentalRadiation(level, pos, radiationStrength, environmentalRadiationRange, random);
        } else {
            RadiationUtil.applyCheapEnvironmentalRadiation(level, pos, radiationStrength, environmentalRadiationRange, random);
        }
    }

    @Override
    public double getRadiationStrength() {
        return radiationStrength;
    }

    @Override
    public int getEnvironmentalRadiationRange() {
        return environmentalRadiationRange;
    }

    @Override
    public int getEntityRadiationRange() {
        return entityRadiationRange;
    }
}
