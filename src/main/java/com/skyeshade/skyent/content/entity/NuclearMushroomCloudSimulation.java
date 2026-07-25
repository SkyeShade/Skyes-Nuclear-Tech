package com.skyeshade.skyent.content.entity;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class NuclearMushroomCloudSimulation {
    public static final int MAX_MUSHROOM_CLOUDLETS = 12_000;
    private static final boolean DEBUG_MUSHROOM = false;
    private static final int MAX_AGE_TICKS = NuclearExplosionEntity.ENTITY_LIFETIME_TICKS;
    private static final int TORUS_SPAWN_TICKS = 2_000;
    private static final int TORUS_CLOUDLETS_PER_TICK = 16;
    private static final int SECONDARY_TORUS_SPAWN_TICKS = TORUS_SPAWN_TICKS;
    private static final int SECONDARY_TORUS_CLOUDLETS_PER_TICK = 1;
    private static final int STEM_EXTRA_SPAWN_TICKS = 1400;
    private static final int STEM_SPAWN_TICKS = TORUS_SPAWN_TICKS + STEM_EXTRA_SPAWN_TICKS;
    private static final int STEM_CLOUDLETS_PER_TICK = 2;
    private static final int TORUS_GROW_TICKS = 2_400;
    private static final int TORUS_RISE_TICKS = TORUS_GROW_TICKS;
    private static final double CLOUD_GROWTH_SPEED_MULTIPLIER = 2.0D;
    private static final int TORUS_FADE_START_TICKS = 200;
    private static final int PARTICLE_HOT_COOL_TICKS = 200;
    private static final int STEM_HOT_COOL_TICKS = PARTICLE_HOT_COOL_TICKS / 2;
    private static final int STEM_LIFETIME_MIN_TICKS = 450;
    private static final int STEM_LIFETIME_RANDOM_TICKS = 100;
    private static final int STEM_BASE_SIZE_BOOST_FADE_TICKS = 60;
    private static final float STEM_BASE_SIZE_BOOST = 2.0F;
    private static final double STEM_BASE_DEPTH_BELOW_EXPLOSION = 50.0D;
    private static final int INITIAL_TORUS_FILL_COUNT = 1800;
    private static final boolean ENABLE_INITIAL_TORUS_FILL = true;
    private static final float CLOUDLET_SIZE_MULTIPLIER = 0.60F;
    private static final double TORUS_ROTATION_TICKS = 80.0D;
    private static final double TORUS_VELOCITY_RESPONSE = 0.24D;
    private static final double TORUS_SOURCE_ANGLE = -Math.PI * 0.75D;
    private static final double TORUS_SOURCE_ANGLE_SPREAD = 0.45D;
    private static final double SECONDARY_TORUS_SOURCE_ANGLE_SPREAD = 0.55D;
    private static final int HEAD_RANDOMNESS_FADE_TICKS = 260;
    private static final double HEAD_INITIAL_POSITION_JITTER = 1.85D;
    private static final double HEAD_FINAL_POSITION_JITTER = 0.18D;
    private static final double HEAD_INITIAL_VELOCITY_JITTER = 0.18D;
    private static final double HEAD_FINAL_VELOCITY_JITTER = 0.02D;
    private static final double HEAD_INITIAL_SHAPE_CORRECTION_MULTIPLIER = 0.35D;
    private static final double HEAD_FINAL_SHAPE_CORRECTION_MULTIPLIER = 1.0D;
    private static final int AIR_RING_LOWER_COUNT = 360;
    private static final int AIR_RING_UPPER_COUNT = 260;
    private static final int AIR_RING_LOWER_DELAY_TICKS = 20;
    private static final int AIR_RING_UPPER_DELAY_TICKS = 60;
    private static final int AIR_RING_LOWER_LIFETIME = 320;
    private static final int AIR_RING_UPPER_LIFETIME = 260;
    private static final double AIR_RING_HEIGHT_EXTRA_Y = 40.0D;
    private static final double TORUS_INITIAL_MAJOR_RADIUS = 8.0D;
    private static final double TORUS_FINAL_MAJOR_RADIUS = 33.0D;
    private static final double TORUS_INITIAL_MINOR_RADIUS = 5.0D;
    private static final double TORUS_FINAL_MINOR_RADIUS = 28.0D;
    private static final double TORUS_INITIAL_CENTER_Y = 10.0D;
    private static final double TORUS_FINAL_CENTER_Y = 140.0D;
    private static final double TORUS_INITIAL_HORIZONTAL_COMPRESSION = 0.75D;
    private static final double TORUS_FINAL_HORIZONTAL_COMPRESSION = 1.0D;
    private static final double SECONDARY_TORUS_FINAL_WIDTH_SCALE = 0.75D;
    private static final double SECONDARY_TORUS_THICKNESS_SCALE = 0.119D;
    private static final double SECONDARY_TORUS_RING_SCALE = 0.696D;
    private static final boolean ENABLE_INITIAL_CHAOS_SPHERE = true;
    private static final int INITIAL_CHAOS_SPHERE_COUNT = 650;
    private static final double INITIAL_CHAOS_SPHERE_RADIUS = 24.0D;
    private static final double INITIAL_CHAOS_SPHERE_RADIUS_SCALE = 1.25D;
    private static final int INITIAL_CHAOS_SPHERE_LIFETIME_MIN_TICKS = 120;
    private static final int INITIAL_CHAOS_SPHERE_LIFETIME_RANDOM_TICKS = 120;
    private static final double INITIAL_CHAOS_SPHERE_INWARD_VELOCITY = 0.03D;
    private static final double INITIAL_CHAOS_SPHERE_UPWARD_VELOCITY = 0.04D;
    private static final double INITIAL_CHAOS_SPHERE_UPWARD_VELOCITY_RANDOM = 0.03D;
    private static final double CRATER_SMOKE_RADIUS_FRACTION = 0.75D;
    private static final int CRATER_SMOKE_SPAWN_TICKS = MAX_AGE_TICKS / 2;
    private static final int CRATER_SMOKE_CLOUDLETS_PER_TICK = 8;
    private static final int CRATER_SMOKE_SPAWN_ATTEMPTS_PER_CLOUDLET = 8;
    private static final int CRATER_SMOKE_LIFETIME_MIN_TICKS = 180;
    private static final int CRATER_SMOKE_LIFETIME_RANDOM_TICKS = 80;
    private static final double CRATER_SMOKE_SURFACE_OFFSET_MIN = 0.5D;
    private static final double CRATER_SMOKE_SURFACE_OFFSET_MAX = 3.0D;
    private static final double CRATER_SMOKE_UPWARD_SPEED_MIN = 0.025D;
    private static final double CRATER_SMOKE_UPWARD_SPEED_MAX = 0.085D;
    private static final double CRATER_SMOKE_HORIZONTAL_JITTER_SPEED = 0.018D;
    private static final double CRATER_SMOKE_MIN_EDGE_SIZE_SCALE = 0.125D;
    private static final double CRATER_SMOKE_MIN_EDGE_SPEED_SCALE = 0.20D;
    private static final double CRATER_SMOKE_DISTANCE_SCALE_POWER = 1.4D;
    private static final TagKey<Block> CRATER_SMOKE_SPAWN_BLOCKS = BlockTags.create(ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "vitrified_stones"
    ));
    private static final double CLOUD_FINAL_HEIGHT_SCALE = 1.5D;
    private static final double CLOUD_START_SCALE = 1.5D;
    private static final double CLOUD_END_SCALE = 1.0D;
    private static final double CLOUD_BASELINE_RADIUS = 100.0D;
    private static final double CLOUD_RADIUS_SCALE_EXPONENT = 1.28D;
    private static final double CLOUD_VISUAL_RADIUS_ANCHOR = 20.0D;
    private static final double CLOUD_VISUAL_RADIUS_TARGET_AT_100 = 50.0D;
    private static final double CLOUD_MIN_VISUAL_SCALE = 0.10D;
    private static final double CLOUD_MAX_VISUAL_SCALE = 4.0D;
    private static final double CLOUD_MIN_PARTICLE_SCALE = 0.12D;

    private final List<MushroomCloudlet> cloudlets = new ArrayList<>();
    private final long seed;
    private final float visualScale;
    private final double particleScale;
    private final float nukeRadius;
    private final double visualCloudRadius;
    private final double rawRadiusScale;
    private int age;
    private double groundY = Double.NaN;
    private boolean groundYInitialized;
    private double majorRadius;
    private double minorRadius;
    private double horizontalMinorRadius;
    private double torusHorizontalCompression = TORUS_INITIAL_HORIZONTAL_COMPRESSION;
    private double torusCenterY;
    private double secondaryMajorRadius;
    private double secondaryMinorRadius;
    private double secondaryCenterY;
    private double cloudScale = CLOUD_START_SCALE;
    private double torusScale = 1.0D;
    private double heat = 1.0D;
    private double stemBottomY;
    private double stemTopY;
    private double stemHeight = 1.0D;
    private double scaledStemDepth;
    private int sourceSpawnCountThisTick;
    private double sourceSpawnRadialOffsetSum;
    private double sourceSpawnVerticalOffsetSum;
    private boolean spawnedLowerAirRing;
    private boolean spawnedUpperAirRing;
    private double lowerAirRingSpawnY = Double.NaN;
    private double upperAirRingSpawnY = Double.NaN;
    private double initialChaosSphereRadius = Double.NaN;
    private double craterSmokeRadius;
    private int craterSmokeSpawnedThisTick;
    private int craterSmokeSpawnAttemptsThisTick;
    private int craterSmokeSkippedNoVitrifiedThisTick;
    private int craterSmokeSkippedChunkUnavailableThisTick;
    private double craterSmokeSizeScaleMinThisTick;
    private double craterSmokeSizeScaleMaxThisTick;
    private double craterSmokeSpeedScaleMinThisTick;
    private double craterSmokeSpeedScaleMaxThisTick;
    private boolean filledInitialTorus;
    private boolean spawnedInitialChaosSphere;

    public NuclearMushroomCloudSimulation(long seed, float radius) {
        this.seed = seed;
        this.nukeRadius = radius;
        this.visualCloudRadius = getVisualCloudRadius(radius);
        this.craterSmokeRadius = Math.max(0.0D, radius * CRATER_SMOKE_RADIUS_FRACTION);
        double radiusScale = Math.max(0.01D, visualCloudRadius / CLOUD_BASELINE_RADIUS);
        this.rawRadiusScale = radiusScale;
        double nonlinearScale = Math.pow(radiusScale, CLOUD_RADIUS_SCALE_EXPONENT);
        this.visualScale = (float) Mth.clamp(nonlinearScale, CLOUD_MIN_VISUAL_SCALE, CLOUD_MAX_VISUAL_SCALE);
        this.particleScale = Mth.clamp(nonlinearScale, CLOUD_MIN_PARTICLE_SCALE, 1.0D);
        this.majorRadius = TORUS_INITIAL_MAJOR_RADIUS * visualScale;
        this.minorRadius = TORUS_INITIAL_MINOR_RADIUS * visualScale;
        this.horizontalMinorRadius = this.minorRadius * torusHorizontalCompression;
        this.torusCenterY = TORUS_INITIAL_CENTER_Y * visualScale;
    }

    private static double getVisualCloudRadius(double actualRadius) {
        double radius = Math.max(0.0D, actualRadius);
        if (radius <= CLOUD_VISUAL_RADIUS_ANCHOR) {
            return radius;
        }
        double sqrtAnchor = Math.sqrt(CLOUD_VISUAL_RADIUS_ANCHOR);
        double sqrtTarget = Math.sqrt(CLOUD_BASELINE_RADIUS);
        double scaleFactor = (CLOUD_VISUAL_RADIUS_TARGET_AT_100 - CLOUD_VISUAL_RADIUS_ANCHOR) / (sqrtTarget - sqrtAnchor);
        return CLOUD_VISUAL_RADIUS_ANCHOR + scaleFactor * (Math.sqrt(radius) - sqrtAnchor);
    }

    public void tick(Level level, Vec3 entityPosition) {
        updateGroundHeight(level, entityPosition);
        updateTorusField();
        spawnInitialChaosSphereIfNeeded();
        fillInitialTorusIfNeeded();
        spawnAirRingsIfNeeded();
        spawnCloudlets(level, entityPosition);
        tickCloudlets();
        trimOldestIfNeeded();
        age++;
    }

    public void setKnownGroundYRelative(double groundYRelative) {
        if (!Double.isFinite(groundYRelative)) {
            return;
        }
        groundY = groundYRelative;
        groundYInitialized = true;
    }

    public void skipToAge(Level level, Vec3 entityPosition, int targetAge) {
        int clampedTargetAge = Mth.clamp(targetAge, 0, MAX_AGE_TICKS);
        if (clampedTargetAge <= age) {
            return;
        }

        updateGroundHeight(level, entityPosition);
        int replayTicks = Math.min(clampedTargetAge - age, 200);
        for (int index = 0; index < replayTicks; index++) {
            tick(level, entityPosition);
        }
        if (age >= clampedTargetAge) {
            return;
        }

        cloudlets.clear();
        age = clampedTargetAge;
        updateTorusField();
        filledInitialTorus = true;
        spawnedInitialChaosSphere = clampedTargetAge > 0;
        spawnedLowerAirRing = age >= AIR_RING_LOWER_DELAY_TICKS;
        spawnedUpperAirRing = age >= AIR_RING_UPPER_DELAY_TICKS;
    }

    public List<MushroomCloudlet> cloudlets() {
        return cloudlets;
    }

    private void updateGroundHeight(Level level, Vec3 entityPosition) {
        if (groundYInitialized) {
            return;
        }

        int x = Mth.floor(entityPosition.x);
        int z = Mth.floor(entityPosition.z);
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        groundY = Math.max(height - entityPosition.y, level.getMinBuildHeight() - entityPosition.y);
        groundYInitialized = true;
    }

    private void updateTorusField() {
        double growProgress = growthProgress(TORUS_GROW_TICKS);
        double riseProgress = growthProgress(TORUS_RISE_TICKS);
        torusHorizontalCompression = Mth.lerp(growProgress, TORUS_INITIAL_HORIZONTAL_COMPRESSION, TORUS_FINAL_HORIZONTAL_COMPRESSION);
        cloudScale = Mth.lerp(growProgress, CLOUD_START_SCALE, CLOUD_END_SCALE);
        double baseMajorRadius = Mth.lerp(growProgress, TORUS_INITIAL_MAJOR_RADIUS * visualScale, TORUS_FINAL_MAJOR_RADIUS * visualScale);
        double baseMinorRadius = Mth.lerp(growProgress, TORUS_INITIAL_MINOR_RADIUS * visualScale, TORUS_FINAL_MINOR_RADIUS * visualScale);
        majorRadius = baseMajorRadius * cloudScale * torusHorizontalCompression;
        minorRadius = baseMinorRadius * cloudScale;
        horizontalMinorRadius = minorRadius * torusHorizontalCompression;
        secondaryMinorRadius = minorRadius * SECONDARY_TORUS_THICKNESS_SCALE;
        double secondaryWidthScale = Mth.lerp(growProgress, 1.0D, SECONDARY_TORUS_FINAL_WIDTH_SCALE);
        secondaryMajorRadius = Math.max(1.0D, ((majorRadius + minorRadius) * SECONDARY_TORUS_RING_SCALE - secondaryMinorRadius) * secondaryWidthScale);
        torusScale = Math.max(1.0D, baseMajorRadius / Math.max(TORUS_INITIAL_MAJOR_RADIUS * visualScale, 1.0D));
        double baseY = Double.isFinite(groundY) ? groundY : 0.0D;
        double finalTorusCenterY = TORUS_FINAL_CENTER_Y * CLOUD_FINAL_HEIGHT_SCALE * visualScale;
        double baseTorusCenterY = Mth.lerp(riseProgress, TORUS_INITIAL_CENTER_Y * visualScale, finalTorusCenterY);
        torusCenterY = baseY + baseTorusCenterY * cloudScale;
        secondaryCenterY = torusCenterY - minorRadius * 2.25D;
        scaledStemDepth = STEM_BASE_DEPTH_BELOW_EXPLOSION * visualScale;
        stemBottomY = -scaledStemDepth;
        stemTopY = torusCenterY - minorRadius * 0.25D;
        stemHeight = Math.max(1.0D, stemTopY - stemBottomY);
        heat = Math.pow(1.0D - Mth.clamp(age / (double) MAX_AGE_TICKS, 0.0D, 1.0D), 0.8D);
        if (age > 600) {
            heat *= Mth.lerp(Mth.clamp((age - 600.0D) / 1_800.0D, 0.0D, 1.0D), 1.0D, 0.35D);
        }
        if (age > 2_400) {
            heat *= 0.35D;
        }
    }

    private double growthProgress(int ticks) {
        return smoothStep(Mth.clamp(age * CLOUD_GROWTH_SPEED_MULTIPLIER / (double) ticks, 0.0D, 1.0D));
    }

    private double headRandomnessProgress() {
        return smoothStep(Mth.clamp(age / (double) HEAD_RANDOMNESS_FADE_TICKS, 0.0D, 1.0D));
    }

    private double headPositionJitterScale() {
        return Mth.lerp(headRandomnessProgress(), HEAD_INITIAL_POSITION_JITTER, HEAD_FINAL_POSITION_JITTER) * visualScale;
    }

    private double headVelocityJitterScale() {
        return Mth.lerp(headRandomnessProgress(), HEAD_INITIAL_VELOCITY_JITTER, HEAD_FINAL_VELOCITY_JITTER) * visualScale;
    }

    private double headShapeCorrectionMultiplier() {
        return Mth.lerp(headRandomnessProgress(), HEAD_INITIAL_SHAPE_CORRECTION_MULTIPLIER, HEAD_FINAL_SHAPE_CORRECTION_MULTIPLIER);
    }

    private double headSourceSpread() {
        return Mth.lerp(headRandomnessProgress(), TORUS_SOURCE_ANGLE_SPREAD * 2.4D, TORUS_SOURCE_ANGLE_SPREAD);
    }

    private void spawnCloudlets(Level level, Vec3 entityPosition) {
        RandomSource random = RandomSource.create(seed + age * 104_729L);
        sourceSpawnCountThisTick = 0;
        sourceSpawnRadialOffsetSum = 0.0D;
        sourceSpawnVerticalOffsetSum = 0.0D;
        craterSmokeSpawnedThisTick = 0;
        craterSmokeSpawnAttemptsThisTick = 0;
        craterSmokeSkippedNoVitrifiedThisTick = 0;
        craterSmokeSkippedChunkUnavailableThisTick = 0;
        craterSmokeSizeScaleMinThisTick = Double.POSITIVE_INFINITY;
        craterSmokeSizeScaleMaxThisTick = 0.0D;
        craterSmokeSpeedScaleMinThisTick = Double.POSITIVE_INFINITY;
        craterSmokeSpeedScaleMaxThisTick = 0.0D;
        if (age < CRATER_SMOKE_SPAWN_TICKS) {
            for (int index = 0; index < scaledCount(CRATER_SMOKE_CLOUDLETS_PER_TICK); index++) {
                if (!spawnCraterSmokeCloudlet(level, entityPosition, random) && cloudlets.size() >= MAX_MUSHROOM_CLOUDLETS) {
                    return;
                }
            }
        }
        if (age < TORUS_SPAWN_TICKS) {
            for (int index = 0; index < scaledCount(TORUS_CLOUDLETS_PER_TICK); index++) {
                if (!makeRoomForCloudlet()) {
                    return;
                }
                spawnTorusCloudlet(random);
            }
        }
        if (age < SECONDARY_TORUS_SPAWN_TICKS) {
            for (int index = 0; index < scaledCount(SECONDARY_TORUS_CLOUDLETS_PER_TICK); index++) {
                if (!makeRoomForCloudlet()) {
                    return;
                }
                spawnSecondaryTorusCloudlet(random);
            }
        }
        if (age < STEM_SPAWN_TICKS) {
            for (int index = 0; index < scaledCount(STEM_CLOUDLETS_PER_TICK); index++) {
                if (!makeRoomForCloudlet()) {
                    return;
                }
                spawnStemCloudlet(random);
            }
        }
    }

    private boolean spawnCraterSmokeCloudlet(Level level, Vec3 entityPosition, RandomSource random) {
        double localX = 0.0D;
        double localZ = 0.0D;
        double surfaceY = Double.NaN;
        for (int attempt = 0; attempt < CRATER_SMOKE_SPAWN_ATTEMPTS_PER_CLOUDLET; attempt++) {
            craterSmokeSpawnAttemptsThisTick++;
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = Math.pow(random.nextDouble(), 0.65D) * craterSmokeRadius;
            localX = Math.cos(angle) * distance;
            localZ = Math.sin(angle) * distance;
            surfaceY = sampleCraterSmokeSurfaceY(level, entityPosition, localX, localZ);
            if (Double.isFinite(surfaceY)) {
                break;
            }
        }
        if (!Double.isFinite(surfaceY) || !makeRoomForCloudlet()) {
            return false;
        }
        double distance = Math.sqrt(localX * localX + localZ * localZ);
        double normalizedDistance = Mth.clamp(distance / Math.max(craterSmokeRadius, 1.0D), 0.0D, 1.0D);
        double distanceFalloff = Math.pow(normalizedDistance, CRATER_SMOKE_DISTANCE_SCALE_POWER);
        double craterSizeScale = Mth.lerp(distanceFalloff, 1.0D, CRATER_SMOKE_MIN_EDGE_SIZE_SCALE);
        double craterSpeedScale = Mth.lerp(distanceFalloff, 1.0D, CRATER_SMOKE_MIN_EDGE_SPEED_SCALE);
        craterSmokeSizeScaleMinThisTick = Math.min(craterSmokeSizeScaleMinThisTick, craterSizeScale);
        craterSmokeSizeScaleMaxThisTick = Math.max(craterSmokeSizeScaleMaxThisTick, craterSizeScale);
        craterSmokeSpeedScaleMinThisTick = Math.min(craterSmokeSpeedScaleMinThisTick, craterSpeedScale);
        craterSmokeSpeedScaleMaxThisTick = Math.max(craterSmokeSpeedScaleMaxThisTick, craterSpeedScale);
        double y = surfaceY + Mth.lerp(random.nextDouble(), CRATER_SMOKE_SURFACE_OFFSET_MIN, CRATER_SMOKE_SURFACE_OFFSET_MAX);
        double sizeScale = Math.sqrt(torusScale);
        float startSize = scaleCloudletSize((float) ((10.0D + random.nextDouble() * 8.0D) * visualScale * sizeScale * craterSizeScale));
        float growSize = scaleCloudletSize((float) ((14.0D + random.nextDouble() * 14.0D) * visualScale * sizeScale * craterSizeScale));
        double velocityX = jitter(random, CRATER_SMOKE_HORIZONTAL_JITTER_SPEED * visualScale) * craterSpeedScale;
        double velocityY = Mth.lerp(random.nextDouble(), CRATER_SMOKE_UPWARD_SPEED_MIN, CRATER_SMOKE_UPWARD_SPEED_MAX) * visualScale * craterSpeedScale;
        double velocityZ = jitter(random, CRATER_SMOKE_HORIZONTAL_JITTER_SPEED * visualScale) * craterSpeedScale;
        cloudlets.add(new MushroomCloudlet(
                MushroomCloudletType.CRATER_SMOKE,
                localX,
                y,
                localZ,
                CRATER_SMOKE_LIFETIME_MIN_TICKS + random.nextInt(CRATER_SMOKE_LIFETIME_RANDOM_TICKS + 1),
                startSize,
                growSize,
                random.nextLong(),
                false,
                1.0F,
                velocityX,
                velocityY,
                velocityZ
        ));
        craterSmokeSpawnedThisTick++;
        return true;
    }

    private double sampleCraterSmokeSurfaceY(Level level, Vec3 entityPosition, double localX, double localZ) {
        int blockX = Mth.floor(entityPosition.x + localX);
        int blockZ = Mth.floor(entityPosition.z + localZ);
        if (!level.hasChunk(blockX >> 4, blockZ >> 4)) {
            craterSmokeSkippedChunkUnavailableThisTick++;
            return Double.NaN;
        }
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        if (height <= level.getMinBuildHeight()) {
            craterSmokeSkippedNoVitrifiedThisTick++;
            return Double.NaN;
        }
        BlockPos surfacePos = new BlockPos(blockX, height - 1, blockZ);
        if (!level.getBlockState(surfacePos).is(CRATER_SMOKE_SPAWN_BLOCKS)) {
            craterSmokeSkippedNoVitrifiedThisTick++;
            return Double.NaN;
        }
        return height - entityPosition.y;
    }

    private void spawnInitialChaosSphereIfNeeded() {
        if (!ENABLE_INITIAL_CHAOS_SPHERE || spawnedInitialChaosSphere || age != 0) {
            return;
        }
        spawnedInitialChaosSphere = true;
        RandomSource random = RandomSource.create(seed ^ 0xD1B54A32D192ED03L);
        double sphereRadius = Math.max(
                INITIAL_CHAOS_SPHERE_RADIUS * visualScale,
                (majorRadius + horizontalMinorRadius) * 0.75D
        ) * INITIAL_CHAOS_SPHERE_RADIUS_SCALE;
        initialChaosSphereRadius = sphereRadius;
        for (int index = 0; index < scaledCount(INITIAL_CHAOS_SPHERE_COUNT); index++) {
            if (!makeRoomForCloudlet()) {
                return;
            }
            double u = random.nextDouble() * 2.0D - 1.0D;
            double theta = random.nextDouble() * Mth.TWO_PI;
            double horizontal = Math.sqrt(Math.max(0.0D, 1.0D - u * u));
            double distance = Math.cbrt(random.nextDouble()) * sphereRadius;
            double x = Math.cos(theta) * horizontal * distance;
            double y = torusCenterY + u * distance * 0.85D;
            double z = Math.sin(theta) * horizontal * distance;
            Vec3 radial = radial(x, z);
            double velocityJitter = headVelocityJitterScale() * 1.35D;
            double horizontalJitter = velocityJitter * 0.35D;
            double velocityX = -radial.x * INITIAL_CHAOS_SPHERE_INWARD_VELOCITY * visualScale + jitter(random, horizontalJitter);
            double velocityY = (INITIAL_CHAOS_SPHERE_UPWARD_VELOCITY
                    + random.nextDouble() * INITIAL_CHAOS_SPHERE_UPWARD_VELOCITY_RANDOM) * visualScale;
            double velocityZ = -radial.z * INITIAL_CHAOS_SPHERE_INWARD_VELOCITY * visualScale + jitter(random, horizontalJitter);
            float startSize = scaleCloudletSize((float) ((5.0D + random.nextDouble() * 6.0D) * visualScale));
            float growSize = scaleCloudletSize((float) ((8.0D + random.nextDouble() * 8.0D) * visualScale));
            cloudlets.add(new MushroomCloudlet(
                    MushroomCloudletType.INITIAL_FIREBALL_CHAOS,
                    x,
                    y,
                    z,
                    INITIAL_CHAOS_SPHERE_LIFETIME_MIN_TICKS + random.nextInt(INITIAL_CHAOS_SPHERE_LIFETIME_RANDOM_TICKS + 1),
                    startSize,
                    growSize,
                    random.nextLong(),
                    false,
                    1.0F,
                    velocityX,
                    velocityY,
                    velocityZ
            ));
        }
    }

    private void fillInitialTorusIfNeeded() {
        if (!ENABLE_INITIAL_TORUS_FILL || filledInitialTorus) {
            return;
        }
        filledInitialTorus = true;
        RandomSource random = RandomSource.create(seed ^ 0xBB67AE8584CAA73BL);
        for (int index = 0; index < scaledInitialTorusFillCount(); index++) {
            if (!makeRoomForCloudlet()) {
                return;
            }
            double angleAroundY = random.nextDouble() * Mth.TWO_PI;
            double angleAroundTube = random.nextDouble() * Mth.TWO_PI;
            double tubeRadiusScale = Math.sqrt(random.nextDouble());
            double radialOffset = Math.cos(angleAroundTube) * horizontalMinorRadius * tubeRadiusScale;
            double verticalOffset = Math.sin(angleAroundTube) * minorRadius * tubeRadiusScale;
            double radialDistance = Math.max(0.0D, majorRadius + radialOffset);
            double jitterScale = headPositionJitterScale() * 1.25D;
            double x = Math.cos(angleAroundY) * radialDistance + jitter(random, jitterScale);
            double z = Math.sin(angleAroundY) * radialDistance + jitter(random, jitterScale);
            double y = torusCenterY + verticalOffset + jitter(random, jitterScale * 0.75D);
            Vec3 radial = radial(x, z);
            Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);
            double normalizedRadial = radialOffset / Math.max(horizontalMinorRadius, 1.0E-5D);
            double normalizedVertical = verticalOffset / Math.max(minorRadius, 1.0E-5D);
            double normalizedTubeDistance = Math.max(Math.sqrt(normalizedRadial * normalizedRadial + normalizedVertical * normalizedVertical), 1.0E-5D);
            double tangentRadial = normalizedVertical / normalizedTubeDistance;
            double tangentVertical = -normalizedRadial / normalizedTubeDistance;
            double effectiveTubeRadius = Math.sqrt(Math.max(horizontalMinorRadius, 1.0E-5D) * Math.max(minorRadius, 1.0E-5D));
            double targetTubeSpeed = torusAngularSpeed() * Math.max(effectiveTubeRadius * normalizedTubeDistance, effectiveTubeRadius * 0.65D);
            double velocityJitter = headVelocityJitterScale() * 1.25D;
            double velocityX = radial.x * tangentRadial * targetTubeSpeed * 0.45D + tangent.x * jitter(random, 0.003D * visualScale) + jitter(random, velocityJitter);
            double velocityY = tangentVertical * targetTubeSpeed * 0.45D + jitter(random, 0.003D * visualScale) + jitter(random, velocityJitter * 0.65D);
            double velocityZ = radial.z * tangentRadial * targetTubeSpeed * 0.45D + tangent.z * jitter(random, 0.003D * visualScale) + jitter(random, velocityJitter);
            double sizeScale = Math.sqrt(torusScale);
            float startSize = scaleCloudletSize((float) ((5.0D + random.nextDouble() * 4.0D) * visualScale * sizeScale));
            float growSize = scaleCloudletSize((float) ((8.0D + random.nextDouble() * 8.0D) * visualScale * sizeScale));
            cloudlets.add(new MushroomCloudlet(
                    MushroomCloudletType.TORUS_FIREBALL,
                    x,
                    y,
                    z,
                    900 + random.nextInt(1_101),
                    startSize,
                    growSize,
                    random.nextLong(),
                    false,
                    1.0F,
                    velocityX,
                    velocityY,
                    velocityZ
            ));
        }
    }

    private void spawnAirRingsIfNeeded() {
        if (!spawnedLowerAirRing && age >= AIR_RING_LOWER_DELAY_TICKS) {
            spawnedLowerAirRing = true;
            RandomSource random = RandomSource.create(seed ^ 0x6A09E667F3BCC909L);
            double baseY = Double.isFinite(groundY) ? groundY : 0.0D;
            lowerAirRingSpawnY = baseY + 35.0D * visualScale + AIR_RING_HEIGHT_EXTRA_Y;
            spawnAirRing(random, scaledCount(AIR_RING_LOWER_COUNT), lowerAirRingSpawnY, 10.0D * visualScale, 180.0D * visualScale, AIR_RING_LOWER_LIFETIME, 1.35D * visualScale);
        }

        if (!spawnedUpperAirRing && age >= AIR_RING_UPPER_DELAY_TICKS) {
            spawnedUpperAirRing = true;
            RandomSource random = RandomSource.create(seed ^ 0xBB67AE8584CAA73BL);
            double baseY = Double.isFinite(groundY) ? groundY : 0.0D;
            upperAirRingSpawnY = baseY + 60.0D * visualScale + AIR_RING_HEIGHT_EXTRA_Y;
            spawnAirRing(random, scaledCount(AIR_RING_UPPER_COUNT), upperAirRingSpawnY, 6.0D * visualScale, 115.0D * visualScale, AIR_RING_UPPER_LIFETIME, 0.95D * visualScale);
        }
    }

    private void spawnAirRing(RandomSource random, int count, double y, double initialRadius, double finalRadius, int lifetime, double expansionSpeed) {
        for (int index = 0; index < count; index++) {
            if (!makeRoomForCloudlet()) {
                return;
            }
            double angle = random.nextDouble() * Mth.TWO_PI;
            double radius = initialRadius + jitter(random, 1.6D * visualScale);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Vec3 radial = radial(x, z);
            float startSize = scaleCloudletSize((float) ((1.5D + random.nextDouble() * 1.5D) * visualScale));
            float growSize = scaleCloudletSize((float) ((2.5D + random.nextDouble() * 2.5D) * visualScale));
            cloudlets.add(new MushroomCloudlet(
                    MushroomCloudletType.WHITE_AIR_RING,
                    x,
                    y + jitter(random, 1.2D * visualScale),
                    z,
                    lifetime + random.nextInt(41) - 20,
                    startSize,
                    growSize,
                    random.nextLong(),
                    false,
                    1.0F,
                    radial.x * expansionSpeed,
                    jitter(random, 0.012D * visualScale),
                    radial.z * expansionSpeed
            ));
        }
    }

    private void spawnTorusCloudlet(RandomSource random) {
        double angleAroundY = random.nextDouble() * Mth.TWO_PI;
        double angleAroundTube = TORUS_SOURCE_ANGLE + jitter(random, headSourceSpread());
        double tubeRadiusScale = Mth.lerp(random.nextDouble(), 0.35D, 1.0D);
        double radialOffset = Math.cos(angleAroundTube) * horizontalMinorRadius * tubeRadiusScale;
        double verticalOffset = Math.sin(angleAroundTube) * minorRadius * tubeRadiusScale;
        double radialDistance = Math.max(0.0D, majorRadius + radialOffset);
        double jitterScale = headPositionJitterScale();
        double x = Math.cos(angleAroundY) * radialDistance + jitter(random, jitterScale);
        double z = Math.sin(angleAroundY) * radialDistance + jitter(random, jitterScale);
        double y = torusCenterY + verticalOffset + jitter(random, jitterScale * 0.75D);
        Vec3 radial = radial(x, z);
        Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);
        double sizeScale = Math.sqrt(torusScale);
        float startSize = scaleCloudletSize((float) ((5.0D + random.nextDouble() * 4.0D) * visualScale * sizeScale));
        float growSize = scaleCloudletSize((float) ((8.0D + random.nextDouble() * 8.0D) * visualScale * sizeScale));
        double swirl = jitter(random, 0.006D * visualScale);
        double velocityJitter = headVelocityJitterScale();

        cloudlets.add(new MushroomCloudlet(
                MushroomCloudletType.TORUS_FIREBALL,
                x,
                y,
                z,
                900 + random.nextInt(1_101),
                startSize,
                growSize,
                random.nextLong(),
                false,
                1.0F,
                tangent.x * swirl + jitter(random, 0.002D * visualScale) + jitter(random, velocityJitter),
                (0.01D + random.nextDouble() * 0.025D) * visualScale + jitter(random, velocityJitter * 0.65D),
                tangent.z * swirl + jitter(random, 0.002D * visualScale) + jitter(random, velocityJitter)
        ));
        sourceSpawnCountThisTick++;
        sourceSpawnRadialOffsetSum += radialOffset;
        sourceSpawnVerticalOffsetSum += verticalOffset;
    }

    private void spawnSecondaryTorusCloudlet(RandomSource random) {
        double angleAroundY = random.nextDouble() * Mth.TWO_PI;
        double angleAroundTube = TORUS_SOURCE_ANGLE + jitter(random, SECONDARY_TORUS_SOURCE_ANGLE_SPREAD);
        double tubeRadius = secondaryMinorRadius * Mth.lerp(random.nextDouble(), 0.35D, 1.0D);
        double radialOffset = Math.cos(angleAroundTube) * tubeRadius;
        double verticalOffset = Math.sin(angleAroundTube) * tubeRadius;
        double radialDistance = Math.max(0.0D, secondaryMajorRadius + radialOffset);
        double x = Math.cos(angleAroundY) * radialDistance;
        double z = Math.sin(angleAroundY) * radialDistance;
        double y = secondaryCenterY + verticalOffset;
        Vec3 radial = radial(x, z);
        Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);
        double sizeScale = Math.sqrt(torusScale);
        float startSize = scaleCloudletSize((float) ((3.0D + random.nextDouble() * 3.0D) * visualScale * sizeScale));
        float growSize = scaleCloudletSize((float) ((5.0D + random.nextDouble() * 5.0D) * visualScale * sizeScale));
        double swirl = jitter(random, 0.005D * visualScale);
        boolean hotAccentParticle = random.nextDouble() < 0.22D * stemHotSpawnHeatFactor();

        cloudlets.add(new MushroomCloudlet(
                MushroomCloudletType.SECONDARY_TORUS,
                x,
                y,
                z,
                1_200 + random.nextInt(1_201),
                startSize,
                growSize,
                random.nextLong(),
                hotAccentParticle,
                1.0F,
                tangent.x * swirl + jitter(random, 0.002D * visualScale),
                (0.008D + random.nextDouble() * 0.018D) * visualScale,
                tangent.z * swirl + jitter(random, 0.002D * visualScale)
        ));
    }

    private void spawnStemCloudlet(RandomSource random) {
        double roll = random.nextDouble();
        double normalizedHeight;
        if (roll < 0.80D) {
            normalizedHeight = Math.pow(random.nextDouble(), 1.7D) * 0.32D;
        } else if (roll < 0.98D) {
            normalizedHeight = Mth.lerp(random.nextDouble(), 0.32D, 0.75D);
        } else {
            normalizedHeight = Mth.lerp(random.nextDouble(), 0.75D, 1.0D);
        }
        double y = Mth.lerp(normalizedHeight, stemBottomY, stemTopY);
        double radius = stemRadiusAt(normalizedHeight);
        double angle = random.nextDouble() * Mth.TWO_PI;
        double distance = normalizedHeight < 0.35D
                ? radius * Mth.lerp(random.nextDouble(), 0.45D, 1.0D)
                : Math.sqrt(random.nextDouble()) * radius;
        double x = Math.cos(angle) * distance;
        double z = Math.sin(angle) * distance;
        Vec3 radial = radial(x, z);
        Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);
        double sizeScale = Math.sqrt(torusScale);
        float startSize = scaleCloudletSize((float) ((5.0D + random.nextDouble() * 4.0D) * visualScale * sizeScale));
        float growSize = scaleCloudletSize((float) ((8.0D + random.nextDouble() * 8.0D) * visualScale * sizeScale));
        double swirl = jitter(random, 0.006D * visualScale);
        boolean hotStemParticle = random.nextDouble() < stemHotSpawnChance();
        float initialSizeBoost = stemBaseBoostFromHeight(normalizedHeight);

        cloudlets.add(new MushroomCloudlet(
                MushroomCloudletType.STEM,
                x,
                y,
                z,
                STEM_LIFETIME_MIN_TICKS + random.nextInt(STEM_LIFETIME_RANDOM_TICKS + 1),
                startSize,
                growSize,
                random.nextLong(),
                hotStemParticle,
                initialSizeBoost,
                tangent.x * swirl + jitter(random, 0.002D * visualScale),
                (0.018D + random.nextDouble() * 0.025D) * visualScale,
                tangent.z * swirl + jitter(random, 0.002D * visualScale)
        ));
    }

    private void tickCloudlets() {
        Iterator<MushroomCloudlet> iterator = cloudlets.iterator();
        while (iterator.hasNext()) {
            MushroomCloudlet cloudlet = iterator.next();
            cloudlet.tick(this);
            if (cloudlet.isExpired()) {
                iterator.remove();
            }
        }
    }

    private void trimOldestIfNeeded() {
        while (cloudlets.size() > MAX_MUSHROOM_CLOUDLETS) {
            removeOldestCloudlet();
        }
    }

    private boolean makeRoomForCloudlet() {
        if (cloudlets.size() < MAX_MUSHROOM_CLOUDLETS) {
            return true;
        }
        removeOldestCloudlet();
        return cloudlets.size() < MAX_MUSHROOM_CLOUDLETS;
    }

    private void removeOldestCloudlet() {
        if (cloudlets.isEmpty()) {
            return;
        }

        int oldestIndex = 0;
        int oldestAge = -1;
        for (int index = 0; index < cloudlets.size(); index++) {
            int cloudletAge = cloudlets.get(index).age;
            if (cloudletAge > oldestAge) {
                oldestAge = cloudletAge;
                oldestIndex = index;
            }
        }
        cloudlets.remove(oldestIndex);
    }

    private double globalAlpha() {
        int fadeStart = Math.min(TORUS_FADE_START_TICKS, MAX_AGE_TICKS - 1);
        if (age > fadeStart) {
            return 1.0D - (age - fadeStart) / (double) (MAX_AGE_TICKS - fadeStart);
        }
        return 1.0D;
    }

    private int scaledCount(int baseCount) {
        return Math.max(1, Mth.floor(baseCount * particleScale));
    }

    private int scaledInitialTorusFillCount() {
        return Math.max(150, Mth.floor(INITIAL_TORUS_FILL_COUNT * particleScale));
    }

    private int countCloudlets(MushroomCloudletType type) {
        int count = 0;
        for (MushroomCloudlet cloudlet : cloudlets) {
            if (cloudlet.type == type) {
                count++;
            }
        }
        return count;
    }

    private int countHotStemCloudlets() {
        int count = 0;
        for (MushroomCloudlet cloudlet : cloudlets) {
            if (cloudlet.type == MushroomCloudletType.STEM && cloudlet.hotStemParticle) {
                count++;
            }
        }
        return count;
    }

    private int countSmokeShade(float shadeVariant) {
        int count = 0;
        for (MushroomCloudlet cloudlet : cloudlets) {
            if (cloudlet.type != MushroomCloudletType.WHITE_AIR_RING && cloudlet.smokeShadeVariant == shadeVariant) {
                count++;
            }
        }
        return count;
    }

    private double stemHotSpawnChance() {
        return 0.16D * stemHotSpawnHeatFactor();
    }

    private double stemHotSpawnHeatFactor() {
        double progress = Mth.clamp(age / (MAX_AGE_TICKS * 0.5D), 0.0D, 1.0D);
        return Math.pow(1.0D - progress, 1.1D);
    }

    private static double torusAngularSpeed() {
        return Mth.TWO_PI / TORUS_ROTATION_TICKS;
    }

    private double targetTubeSpeedAtMinorRadius() {
        return torusAngularSpeed() * minorRadius;
    }

    private double maxTorusSpeed() {
        return 0.65D * visualScale;
    }

    private double stemRadiusAt(double normalizedHeight) {
        double h = Mth.clamp(normalizedHeight, 0.0D, 1.0D);
        double baseRadius = minorRadius * 1.35D;
        double midRadius = minorRadius * 0.42D;
        double topRadius = minorRadius * 0.16D;
        if (h < 0.30D) {
            return Mth.lerp(smoothStep(h / 0.30D), baseRadius, midRadius);
        }

        return Mth.lerp(smoothStep((h - 0.30D) / 0.70D), midRadius, topRadius);
    }

    private static double smoothStep(double amount) {
        double clamped = Mth.clamp(amount, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static float scaleCloudletSize(float size) {
        return size * CLOUDLET_SIZE_MULTIPLIER;
    }

    private static float stemBaseBoostFromHeight(double normalizedHeight) {
        if (normalizedHeight <= 0.35D) {
            return STEM_BASE_SIZE_BOOST;
        }
        if (normalizedHeight >= 0.65D) {
            return 1.0F;
        }
        double amount = smoothStep((normalizedHeight - 0.35D) / 0.30D);
        return (float) Mth.lerp(amount, STEM_BASE_SIZE_BOOST, 1.0D);
    }

    private static double jitter(RandomSource random, double amount) {
        return (random.nextDouble() * 2.0D - 1.0D) * amount;
    }

    private static float randomSmokeShadeVariant(long seed) {
        long mixed = seed ^ 0x5A17BEEFL;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        double roll = ((mixed >>> 11) & ((1L << 53) - 1)) * 0x1.0p-53;
        if (roll < 0.75D) {
            return 0.0F;
        }
        if (roll < 0.93D) {
            return 0.5F;
        }
        return 1.0F;
    }

    private static Vec3 radial(double x, double z) {
        double length = Math.sqrt(x * x + z * z);
        if (length < 1.0E-5D) {
            return Vec3.ZERO;
        }
        return new Vec3(x / length, 0.0D, z / length);
    }

    private enum MushroomCloudletType {
        INITIAL_FIREBALL_CHAOS,
        CRATER_SMOKE,
        TORUS_FIREBALL,
        STEM,
        SECONDARY_TORUS,
        WHITE_AIR_RING
    }

    public static final class MushroomCloudlet {
        private final MushroomCloudletType type;
        private final int lifetime;
        private final float startSize;
        private final float growSize;
        private final long seed;
        private final boolean hotStemParticle;
        private final float initialSizeBoost;
        private final float smokeShadeVariant;
        private double x;
        private double y;
        private double z;
        private double prevX;
        private double prevY;
        private double prevZ;
        private double velocityX;
        private double velocityY;
        private double velocityZ;
        private double radialOffset;
        private double verticalOffset;
        private double currentMinorRadius = 1.0D;
        private double stemNormalizedHeight;
        private double localHeat = 1.0D;
        private double localStemHotHeat = 1.0D;
        private double globalAlpha = 1.0D;
        private int age;

        private MushroomCloudlet(
                MushroomCloudletType type,
                double x,
                double y,
                double z,
                int lifetime,
                float startSize,
                float growSize,
                long seed,
                boolean hotStemParticle,
                float initialSizeBoost,
                double velocityX,
                double velocityY,
                double velocityZ
        ) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.prevX = x;
            this.prevY = y;
            this.prevZ = z;
            this.lifetime = lifetime;
            this.startSize = startSize;
            this.growSize = growSize;
            this.seed = seed;
            this.hotStemParticle = hotStemParticle;
            this.initialSizeBoost = initialSizeBoost;
            this.smokeShadeVariant = randomSmokeShadeVariant(seed);
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.velocityZ = velocityZ;
        }

        private void tick(NuclearMushroomCloudSimulation simulation) {
            age++;
            prevX = x;
            prevY = y;
            prevZ = z;
            localHeat = simulation.heat;
            localStemHotHeat = simulation.stemHotSpawnHeatFactor();
            globalAlpha = simulation.globalAlpha();
            currentMinorRadius = simulation.minorRadius;
            if (type == MushroomCloudletType.STEM) {
                addStemMotion(simulation);
                dampAndClamp(0.975D, 0.45D * simulation.visualScale, 0.35D * simulation.visualScale);
            } else if (type == MushroomCloudletType.SECONDARY_TORUS) {
                addTorusMotion(simulation, simulation.secondaryMajorRadius, simulation.secondaryMinorRadius, simulation.secondaryCenterY, 0.85D, 1.0D);
                dampAndClamp(0.988D, simulation.maxTorusSpeed() * 0.85D, 0.48D * simulation.visualScale);
            } else if (type == MushroomCloudletType.WHITE_AIR_RING) {
                dampAirRing();
            } else if (type == MushroomCloudletType.INITIAL_FIREBALL_CHAOS) {
                dampInitialChaos();
            } else if (type == MushroomCloudletType.CRATER_SMOKE) {
                dampCraterSmoke(simulation);
            } else {
                addTorusMotion(simulation);
                dampAndClamp(0.988D, simulation.maxTorusSpeed(), 0.55D * simulation.visualScale);
            }
            x += velocityX;
            y += velocityY;
            z += velocityZ;
        }

        private void addTorusMotion(NuclearMushroomCloudSimulation simulation) {
            addTorusMotion(
                    simulation,
                    simulation.majorRadius,
                    simulation.horizontalMinorRadius,
                    simulation.minorRadius,
                    simulation.torusCenterY,
                    1.0D,
                    simulation.headShapeCorrectionMultiplier()
            );
        }

        private void addTorusMotion(
                NuclearMushroomCloudSimulation simulation,
                double motionMajorRadius,
                double motionMinorRadius,
                double motionCenterY,
                double speedMultiplier,
                double correctionMultiplier
        ) {
            addTorusMotion(simulation, motionMajorRadius, motionMinorRadius, motionMinorRadius, motionCenterY, speedMultiplier, correctionMultiplier);
        }

        private void addTorusMotion(
                NuclearMushroomCloudSimulation simulation,
                double motionMajorRadius,
                double motionHorizontalMinorRadius,
                double motionVerticalMinorRadius,
                double motionCenterY,
                double speedMultiplier,
                double correctionMultiplier
        ) {
            double horizontalDistance = Math.sqrt(x * x + z * z);
            Vec3 radialDir = radial(x, z);
            Vec3 tangentDir = new Vec3(-radialDir.z, 0.0D, radialDir.x);
            radialOffset = horizontalDistance - motionMajorRadius;
            verticalOffset = y - motionCenterY;
            double horizontalRadius = Math.max(motionHorizontalMinorRadius, 1.0E-5D);
            double verticalRadius = Math.max(motionVerticalMinorRadius, 1.0E-5D);
            double normalizedRadial = radialOffset / horizontalRadius;
            double normalizedVertical = verticalOffset / verticalRadius;
            double normalizedTubeDistance = Math.sqrt(normalizedRadial * normalizedRadial + normalizedVertical * normalizedVertical);
            double effectiveTubeRadius = Math.sqrt(horizontalRadius * verticalRadius);
            double innerFactor = Mth.clamp(-normalizedRadial, 0.0D, 1.0D);
            double bottomFactor = Mth.clamp(-normalizedVertical, 0.0D, 1.0D);

            double tubeDistanceSafe = Math.max(normalizedTubeDistance, 1.0E-5D);
            double tangentRadial = normalizedVertical / tubeDistanceSafe;
            double tangentVertical = -normalizedRadial / tubeDistanceSafe;
            double targetTubeSpeed = torusAngularSpeed() * Math.max(effectiveTubeRadius * normalizedTubeDistance, effectiveTubeRadius * 0.65D) * speedMultiplier;
            double targetVelocityX = radialDir.x * tangentRadial * targetTubeSpeed;
            double targetVelocityY = tangentVertical * targetTubeSpeed;
            double targetVelocityZ = radialDir.z * tangentRadial * targetTubeSpeed;
            double currentSwirlVelocity = velocityX * tangentDir.x + velocityZ * tangentDir.z;
            double currentTubeVelocityX = velocityX - tangentDir.x * currentSwirlVelocity;
            double currentTubeVelocityZ = velocityZ - tangentDir.z * currentSwirlVelocity;
            velocityX += (targetVelocityX - currentTubeVelocityX) * TORUS_VELOCITY_RESPONSE;
            velocityY += (targetVelocityY - velocityY) * TORUS_VELOCITY_RESPONSE;
            velocityZ += (targetVelocityZ - currentTubeVelocityZ) * TORUS_VELOCITY_RESPONSE;

            double liftStrength = 0.003D * simulation.visualScale;
            double extraReturnLift = 0.005D * simulation.visualScale;
            double swirlStrength = 0.0026D * simulation.visualScale;

            velocityY += innerFactor * liftStrength;
            velocityY += innerFactor * bottomFactor * extraReturnLift;
            velocityX += tangentDir.x * swirlStrength;
            velocityZ += tangentDir.z * swirlStrength;

            if (normalizedTubeDistance > 1.0E-5D) {
                double correction = Mth.clamp(1.0D - normalizedTubeDistance, -1.0D, 1.0D);
                double correctionStrength = 0.035D * simulation.visualScale * correction * correctionMultiplier;
                velocityX += radialDir.x * (normalizedRadial / normalizedTubeDistance) * correctionStrength;
                velocityY += (normalizedVertical / normalizedTubeDistance) * correctionStrength;
                velocityZ += radialDir.z * (normalizedRadial / normalizedTubeDistance) * correctionStrength;
            }
        }

        private void addStemMotion(NuclearMushroomCloudSimulation simulation) {
            double horizontalDistance = Math.sqrt(x * x + z * z);
            Vec3 radialDir = radial(x, z);
            Vec3 tangentDir = new Vec3(-radialDir.z, 0.0D, radialDir.x);
            stemNormalizedHeight = Mth.clamp((y - simulation.stemBottomY) / simulation.stemHeight, 0.0D, 1.0D);
            double targetRadius = simulation.stemRadiusAt(stemNormalizedHeight);
            radialOffset = horizontalDistance - targetRadius;
            verticalOffset = y - simulation.stemTopY;

            double nextHeight = Mth.clamp(stemNormalizedHeight + 0.16D, 0.0D, 1.0D);
            double futureRadius = simulation.stemRadiusAt(nextHeight);
            double curveTargetRadius = Math.min(targetRadius, futureRadius);
            double radialDelta = curveTargetRadius - horizontalDistance;
            double upwardLift = Mth.lerp(stemNormalizedHeight, 0.070D, 0.026D) * simulation.visualScale;
            double swirlStrength = Mth.lerp(stemNormalizedHeight, 0.004D, 0.010D) * simulation.visualScale;
            velocityY += upwardLift;
            velocityX += radialDir.x * radialDelta * 0.014D;
            velocityZ += radialDir.z * radialDelta * 0.014D;
            velocityX += tangentDir.x * swirlStrength;
            velocityZ += tangentDir.z * swirlStrength;

            if (stemNormalizedHeight > 0.88D) {
                double topBlend = smoothStep((stemNormalizedHeight - 0.88D) / 0.12D);
                double thinTopRadius = simulation.stemRadiusAt(1.0D) * 0.75D;
                double targetX = radialDir.x * thinTopRadius;
                double targetZ = radialDir.z * thinTopRadius;
                double pull = 0.006D * topBlend;
                velocityX += (targetX - x) * pull;
                velocityY += (simulation.stemTopY - y) * pull;
                velocityZ += (targetZ - z) * pull;
            }
        }

        private void dampAndClamp(double drag, double maxSpeed, double maxVerticalSpeed) {
            velocityX *= drag;
            velocityY *= drag;
            velocityZ *= drag;
            velocityY = Mth.clamp(velocityY, -maxVerticalSpeed, maxVerticalSpeed);
            double speedSqr = velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ;
            if (speedSqr > maxSpeed * maxSpeed) {
                double scale = maxSpeed / Math.sqrt(speedSqr);
                velocityX *= scale;
                velocityY *= scale;
                velocityZ *= scale;
            }
        }

        private void dampAirRing() {
            double progress = Mth.clamp(age / (double) lifetime, 0.0D, 1.0D);
            double drag = Mth.lerp(progress, 0.995D, 0.965D);
            velocityX *= drag;
            velocityZ *= drag;
            velocityY *= 0.985D;
            velocityY = Mth.clamp(velocityY, -0.05D, 0.05D);
        }

        private void dampInitialChaos() {
            double progress = Mth.clamp(age / (double) lifetime, 0.0D, 1.0D);
            double drag = Mth.lerp(progress, 0.982D, 0.955D);
            velocityX *= drag;
            velocityY *= Mth.lerp(progress, 0.990D, 0.965D);
            velocityZ *= drag;
        }

        private void dampCraterSmoke(NuclearMushroomCloudSimulation simulation) {
            velocityX *= 0.978D;
            velocityZ *= 0.978D;
            velocityY = Math.max(0.0D, velocityY * 0.984D + 0.002D * simulation.visualScale);
            double maxHorizontalSpeed = 0.08D * simulation.visualScale;
            double horizontalSpeedSqr = velocityX * velocityX + velocityZ * velocityZ;
            if (horizontalSpeedSqr > maxHorizontalSpeed * maxHorizontalSpeed) {
                double scale = maxHorizontalSpeed / Math.sqrt(horizontalSpeedSqr);
                velocityX *= scale;
                velocityZ *= scale;
            }
            velocityY = Mth.clamp(velocityY, 0.0D, 0.12D * simulation.visualScale);
        }

        public boolean isExpired() {
            return age >= lifetime;
        }

        public double x(float partialTick) {
            return Mth.lerp(partialTick, prevX, x);
        }

        public double y(float partialTick) {
            return Mth.lerp(partialTick, prevY, y);
        }

        public double z(float partialTick) {
            return Mth.lerp(partialTick, prevZ, z);
        }

        public float size(float partialTick) {
            float progress = Mth.clamp((age + partialTick) / (float) lifetime, 0.0F, 1.0F);
            float normalSize = Mth.lerp(progress, startSize, startSize + growSize);
            if (initialSizeBoost <= 1.0F) {
                return normalSize;
            }
            float boostProgress = Mth.clamp((age + partialTick) / (float) STEM_BASE_SIZE_BOOST_FADE_TICKS, 0.0F, 1.0F);
            float currentBoost = Mth.lerp((float) smoothStep(boostProgress), initialSizeBoost, 1.0F);
            return normalSize * currentBoost;
        }

        public float alpha(float partialTick) {
            float progress = Mth.clamp((age + partialTick) / (float) lifetime, 0.0F, 1.0F);
            float fade;
            if (type == MushroomCloudletType.WHITE_AIR_RING) {
                fade = progress < 0.30F ? 1.0F : 1.0F - (progress - 0.30F) / 0.70F;
                return Mth.clamp(0.68F * fade, 0.0001F, 0.72F);
            }
            if (type == MushroomCloudletType.INITIAL_FIREBALL_CHAOS) {
                fade = progress < 0.40F ? 1.0F : 1.0F - (progress - 0.40F) / 0.60F;
                return Mth.clamp(0.98F * fade, 0.0001F, 1.0F);
            }
            if (type == MushroomCloudletType.CRATER_SMOKE) {
                fade = progress < 0.65F ? 1.0F : 1.0F - (progress - 0.65F) / 0.35F;
                return Mth.clamp((float) (0.88F * fade * globalAlpha), 0.0001F, 0.92F);
            }
            if (type == MushroomCloudletType.STEM) {
                fade = progress > 0.82F ? 1.0F - (progress - 0.82F) / 0.18F : 1.0F;
            } else {
                fade = progress > 0.75F ? 1.0F - (progress - 0.75F) / 0.25F : 1.0F;
            }
            return Mth.clamp((float) (0.95F * fade * globalAlpha), 0.0001F, 1.0F);
        }

        public int red(float partialTick) {
            return color(partialTick, 0);
        }

        public int green(float partialTick) {
            return color(partialTick, 1);
        }

        public int blue(float partialTick) {
            return color(partialTick, 2);
        }

        private int color(float partialTick, int component) {
            if (type == MushroomCloudletType.STEM) {
                return stemColor(component);
            }
            if (type == MushroomCloudletType.SECONDARY_TORUS) {
                return secondaryTorusColor(component);
            }
            if (type == MushroomCloudletType.WHITE_AIR_RING) {
                return whiteAirRingColor(partialTick, component);
            }
            if (type == MushroomCloudletType.INITIAL_FIREBALL_CHAOS) {
                return initialFireballChaosColor(partialTick, component);
            }
            if (type == MushroomCloudletType.CRATER_SMOKE) {
                return craterSmokeColor(component);
            }
            float progress = Mth.clamp((age + partialTick) / (float) lifetime, 0.0F, 1.0F);
            float particleCoolProgress = Mth.clamp((age + partialTick) / (float) PARTICLE_HOT_COOL_TICKS, 0.0F, 1.0F);
            double innerHeat = Mth.clamp(-radialOffset / Math.max(currentMinorRadius, 1.0E-5D), 0.0D, 1.0D);
            double heatFactor = Mth.clamp(localHeat * innerHeat * (1.0D - particleCoolProgress), 0.0D, 1.0D);

            Color darkSmoke = smokeColor();
            Color darkOrange = new Color(0.30F, 0.10F, 0.045F);
            Color orange = new Color(0.85F, 0.32F, 0.08F);
            Color hotYellow = new Color(1.0F, 0.72F, 0.22F);
            Color hotWhite = new Color(1.0F, 0.96F, 0.78F);

            Color cool;
            if (particleCoolProgress < 0.20F) {
                cool = lerpColor(particleCoolProgress / 0.20F, hotYellow, orange);
            } else if (particleCoolProgress < 0.55F) {
                cool = lerpColor((particleCoolProgress - 0.20F) / 0.35F, orange, darkOrange);
            } else {
                cool = lerpColor((particleCoolProgress - 0.55F) / 0.45F, darkOrange, darkSmoke);
            }
            Color hot = heatFactor > 0.72D
                    ? lerpColor((float) ((heatFactor - 0.72D) / 0.28D), hotYellow, hotWhite)
                    : lerpColor((float) (heatFactor / 0.72D), orange, hotYellow);
            Color color = lerpColor((float) heatFactor, cool, hot);
            float seedTint = 0.93F + (((seed >>> 24) & 0xFFFF) / 65535.0F) * 0.14F;
            float value = component == 0 ? color.red : component == 1 ? color.green : color.blue;
            return Math.round(Mth.clamp(value * seedTint, 0.0F, 1.0F) * 255.0F);
        }

        private int secondaryTorusColor(int component) {
            Color darkSmoke = smokeColor();
            if (hotStemParticle) {
                float coolProgress = Mth.clamp(age / (float) STEM_HOT_COOL_TICKS, 0.0F, 1.0F);
                Color hotOrange = new Color(0.90F, 0.30F, 0.07F);
                Color emberOrange = new Color(0.48F, 0.14F, 0.04F);
                Color color = coolProgress < 0.25F
                        ? lerpColor(coolProgress / 0.25F, hotOrange, emberOrange)
                        : lerpColor((coolProgress - 0.25F) / 0.75F, emberOrange, darkSmoke);
                color = lerpColor((float) Mth.clamp(localStemHotHeat, 0.0D, 1.0D), darkSmoke, color);
                float seedTint = 0.92F + (((seed >>> 28) & 0xFFFF) / 65535.0F) * 0.12F;
                float value = component == 0 ? color.red : component == 1 ? color.green : color.blue;
                return Math.round(Mth.clamp(value * seedTint, 0.0F, 1.0F) * 255.0F);
            }

            Color darkGray = smokeColor();
            Color warmSmoke = new Color(0.34F, 0.16F, 0.075F);
            float warmth = (float) Mth.clamp(localHeat * 0.34D + 0.08D, 0.0D, 1.0D);
            Color color = lerpColor(warmth, darkSmoke, lerpColor(0.55F, darkGray, warmSmoke));
            float seedTint = 0.90F + (((seed >>> 32) & 0xFFFF) / 65535.0F) * 0.12F;
            float value = component == 0 ? color.red : component == 1 ? color.green : color.blue;
            return Math.round(Mth.clamp(value * seedTint, 0.0F, 1.0F) * 255.0F);
        }

        private int whiteAirRingColor(float partialTick, int component) {
            float progress = Mth.clamp((age + partialTick) / (float) lifetime, 0.0F, 1.0F);
            Color white = new Color(0.92F, 0.96F, 1.0F);
            Color paleGray = new Color(0.72F, 0.76F, 0.78F);
            Color color = lerpColor(progress, white, paleGray);
            float value = component == 0 ? color.red : component == 1 ? color.green : color.blue;
            return Math.round(Mth.clamp(value, 0.0F, 1.0F) * 255.0F);
        }

        private int initialFireballChaosColor(float partialTick, int component) {
            float progress = Mth.clamp((age + partialTick) / (float) lifetime, 0.0F, 1.0F);
            Color darkSmoke = smokeColor();
            Color darkOrange = new Color(0.34F, 0.10F, 0.035F);
            Color orange = new Color(0.92F, 0.30F, 0.055F);
            Color hotYellow = new Color(1.0F, 0.78F, 0.22F);
            Color hotWhite = new Color(1.0F, 0.96F, 0.78F);
            Color color;
            if (progress < 0.20F) {
                color = lerpColor(progress / 0.20F, hotWhite, hotYellow);
            } else if (progress < 0.60F) {
                color = lerpColor((progress - 0.20F) / 0.40F, orange, darkOrange);
            } else {
                color = lerpColor((progress - 0.60F) / 0.40F, darkOrange, darkSmoke);
            }
            float seedTint = 0.93F + (((seed >>> 20) & 0xFFFF) / 65535.0F) * 0.14F;
            float value = component == 0 ? color.red : component == 1 ? color.green : color.blue;
            return Math.round(Mth.clamp(value * seedTint, 0.0F, 1.0F) * 255.0F);
        }

        private int craterSmokeColor(int component) {
            Color color = smokeColor();
            float seedTint = 0.92F + (((seed >>> 28) & 0xFFFF) / 65535.0F) * 0.12F;
            float value = component == 0 ? color.red : component == 1 ? color.green : color.blue;
            return Math.round(Mth.clamp(value * seedTint, 0.0F, 1.0F) * 255.0F);
        }

        private int stemColor(int component) {
            Color darkSmoke = smokeColor();
            if (hotStemParticle) {
                float coolProgress = Mth.clamp(age / (float) STEM_HOT_COOL_TICKS, 0.0F, 1.0F);
                Color hotOrange = new Color(0.95F, 0.38F, 0.08F);
                Color emberOrange = new Color(0.55F, 0.16F, 0.04F);
                Color color = coolProgress < 0.25F
                        ? lerpColor(coolProgress / 0.25F, hotOrange, emberOrange)
                        : lerpColor((coolProgress - 0.25F) / 0.75F, emberOrange, darkSmoke);
                color = lerpColor((float) Mth.clamp(localStemHotHeat, 0.0D, 1.0D), darkSmoke, color);
                float seedTint = 0.92F + (((seed >>> 32) & 0xFFFF) / 65535.0F) * 0.12F;
                float value = component == 0 ? color.red : component == 1 ? color.green : color.blue;
                return Math.round(Mth.clamp(value * seedTint, 0.0F, 1.0F) * 255.0F);
            }

            Color darkGray = smokeColor();
            Color warmSmoke = new Color(0.26F, 0.12F, 0.055F);
            Color orangeTint = new Color(0.55F, 0.20F, 0.06F);
            float heightWarmth = (float) Mth.clamp(localHeat * stemNormalizedHeight * 0.45D, 0.0D, 1.0D);
            Color base = lerpColor((float) Mth.clamp(stemNormalizedHeight * 0.35D, 0.0D, 1.0D), darkSmoke, darkGray);
            Color warm = lerpColor(heightWarmth, warmSmoke, orangeTint);
            Color color = lerpColor(heightWarmth, base, warm);
            float seedTint = 0.90F + (((seed >>> 32) & 0xFFFF) / 65535.0F) * 0.12F;
            float value = component == 0 ? color.red : component == 1 ? color.green : color.blue;
            return Math.round(Mth.clamp(value * seedTint, 0.0F, 1.0F) * 255.0F);
        }

        private Color smokeColor() {
            Color darkSmoke = new Color(0.055F, 0.052F, 0.050F);
            Color mediumSmoke = new Color(0.18F, 0.18F, 0.17F);
            Color lightSmoke = new Color(0.36F, 0.36F, 0.34F);
            Color base;
            if (smokeShadeVariant >= 1.0F) {
                base = lightSmoke;
            } else if (smokeShadeVariant >= 0.5F) {
                base = mediumSmoke;
            } else {
                base = darkSmoke;
            }
            float brightness = 0.95F + (((seed >>> 44) & 0xFF) / 255.0F) * 0.10F;
            return new Color(
                    Mth.clamp(base.red * brightness, 0.0F, 1.0F),
                    Mth.clamp(base.green * brightness, 0.0F, 1.0F),
                    Mth.clamp(base.blue * brightness, 0.0F, 1.0F)
            );
        }

        private static Color lerpColor(float amount, Color start, Color end) {
            float clamped = Mth.clamp(amount, 0.0F, 1.0F);
            return new Color(
                    Mth.lerp(clamped, start.red, end.red),
                    Mth.lerp(clamped, start.green, end.green),
                    Mth.lerp(clamped, start.blue, end.blue)
            );
        }

        private record Color(float red, float green, float blue) {
        }
    }
}

