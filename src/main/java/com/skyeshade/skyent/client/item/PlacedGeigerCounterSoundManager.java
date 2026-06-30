package com.skyeshade.skyent.client.item;

import com.skyeshade.skyent.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class PlacedGeigerCounterSoundManager {
    private static final double LOOP_THRESHOLD_GEIGER_3 = 0.1D;
    private static final double LOOP_THRESHOLD_GEIGER_4 = 5.0D;
    private static final double LOOP_THRESHOLD_GEIGER_5 = 25.0D;
    private static final double LOOP_THRESHOLD_GEIGER_6 = 100.0D;
    private static final double LOOP_THRESHOLD_GEIGER_7 = 250.0D;
    private static final double LOOP_THRESHOLD_GEIGER_8 = 500.0D;

    private static final double MAX_AUDIBLE_DISTANCE_SQUARED = 48.0D * 48.0D;
    private static final int STALE_TICKS_BEFORE_STOP = 8;
    private static final int MIN_PASSIVE_COOLDOWN_TICKS = 40;
    private static final int MAX_PASSIVE_COOLDOWN_TICKS = 120;

    private static final float LOOP_VOLUME = 0.85F;
    private static final float BLIP_VOLUME = 0.7F;

    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<BlockPos, PlacedGeigerSoundState> SOUND_STATES = new HashMap<>();

    private static int clientTickCount;

    private PlacedGeigerCounterSoundManager() {
    }

    public static void clientTick() {
        clientTickCount++;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            stopAll();
            return;
        }

        Iterator<Map.Entry<BlockPos, PlacedGeigerSoundState>> iterator = SOUND_STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, PlacedGeigerSoundState> entry = iterator.next();
            PlacedGeigerSoundState state = entry.getValue();

            if (clientTickCount - state.lastSeenTick > STALE_TICKS_BEFORE_STOP) {
                state.stopLoop();
                iterator.remove();
            }
        }
    }

    public static void tickPlacedGeiger(BlockPos pos, boolean audioEnabled, double exposureMillisievertsPerSecond) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;
        BlockPos immutablePos = pos.immutable();

        if (player == null || level == null || !audioEnabled || !isAudible(player, immutablePos)) {
            stop(immutablePos);
            return;
        }

        PlacedGeigerSoundState state = SOUND_STATES.computeIfAbsent(immutablePos, PlacedGeigerSoundState::new);
        state.lastSeenTick = clientTickCount;
        state.exposureMillisievertsPerSecond = Math.max(0.0D, exposureMillisievertsPerSecond);

        int loopTier = loopTierForExposure(state.exposureMillisievertsPerSecond);
        if (state.activeLoopTier != loopTier) {
            state.stopLoop();
            state.activeLoopTier = loopTier;

            if (loopTier > 0) {
                state.loop = new PlacedGeigerLoopSoundInstance(soundForTier(loopTier), immutablePos, LOOP_VOLUME);
                minecraft.getSoundManager().play(state.loop);
            }
        }

        state.tickPassiveClicks(level);
    }

    private static boolean isAudible(LocalPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= MAX_AUDIBLE_DISTANCE_SQUARED;
    }

    private static void stop(BlockPos pos) {
        PlacedGeigerSoundState state = SOUND_STATES.remove(pos);
        if (state != null) {
            state.stopLoop();
        }
    }

    private static void stopAll() {
        for (PlacedGeigerSoundState state : SOUND_STATES.values()) {
            state.stopLoop();
        }

        SOUND_STATES.clear();
    }

    private static int loopTierForExposure(double exposure) {
        if (exposure < LOOP_THRESHOLD_GEIGER_3) {
            return 0;
        }
        if (exposure < LOOP_THRESHOLD_GEIGER_4) {
            return 3;
        }
        if (exposure < LOOP_THRESHOLD_GEIGER_5) {
            return 4;
        }
        if (exposure < LOOP_THRESHOLD_GEIGER_6) {
            return 5;
        }
        if (exposure < LOOP_THRESHOLD_GEIGER_7) {
            return 6;
        }
        if (exposure < LOOP_THRESHOLD_GEIGER_8) {
            return 7;
        }

        return 8;
    }

    private static SoundEvent soundForTier(int tier) {
        return switch (tier) {
            case 3 -> ModSounds.GEIGER_3.get();
            case 4 -> ModSounds.GEIGER_4.get();
            case 5 -> ModSounds.GEIGER_5.get();
            case 6 -> ModSounds.GEIGER_6.get();
            case 7 -> ModSounds.GEIGER_7.get();
            case 8 -> ModSounds.GEIGER_8.get();
            default -> throw new IllegalArgumentException("Unsupported placed Geiger loop tier: " + tier);
        };
    }

    private static DeferredHolder<SoundEvent, SoundEvent> passiveClickSound() {
        return RANDOM.nextBoolean() ? ModSounds.GEIGER_1 : ModSounds.GEIGER_2;
    }

    private static int nextPassiveCooldown(double exposure) {
        int minCooldown = exposure < LOOP_THRESHOLD_GEIGER_3 ? MIN_PASSIVE_COOLDOWN_TICKS : 60;
        int maxCooldown = exposure < LOOP_THRESHOLD_GEIGER_3 ? MAX_PASSIVE_COOLDOWN_TICKS : 160;
        return minCooldown + RANDOM.nextInt(maxCooldown - minCooldown + 1);
    }

    private static final class PlacedGeigerSoundState {
        private final BlockPos pos;

        private PlacedGeigerLoopSoundInstance loop;
        private double exposureMillisievertsPerSecond;
        private int activeLoopTier;
        private int passiveClickCooldown;
        private int lastSeenTick;

        private PlacedGeigerSoundState(BlockPos pos) {
            this.pos = pos;
            this.passiveClickCooldown = nextPassiveCooldown(0.0D);
            this.lastSeenTick = clientTickCount;
        }

        private void tickPassiveClicks(Level level) {
            if (passiveClickCooldown > 0) {
                passiveClickCooldown--;
                return;
            }

            float pitch = 0.95F + RANDOM.nextFloat() * 0.1F;
            level.playLocalSound(
                    pos.getX() + 0.5D,
                    pos.getY() + 0.5D,
                    pos.getZ() + 0.5D,
                    passiveClickSound().get(),
                    SoundSource.BLOCKS,
                    BLIP_VOLUME,
                    pitch,
                    false
            );
            passiveClickCooldown = nextPassiveCooldown(exposureMillisievertsPerSecond);
        }

        private void stopLoop() {
            if (loop != null) {
                loop.stopNow();
                loop = null;
            }

            activeLoopTier = 0;
        }
    }

    private static final class PlacedGeigerLoopSoundInstance extends AbstractTickableSoundInstance {
        private PlacedGeigerLoopSoundInstance(SoundEvent sound, BlockPos pos, float volume) {
            super(sound, SoundSource.BLOCKS, RandomSource.create());

            this.looping = true;
            this.delay = 0;
            this.volume = volume;
            this.pitch = 1.0F;
            this.relative = false;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.x = pos.getX() + 0.5D;
            this.y = pos.getY() + 0.5D;
            this.z = pos.getZ() + 0.5D;
        }

        @Override
        public void tick() {
        }

        private void stopNow() {
            stop();
        }
    }
}
