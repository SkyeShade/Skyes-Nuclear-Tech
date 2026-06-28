package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.LVConnectorBlockEntity;
import com.skyeshade.skyent.event.systems.LVElectricalNetworkSystem;
import com.skyeshade.skyent.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LVConnectorBlock extends BaseEntityBlock {
    public static final MapCodec<LVConnectorBlock> CODEC = simpleCodec(LVConnectorBlock::new);

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(5.0D, 0.0D, 5.0D, 11.0D, 2.0D, 11.0D),
            Block.box(6.0D, 2.0D, 6.0D, 10.0D, 3.0D, 10.0D),
            Block.box(5.0D, 3.0D, 5.0D, 11.0D, 5.0D, 11.0D),
            Block.box(6.0D, 5.0D, 6.0D, 10.0D, 6.0D, 10.0D),
            Block.box(6.5D, 6.0D, 6.5D, 9.5D, 7.0D, 9.5D)
    );

    public LVConnectorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LVConnectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide) {
            return null;
        }

        return createTickerHelper(
                blockEntityType,
                ModBlockEntities.LV_CONNECTOR.get(),
                (tickerLevel, pos, tickerState, connector) -> LVElectricalNetworkSystem.onConnectorTick(connector)
        );
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LVConnectorBlockEntity connector) {
            connector.removeAllConnections();
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }
}
