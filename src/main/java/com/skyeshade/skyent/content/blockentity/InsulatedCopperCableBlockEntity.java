package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.registry.ModBlockEntities;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class InsulatedCopperCableBlockEntity extends BlockEntity {
    private static final String HEAT_TAG = "Heat";

    private final Map<Direction, Integer> currentTickTransferredRJ = new EnumMap<>(Direction.class);
    private final Map<Direction, Double> sideHeat = new EnumMap<>(Direction.class);

    public InsulatedCopperCableBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.INSULATED_COPPER_CABLE.get(), pos, blockState);
    }

    public void clearCableLoads() {
        currentTickTransferredRJ.clear();
    }

    public void recordCableLoad(BlockPos connection, int sentRJ) {
        Direction direction = directionTo(connection);
        if (direction != null) {
            currentTickTransferredRJ.merge(direction, sentRJ, Integer::sum);
        }
    }

    public int getCurrentTickTransferredRJ(BlockPos connection) {
        Direction direction = directionTo(connection);
        return direction == null ? 0 : currentTickTransferredRJ.getOrDefault(direction, 0);
    }

    public double getConnectionHeat(BlockPos connection) {
        Direction direction = directionTo(connection);
        return direction == null ? 0.0D : sideHeat.getOrDefault(direction, 0.0D);
    }

    public void setConnectionHeat(BlockPos connection, double heat) {
        Direction direction = directionTo(connection);
        if (direction == null) {
            return;
        }

        double clampedHeat = Math.max(0.0D, heat);
        if (Math.abs(sideHeat.getOrDefault(direction, 0.0D) - clampedHeat) < 0.01D) {
            return;
        }

        if (clampedHeat <= 0.0D) {
            sideHeat.remove(direction);
        } else {
            sideHeat.put(direction, clampedHeat);
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag heatTag = new CompoundTag();
        for (Map.Entry<Direction, Double> entry : sideHeat.entrySet()) {
            if (entry.getValue() > 0.0D) {
                heatTag.putDouble(entry.getKey().getName(), entry.getValue());
            }
        }
        tag.put(HEAT_TAG, heatTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        currentTickTransferredRJ.clear();
        sideHeat.clear();
        CompoundTag heatTag = tag.getCompound(HEAT_TAG);
        for (Direction direction : Direction.values()) {
            if (heatTag.contains(direction.getName())) {
                sideHeat.put(direction, Math.max(0.0D, heatTag.getDouble(direction.getName())));
            }
        }
    }

    private Direction directionTo(BlockPos connection) {
        BlockPos delta = connection.subtract(worldPosition);
        for (Direction direction : Direction.values()) {
            if (delta.equals(direction.getNormal())) {
                return direction;
            }
        }
        return null;
    }
}
