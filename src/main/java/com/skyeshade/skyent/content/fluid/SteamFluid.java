package com.skyeshade.skyent.content.fluid;

import com.skyeshade.skyent.registry.ModFluidTypes;
import com.skyeshade.skyent.registry.ModFluids;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

public abstract class SteamFluid extends BaseFlowingFluid {
    private static final Properties PROPERTIES = new Properties(
            ModFluidTypes.STEAM,
            ModFluids.STEAM,
            ModFluids.FLOWING_STEAM
    )
            .slopeFindDistance(4)
            .levelDecreasePerBlock(1)
            .tickRate(5)
            .explosionResistance(1.0F);

    protected SteamFluid() {
        super(PROPERTIES);
    }

    public static class Source extends SteamFluid {
        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }

    public static class Flowing extends SteamFluid {
        public Flowing() {
            registerDefaultState(getStateDefinition().any().setValue(LEVEL, 7));
        }

        @Override
        protected void createFluidStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }
}
