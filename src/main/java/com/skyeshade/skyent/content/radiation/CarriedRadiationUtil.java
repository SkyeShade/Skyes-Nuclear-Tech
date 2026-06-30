package com.skyeshade.skyent.content.radiation;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class CarriedRadiationUtil {
    public static final double MAX_CARRIED_RADIATION_RANGE = 128.0D;
    private static final double MIN_CARRIED_ENVIRONMENTAL_RAY_STRENGTH = 250.0D;

    private static final int BASE_CORIUM_CARRIED_RAYS = 12;
    private static final int MAX_CARRIED_ENVIRONMENTAL_RAYS_PER_BATCH = 64;

    private static final int BASE_CORIUM_CONVERSIONS = 8;
    private static final int MAX_CARRIED_ENVIRONMENTAL_CONVERSIONS_PER_BATCH = 64;
    private static final boolean DEBUG_CARRIED_ENVIRONMENTAL_RAYS = false;

    private CarriedRadiationUtil() {
    }

    public static double carriedRadiationStrength(LivingEntity entity) {
        return RadiationItemValues.calculateInventoryRadiation(entity);
    }

    public static int carriedRadiationRange(double strength) {
        if (strength < 1.0D) {
            return 0;
        }

        return Mth.clamp((int) (8.0D + Math.sqrt(strength) * 4.0D), 8, (int) MAX_CARRIED_RADIATION_RANGE);
    }

    public static int carriedEnvironmentalRayCount(double strength) {
        if (strength < MIN_CARRIED_ENVIRONMENTAL_RAY_STRENGTH) {
            return 0;
        }

        double coriumEquivalent = carriedCoriumEquivalent(strength);

        if (coriumEquivalent < 1.0D) {
            return Mth.clamp((int) Math.ceil(coriumEquivalent * BASE_CORIUM_CARRIED_RAYS), 1, BASE_CORIUM_CARRIED_RAYS);
        }

        return Mth.clamp(
                (int) Math.round(coriumEquivalent * BASE_CORIUM_CARRIED_RAYS),
                BASE_CORIUM_CARRIED_RAYS,
                MAX_CARRIED_ENVIRONMENTAL_RAYS_PER_BATCH
        );
    }
    public static double carriedCoriumEquivalent(double strength) {
        return Math.max(0.0D, strength / RadiationBlockProfiles.coriumStrength());
    }
    public static int carriedEnvironmentalRayRange(double strength) {
        if (strength < MIN_CARRIED_ENVIRONMENTAL_RAY_STRENGTH) {
            return 0;
        }

        double coriumEquivalent = carriedCoriumEquivalent(strength);
        int coriumRange = RadiationBlockProfiles.coriumEnvironmentalRange();

        if (coriumEquivalent < 1.0D) {
            return Mth.clamp(
                    (int) Math.ceil(coriumRange * Math.sqrt(coriumEquivalent)),
                    4,
                    coriumRange
            );
        }

        return Mth.clamp(
                (int) Math.round(coriumRange * Math.sqrt(coriumEquivalent)),
                coriumRange,
                128
        );
    }
    public static int carriedEnvironmentalConversionCap(double strength, int rays) {
        if (strength < MIN_CARRIED_ENVIRONMENTAL_RAY_STRENGTH) {
            return 0;
        }

        double coriumEquivalent = carriedCoriumEquivalent(strength);

        if (coriumEquivalent < 1.0D) {
            return Mth.clamp((int) Math.ceil(coriumEquivalent * BASE_CORIUM_CONVERSIONS), 1, BASE_CORIUM_CONVERSIONS);
        }

        return Mth.clamp(
                (int) Math.round(coriumEquivalent * BASE_CORIUM_CONVERSIONS),
                BASE_CORIUM_CONVERSIONS,
                Math.min(rays, MAX_CARRIED_ENVIRONMENTAL_CONVERSIONS_PER_BATCH)
        );
    }
    public static void emitEnvironmentalRays(ServerLevel level, LivingEntity entity, double strength) {
        int rays = carriedEnvironmentalRayCount(strength);
        int range = carriedEnvironmentalRayRange(strength);
        if (rays <= 0 || range <= 0) {
            return;
        }

        Vec3 origin = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        double effectiveStrength = strength;
        if (DEBUG_CARRIED_ENVIRONMENTAL_RAYS) {
            SkyesNuclearTech.LOGGER.info(
                    "Carried environmental rays: entity={} pos={} strength={} effectiveStrength={} rays={} range={}",
                    entity.getType().builtInRegistryHolder().key().location(),
                    origin,
                    strength,
                    effectiveStrength,
                    rays,
                    range
            );
        }

        int conversionCap = carriedEnvironmentalConversionCap(strength, rays);

        RadiationUtil.applyFullEnvironmentalRadiation(
                level,
                origin,
                effectiveStrength,
                range,
                rays,
                conversionCap,
                entity.getRandom()
        );
    }
}
