package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.client.model.SkyentModelData;
import com.skyeshade.skyent.client.render.HeatingChamberLighting;
import com.skyeshade.skyent.content.block.ZoneGateBlock;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public class ZoneGateBlockEntity extends BlockEntity {
    public static final int ZONE_GATE_ANIMATION_TICKS = 50;
    public static final double ZONE_GATE_DOOR_TRAVEL_Y = 3.0D;
    private static final float MOVEMENT_SOUND_VOLUME = 2.0F;
    private static final float MOVEMENT_SOUND_PITCH = 1.0F;
    private static final int LIGHT_CHECK_INTERVAL_TICKS = 40;
    private static final String TAG_STATE = "GateState";
    private static final String TAG_POWERED = "Powered";
    private static final String TAG_OPEN_PROGRESS = "OpenProgress";

    private GateState gateState = GateState.CLOSED;
    private boolean powered;
    private float openProgress;
    private float previousOpenProgress;
    private int cachedSharedPackedLight = -1;
    private int lightCheckTicks;

    public ZoneGateBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ZONE_GATE.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ZoneGateBlockEntity zoneGate) {
        if (level.isClientSide) {
            return;
        }
        zoneGate.updateRedstonePower();
        zoneGate.tickAnimation(true);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ZoneGateBlockEntity zoneGate) {
        if (!level.isClientSide) {
            return;
        }
        zoneGate.tickAnimation(false);
        zoneGate.lightCheckTicks++;
        if (zoneGate.lightCheckTicks >= LIGHT_CHECK_INTERVAL_TICKS) {
            zoneGate.lightCheckTicks = 0;
            zoneGate.refreshSharedLight(false);
        }
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
        if (level != null && !level.isClientSide) {
            updateRedstonePower();
        }
    }

    public void updateRedstonePower() {
        if (level == null || level.isClientSide) {
            return;
        }

        boolean nowPowered = ZoneGateBlock.isPoweredAnywhere(level, worldPosition, getFacing());
        if (powered == nowPowered) {
            return;
        }

        powered = nowPowered;
        GateState previousState = gateState;
        applyPoweredTarget();
        if (previousState != gateState) {
            notifyShapeChanged();
        }
        setChangedAndSync();
    }

    public boolean isOpen() {
        return openProgress >= 1.0F && gateState == GateState.OPEN;
    }

    public boolean isMoving() {
        return gateState == GateState.OPENING || gateState == GateState.CLOSING;
    }

    public float getOpenProgress() {
        return openProgress;
    }

    public float getAnimationProgress(float partialTicks) {
        if (!isMoving()) {
            return openProgress;
        }
        return Mth.lerp(Mth.clamp(partialTicks, 0.0F, 1.0F), previousOpenProgress, openProgress);
    }

    public GateState getGateState() {
        return gateState;
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

    private void tickAnimation(boolean authoritative) {
        if (authoritative) {
            applyPoweredTarget();
        }

        previousOpenProgress = openProgress;
        float step = 1.0F / ZONE_GATE_ANIMATION_TICKS;
        GateState previousState = gateState;
        boolean wasOpen = isOpen();

        switch (gateState) {
            case OPENING -> {
                openProgress = Math.min(1.0F, openProgress + step);
                if (openProgress >= 1.0F) {
                    gateState = GateState.OPEN;
                    applyPoweredTarget();
                }
            }
            case CLOSING -> {
                openProgress = Math.max(0.0F, openProgress - step);
                if (openProgress <= 0.0F) {
                    gateState = GateState.CLOSED;
                    applyPoweredTarget();
                }
            }
            case OPEN -> openProgress = 1.0F;
            case CLOSED -> openProgress = 0.0F;
        }

        if (!authoritative) {
            return;
        }

        if (previousState != gateState || wasOpen != isOpen()) {
            setChangedAndSync();
            notifyShapeChanged();
        } else if (isMoving()) {
            setChanged();
        }
    }

    private void applyPoweredTarget() {
        switch (gateState) {
            case CLOSED -> {
                openProgress = 0.0F;
                if (powered) {
                    startOpening();
                }
            }
            case OPEN -> {
                openProgress = 1.0F;
                if (!powered) {
                    startClosing();
                }
            }
            case OPENING, CLOSING -> {
                // only rechecks redstone after reaching fully open or fully closed.
            }
        }
    }

    private void startOpening() {
        if (gateState != GateState.OPENING) {
            gateState = GateState.OPENING;
            playMovementSound();
        }
    }

    private void startClosing() {
        if (gateState != GateState.CLOSING) {
            gateState = GateState.CLOSING;
            playMovementSound();
        }
    }

    private void playMovementSound() {
        if (level == null || level.isClientSide || !isMoving()) {
            return;
        }
        Vec3 center = getGateSoundPosition();
        level.playSound(
                null,
                center.x,
                center.y,
                center.z,
                ModSounds.HEAVY_DOOR.get(),
                SoundSource.BLOCKS,
                MOVEMENT_SOUND_VOLUME,
                MOVEMENT_SOUND_PITCH
        );
    }

    private Vec3 getGateSoundPosition() {
        return Vec3.atCenterOf(worldPosition).add(0.0D, 2.0D, 0.0D);
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void notifyShapeChanged() {
        if (level == null || level.isClientSide) {
            return;
        }

        Direction facing = getFacing();
        for (int y = 0; y < ZoneGateBlock.SIZE_Y; y++) {
            for (int x = 0; x < ZoneGateBlock.SIZE_X; x++) {
                for (int z = 0; z < ZoneGateBlock.SIZE_Z; z++) {
                    BlockPos pos = ZoneGateBlock.localToWorld(worldPosition, facing, x, y, z);
                    BlockState state = level.getBlockState(pos);
                    level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
                }
            }
        }
    }

    private int computePackedLight(Level level) {
        return HeatingChamberLighting.computeMaxPackedLight(
                level,
                worldPosition,
                getFacing(),
                ZoneGateBlock.SIZE_X,
                ZoneGateBlock.SIZE_Y,
                ZoneGateBlock.SIZE_Z,
                ZoneGateBlock::localToWorld
        );
    }

    private Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(ZoneGateBlock.FACING) ? state.getValue(ZoneGateBlock.FACING) : Direction.NORTH;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_STATE, gateState.serializedName);
        tag.putBoolean(TAG_POWERED, powered);
        tag.putFloat(TAG_OPEN_PROGRESS, openProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gateState = GateState.byName(tag.getString(TAG_STATE));
        powered = tag.getBoolean(TAG_POWERED);
        openProgress = tag.contains(TAG_OPEN_PROGRESS)
                ? Mth.clamp(tag.getFloat(TAG_OPEN_PROGRESS), 0.0F, 1.0F)
                : progressFromState(gateState);
        previousOpenProgress = openProgress;
        normalizeLoadedState();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        loadAdditional(pkt.getTag(), lookupProvider);
    }

    private static float progressFromState(GateState state) {
        return state == GateState.OPEN || state == GateState.CLOSING ? 1.0F : 0.0F;
    }

    private void normalizeLoadedState() {
        if (openProgress <= 0.0F && gateState != GateState.OPENING) {
            gateState = GateState.CLOSED;
        } else if (openProgress >= 1.0F && gateState != GateState.CLOSING) {
            gateState = GateState.OPEN;
        } else if (gateState == GateState.CLOSED && openProgress > 0.0F) {
            gateState = GateState.CLOSING;
        } else if (gateState == GateState.OPEN && openProgress < 1.0F) {
            gateState = GateState.OPENING;
        }
    }

    public enum GateState {
        CLOSED("closed"),
        OPEN("open"),
        OPENING("opening"),
        CLOSING("closing");

        private final String serializedName;

        GateState(String serializedName) {
            this.serializedName = serializedName;
        }

        public static GateState byName(String name) {
            for (GateState state : values()) {
                if (state.serializedName.equals(name)) {
                    return state;
                }
            }
            return CLOSED;
        }
    }
}
