package com.skyeshade.skyent.content.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyeshade.skyent.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

public class StreakParticleOptions implements ParticleOptions {
    public static final MapCodec<StreakParticleOptions> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Vec3.CODEC.fieldOf("dir").forGetter(StreakParticleOptions::dir),
                    Codec.FLOAT.fieldOf("length").forGetter(StreakParticleOptions::length),
                    Codec.FLOAT.fieldOf("width").forGetter(StreakParticleOptions::width),
                    Codec.INT.fieldOf("lifetime").forGetter(StreakParticleOptions::lifetime),
                    Codec.FLOAT.fieldOf("r").forGetter(StreakParticleOptions::r),
                    Codec.FLOAT.fieldOf("g").forGetter(StreakParticleOptions::g),
                    Codec.FLOAT.fieldOf("b").forGetter(StreakParticleOptions::b),
                    Codec.FLOAT.fieldOf("a").forGetter(StreakParticleOptions::a),
                    Codec.FLOAT.optionalFieldOf("endR", Float.NaN).forGetter(StreakParticleOptions::endR),
                    Codec.FLOAT.optionalFieldOf("endG", Float.NaN).forGetter(StreakParticleOptions::endG),
                    Codec.FLOAT.optionalFieldOf("endB", Float.NaN).forGetter(StreakParticleOptions::endB),
                    Codec.FLOAT.optionalFieldOf("roll", 0.0F).forGetter(StreakParticleOptions::rollDeg),
                    Codec.FLOAT.optionalFieldOf("faceBias", 1.0F).forGetter(StreakParticleOptions::faceBias),
                    Codec.FLOAT.optionalFieldOf("gravity", 0.0F).forGetter(StreakParticleOptions::gravity)
            ).apply(instance, (dir, length, width, lifetime, r, g, b, a, endR, endG, endB, roll, faceBias, gravity) ->
                    new StreakParticleOptions(
                            dir,
                            length,
                            width,
                            lifetime,
                            r,
                            g,
                            b,
                            a,
                            Float.isNaN(endR) ? r : endR,
                            Float.isNaN(endG) ? g : endG,
                            Float.isNaN(endB) ? b : endB,
                            roll,
                            faceBias,
                            gravity
                    )));

    public static final StreamCodec<RegistryFriendlyByteBuf, StreakParticleOptions> STREAM_CODEC =
            StreamCodec.of(
                    (buf, options) -> {
                        buf.writeDouble(options.dir.x);
                        buf.writeDouble(options.dir.y);
                        buf.writeDouble(options.dir.z);
                        buf.writeFloat(options.length);
                        buf.writeFloat(options.width);
                        buf.writeVarInt(options.lifetime);
                        buf.writeFloat(options.r);
                        buf.writeFloat(options.g);
                        buf.writeFloat(options.b);
                        buf.writeFloat(options.a);
                        buf.writeFloat(options.endR);
                        buf.writeFloat(options.endG);
                        buf.writeFloat(options.endB);
                        buf.writeFloat(options.rollDeg);
                        buf.writeFloat(options.faceBias);
                        buf.writeFloat(options.gravity);
                    },
                    buf -> new StreakParticleOptions(
                            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readVarInt(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat()
                    )
            );

    private final Vec3 dir;
    private final float length;
    private final float width;
    private final int lifetime;
    private final float r;
    private final float g;
    private final float b;
    private final float a;
    private final float endR;
    private final float endG;
    private final float endB;
    private final float rollDeg;
    private final float faceBias;
    private final float gravity;

    public StreakParticleOptions(Vec3 dir, float length, float width, int lifetime, float r, float g, float b, float a, float rollDeg, float faceBias, float gravity) {
        this(dir, length, width, lifetime, r, g, b, a, r, g, b, rollDeg, faceBias, gravity);
    }

    public StreakParticleOptions(Vec3 dir, float length, float width, int lifetime, float r, float g, float b, float a, float endR, float endG, float endB, float rollDeg, float faceBias, float gravity) {
        this.dir = dir;
        this.length = length;
        this.width = width;
        this.lifetime = lifetime;
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
        this.endR = endR;
        this.endG = endG;
        this.endB = endB;
        this.rollDeg = rollDeg;
        this.faceBias = faceBias;
        this.gravity = gravity;
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticles.SPARK_STREAK.get();
    }

    public Vec3 dir() {
        return dir;
    }

    public float length() {
        return length;
    }

    public float width() {
        return width;
    }

    public int lifetime() {
        return lifetime;
    }

    public float r() {
        return r;
    }

    public float g() {
        return g;
    }

    public float b() {
        return b;
    }

    public float a() {
        return a;
    }

    public float endR() {
        return endR;
    }

    public float endG() {
        return endG;
    }

    public float endB() {
        return endB;
    }

    public float rollDeg() {
        return rollDeg;
    }

    public float faceBias() {
        return faceBias;
    }

    public float gravity() {
        return gravity;
    }
}
