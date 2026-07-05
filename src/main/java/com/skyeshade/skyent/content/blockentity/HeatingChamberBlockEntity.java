package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.client.model.SkyentModelData;
import com.skyeshade.skyent.client.render.HeatingChamberLightRefreshTracker;
import com.skyeshade.skyent.client.render.HeatingChamberLighting;
import com.skyeshade.skyent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

public class HeatingChamberBlockEntity extends BlockEntity {
    private static final int LIGHT_CHECK_INTERVAL_TICKS = 40;

    private int cachedSharedPackedLight = -1;
    private int lightCheckTicks;

    public HeatingChamberBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.HEATING_CHAMBER.get(), pos, blockState);
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
        if (level != null && level.isClientSide) {
            HeatingChamberLightRefreshTracker.register(worldPosition);
        }
        refreshSharedLight(true);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide) {
            HeatingChamberLightRefreshTracker.unregister(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            HeatingChamberLightRefreshTracker.unregister(worldPosition);
        }
        super.setRemoved();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, HeatingChamberBlockEntity chamber) {
        if (!level.isClientSide) {
            return;
        }
        chamber.lightCheckTicks++;
        if (chamber.lightCheckTicks >= LIGHT_CHECK_INTERVAL_TICKS) {
            chamber.lightCheckTicks = 0;
            chamber.refreshSharedLight(false);
        }
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
        return HeatingChamberLighting.computeControllerPackedLight(level, worldPosition);
    }
}
