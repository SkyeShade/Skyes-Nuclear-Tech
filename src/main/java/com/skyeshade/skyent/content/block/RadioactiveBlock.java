package com.skyeshade.skyent.content.block;

import com.skyeshade.skyent.content.radiation.EnvironmentalRadiationMode;
import com.skyeshade.skyent.content.radiation.RadioactiveSourceRegistry;
import com.skyeshade.skyent.content.radiation.RadiationMeltdownUtil;
import com.skyeshade.skyent.content.radiation.RadiationBlockProfiles;
import com.skyeshade.skyent.content.radiation.RadioactiveSource;
import com.skyeshade.skyent.event.systems.RadiationSourceTickSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class RadioactiveBlock extends Block implements RadioactiveSource {
    private final EnvironmentalRadiationMode environmentalMode;

    public RadioactiveBlock(Properties properties) {
        this(properties, EnvironmentalRadiationMode.CHEAP);
    }

    public RadioactiveBlock(Properties properties, EnvironmentalRadiationMode environmentalMode) {
        super(environmentalMode == EnvironmentalRadiationMode.PASSIVE_SOURCE_ONLY ? properties : properties.randomTicks());
        this.environmentalMode = environmentalMode;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) {
            RadioactiveSourceRegistry.register(serverLevel, pos);
            RadiationSourceTickSystem.registerActiveSourceIfNeeded(serverLevel, pos, state);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !newState.is(state.getBlock())) {
            RadioactiveSourceRegistry.unregister(serverLevel, pos);
            RadiationSourceTickSystem.unregisterActiveSource(serverLevel, pos);
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return environmentalMode != EnvironmentalRadiationMode.PASSIVE_SOURCE_ONLY && super.isRandomlyTicking(state);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Registration/meltdown fallback only; environmental rays run through RadiationSourceTickSystem.
        RadioactiveSourceRegistry.register(level, pos);
        RadiationSourceTickSystem.registerActiveSourceIfNeeded(level, pos, state);
        RadiationMeltdownUtil.tryTriggerMeltdown(level, pos, random);
    }

    @Override
    public double getRadiationStrength() {
        return RadiationBlockProfiles.getRadiationStrength(this);
    }

    @Override
    public int getEnvironmentalRadiationRange() {
        return RadiationBlockProfiles.getEnvironmentalRange(this);
    }

    @Override
    public int getEntityRadiationRange() {
        return RadiationBlockProfiles.getEntityRange(this);
    }
}
