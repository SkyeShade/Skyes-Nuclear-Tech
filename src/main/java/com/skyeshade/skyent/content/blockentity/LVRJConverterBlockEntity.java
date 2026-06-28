package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class LVRJConverterBlockEntity extends BlockEntity {
    private final IEnergyStorage feOutput = new FEOutput();

    public LVRJConverterBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.LV_RJ_CONVERTER.get(), pos, blockState);
    }

    public IEnergyStorage getFEOutput() {
        return feOutput;
    }

    public int getAvailableRJCapacity() {
        return pushFE(Integer.MAX_VALUE, true);
    }

    public int receiveRJ(int amount, boolean simulate) {
        return pushFE(amount, simulate);
    }

    private int pushFE(int amount, boolean simulate) {
        if (!(level instanceof ServerLevel) || amount <= 0) {
            return 0;
        }

        int remaining = amount;

        for (Direction direction : Direction.values()) {
            if (remaining <= 0) {
                break;
            }

            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(direction));
            if (neighbor instanceof LVRJConverterBlockEntity || neighbor instanceof LVFEConverterBlockEntity) {
                continue;
            }

            IEnergyStorage receiver = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK,
                    worldPosition.relative(direction),
                    direction.getOpposite()
            );

            if (receiver == null || !receiver.canReceive()) {
                continue;
            }

            remaining -= receiver.receiveEnergy(remaining, simulate);
        }

        return amount - remaining;
    }

    private static final class FEOutput implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
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
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }
}