package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.radiation.RadiationExposureUtil;
import com.skyeshade.skyent.content.item.GeigerNeedleUtil;
import com.skyeshade.skyent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class GeigerCounterPlacedBlockEntity extends BlockEntity {
    private static final int EXPOSURE_UPDATE_INTERVAL_TICKS = 20;
    private static final double EXPOSURE_SYNC_THRESHOLD = 0.05D;
    private static final String AUDIO_ENABLED_TAG = "AudioEnabled";
    private static final String CURRENT_EXPOSURE_TAG = "CurrentExposure";

    private boolean audioEnabled = true;
    private double currentExposureMillisievertsPerSecond;
    private double displayedExposureMillisievertsPerSecond;
    private float targetNeedleValue;
    private float displayedNeedleValue;
    private double lastSyncedExposureMillisievertsPerSecond = Double.NaN;
    private int exposureTickCounter;

    public GeigerCounterPlacedBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.GEIGER_COUNTER_PLACED.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeigerCounterPlacedBlockEntity geiger) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        geiger.exposureTickCounter++;
        if (geiger.exposureTickCounter < EXPOSURE_UPDATE_INTERVAL_TICKS) {
            return;
        }

        geiger.exposureTickCounter = 0;
        Vec3 samplePos = Vec3.atCenterOf(pos).add(0.0D, 0.25D, 0.0D);
        double exposure = RadiationExposureUtil.calculateEnvironmentalExposure(
                serverLevel,
                samplePos,
                RadiationExposureUtil.DEFAULT_PLAYER_SCAN_RADIUS
        );

        geiger.setCurrentExposureMillisievertsPerSecond(exposure);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, GeigerCounterPlacedBlockEntity geiger) {
        geiger.displayedExposureMillisievertsPerSecond += (geiger.currentExposureMillisievertsPerSecond - geiger.displayedExposureMillisievertsPerSecond) * 0.15D;
        geiger.targetNeedleValue = GeigerNeedleUtil.exposureToNeedleValue(geiger.displayedExposureMillisievertsPerSecond);
        geiger.displayedNeedleValue += (geiger.targetNeedleValue - geiger.displayedNeedleValue) * 0.15F;
        geiger.tickClientSound();
    }

    public boolean isAudioEnabled() {
        return audioEnabled;
    }

    public void setAudioEnabled(boolean audioEnabled) {
        if (this.audioEnabled == audioEnabled) {
            return;
        }

        this.audioEnabled = audioEnabled;
        sync();
    }

    public boolean toggleAudio() {
        setAudioEnabled(!audioEnabled);
        return audioEnabled;
    }

    public double getCurrentExposureMillisievertsPerSecond() {
        return currentExposureMillisievertsPerSecond;
    }

    public double getDisplayedExposureMillisievertsPerSecond() {
        return displayedExposureMillisievertsPerSecond;
    }

    public float getDisplayedNeedleValue() {
        return displayedNeedleValue;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(AUDIO_ENABLED_TAG, audioEnabled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        audioEnabled = !tag.contains(AUDIO_ENABLED_TAG) || tag.getBoolean(AUDIO_ENABLED_TAG);
        currentExposureMillisievertsPerSecond = tag.getDouble(CURRENT_EXPOSURE_TAG);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveClientData(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        loadClientData(pkt.getTag());
    }

    private void setCurrentExposureMillisievertsPerSecond(double exposure) {
        double previousExposure = currentExposureMillisievertsPerSecond;
        currentExposureMillisievertsPerSecond = Math.max(0.0D, exposure);
        setChanged();

        if (shouldSyncExposure(previousExposure, currentExposureMillisievertsPerSecond)) {
            lastSyncedExposureMillisievertsPerSecond = currentExposureMillisievertsPerSecond;
            sendBlockUpdate();
        }
    }

    private boolean shouldSyncExposure(double previousExposure, double exposure) {
        if (Double.isNaN(lastSyncedExposureMillisievertsPerSecond)) {
            return true;
        }

        if (previousExposure > 0.0D && exposure <= 0.0D || previousExposure <= 0.0D && exposure > 0.0D) {
            return true;
        }

        return Math.abs(exposure - lastSyncedExposureMillisievertsPerSecond) >= EXPOSURE_SYNC_THRESHOLD;
    }

    private void saveClientData(CompoundTag tag) {
        tag.putBoolean(AUDIO_ENABLED_TAG, audioEnabled);
        tag.putDouble(CURRENT_EXPOSURE_TAG, currentExposureMillisievertsPerSecond);
    }

    private void loadClientData(CompoundTag tag) {
        audioEnabled = !tag.contains(AUDIO_ENABLED_TAG) || tag.getBoolean(AUDIO_ENABLED_TAG);
        currentExposureMillisievertsPerSecond = tag.getDouble(CURRENT_EXPOSURE_TAG);
        if (displayedExposureMillisievertsPerSecond <= 0.0D) {
            displayedExposureMillisievertsPerSecond = currentExposureMillisievertsPerSecond;
            targetNeedleValue = GeigerNeedleUtil.exposureToNeedleValue(displayedExposureMillisievertsPerSecond);
            displayedNeedleValue = targetNeedleValue;
        }
    }

    private void sync() {
        setChanged();
        sendBlockUpdate();
    }

    private void sendBlockUpdate() {
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void tickClientSound() {
        try {
            Class<?> managerClass = Class.forName("com.skyeshade.skyent.client.item.PlacedGeigerCounterSoundManager");
            Method tickMethod = managerClass.getMethod("tickPlacedGeiger", BlockPos.class, boolean.class, double.class);
            tickMethod.invoke(null, worldPosition, audioEnabled, currentExposureMillisievertsPerSecond);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Failed to tick placed Geiger Counter sound", exception);
        }
    }
}
