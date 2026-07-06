package com.skyeshade.skyent.client.sound;

import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.registry.ModSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class ConveyorSoundHandler {
    private static final int SCAN_INTERVAL_TICKS = 5;
    private static final int RESYNC_COOLDOWN_TICKS = 20;
    private static final int ADD_RADIUS = 14;
    private static final int REMOVE_RADIUS = 18;
    private static final int MAX_ACTIVE_CONVEYOR_SOUNDS = 12;
    private static final float CONVEYOR_LOOP_VOLUME = 0.032F;
    private static final float CONVEYOR_LOOP_PITCH = 1.0F;

    private static int scanTicker;
    private static int resyncCooldown;
    private static final Set<BlockPos> activeConveyorSoundPositions = new HashSet<>();

    private ConveyorSoundHandler() {
    }

    public static void clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.level instanceof ClientLevel level) || minecraft.player == null) {
            activeConveyorSoundPositions.clear();
            scanTicker = 0;
            resyncCooldown = 0;
            return;
        }

        if (resyncCooldown > 0) {
            resyncCooldown--;
        }

        if (++scanTicker < SCAN_INTERVAL_TICKS) {
            return;
        }
        scanTicker = 0;

        Vec3 playerPos = minecraft.player.position();
        removeInvalidOrFarActiveSounds(level, playerPos);

        Set<BlockPos> desired = findNearestConveyors(level, playerPos, ADD_RADIUS);
        if (desired.isEmpty()) {
            if (activeConveyorSoundPositions.isEmpty()) {
                return;
            }

            if (!hasAnyActiveSoundInRange(playerPos)) {
                stopAll(level);
            }
            return;
        }

        if (activeConveyorSoundPositions.isEmpty()) {
            startGroupSynced(level, desired);
            return;
        }

        if (!activeConveyorSoundPositions.equals(desired) && resyncCooldown <= 0) {
            restartGroupSynced(level, desired);
            resyncCooldown = RESYNC_COOLDOWN_TICKS;
        }
    }

    private static void removeInvalidOrFarActiveSounds(ClientLevel level, Vec3 playerPos) {
        double removeRadiusSqr = REMOVE_RADIUS * REMOVE_RADIUS;
        for (BlockPos oldPos : new HashSet<>(activeConveyorSoundPositions)) {
            if (isConveyorAt(level, oldPos) && distanceToCenterSqr(oldPos, playerPos) <= removeRadiusSqr) {
                continue;
            }

            MachineSoundManager.stopNamedLoop(level, conveyorKey(oldPos), ModSounds.CONVEYOR_LOOP.get());
            activeConveyorSoundPositions.remove(oldPos);
        }
    }

    private static boolean hasAnyActiveSoundInRange(Vec3 playerPos) {
        double removeRadiusSqr = REMOVE_RADIUS * REMOVE_RADIUS;
        for (BlockPos pos : activeConveyorSoundPositions) {
            if (distanceToCenterSqr(pos, playerPos) <= removeRadiusSqr) {
                return true;
            }
        }
        return false;
    }

    private static void startGroupSynced(ClientLevel level, Set<BlockPos> positions) {
        activeConveyorSoundPositions.clear();
        for (BlockPos pos : positions) {
            BlockPos soundPos = pos.immutable();
            startConveyorLoop(level, soundPos);
            activeConveyorSoundPositions.add(soundPos);
        }
    }

    private static void restartGroupSynced(ClientLevel level, Set<BlockPos> positions) {
        stopAll(level);
        startGroupSynced(level, positions);
    }

    private static void stopAll(ClientLevel level) {
        for (BlockPos pos : new HashSet<>(activeConveyorSoundPositions)) {
            MachineSoundManager.stopNamedLoop(level, conveyorKey(pos), ModSounds.CONVEYOR_LOOP.get());
        }
        activeConveyorSoundPositions.clear();
    }

    private static void startConveyorLoop(ClientLevel level, BlockPos soundPos) {
        MachineSoundManager.startOrUpdateNamedLoop(
                level,
                conveyorKey(soundPos),
                ModSounds.CONVEYOR_LOOP.get(),
                SoundSource.BLOCKS,
                () -> Vec3.atCenterOf(soundPos),
                CONVEYOR_LOOP_VOLUME,
                CONVEYOR_LOOP_PITCH,
                () -> isConveyorAt(level, soundPos)
        );
    }

    private static Set<BlockPos> findNearestConveyors(ClientLevel level, Vec3 playerPos, int radius) {
        BlockPos scanCenter = BlockPos.containing(playerPos);
        List<BlockPos> conveyors = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(
                scanCenter.offset(-radius, -radius, -radius),
                scanCenter.offset(radius, radius, radius)
        )) {
            if (!isConveyorAt(level, pos)) {
                continue;
            }

            conveyors.add(pos.immutable());
        }

        conveyors.sort(Comparator.comparingDouble(pos -> distanceToCenterSqr(pos, playerPos)));

        Set<BlockPos> selected = new HashSet<>();
        for (int index = 0; index < conveyors.size() && selected.size() < MAX_ACTIVE_CONVEYOR_SOUNDS; index++) {
            selected.add(conveyors.get(index));
        }
        return selected;
    }

    private static boolean isConveyorAt(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ConveyorBeltSurface;
    }

    private static double distanceToCenterSqr(BlockPos pos, Vec3 point) {
        double dx = pos.getX() + 0.5D - point.x;
        double dy = pos.getY() + 0.5D - point.y;
        double dz = pos.getZ() + 0.5D - point.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static String conveyorKey(BlockPos pos) {
        return "conveyor:" + pos.asLong();
    }
}
