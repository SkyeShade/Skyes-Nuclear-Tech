package com.skyeshade.skyent.client.sound;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public class MovableMachineLoopSound extends AbstractTickableSoundInstance {
    private final Supplier<Vec3> position;
    private final BooleanSupplier shouldContinue;
    private boolean stopped;

    public MovableMachineLoopSound(SoundEvent sound, SoundSource source, Supplier<Vec3> position, float volume, float pitch, BooleanSupplier shouldContinue) {
        super(sound, source, RandomSource.create());
        this.position = position;
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
        Vec3 pos = position.get();
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
    }
}
