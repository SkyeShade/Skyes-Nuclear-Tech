package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.client.model.SkyentModelData;
import com.skyeshade.skyent.client.render.HeatingChamberLighting;
import com.skyeshade.skyent.content.block.BlastDoorBlock;
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

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class BlastDoorBlockEntity extends BlockEntity {
    private static final int BLAST_DOOR_ANIMATION_DURATION_TICKS = 220;
    private static final int DEFAULT_ANIMATION_DURATION_TICKS = BLAST_DOOR_ANIMATION_DURATION_TICKS;
    private static final int LIGHT_CHECK_INTERVAL_TICKS = 40;
    private static final float MOVEMENT_SOUND_VOLUME = 3.0F;
    private static final float MOVEMENT_SOUND_PITCH = 1.0F;
    private static final String TAG_STATE = "DoorState";
    private static final String TAG_POWERED = "Powered";
    private static final String TAG_OPEN_PROGRESS = "OpenProgress";
    private static final String TAG_ANIMATION_DURATION_TICKS = "AnimationDurationTicks";

    private DoorState doorState = DoorState.CLOSED;
    private boolean powered;
    private float openProgress;
    private float previousOpenProgress;
    private int animationDurationTicks = DEFAULT_ANIMATION_DURATION_TICKS;
    private int cachedSharedPackedLight = -1;
    private int lightCheckTicks;
    private boolean clientMovementLoopActive;
    private DoorState clientMovementLoopState;

    public BlastDoorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.BLAST_DOOR.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlastDoorBlockEntity blastDoor) {
        if (level.isClientSide) {
            return;
        }
        blastDoor.updateRedstonePower();
        blastDoor.tickAnimation(true);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, BlastDoorBlockEntity blastDoor) {
        if (!level.isClientSide) {
            return;
        }
        blastDoor.tickAnimation(false);
        blastDoor.lightCheckTicks++;
        if (blastDoor.lightCheckTicks >= LIGHT_CHECK_INTERVAL_TICKS) {
            blastDoor.lightCheckTicks = 0;
            blastDoor.refreshSharedLight(false);
        }
        blastDoor.tickClientMovementSound();
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
        Direction facing = getFacing();
        boolean nowPowered = BlastDoorBlock.isPoweredAnywhere(level, worldPosition, facing);
        boolean changed = powered != nowPowered;
        if (changed) {
            powered = nowPowered;
        }

        DoorState previousState = doorState;
        applyPowerRequestToState();
        if (changed || previousState != doorState) {
            setChangedAndSync();
            if (isOpenStateChange(previousState, doorState)) {
                notifyShapeChanged();
            }
        }
    }

    public boolean isClosed() {
        return openProgress <= 0.0F && doorState == DoorState.CLOSED;
    }

    public boolean isOpen() {
        return openProgress >= 1.0F && doorState == DoorState.OPEN;
    }

    public boolean isMoving() {
        return doorState == DoorState.OPENING || doorState == DoorState.CLOSING;
    }

    public boolean isRadiationOpen() {
        return isOpen();
    }

    public boolean isRadiationClosed() {
        return !isOpen();
    }

    public DoorState getDoorState() {
        return doorState;
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

    private void tickAnimation(boolean authoritative) {
        if (authoritative) {
            applyPowerRequestToState();
        }
        boolean wasOpen = isOpen();
        previousOpenProgress = openProgress;
        float step = 1.0F / Math.max(1, animationDurationTicks);
        DoorState previousState = doorState;
        switch (doorState) {
            case OPENING -> {
                openProgress = Math.min(1.0F, openProgress + step);
                if (openProgress >= 1.0F) {
                    doorState = powered ? DoorState.OPEN : DoorState.CLOSING;
                }
            }
            case CLOSING -> {
                if (powered) {
                    doorState = DoorState.OPENING;
                    openProgress = Math.min(1.0F, openProgress + step);
                    if (openProgress >= 1.0F) {
                        doorState = DoorState.OPEN;
                    }
                } else {
                    openProgress = Math.max(0.0F, openProgress - step);
                    if (openProgress <= 0.0F) {
                        doorState = DoorState.CLOSED;
                    }
                }
            }
            case OPEN -> {
                openProgress = 1.0F;
                if (!powered) {
                    doorState = DoorState.CLOSING;
                }
            }
            case CLOSED -> {
                openProgress = 0.0F;
                if (powered) {
                    doorState = DoorState.OPENING;
                }
            }
        }
        boolean openChanged = wasOpen != isOpen();
        if (authoritative && (previousState != doorState || openChanged)) {
            setChangedAndSync();
            if (openChanged) {
                notifyShapeChanged();
            }
        } else if (authoritative) {
            setChanged();
        }
    }

    private void applyPowerRequestToState() {
        switch (doorState) {
            case CLOSED -> {
                openProgress = 0.0F;
                if (powered) {
                    doorState = DoorState.OPENING;
                }
            }
            case OPEN -> {
                openProgress = 1.0F;
                if (!powered) {
                    doorState = DoorState.CLOSING;
                }
            }
            case CLOSING -> {
                if (powered) {
                    doorState = DoorState.OPENING;
                }
            }
            case OPENING -> {
                // Opening is latched: a pulse must carry the door to full open before it can close.
            }
        }
    }

    private static boolean isOpenStateChange(DoorState previous, DoorState current) {
        return previous == DoorState.OPEN || current == DoorState.OPEN;
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
        for (int y = 0; y < BlastDoorBlock.SIZE_Y; y++) {
            for (int x = 0; x < BlastDoorBlock.SIZE_X; x++) {
                for (int z = 0; z < BlastDoorBlock.SIZE_Z; z++) {
                    BlockPos pos = BlastDoorBlock.localToWorld(worldPosition, facing, x, y, z);
                    BlockState state = level.getBlockState(pos);
                    level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
                }
            }
        }
    }

    private void tickClientMovementSound() {
        if (level == null || !level.isClientSide) {
            return;
        }
        if (isMoving()) {
            if (!clientMovementLoopActive || clientMovementLoopState != doorState) {
                if (clientMovementLoopActive) {
                    stopClientMovementLoop(level, worldPosition);
                }
                playClientMovementOneShot(level, ModSounds.HEAVY_MOVING_METAL_START.get(), getDoorSoundPosition());
                clientMovementLoopActive = true;
                clientMovementLoopState = doorState;
            }
            startClientMovementLoop(level, worldPosition, getDoorSoundPosition());
        } else if (clientMovementLoopActive) {
            stopClientMovementSound(level, worldPosition);
            playClientMovementOneShot(level, ModSounds.HEAVY_MOVING_METAL_END.get(), getDoorSoundPosition());
            clientMovementLoopActive = false;
            clientMovementLoopState = null;
        }
    }

    private Vec3 getDoorSoundPosition() {
        return Vec3.atCenterOf(worldPosition).add(0.0D, 1.5D, 0.0D);
    }

    private static void startClientMovementLoop(Level level, BlockPos pos, Vec3 center) {
        invokeClientMovementLoop("startOrUpdateNamedLoop", level, pos, center);
    }

    private static void stopClientMovementLoop(Level level, BlockPos pos) {
        invokeClientMovementLoop("stopNamedLoop", level, pos, Vec3.ZERO);
    }

    private static void stopClientMovementSound(Level level, BlockPos pos) {
        stopClientMovementLoop(level, pos);
    }

    private static String movementSoundKey(BlockPos pos) {
        return "blast_door_movement:" + pos.asLong();
    }

    private static void playClientMovementOneShot(Level level, net.minecraft.sounds.SoundEvent sound, Vec3 center) {
        level.playLocalSound(
                center.x,
                center.y,
                center.z,
                sound,
                SoundSource.BLOCKS,
                MOVEMENT_SOUND_VOLUME,
                MOVEMENT_SOUND_PITCH,
                false
        );
    }

    private static void invokeClientMovementLoop(String methodName, Level level, BlockPos pos, Vec3 center) {
        if (!level.isClientSide) {
            return;
        }
        try {
            Class<?> clientLevelClass = Class.forName("net.minecraft.client.multiplayer.ClientLevel");
            if (!clientLevelClass.isInstance(level)) {
                return;
            }
            Class<?> managerClass = Class.forName("com.skyeshade.skyent.client.sound.MachineSoundManager");
            if ("startOrUpdateNamedLoop".equals(methodName)) {
                Method method = managerClass.getMethod(
                        methodName,
                        clientLevelClass,
                        String.class,
                        net.minecraft.sounds.SoundEvent.class,
                        SoundSource.class,
                        Supplier.class,
                        float.class,
                        float.class,
                        BooleanSupplier.class
                );
                method.invoke(
                        null,
                        level,
                        movementSoundKey(pos),
                        ModSounds.HEAVY_MOVING_METAL_LOOP.get(),
                        SoundSource.BLOCKS,
                        (Supplier<Vec3>) () -> center,
                        MOVEMENT_SOUND_VOLUME,
                        MOVEMENT_SOUND_PITCH,
                        (BooleanSupplier) () -> level.getBlockEntity(pos) instanceof BlastDoorBlockEntity blastDoor && blastDoor.isMoving()
                );
            } else {
                Method method = managerClass.getMethod(methodName, clientLevelClass, String.class, net.minecraft.sounds.SoundEvent.class);
                method.invoke(null, level, movementSoundKey(pos), ModSounds.HEAVY_MOVING_METAL_LOOP.get());
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to update Blast Door movement loop sound", exception);
        }
    }

    private int computePackedLight(Level level) {
        return HeatingChamberLighting.computeMaxPackedLight(
                level,
                worldPosition,
                getFacing(),
                BlastDoorBlock.SIZE_X,
                BlastDoorBlock.SIZE_Y,
                BlastDoorBlock.SIZE_Z,
                BlastDoorBlock::localToWorld
        );
    }

    private Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(BlastDoorBlock.FACING) ? state.getValue(BlastDoorBlock.FACING) : Direction.NORTH;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_STATE, doorState.serializedName);
        tag.putBoolean(TAG_POWERED, powered);
        tag.putFloat(TAG_OPEN_PROGRESS, openProgress);
        tag.putInt(TAG_ANIMATION_DURATION_TICKS, animationDurationTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        doorState = DoorState.byName(tag.getString(TAG_STATE));
        powered = tag.getBoolean(TAG_POWERED);
        animationDurationTicks = tag.contains(TAG_ANIMATION_DURATION_TICKS)
                ? Math.max(1, tag.getInt(TAG_ANIMATION_DURATION_TICKS))
                : DEFAULT_ANIMATION_DURATION_TICKS;
        openProgress = tag.contains(TAG_OPEN_PROGRESS)
                ? Mth.clamp(tag.getFloat(TAG_OPEN_PROGRESS), 0.0F, 1.0F)
                : progressFromLegacyState(doorState);
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

    private static float progressFromLegacyState(DoorState state) {
        return state == DoorState.OPEN || state == DoorState.CLOSING ? 1.0F : 0.0F;
    }

    private void normalizeLoadedState() {
        if (openProgress <= 0.0F && doorState != DoorState.OPENING) {
            doorState = DoorState.CLOSED;
        } else if (openProgress >= 1.0F && doorState != DoorState.CLOSING) {
            doorState = DoorState.OPEN;
        } else if (doorState == DoorState.CLOSED && openProgress > 0.0F) {
            doorState = DoorState.CLOSING;
        } else if (doorState == DoorState.OPEN && openProgress < 1.0F) {
            doorState = DoorState.OPENING;
        }
    }

    public enum DoorState {
        CLOSED("closed"),
        OPEN("open"),
        OPENING("opening"),
        CLOSING("closing");

        private final String serializedName;

        DoorState(String serializedName) {
            this.serializedName = serializedName;
        }

        public String getSerializedName() {
            return serializedName;
        }

        public static DoorState byName(String name) {
            for (DoorState state : values()) {
                if (state.serializedName.equals(name)) {
                    return state;
                }
            }
            return CLOSED;
        }
    }
}
