package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.particle.StreakParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(
            BuiltInRegistries.PARTICLE_TYPE,
            SkyesNuclearTech.MOD_ID
    );

    public static final DeferredHolder<ParticleType<?>, ParticleType<StreakParticleOptions>> SPARK_STREAK =
            PARTICLES.register("spark_streak", () -> new ParticleType<StreakParticleOptions>(false) {
                @Override
                public com.mojang.serialization.MapCodec<StreakParticleOptions> codec() {
                    return StreakParticleOptions.CODEC;
                }

                @Override
                public net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, StreakParticleOptions> streamCodec() {
                    return StreakParticleOptions.STREAM_CODEC;
                }
            });

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> NUKE_CLOUD =
            PARTICLES.register("nuke_cloud", () -> new SimpleParticleType(false));

    private ModParticles() {
    }

    public static void register(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }
}
