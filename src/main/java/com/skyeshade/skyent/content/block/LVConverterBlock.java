package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.LVFEConverterBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVRJConverterBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class LVConverterBlock extends BaseEntityBlock {
    private final MapCodec<LVConverterBlock> codec;
    private final ConverterMode mode;

    public LVConverterBlock(BlockBehaviour.Properties properties, ConverterMode mode) {
        super(properties);
        this.mode = mode;
        this.codec = simpleCodec(converterProperties -> new LVConverterBlock(converterProperties, mode));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (mode) {
            case RJ_TO_FE -> new LVRJConverterBlockEntity(pos, state);
            case FE_TO_RJ -> new LVFEConverterBlockEntity(pos, state);
        };
    }

    public enum ConverterMode {
        RJ_TO_FE,
        FE_TO_RJ
    }
}
