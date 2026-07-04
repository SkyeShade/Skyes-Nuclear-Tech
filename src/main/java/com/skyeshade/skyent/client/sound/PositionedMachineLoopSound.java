package com.skyeshade.skyent.client.sound;

import java.util.function.BooleanSupplier;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class PositionedMachineLoopSound extends AbstractTickableSoundInstance {
    private final BlockPos pos;
    private final BooleanSupplier shouldContinue;
    private boolean stopped;

    public PositionedMachineLoopSound(SoundEvent sound, SoundSource source, BlockPos pos, float volume, float pitch, BooleanSupplier shouldContinue) {
        super(sound, source, RandomSource.create());
        this.pos = pos.immutable();
        this.shouldContinue = shouldContinue;
        this.looping = true;
        this.delay = 0;
        this.volume = volume;
        this.pitch = pitch;
        this.relative = false;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        updatePosition();
    }

    @Override
    public void tick() {
        if (!shouldContinue.getAsBoolean()) {
            stopNow();
            return;
        }

        updatePosition();
    }

    public boolean isStopped() {
        return stopped;
    }

    public void stopNow() {
        if (stopped) {
            return;
        }

        stopped = true;
        stop();
    }

    private void updatePosition() {
        this.x = pos.getX() + 0.5D;
        this.y = pos.getY() + 0.5D;
        this.z = pos.getZ() + 0.5D;
    }
}
