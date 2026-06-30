package com.skyeshade.skyent.content.fluid;

import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModFluidTypes;
import com.skyeshade.skyent.registry.ModFluids;
import com.skyeshade.skyent.registry.ModItems;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

public abstract class MoltenCoriumFluid extends BaseFlowingFluid {
    private static final Properties PROPERTIES = new Properties(
            ModFluidTypes.MOLTEN_CORIUM,
            ModFluids.MOLTEN_CORIUM,
            ModFluids.FLOWING_MOLTEN_CORIUM
    )
            .bucket(ModItems.MOLTEN_CORIUM_BUCKET)
            .block(ModBlocks.MOLTEN_CORIUM_BLOCK)
            .slopeFindDistance(2)
            .levelDecreasePerBlock(2)
            .tickRate(30)
            .explosionResistance(100.0F);

    protected MoltenCoriumFluid() {
        super(PROPERTIES);
    }

    public static class Source extends MoltenCoriumFluid {
        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }

    public static class Flowing extends MoltenCoriumFluid {
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
