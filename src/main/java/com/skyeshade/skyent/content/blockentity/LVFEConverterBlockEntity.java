package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.event.systems.LVElectricalNetworkSystem;
import com.skyeshade.skyent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class LVFEConverterBlockEntity extends BlockEntity {
    private final IEnergyStorage feInput = new FEInput();

    public LVFEConverterBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.LV_FE_CONVERTER.get(), pos, blockState);
    }

    public IEnergyStorage getFEInput() {
        return feInput;
    }

    private int receiveFE(int amount, boolean simulate) {
        if (!(level instanceof ServerLevel serverLevel) || amount <= 0) {
            return 0;
        }

        return LVElectricalNetworkSystem.insertRJFromConverter(serverLevel, worldPosition, amount, simulate);
    }

    private final class FEInput implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return receiveFE(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return 0;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
