package com.skyeshade.skyent.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public class NukeCloudParticle extends SimpleAnimatedParticle {
    private final SpriteSet sprites;
    private final float baseSize;

    protected NukeCloudParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, sprites, 0.0F);
        this.sprites = sprites;
        double velocity = Math.sqrt(xd * xd + yd * yd + zd * zd);
        lifetime = Mth.clamp((int) (60.0D + velocity * 260.0D + random.nextInt(30)), 60, 180);
        baseSize = (float) Mth.clamp(2.0D + velocity * 12.0D + random.nextFloat() * 1.5D, 2.0D, 8.0D);
        quadSize = baseSize;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        gravity = -0.01F;
        friction = 0.92F;
        setSpriteFromAge(sprites);
        updateColor(0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        float progress = Mth.clamp(age / (float) lifetime, 0.0F, 1.0F);
        updateColor(progress);
        setSpriteFromAge(sprites);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float progress = Mth.clamp((age + partialTick) / (float) lifetime, 0.0F, 1.0F);
        return baseSize * Mth.lerp(progress, 0.45F, 1.0F);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    private void updateColor(float progress) {
        Color color;
        if (progress < 0.25F) {
            color = lerpColor(progress / 0.25F, new Color(1.0F, 0.95F, 0.15F), new Color(1.0F, 0.45F, 0.05F));
        } else if (progress < 0.55F) {
            color = lerpColor((progress - 0.25F) / 0.3F, new Color(1.0F, 0.45F, 0.05F), new Color(0.45F, 0.16F, 0.08F));
        } else {
            color = lerpColor((progress - 0.55F) / 0.45F, new Color(0.45F, 0.16F, 0.08F), new Color(0.05F, 0.05F, 0.05F));
        }

        rCol = color.r;
        gCol = color.g;
        bCol = color.b;
        alpha = progress > 0.72F ? Mth.lerp((progress - 0.72F) / 0.28F, 0.92F, 0.0F) : 0.92F;
    }

    private static Color lerpColor(float amount, Color from, Color to) {
        float clamped = Mth.clamp(amount, 0.0F, 1.0F);
        return new Color(
                Mth.lerp(clamped, from.r, to.r),
                Mth.lerp(clamped, from.g, to.g),
                Mth.lerp(clamped, from.b, to.b)
        );
    }

    private record Color(float r, float g, float b) {
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType particleType, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new NukeCloudParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
