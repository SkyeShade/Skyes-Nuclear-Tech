package com.skyeshade.skyent.client.effect;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class CameraShakeManager {
    private static final float FREQUENCY = 12.0F;
    private static float strength;
    private static int ticks;
    private static int maxTicks;
    private static Vec3 impulse = Vec3.ZERO;

    private CameraShakeManager() {
    }

    public static void addShake(float amount, int duration) {
        strength = Math.max(strength, amount);
        if (duration > ticks) {
            ticks = duration;
            maxTicks = duration;
        }
    }

    public static void addDirectionalShake(Vec3 direction, float amount, int duration) {
        addShake(amount, duration);
        if (direction.lengthSqr() > 1.0E-6D) {
            impulse = impulse.add(direction.normalize().scale(amount));
        }
    }

    public static void tick() {
        if (ticks > 0) {
            ticks--;
        }
        if (ticks <= 0) {
            strength = 0.0F;
            maxTicks = 0;
            impulse = Vec3.ZERO;
        }
    }

    public static Vec2 tickShake() {
        if (ticks <= 0 || maxTicks <= 0) {
            return Vec2.ZERO;
        }

        float decay = ticks / (float) maxTicks;
        float scaledStrength = strength * decay;
        double time = System.nanoTime() * 0.000000002D * FREQUENCY;

        float yaw = noise((float) time, 0.0F) * scaledStrength * 1.3F
                + noise((float) time * 4.0F, 10.0F) * scaledStrength * 0.35F;
        float pitch = noise(0.0F, (float) time) * scaledStrength
                + noise(10.0F, (float) time * 4.0F) * scaledStrength * 0.35F;

        yaw += (float) impulse.x * scaledStrength * 0.4F;
        pitch += (float) impulse.y * scaledStrength * 0.4F;
        return new Vec2(yaw, pitch);
    }

    public static Vec3 tickOffset() {
        if (ticks <= 0 || maxTicks <= 0) {
            return Vec3.ZERO;
        }

        float decay = ticks / (float) maxTicks;
        float scaledStrength = strength * decay * 0.05F;
        double time = System.nanoTime() * 0.000000002D * FREQUENCY;

        float x = noise((float) time, 5.0F) * scaledStrength
                + noise((float) time * 4.0F, 12.0F) * scaledStrength * 0.3F;
        float y = noise(5.0F, (float) time) * scaledStrength
                + noise(12.0F, (float) time * 4.0F) * scaledStrength * 0.3F;
        float z = noise((float) time, 20.0F) * scaledStrength * 0.4F;

        return new Vec3(x, y, z).add(impulse.scale(scaledStrength * 0.3F));
    }

    private static float noise(float x, float y) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        float xf = x - xi;
        float yf = y - yi;

        float v00 = hash(xi, yi);
        float v10 = hash(xi + 1, yi);
        float v01 = hash(xi, yi + 1);
        float v11 = hash(xi + 1, yi + 1);

        float u = smooth(xf);
        float v = smooth(yf);
        float x1 = Mth.lerp(u, v00, v10);
        float x2 = Mth.lerp(u, v01, v11);
        return Mth.lerp(v, x1, x2);
    }

    private static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static float hash(int x, int y) {
        int hash = x * 374761393 + y * 668265263;
        hash = (hash ^ hash >> 13) * 1274126177;
        return ((hash ^ hash >> 16) & 0x7fffffff) / (float) Integer.MAX_VALUE * 2.0F - 1.0F;
    }
}
