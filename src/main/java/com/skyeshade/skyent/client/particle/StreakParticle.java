package com.skyeshade.skyent.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.skyeshade.skyent.content.particle.StreakParticleOptions;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class StreakParticle extends TextureSheetParticle {
    private final Vec3 forward;
    private final float length;
    private final float halfWidth;
    private final float r;
    private final float g;
    private final float b;
    private final float a;
    private final float endR;
    private final float endG;
    private final float endB;
    private final float rollRad;
    private final float faceBias;

    public StreakParticle(StreakParticleOptions options, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        pickSprite(sprites);
        Vec3 dir = options.dir();
        forward = dir.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : dir.normalize();
        length = options.length();
        halfWidth = options.width() * 0.5F;
        lifetime = Math.max(1, options.lifetime());
        r = options.r();
        g = options.g();
        b = options.b();
        a = options.a();
        endR = options.endR();
        endG = options.endG();
        endB = options.endB();
        rollRad = (float) Math.toRadians(options.rollDeg());
        faceBias = Mth.clamp(options.faceBias(), 0.0F, 1.0F);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        gravity = options.gravity();
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float partialTick) {
        double ix = Mth.lerp(partialTick, xo, x);
        double iy = Mth.lerp(partialTick, yo, y);
        double iz = Mth.lerp(partialTick, zo, z);
        Vec3 cameraRelative = new Vec3(ix, iy, iz).subtract(camera.getPosition());

        float ageProgress = (age + partialTick) / (float) lifetime;
        if (ageProgress >= 1.0F) {
            return;
        }

        float tail = ageProgress * ageProgress * length;
        float tip = length;
        if (tail >= tip) {
            return;
        }

        Vec3 f = forward;
        Vec3 worldUp = Math.abs(f.y) > 0.99D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right0 = f.cross(worldUp).normalize();
        Vec3 up0 = right0.cross(f).normalize();
        Vec3 rightR = rotateAroundAxis(right0, f, rollRad);
        Vec3 upR = rotateAroundAxis(up0, f, rollRad);

        Vec3 toCamera = camera.getPosition().subtract(ix, iy, iz);
        Vec3 normal = toCamera.subtract(f.scale(toCamera.dot(f)));
        normal = normal.lengthSqr() >= 1.0E-8D ? normal.normalize() : rightR;

        double compX = normal.dot(rightR);
        double compY = normal.dot(upR);
        float thetaCam = (float) Math.atan2(compX, compY);
        float thetaAdjusted = faceBias * thetaCam;
        Vec3 right = rotateAroundAxis(rightR, f, thetaAdjusted);

        Vec3 tailPos = cameraRelative.add(f.scale(tail));
        Vec3 tipPos = cameraRelative.add(f.scale(tip));
        Vec3 rightOffset = right.scale(halfWidth);
        Vec3 v0 = tailPos.subtract(rightOffset);
        Vec3 v1 = tailPos.add(rightOffset);
        Vec3 v2 = tipPos.add(rightOffset);
        Vec3 v3 = tipPos.subtract(rightOffset);

        float tr = Mth.lerp(ageProgress, r, endR);
        float tg = Mth.lerp(ageProgress, g, endG);
        float tb = Mth.lerp(ageProgress, b, endB);
        int cr = (int) (Mth.clamp(tr, 0.0F, 1.0F) * 255.0F);
        int cg = (int) (Mth.clamp(tg, 0.0F, 1.0F) * 255.0F);
        int cb = (int) (Mth.clamp(tb, 0.0F, 1.0F) * 255.0F);
        int ca = (int) (Mth.clamp(a, 0.0F, 1.0F) * 255.0F);
        int light = getLightColor(partialTick);

        vertexConsumer.addVertex((float) v0.x, (float) v0.y, (float) v0.z).setUv(getU0(), getV1()).setColor(cr, cg, cb, ca).setLight(light);
        vertexConsumer.addVertex((float) v1.x, (float) v1.y, (float) v1.z).setUv(getU1(), getV1()).setColor(cr, cg, cb, ca).setLight(light);
        vertexConsumer.addVertex((float) v2.x, (float) v2.y, (float) v2.z).setUv(getU1(), getV0()).setColor(cr, cg, cb, ca).setLight(light);
        vertexConsumer.addVertex((float) v3.x, (float) v3.y, (float) v3.z).setUv(getU0(), getV0()).setColor(cr, cg, cb, ca).setLight(light);
    }

    private static Vec3 rotateAroundAxis(Vec3 vector, Vec3 axis, float angleRad) {
        Vec3 k = axis.normalize();
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        double dot = vector.dot(k);
        return vector.scale(cos).add(k.cross(vector).scale(sin)).add(k.scale(dot * (1.0D - cos)));
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Factory implements ParticleProvider<StreakParticleOptions> {
        private final SpriteSet sprites;

        public Factory(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(StreakParticleOptions options, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new StreakParticle(options, level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
