package com.skyeshade.skyent.client.effect;

import com.skyeshade.skyent.content.entity.NuclearExplosionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class NukeShockwaveSoundClient {
    private static final double ARRIVAL_BAND_BLOCKS = 3.0D;
    private static final float BASE_SHAKE_STRENGTH = 8.0F;
    private static final int MIN_SHAKE_DURATION_TICKS = 20;
    private static final int MAX_SHAKE_DURATION_TICKS = 40;
    private static final Map<Integer, ShockwaveSoundState> STATES = new HashMap<>();

    private NukeShockwaveSoundClient() {
    }

    public static void tick(NuclearExplosionEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            STATES.clear();
            return;
        }

        removeStaleStates(entity.getId());

        ShockwaveSoundState state = STATES.computeIfAbsent(entity.getId(), ignored -> new ShockwaveSoundState());
        if (!state.triggered) {
            maybeTrigger(entity, minecraft.player, state);
        }

        if (state.remainingTicks > 0) {
            playArrivalSound(entity, state.remainingTicks);
            state.remainingTicks--;
        }

        if (entity.isRemoved() && state.remainingTicks <= 0) {
            STATES.remove(entity.getId());
        }
    }

    private static void removeStaleStates(int activeEntityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.getEntity(activeEntityId) != null) {
            return;
        }

        Iterator<Integer> iterator = STATES.keySet().iterator();
        while (iterator.hasNext()) {
            int entityId = iterator.next();
            if (minecraft.level.getEntity(entityId) == null) {
                iterator.remove();
            }
        }
    }

    private static void maybeTrigger(NuclearExplosionEntity entity, LocalPlayer player, ShockwaveSoundState state) {
        double dx = player.getX() - entity.getX();
        double dz = player.getZ() - entity.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        int visualAge = entity.getVisualAge();
        double previousRadius = Math.max(0.0D, (visualAge - 1) * NuclearExplosionEntity.SHOCKWAVE_SPEED_BLOCKS_PER_TICK);
        double currentRadius = visualAge * NuclearExplosionEntity.SHOCKWAVE_SPEED_BLOCKS_PER_TICK;

        if (distance <= currentRadius + ARRIVAL_BAND_BLOCKS && distance >= previousRadius - ARRIVAL_BAND_BLOCKS) {
            state.triggered = true;
            state.remainingTicks = NuclearExplosionEntity.SHOCKWAVE_SOUND_TICKS;
            triggerCameraShake(entity, distance);
        }
    }

    private static void triggerCameraShake(NuclearExplosionEntity entity, double distance) {
        double maxRadius = entity.getRadius() * NuclearExplosionEntity.SHOCKWAVE_MAX_RADIUS_MULTIPLIER;
        float distanceFactor = (float) Math.max(0.0D, Math.min(1.0D, 1.0D - distance / maxRadius));
        float strength = BASE_SHAKE_STRENGTH * (0.25F + 0.75F * distanceFactor);
        int duration = MIN_SHAKE_DURATION_TICKS + Math.round((MAX_SHAKE_DURATION_TICKS - MIN_SHAKE_DURATION_TICKS) * distanceFactor);
        CameraShakeManager.addShake(strength, duration);
    }

    private static void playArrivalSound(NuclearExplosionEntity entity, int remainingTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        RandomSource random = RandomSource.create(entity.getVisualSeed() + entity.getVisualAge() * 31L + remainingTicks * 17L);
        float pitch = 0.8F + random.nextFloat() * 0.4F;
        minecraft.level.playLocalSound(
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.AMBIENT,
                NuclearExplosionEntity.SHOCKWAVE_SOUND_VOLUME,
                pitch,
                false
        );
    }

    private static final class ShockwaveSoundState {
        private boolean triggered;
        private int remainingTicks;
    }
}
