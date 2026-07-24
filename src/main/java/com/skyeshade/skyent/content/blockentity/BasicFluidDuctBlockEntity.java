package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.content.block.BasicFluidDuctBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public class BasicFluidDuctBlockEntity extends BlockEntity {
    public static final int BASIC_FLUID_DUCT_TRANSFER_MB_PER_TICK = 100;

    public BasicFluidDuctBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.BASIC_FLUID_DUCT.get(), pos, blockState);
    }

    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        return new DuctFluidHandler(side);
    }

    private int insertFromSide(FluidStack resource, @Nullable Direction side, IFluidHandler.FluidAction action) {
        if (!(level instanceof ServerLevel serverLevel) || resource.isEmpty()) {
            return 0;
        }
        if (side != null && !BasicFluidDuctBlock.canUseSide(getBlockState(), side)) {
            return 0;
        }

        BlockPos excludedNeighbor = side == null ? null : worldPosition.relative(side);
        Map<BlockPos, Integer> distances = collectNetworkDistances(serverLevel, worldPosition);
        Set<BlockPos> network = distances.keySet();
        List<Endpoint> endpoints = collectEndpoints(serverLevel, network, excludedNeighbor, distances);
        int remaining = Math.min(resource.getAmount(), BASIC_FLUID_DUCT_TRANSFER_MB_PER_TICK);

        for (Endpoint endpoint : endpoints) {
            if (remaining <= 0) {
                break;
            }

            FluidStack offered = copyWithAmount(resource, remaining);
            int accepted = endpoint.handler.fill(offered, action);
            remaining -= accepted;
        }

        return Math.min(resource.getAmount(), BASIC_FLUID_DUCT_TRANSFER_MB_PER_TICK) - remaining;
    }

    private static Map<BlockPos, Integer> collectNetworkDistances(ServerLevel level, BlockPos start) {
        Map<BlockPos, Integer> distances = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        distances.put(start, 0);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            int distance = distances.get(pos);

            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                BlockState state = level.getBlockState(pos);
                BlockState neighborState = level.getBlockState(neighbor);
                if (!distances.containsKey(neighbor) && BasicFluidDuctBlock.canConnectDucts(state, direction, neighborState)) {
                    distances.put(neighbor, distance + 1);
                    queue.add(neighbor);
                }
            }
        }

        return distances;
    }

    private static List<Endpoint> collectEndpoints(
            ServerLevel level,
            Set<BlockPos> network,
            @Nullable BlockPos excludedNeighbor,
            @Nullable Map<BlockPos, Integer> distances
    ) {
        List<Endpoint> endpoints = new ArrayList<>();
        for (BlockPos ductPos : network) {
            BlockState ductState = level.getBlockState(ductPos);
            int distance = distances == null ? 0 : distances.getOrDefault(ductPos, Integer.MAX_VALUE);
            for (Direction direction : Direction.values()) {
                if (!BasicFluidDuctBlock.canUseSide(ductState, direction)) {
                    continue;
                }

                BlockPos neighborPos = ductPos.relative(direction);
                if (network.contains(neighborPos) || neighborPos.equals(excludedNeighbor)) {
                    continue;
                }

                IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, neighborPos, direction.getOpposite());
                if (handler != null) {
                    endpoints.add(new Endpoint(neighborPos, direction.getOpposite(), distance, handler));
                }
            }
        }

        endpoints.sort(Comparator
                .comparingInt(Endpoint::distance)
                .thenComparingInt(endpoint -> endpoint.pos().getX())
                .thenComparingInt(endpoint -> endpoint.pos().getY())
                .thenComparingInt(endpoint -> endpoint.pos().getZ())
                .thenComparingInt(endpoint -> endpoint.side().ordinal()));
        return endpoints;
    }

    private static FluidStack copyWithAmount(FluidStack stack, int amount) {
        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }

    private final class DuctFluidHandler implements IFluidHandler {
        @Nullable
        private final Direction side;

        private DuctFluidHandler(@Nullable Direction side) {
            this.side = side;
        }

        @Override
        public int getTanks() {
            return 0;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return insertFromSide(resource, side, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    private record Endpoint(BlockPos pos, Direction side, int distance, IFluidHandler handler) {
    }
}
