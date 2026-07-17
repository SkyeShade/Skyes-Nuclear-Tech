package com.skyeshade.skyent.content.radiation;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

public final class ModDamageSources {
    public static final ResourceKey<DamageType> RADIATION = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "radiation")
    );
    public static final ResourceKey<DamageType> SHOCKWAVE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "shockwave")
    );
    public static final ResourceKey<DamageType> NUCLEAR_EXPLOSION = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "nuclear_explosion")
    );

    private ModDamageSources() {
    }

    public static DamageSource radiation(ServerLevel level) {
        return source(level, RADIATION);
    }

    public static DamageSource shockwave(ServerLevel level) {
        return source(level, SHOCKWAVE);
    }

    public static DamageSource nuclearExplosion(ServerLevel level) {
        return source(level, NUCLEAR_EXPLOSION);
    }

    private static DamageSource source(ServerLevel level, ResourceKey<DamageType> key) {
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(key);
        return new DamageSource(holder);
    }

    public static boolean isRadiation(DamageSource source) {
        return source.is(RADIATION);
    }
}
