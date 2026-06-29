package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.CoriumBlockEntity;
import com.skyeshade.skyent.content.radiation.RadiationConstants;
import com.skyeshade.skyent.content.radiation.RadioactiveSourceRegistry;
import com.skyeshade.skyent.content.radiation.RadioactiveSource;
import com.skyeshade.skyent.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CoriumBlock extends BaseEntityBlock implements RadioactiveSource {
    public static final MapCodec<CoriumBlock> CODEC = simpleCodec(CoriumBlock::new);

    public CoriumBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoriumBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide) {
            return null;
        }

        return createTickerHelper(
                blockEntityType,
                ModBlockEntities.CORIUM_BLOCK.get(),
                CoriumBlockEntity::serverTick
        );
    }

    @Override
    public double getRadiationStrength() {
        return RadiationConstants.CORIUM_BLOCK_RADIATION_STRENGTH;
    }

    @Override
    public int getEnvironmentalRadiationRange() {
        return RadiationConstants.CORIUM_BLOCK_RADIATION_RANGE;
    }

    @Override
    public int getEntityRadiationRange() {
        return RadiationConstants.CORIUM_BLOCK_ENTITY_RADIATION_RANGE;
    }
}
