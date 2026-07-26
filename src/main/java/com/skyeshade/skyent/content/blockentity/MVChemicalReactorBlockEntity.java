package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.client.model.SkyentModelData;
import com.skyeshade.skyent.client.render.HeatingChamberLighting;
import com.skyeshade.skyent.content.block.MVChemicalReactorBlock;
import com.skyeshade.skyent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

public class MVChemicalReactorBlockEntity extends BlockEntity {
    private static final int LIGHT_CHECK_INTERVAL_TICKS = 40;

    private int cachedSharedPackedLight = -1;
    private int lightCheckTicks;

    public MVChemicalReactorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.MV_CHEMICAL_REACTOR.get(), pos, blockState);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, MVChemicalReactorBlockEntity reactor) {
        if (!level.isClientSide) {
            return;
        }

        reactor.lightCheckTicks++;
        if (reactor.lightCheckTicks >= LIGHT_CHECK_INTERVAL_TICKS) {
            reactor.lightCheckTicks = 0;
            reactor.refreshSharedLight(false);
        }
    }

    public Component getDisplayName() {
        return Component.translatable("container.skyent.mv_chemical_reactor");
    }

    @Override
    public ModelData getModelData() {
        if (level == null && cachedSharedPackedLight < 0) {
            return ModelData.EMPTY;
        }

        int packedLight = cachedSharedPackedLight >= 0 ? cachedSharedPackedLight : computePackedLight(level);
        return ModelData.of(SkyentModelData.SHARED_PACKED_LIGHT, packedLight);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshSharedLight(true);
    }

    public void refreshSharedLight(boolean forceRenderUpdate) {
        if (level == null || !level.isClientSide) {
            return;
        }

        int packedLight = computePackedLight(level);
        if (!forceRenderUpdate && packedLight == cachedSharedPackedLight) {
            return;
        }

        cachedSharedPackedLight = packedLight;
        requestModelDataUpdate();
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    private int computePackedLight(Level level) {
        Direction facing = getFacing();
        return HeatingChamberLighting.computeMaxPackedLight(
                level,
                worldPosition,
                facing,
                MVChemicalReactorBlock.SIZE_X,
                MVChemicalReactorBlock.SIZE_Y,
                MVChemicalReactorBlock.SIZE_Z,
                MVChemicalReactorBlock::localToWorld
        );
    }

    private Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(MVChemicalReactorBlock.FACING) ? state.getValue(MVChemicalReactorBlock.FACING) : Direction.NORTH;
    }
}
