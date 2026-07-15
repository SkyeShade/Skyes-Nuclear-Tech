package com.skyeshade.skyent.content.entity;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class NuclearMushroomCloudSimulation {
    public static final int MAX_MUSHROOM_CLOUDLETS = 12_000;
    private static final boolean DEBUG_MUSHROOM = Boolean.getBoolean("skyent.debugNukeMushroom");
    private static final int MAX_AGE_TICKS = NuclearExplosionEntity.ENTITY_LIFETIME_TICKS;
    private static final int TORUS_SPAWN_TICKS = 2_000;
    private static final int TORUS_CLOUDLETS_PER_TICK = 16;
    private static final int SECONDARY_TORUS_SPAWN_TICKS = TORUS_SPAWN_TICKS;
    private static final int SECONDARY_TORUS_CLOUDLETS_PER_TICK = 1;
    private static final int STEM_EXTRA_SPAWN_TICKS = 1000;
    private static final int STEM_SPAWN_TICKS = TORUS_SPAWN_TICKS + STEM_EXTRA_SPAWN_TICKS;
    private static final int STEM_CLOUDLETS_PER_TICK = 2;
    private static final int TORUS_GROW_TICKS = 2_400;
    private static final int TORUS_RISE_TICKS = TORUS_GROW_TICKS;
    private static final int TORUS_FADE_START_TICKS = 200;
    private static final int PARTICLE_HOT_COOL_TICKS = 200;
    private static final int STEM_HOT_COOL_TICKS = PARTICLE_HOT_COOL_TICKS / 2;
    private static final int STEM_LIFETIME_MIN_TICKS = 250;
    private static final int STEM_LIFETIME_RANDOM_TICKS = 300;
    private static final int STEM_BASE_SIZE_BOOST_FADE_TICKS = 60;
    private static final float STEM_BASE_SIZE_BOOST = 2.0F;
    private static final double STEM_BASE_DEPTH_BELOW_EXPLOSION = 50.0D;
    private static final int INITIAL_TORUS_FILL_COUNT = 900;
    private static final boolean ENABLE_INITIAL_TORUS_FILL = true;
    private static final float CLOUDLET_SIZE_MULTIPLIER = 0.60F;
    private static final double TORUS_ROTATION_TICKS = 80.0D;
    private static final double TORUS_VELOCITY_RESPONSE = 0.24D;
    private static final double TORUS_SOURCE_ANGLE = -Math.PI * 0.75D;
    private static final double TORUS_SOURCE_ANGLE_SPREAD = 0.45D;
    private static final double SECONDARY_TORUS_SOURCE_ANGLE_SPREAD = 0.55D;
    private static final int AIR_RING_LOWER_COUNT = 360;
    private static final int AIR_RING_UPPER_COUNT = 260;
    private static final int AIR_RING_LOWER_DELAY_TICKS = 20;
    private static final int AIR_RING_UPPER_DELAY_TICKS = 60;
    private static final int AIR_RING_LOWER_LIFETIME = 320;
    private static final int AIR_RING_UPPER_LIFETIME = 260;
    private static final double AIR_RING_HEIGHT_EXTRA_Y = 40.0D;
    private static final double TORUS_INITIAL_MAJOR_RADIUS = 8.0D;
    private static final double TORUS_FINAL_MAJOR_RADIUS = 55.0D;
    private static final double TORUS_INITIAL_MINOR_RADIUS = 5.0D;
    private static final double TORUS_FINAL_MINOR_RADIUS = 28.0D;
    private static final double TORUS_INITIAL_CENTER_Y = 10.0D;
    private static final double TORUS_FINAL_CENTER_Y = 140.0D;
    private static final double CLOUD_FINAL_HEIGHT_SCALE = 1.5D;
    private static final double CLOUD_START_SCALE = 1.5D;
    private static final double CLOUD_END_SCALE = 1.0D;
    private static final double CLOUD_BASELINE_RADIUS = 200.0D;

    private final List<MushroomCloudlet> cloudlets = new ArrayList<>();
    private final long seed;
    private final float visualScale;
    private int age;
    private double groundY = Double.NaN;
    private boolean groundYInitialized;
    private double majorRadius;
    private double minorRadius;
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
    private boolean filledInitialTorus;

    public NuclearMushroomCloudSimulation(long seed, float radius) {
        this.seed = seed;
        this.visualScale = (float) Math.max(0.25D, radius / CLOUD_BASELINE_RADIUS);
        this.majorRadius = TORUS_INITIAL_MAJOR_RADIUS * visualScale;
        this.minorRadius = TORUS_INITIAL_MINOR_RADIUS * visualScale;
        this.torusCenterY = TORUS_INITIAL_CENTER_Y * visualScale;
    }

    public void tick(Level level, Vec3 entityPosition) {
        updateGroundHeight(level, entityPosition);
        updateTorusField();
        fillInitialTorusIfNeeded();
        spawnAirRingsIfNeeded();
        spawnCloudlets();
        tickCloudlets();
        trimOldestIfNeeded();
        logDebug();
        age++;
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
        double growProgress = smoothStep(Mth.clamp(age / (double) TORUS_GROW_TICKS, 0.0D, 1.0D));
        double riseProgress = smoothStep(Mth.clamp(age / (double) TORUS_RISE_TICKS, 0.0D, 1.0D));
        cloudScale = Mth.lerp(growProgress, CLOUD_START_SCALE, CLOUD_END_SCALE);
        double baseMajorRadius = Mth.lerp(growProgress, TORUS_INITIAL_MAJOR_RADIUS * visualScale, TORUS_FINAL_MAJOR_RADIUS * visualScale);
        double baseMinorRadius = Mth.lerp(growProgress, TORUS_INITIAL_MINOR_RADIUS * visualScale, TORUS_FINAL_MINOR_RADIUS * visualScale);
        majorRadius = baseMajorRadius * cloudScale;
        minorRadius = baseMinorRadius * cloudScale;
        secondaryMinorRadius = minorRadius * 0.17D;
        secondaryMajorRadius = Math.max(1.0D, (majorRadius + minorRadius) * 0.58D - secondaryMinorRadius);
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

    private void spawnCloudlets() {
        RandomSource random = RandomSource.create(seed + age * 104_729L);
        sourceSpawnCountThisTick = 0;
        sourceSpawnRadialOffsetSum = 0.0D;
        sourceSpawnVerticalOffsetSum = 0.0D;
        if (age < TORUS_SPAWN_TICKS) {
            for (int index = 0; index < TORUS_CLOUDLETS_PER_TICK; index++) {
                if (!makeRoomForCloudlet()) {
                    return;
                }
                spawnTorusCloudlet(random);
            }
        }
        if (age < SECONDARY_TORUS_SPAWN_TICKS) {
            for (int index = 0; index < SECONDARY_TORUS_CLOUDLETS_PER_TICK; index++) {
                if (!makeRoomForCloudlet()) {
                    return;
                }
                spawnSecondaryTorusCloudlet(random);
            }
        }
        if (age < STEM_SPAWN_TICKS) {
            for (int index = 0; index < STEM_CLOUDLETS_PER_TICK; index++) {
                if (!makeRoomForCloudlet()) {
                    return;
                }
                spawnStemCloudlet(random);
            }
        }
    }

    private void fillInitialTorusIfNeeded() {
        if (!ENABLE_INITIAL_TORUS_FILL || filledInitialTorus) {
            return;
        }
        filledInitialTorus = true;
        RandomSource random = RandomSource.create(seed ^ 0xBB67AE8584CAA73BL);
        for (int index = 0; index < INITIAL_TORUS_FILL_COUNT; index++) {
            if (!makeRoomForCloudlet()) {
                return;
            }
            double angleAroundY = random.nextDouble() * Mth.TWO_PI;
            double angleAroundTube = random.nextDouble() * Mth.TWO_PI;
            double tubeRadius = minorRadius * Math.sqrt(random.nextDouble());
            double radialOffset = Math.cos(angleAroundTube) * tubeRadius;
            double verticalOffset = Math.sin(angleAroundTube) * tubeRadius;
            double radialDistance = Math.max(0.0D, majorRadius + radialOffset);
            double x = Math.cos(angleAroundY) * radialDistance + jitter(random, 0.3D * visualScale);
            double z = Math.sin(angleAroundY) * radialDistance + jitter(random, 0.3D * visualScale);
            double y = torusCenterY + verticalOffset + jitter(random, 0.22D * visualScale);
            Vec3 radial = radial(x, z);
            Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);
            double tubeDistance = Math.sqrt(radialOffset * radialOffset + verticalOffset * verticalOffset);
            double tubeDistanceSafe = Math.max(tubeDistance, 1.0E-5D);
            double tangentRadial = verticalOffset / tubeDistanceSafe;
            double tangentVertical = -radialOffset / tubeDistanceSafe;
            double targetTubeSpeed = torusAngularSpeed() * Math.max(tubeDistance, minorRadius * 0.65D);
            double velocityX = radial.x * tangentRadial * targetTubeSpeed * 0.45D + tangent.x * jitter(random, 0.003D * visualScale);
            double velocityY = tangentVertical * targetTubeSpeed * 0.45D + jitter(random, 0.003D * visualScale);
            double velocityZ = radial.z * tangentRadial * targetTubeSpeed * 0.45D + tangent.z * jitter(random, 0.003D * visualScale);
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
            spawnAirRing(random, AIR_RING_LOWER_COUNT, lowerAirRingSpawnY, 10.0D * visualScale, 180.0D * visualScale, AIR_RING_LOWER_LIFETIME, 1.35D * visualScale);
            logAirRingSpawn("lower", lowerAirRingSpawnY);
        }

        if (!spawnedUpperAirRing && age >= AIR_RING_UPPER_DELAY_TICKS) {
            spawnedUpperAirRing = true;
            RandomSource random = RandomSource.create(seed ^ 0xBB67AE8584CAA73BL);
            double baseY = Double.isFinite(groundY) ? groundY : 0.0D;
            upperAirRingSpawnY = baseY + 60.0D * visualScale + AIR_RING_HEIGHT_EXTRA_Y;
            spawnAirRing(random, AIR_RING_UPPER_COUNT, upperAirRingSpawnY, 6.0D * visualScale, 115.0D * visualScale, AIR_RING_UPPER_LIFETIME, 0.95D * visualScale);
            logAirRingSpawn("upper", upperAirRingSpawnY);
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

    private void logAirRingSpawn(String ringName, double y) {
        if (!DEBUG_MUSHROOM) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke mushroom air ring spawned: ring={} age={} y={} lowerDelay={} upperDelay={} torusCenterY={} torusTopY={} visualScale={}",
                ringName,
                age,
                y,
                AIR_RING_LOWER_DELAY_TICKS,
                AIR_RING_UPPER_DELAY_TICKS,
                torusCenterY,
                torusCenterY + minorRadius,
                visualScale
        );
    }

    private void spawnTorusCloudlet(RandomSource random) {
        double angleAroundY = random.nextDouble() * Mth.TWO_PI;
        double angleAroundTube = TORUS_SOURCE_ANGLE + jitter(random, TORUS_SOURCE_ANGLE_SPREAD);
        double tubeRadius = minorRadius * Mth.lerp(random.nextDouble(), 0.35D, 1.0D);
        double radialOffset = Math.cos(angleAroundTube) * tubeRadius;
        double verticalOffset = Math.sin(angleAroundTube) * tubeRadius;
        double radialDistance = Math.max(0.0D, majorRadius + radialOffset);
        double x = Math.cos(angleAroundY) * radialDistance;
        double z = Math.sin(angleAroundY) * radialDistance;
        double y = torusCenterY + verticalOffset;
        Vec3 radial = radial(x, z);
        Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);
        double sizeScale = Math.sqrt(torusScale);
        float startSize = scaleCloudletSize((float) ((5.0D + random.nextDouble() * 4.0D) * visualScale * sizeScale));
        float growSize = scaleCloudletSize((float) ((8.0D + random.nextDouble() * 8.0D) * visualScale * sizeScale));
        double swirl = jitter(random, 0.006D * visualScale);

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
                tangent.x * swirl + jitter(random, 0.002D * visualScale),
                (0.01D + random.nextDouble() * 0.025D) * visualScale,
                tangent.z * swirl + jitter(random, 0.002D * visualScale)
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

    private void logDebug() {
        if (!DEBUG_MUSHROOM || age % 40 != 0) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke mushroom debug: age={} cloudlets={} groundY={} groundYInitialized={} visualScale={} cloudScale={} finalHeightScale={} growTicks={} riseTicks={} filledInitialTorus={} initialTorusFillCount={} majorRadius={} minorRadius={} torusCenterY={} torusBottomY={} torusTopY={} secondaryMajorRadius={} secondaryMinorRadius={} secondaryCenterY={} stemBaseDepthBelowExplosion={} scaledStemDepth={} stemBottomY={} stemTopY={} stemHeight={} stemRadius0={} stemRadius030={} stemRadius075={} stemRadius1={} globalHeat={} stemHotSpawnHeatFactor={} stemHotSpawnChance={} torusSpawnTicks={} stemExtraSpawnTicks={} stemSpawnTicks={} torusSpawnRate={} secondarySpawnRate={} stemSpawnRate={} stemLifetimeMin={} stemLifetimeRandom={} airRingHeightExtraY={} airRingLowerDelay={} airRingUpperDelay={} airRingLowerLifetime={} airRingUpperLifetime={} lowerAirRingSpawnY={} upperAirRingSpawnY={} sourceAvgRadialOffset={} sourceAvgVerticalOffset={} torusScale={} angularSpeed={} targetTubeSpeed={} maxSpeed={} spawnedLowerAirRing={} spawnedUpperAirRing={} TORUS_FIREBALL={} SECONDARY_TORUS={} STEM={} WHITE_AIR_RING={} hotSTEM={}",
                age,
                cloudlets.size(),
                groundY,
                groundYInitialized,
                visualScale,
                cloudScale,
                CLOUD_FINAL_HEIGHT_SCALE,
                TORUS_GROW_TICKS,
                TORUS_RISE_TICKS,
                filledInitialTorus,
                INITIAL_TORUS_FILL_COUNT,
                majorRadius,
                minorRadius,
                torusCenterY,
                torusCenterY - minorRadius,
                torusCenterY + minorRadius,
                secondaryMajorRadius,
                secondaryMinorRadius,
                secondaryCenterY,
                STEM_BASE_DEPTH_BELOW_EXPLOSION,
                scaledStemDepth,
                stemBottomY,
                stemTopY,
                stemHeight,
                stemRadiusAt(0.0D),
                stemRadiusAt(0.30D),
                stemRadiusAt(0.75D),
                stemRadiusAt(1.0D),
                heat,
                stemHotSpawnHeatFactor(),
                stemHotSpawnChance(),
                TORUS_SPAWN_TICKS,
                STEM_EXTRA_SPAWN_TICKS,
                STEM_SPAWN_TICKS,
                TORUS_CLOUDLETS_PER_TICK,
                SECONDARY_TORUS_CLOUDLETS_PER_TICK,
                STEM_CLOUDLETS_PER_TICK,
                STEM_LIFETIME_MIN_TICKS,
                STEM_LIFETIME_RANDOM_TICKS,
                AIR_RING_HEIGHT_EXTRA_Y,
                AIR_RING_LOWER_DELAY_TICKS,
                AIR_RING_UPPER_DELAY_TICKS,
                AIR_RING_LOWER_LIFETIME,
                AIR_RING_UPPER_LIFETIME,
                lowerAirRingSpawnY,
                upperAirRingSpawnY,
                sourceSpawnCountThisTick == 0 ? 0.0D : sourceSpawnRadialOffsetSum / sourceSpawnCountThisTick,
                sourceSpawnCountThisTick == 0 ? 0.0D : sourceSpawnVerticalOffsetSum / sourceSpawnCountThisTick,
                torusScale,
                torusAngularSpeed(),
                targetTubeSpeedAtMinorRadius(),
                maxTorusSpeed(),
                spawnedLowerAirRing,
                spawnedUpperAirRing,
                countCloudlets(MushroomCloudletType.TORUS_FIREBALL),
                countCloudlets(MushroomCloudletType.SECONDARY_TORUS),
                countCloudlets(MushroomCloudletType.STEM),
                countCloudlets(MushroomCloudletType.WHITE_AIR_RING),
                countHotStemCloudlets()
        );
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

    private static Vec3 radial(double x, double z) {
        double length = Math.sqrt(x * x + z * z);
        if (length < 1.0E-5D) {
            return Vec3.ZERO;
        }
        return new Vec3(x / length, 0.0D, z / length);
    }

    private enum MushroomCloudletType {
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
            } else {
                addTorusMotion(simulation);
                dampAndClamp(0.988D, simulation.maxTorusSpeed(), 0.55D * simulation.visualScale);
            }
            x += velocityX;
            y += velocityY;
            z += velocityZ;
        }

        private void addTorusMotion(NuclearMushroomCloudSimulation simulation) {
            addTorusMotion(simulation, simulation.majorRadius, simulation.minorRadius, simulation.torusCenterY, 1.0D, 1.0D);
        }

        private void addTorusMotion(
                NuclearMushroomCloudSimulation simulation,
                double motionMajorRadius,
                double motionMinorRadius,
                double motionCenterY,
                double speedMultiplier,
                double correctionMultiplier
        ) {
            double horizontalDistance = Math.sqrt(x * x + z * z);
            Vec3 radialDir = radial(x, z);
            Vec3 tangentDir = new Vec3(-radialDir.z, 0.0D, radialDir.x);
            radialOffset = horizontalDistance - motionMajorRadius;
            verticalOffset = y - motionCenterY;
            double radius = Math.max(motionMinorRadius, 1.0E-5D);
            double tubeDistance = Math.sqrt(radialOffset * radialOffset + verticalOffset * verticalOffset);
            double innerFactor = Mth.clamp(-radialOffset / radius, 0.0D, 1.0D);
            double bottomFactor = Mth.clamp(-verticalOffset / radius, 0.0D, 1.0D);

            double tubeDistanceSafe = Math.max(tubeDistance, 1.0E-5D);
            double tangentRadial = verticalOffset / tubeDistanceSafe;
            double tangentVertical = -radialOffset / tubeDistanceSafe;
            double targetTubeSpeed = torusAngularSpeed() * Math.max(tubeDistance, motionMinorRadius * 0.65D) * speedMultiplier;
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

            if (tubeDistance > 1.0E-5D) {
                double correction = Mth.clamp((motionMinorRadius - tubeDistance) / motionMinorRadius, -1.0D, 1.0D);
                double correctionStrength = 0.035D * simulation.visualScale * correction * correctionMultiplier;
                velocityX += radialDir.x * (radialOffset / tubeDistance) * correctionStrength;
                velocityY += (verticalOffset / tubeDistance) * correctionStrength;
                velocityZ += radialDir.z * (radialOffset / tubeDistance) * correctionStrength;
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
            float progress = Mth.clamp((age + partialTick) / (float) lifetime, 0.0F, 1.0F);
            float particleCoolProgress = Mth.clamp((age + partialTick) / (float) PARTICLE_HOT_COOL_TICKS, 0.0F, 1.0F);
            double innerHeat = Mth.clamp(-radialOffset / Math.max(currentMinorRadius, 1.0E-5D), 0.0D, 1.0D);
            double heatFactor = Mth.clamp(localHeat * innerHeat * (1.0D - particleCoolProgress), 0.0D, 1.0D);

            Color darkSmoke = new Color(0.055F, 0.052F, 0.05F);
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
            Color darkSmoke = new Color(0.055F, 0.052F, 0.05F);
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

            Color darkGray = new Color(0.14F, 0.125F, 0.105F);
            Color warmSmoke = new Color(0.34F, 0.14F, 0.055F);
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

        private int stemColor(int component) {
            Color darkSmoke = new Color(0.055F, 0.052F, 0.05F);
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

            Color darkGray = new Color(0.10F, 0.095F, 0.085F);
            Color warmSmoke = new Color(0.24F, 0.10F, 0.045F);
            Color orangeTint = new Color(0.55F, 0.20F, 0.06F);
            float heightWarmth = (float) Mth.clamp(localHeat * stemNormalizedHeight * 0.45D, 0.0D, 1.0D);
            Color base = lerpColor((float) Mth.clamp(stemNormalizedHeight * 0.35D, 0.0D, 1.0D), darkSmoke, darkGray);
            Color warm = lerpColor(heightWarmth, warmSmoke, orangeTint);
            Color color = lerpColor(heightWarmth, base, warm);
            float seedTint = 0.90F + (((seed >>> 32) & 0xFFFF) / 65535.0F) * 0.12F;
            float value = component == 0 ? color.red : component == 1 ? color.green : color.blue;
            return Math.round(Mth.clamp(value * seedTint, 0.0F, 1.0F) * 255.0F);
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
