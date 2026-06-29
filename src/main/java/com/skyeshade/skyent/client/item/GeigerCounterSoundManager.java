package com.skyeshade.skyent.client.item;

import com.skyeshade.skyent.content.item.GeigerCounterItem;
import com.skyeshade.skyent.registry.ModItems;
import com.skyeshade.skyent.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class GeigerCounterSoundManager {
    private static final double LOOP_THRESHOLD_GEIGER_3 = 0.1D;
    private static final double LOOP_THRESHOLD_GEIGER_4 = 5.0D;
    private static final double LOOP_THRESHOLD_GEIGER_5 = 25.0D;
    private static final double LOOP_THRESHOLD_GEIGER_6 = 100.0D;
    private static final double LOOP_THRESHOLD_GEIGER_7 = 250.0D;
    private static final double LOOP_THRESHOLD_GEIGER_8 = 500.0D;

    private static final float LOOP_VOLUME = 0.85F;
    private static final float BLIP_VOLUME = 0.8F;

    private static final int CROSSFADE_TICKS = 20;

    private static final int MIN_PASSIVE_COOLDOWN_TICKS = 40;
    private static final int MAX_PASSIVE_COOLDOWN_TICKS = 120;
    private static final int AUDIO_ENABLE_PASSIVE_COOLDOWN_MIN_TICKS = 20;
    private static final int AUDIO_ENABLE_PASSIVE_COOLDOWN_RANDOM_TICKS = 40;

    private static final RandomSource RANDOM = RandomSource.create();

    private static final List<GeigerLoopSoundInstance> activeLoops = new ArrayList<>();

    private static GeigerLoopSoundInstance currentLoop;
    private static int activeLoopTier;
    private static int passiveClickCooldown;
    private static boolean hadAudioEnabledLastTick;

    private GeigerCounterSoundManager() {
    }

    public static void clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        boolean hasAudioEnabled = player != null && hasEnabledGeigerCounter(player);
        if (!hasAudioEnabled) {
            fadeOutAllLoops();
            passiveClickCooldown = 0;
            hadAudioEnabledLastTick = false;
            return;
        }

        if (!hadAudioEnabledLastTick) {
            passiveClickCooldown = AUDIO_ENABLE_PASSIVE_COOLDOWN_MIN_TICKS + RANDOM.nextInt(AUDIO_ENABLE_PASSIVE_COOLDOWN_RANDOM_TICKS + 1);
        }
        hadAudioEnabledLastTick = true;

        double exposure = GeigerCounterClientState.getDisplayedExposureMillisievertsPerSecond();
        int loopTier = loopTierForExposure(exposure);

        updateLoopTier(minecraft, loopTier);
        cleanupStoppedLoops();

        tickPassiveClicks(minecraft, exposure);
    }

    private static void updateLoopTier(Minecraft minecraft, int loopTier) {
        if (loopTier == activeLoopTier) {
            return;
        }

        if (currentLoop != null) {
            currentLoop.fadeOut(CROSSFADE_TICKS);
        }

        currentLoop = null;
        activeLoopTier = loopTier;

        if (loopTier > 0) {
            GeigerLoopSoundInstance nextLoop = new GeigerLoopSoundInstance(soundForTier(loopTier), LOOP_VOLUME);
            nextLoop.fadeIn(CROSSFADE_TICKS);

            currentLoop = nextLoop;
            activeLoops.add(nextLoop);

            minecraft.getSoundManager().play(nextLoop);
        }
    }

    private static void fadeOutAllLoops() {
        for (GeigerLoopSoundInstance loop : activeLoops) {
            loop.fadeOut(CROSSFADE_TICKS);
        }

        currentLoop = null;
        activeLoopTier = 0;
    }

    private static void cleanupStoppedLoops() {
        Iterator<GeigerLoopSoundInstance> iterator = activeLoops.iterator();

        while (iterator.hasNext()) {
            GeigerLoopSoundInstance loop = iterator.next();

            if (loop.isStopped()) {
                iterator.remove();
            }
        }
    }

    private static void tickPassiveClicks(Minecraft minecraft, double exposure) {
        if (passiveClickCooldown > 0) {
            passiveClickCooldown--;
            return;
        }

        DeferredHolder<SoundEvent, SoundEvent> sound = RANDOM.nextBoolean() ? ModSounds.GEIGER_1 : ModSounds.GEIGER_2;
        float pitch = 0.95F + RANDOM.nextFloat() * 0.1F;

        minecraft.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(sound.get(), pitch, BLIP_VOLUME));
        passiveClickCooldown = nextPassiveCooldown(exposure);
    }

    private static int nextPassiveCooldown(double exposure) {
        int maxCooldown = exposure < LOOP_THRESHOLD_GEIGER_3 ? MAX_PASSIVE_COOLDOWN_TICKS : 160;
        int minCooldown = exposure < LOOP_THRESHOLD_GEIGER_3 ? MIN_PASSIVE_COOLDOWN_TICKS : 60;

        return minCooldown + RANDOM.nextInt(maxCooldown - minCooldown + 1);
    }

    private static boolean hasEnabledGeigerCounter(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            if (isEnabledGeigerCounter(stack)) {
                return true;
            }
        }

        for (ItemStack stack : inventory.offhand) {
            if (isEnabledGeigerCounter(stack)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isEnabledGeigerCounter(ItemStack stack) {
        return stack.is(ModItems.GEIGER_COUNTER.get()) && GeigerCounterItem.isAudioEnabled(stack);
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
            default -> throw new IllegalArgumentException("Unsupported Geiger loop tier: " + tier);
        };
    }

    private static final class GeigerLoopSoundInstance extends AbstractTickableSoundInstance {
        private final float targetVolume;

        private int fadeTicks;
        private int fadeProgress;
        private FadeMode fadeMode = FadeMode.NONE;
        private boolean stopped;

        private GeigerLoopSoundInstance(SoundEvent sound, float targetVolume) {
            super(sound, SoundSource.PLAYERS, RandomSource.create());

            this.targetVolume = targetVolume;

            this.looping = true;
            this.delay = 0;
            this.volume = targetVolume;
            this.pitch = 1.0F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            Minecraft minecraft = Minecraft.getInstance();

            if (minecraft.player == null || !hasEnabledGeigerCounter(minecraft.player)) {
                fadeOut(CROSSFADE_TICKS);
            }

            tickFade();
        }

        private static final float MIN_AUDIBLE_VOLUME = 0.001F;

        private void fadeIn(int ticks) {
            this.fadeMode = FadeMode.IN;
            this.fadeTicks = Math.max(1, ticks);
            this.fadeProgress = 0;
            this.volume = MIN_AUDIBLE_VOLUME;
        }

        private void fadeOut(int ticks) {
            if (this.fadeMode == FadeMode.OUT || this.stopped) {
                return;
            }

            this.fadeMode = FadeMode.OUT;
            this.fadeTicks = Math.max(1, ticks);
            this.fadeProgress = 0;
        }

        private void tickFade() {
            if (this.fadeMode == FadeMode.NONE) {
                return;
            }

            this.fadeProgress++;

            float t = Math.min(1.0F, this.fadeProgress / (float) this.fadeTicks);

            if (this.fadeMode == FadeMode.IN) {
                this.volume = Math.max(MIN_AUDIBLE_VOLUME, targetVolume * equalPowerFadeIn(t));

                if (t >= 1.0F) {
                    this.volume = targetVolume;
                    this.fadeMode = FadeMode.NONE;
                }

                return;
            }

            this.volume = targetVolume * equalPowerFadeOut(t);

            if (t >= 1.0F) {
                this.volume = 0.0F;
                this.stopped = true;
                stop();
            }
        }

        public boolean isStopped() {
            return this.stopped;
        }

        private static float equalPowerFadeIn(float t) {
            return (float) Math.sin(t * Math.PI * 0.5D);
        }

        private static float equalPowerFadeOut(float t) {
            return (float) Math.cos(t * Math.PI * 0.5D);
        }

        private enum FadeMode {
            NONE,
            IN,
            OUT
        }
    }
}
