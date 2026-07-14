package com.skyeshade.skyent.content.entity;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class NuclearExplosionEntity extends Entity {
    private static final EntityDataAccessor<Boolean> DATA_SPAWN_CLOUD = SynchedEntityData.defineId(
            NuclearExplosionEntity.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Boolean> DATA_FLASH_SKY = SynchedEntityData.defineId(
            NuclearExplosionEntity.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Long> DATA_VISUAL_SEED = SynchedEntityData.defineId(
            NuclearExplosionEntity.class,
            EntityDataSerializers.LONG
    );
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(
            NuclearExplosionEntity.class,
            EntityDataSerializers.FLOAT
    );
    public static final float VANILLA_EXPLOSION_STRENGTH = 16.0F;
    public static final int ENTITY_LIFETIME_TICKS = 20 * 60 * 4;
    public static final float DEFAULT_NUKE_RADIUS = 100.0F;
    public static final double SHOCKWAVE_MAX_RADIUS_MULTIPLIER = 4.0D;
    public static final double SHOCKWAVE_SPEED_BLOCKS_PER_TICK = 2.0D;
    public static final int SHOCKWAVE_VISUAL_INTERVAL_TICKS = 1;
    public static final int SHOCKWAVE_SOUND_TICKS = 5;
    public static final float SHOCKWAVE_SOUND_VOLUME = 2000.0F;
    private static final double SHOCKWAVE_DAMAGE_BAND_WIDTH = 5.0D;
    private static final int SHOCKWAVE_MIN_PUFFS = 32;
    private static final int SHOCKWAVE_MAX_PUFFS = 260;
    private static final int SHOCKWAVE_LIFETIME_MIN_TICKS = 18;
    private static final int SHOCKWAVE_LIFETIME_MAX_TICKS = 34;
    private static final double SHOCKWAVE_BAND_BEHIND_BLOCKS = 4.0D;
    private static final double SHOCKWAVE_BAND_AHEAD_BLOCKS = 1.0D;
    private static final int SHOCKWAVE_SURFACE_SCAN_PADDING = 8;
    public static final int MAX_CLOUDLETS = 5200;
    public static final int RAY_GROW_TICKS = 10;
    public static final int RAY_FADE_TICKS = 40;
    public static final int RAY_TOTAL_TICKS = RAY_GROW_TICKS + RAY_FADE_TICKS;
    public static final float RAY_SCALE = 56.0F;
    private static final boolean DEBUG_SHOCKWAVE_VISUALS = Boolean.getBoolean("skyent.debugNukeShockwave");
    private static final boolean DEBUG_FORCE_SHOCKWAVE_TEST_CLOUDLET = Boolean.getBoolean("skyent.debugNukeShockwaveTestCloudlet");

    private float strength = VANILLA_EXPLOSION_STRENGTH;
    private float radius = DEFAULT_NUKE_RADIUS;
    private boolean destroyBlocks = true;
    private boolean playSounds = true;
    private boolean explosionDone;
    @Nullable
    private UUID sourceUuid;
    private final List<NuclearCloudlet> cloudlets = new ArrayList<>();
    @Nullable
    private NuclearMushroomCloudSimulation mushroomCloudSimulation;
    private final Set<UUID> shockwaveDamagedEntities = new HashSet<>();
    private int shockwaveSpawnMethodCalls;
    private int shockwaveSpawnConditionPasses;
    private int shockwaveCloudletsAttempted;
    private int shockwaveCloudletsAdded;
    private int shockwaveSurfaceFound;
    private int shockwaveSurfaceNotFound;
    private int shockwaveChunkMissing;
    private int shockwaveInvalidY;
    private int shockwaveHeightmapInvalid;
    private int shockwaveFoundSurfaceTopDown;
    private int shockwaveFallbackHeightmap;
    private int shockwaveFallbackEntityY;
    private int shockwaveCloudletsSkipped;
    private boolean debugShockwaveTestCloudletSpawned;

    public NuclearExplosionEntity(EntityType<NuclearExplosionEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public NuclearExplosionEntity(Level level, Vec3 center) {
        this(ModEntities.NUCLEAR_EXPLOSION.get(), level);
        setPos(center.x, center.y, center.z);
    }

    public void configure(float strength, boolean destroyBlocks, boolean spawnCloud, boolean flashSky, boolean playSounds, @Nullable Entity source) {
        this.strength = strength;
        this.radius = DEFAULT_NUKE_RADIUS;
        entityData.set(DATA_RADIUS, this.radius);
        this.destroyBlocks = destroyBlocks;
        entityData.set(DATA_SPAWN_CLOUD, spawnCloud);
        entityData.set(DATA_FLASH_SKY, flashSky);
        this.playSounds = playSounds;
        this.sourceUuid = source == null ? null : source.getUUID();
        entityData.set(DATA_VISUAL_SEED, level().random.nextLong());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SPAWN_CLOUD, true);
        builder.define(DATA_FLASH_SKY, true);
        builder.define(DATA_VISUAL_SEED, 0L);
        builder.define(DATA_RADIUS, DEFAULT_NUKE_RADIUS);
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);

        if (level().isClientSide) {
            tickClientEffects();
        } else {
            tickServerEffects();
        }

        if (tickCount > ENTITY_LIFETIME_TICKS) {
            discard();
        }
    }

    private void tickServerEffects() {
        if (!explosionDone) {
            explosionDone = true;
            Entity source = sourceUuid == null || level().getServer() == null
                    ? null
                    : level().getServer().getPlayerList().getPlayer(sourceUuid);
            level().explode(
                    source,
                    getX(),
                    getY(),
                    getZ(),
                    strength,
                    destroyBlocks ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE
            );
        }

        tickShockwaveServer();
    }

    private void tickClientEffects() {
        tickMushroomCloudSimulation();
        tickCloudlets();
        if (DEBUG_FORCE_SHOCKWAVE_TEST_CLOUDLET && !debugShockwaveTestCloudletSpawned && tickCount >= 5) {
            debugShockwaveTestCloudletSpawned = true;
            cloudlets.add(new NuclearCloudlet(NuclearCloudletType.SHOCKWAVE, 5.0D, 2.0D, 0.0D, 200, 20.0F, 20.0F, getVisualSeed()));
        }
        boolean shockwaveVisualTick = tickCount % SHOCKWAVE_VISUAL_INTERVAL_TICKS == 0;
        if (shockwaveVisualTick) {
            spawnShockwaveCloudlets();
        }
        logShockwaveVisualDebug(shockwaveVisualTick);
        tickClientShockwaveArrivalSound();
    }

    private void tickMushroomCloudSimulation() {
        if (!shouldSpawnCloud()) {
            mushroomCloudSimulation = null;
            return;
        }
        if (mushroomCloudSimulation == null) {
            mushroomCloudSimulation = new NuclearMushroomCloudSimulation(getVisualSeed(), getRadius());
        }
        mushroomCloudSimulation.tick(level(), position());
    }

    private void tickCloudlets() {
        Iterator<NuclearCloudlet> iterator = cloudlets.iterator();
        while (iterator.hasNext()) {
            NuclearCloudlet cloudlet = iterator.next();
            cloudlet.tick();
            if (cloudlet.isExpired()) {
                iterator.remove();
            }
        }
    }

    private void spawnShockwaveCloudlets() {
        shockwaveSpawnMethodCalls++;
        double shockwaveRadius = getShockwaveRadius();
        double maxRadius = getShockwaveMaxRadius();
        if (shockwaveRadius <= 0.0D || shockwaveRadius > maxRadius) {
            return;
        }
        shockwaveSpawnConditionPasses++;

        float distanceFactor = (float) Mth.clamp(shockwaveRadius / maxRadius, 0.0D, 1.0D);
        float baseSize = Mth.clamp(2.5F + (float) shockwaveRadius * 0.025F, 3.0F, 8.0F);
        double desiredSpacing = Math.max(2.5D, baseSize * 0.75D);
        int count = Mth.clamp((int) (Math.PI * 2.0D * shockwaveRadius / desiredSpacing), SHOCKWAVE_MIN_PUFFS, SHOCKWAVE_MAX_PUFFS);
        RandomSource random = RandomSource.create(getVisualSeed() ^ 0x5DEECE66DL ^ tickCount * 104729L);
        int addedThisTick = 0;

        for (int index = 0; index < count; index++) {
            shockwaveCloudletsAttempted++;
            if (cloudlets.size() >= MAX_CLOUDLETS) {
                removeOldestCloudletForShockwave();
                if (cloudlets.size() >= MAX_CLOUDLETS) {
                    shockwaveCloudletsSkipped += count - index;
                    break;
                }
            }

            double angle = Math.PI * 2.0D * index / count + random.nextDouble() * 0.05D;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double puffRadius = shockwaveRadius - random.nextDouble() * SHOCKWAVE_BAND_BEHIND_BLOCKS + random.nextDouble() * SHOCKWAVE_BAND_AHEAD_BLOCKS;
            puffRadius = Mth.clamp(puffRadius, 0.0D, maxRadius);
            double worldX = getX() + cos * puffRadius;
            double worldZ = getZ() + sin * puffRadius;
            SurfaceSample surface = findShockwaveSurfaceTopY(worldX, worldZ, getY() + 1.0D);
            if (surface.chunkMissing()) {
                shockwaveChunkMissing++;
            } else if (surface.found()) {
                shockwaveSurfaceFound++;
            } else {
                shockwaveSurfaceNotFound++;
            }
            double surfaceY = surface.surfaceY();
            if (!Double.isFinite(surfaceY) || surfaceY <= level().getMinBuildHeight() || surfaceY >= level().getMaxBuildHeight() + 32.0D) {
                surfaceY = getY() + 1.0D;
                shockwaveInvalidY++;
            }
            double spawnY = surfaceY + 2.0D + random.nextDouble() * 1.5D;
            maybeLogShockwaveSurfaceSample(index, count, worldX, worldZ, surface, spawnY);
            addShockwaveCloudlet(random, worldX, spawnY, worldZ, cos, sin, baseSize, distanceFactor);
            addedThisTick++;
        }

        if (addedThisTick == 0 && count > 0) {
            spawnFallbackShockwaveRing(count, shockwaveRadius, baseSize, distanceFactor, random);
        }
    }

    private void spawnFallbackShockwaveRing(int expectedCount, double shockwaveRadius, float baseSize, float distanceFactor, RandomSource random) {
        int fallbackCount = Math.min(expectedCount, SHOCKWAVE_MIN_PUFFS);
        for (int index = 0; index < fallbackCount; index++) {
            if (cloudlets.size() >= MAX_CLOUDLETS) {
                removeOldestCloudletForShockwave();
                if (cloudlets.size() >= MAX_CLOUDLETS) {
                    shockwaveCloudletsSkipped += fallbackCount - index;
                    return;
                }
            }

            double angle = Math.PI * 2.0D * index / fallbackCount;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double worldX = getX() + cos * shockwaveRadius;
            double worldZ = getZ() + sin * shockwaveRadius;
            addShockwaveCloudlet(random, worldX, getY() + 2.0D, worldZ, cos, sin, baseSize, distanceFactor);
        }
    }

    private void addShockwaveCloudlet(
            RandomSource random,
            double worldX,
            double worldY,
            double worldZ,
            double cos,
            double sin,
            float baseSize,
            float distanceFactor
    ) {
        int lifetime = SHOCKWAVE_LIFETIME_MIN_TICKS + random.nextInt(SHOCKWAVE_LIFETIME_MAX_TICKS - SHOCKWAVE_LIFETIME_MIN_TICKS + 1);
        float size = baseSize * (0.82F + random.nextFloat() * 0.36F);
        double speed = 0.35D + random.nextDouble() * 0.40D + distanceFactor * 0.10D;
        double acceleration = 0.015D + random.nextDouble() * 0.015D;
        NuclearCloudlet puff = new NuclearCloudlet(
                NuclearCloudletType.SHOCKWAVE,
                worldX - getX(),
                worldY - getY(),
                worldZ - getZ(),
                lifetime,
                size,
                size + 2.0F + random.nextFloat() * 3.0F,
                random.nextLong(),
                cos * speed,
                0.03D + random.nextDouble() * 0.07D,
                sin * speed,
                cos * acceleration,
                sin * acceleration
        );
        cloudlets.add(puff);
        shockwaveCloudletsAdded++;
    }

    private SurfaceSample findShockwaveSurfaceTopY(double worldX, double worldZ, double fallbackY) {
        int blockX = Mth.floor(worldX);
        int blockZ = Mth.floor(worldZ);
        if (!level().hasChunkAt(new BlockPos(blockX, Mth.floor(getY()), blockZ))) {
            shockwaveFallbackEntityY++;
            return new SurfaceSample(fallbackY, false, true, true, false, true, blockX, blockZ, level().getMinBuildHeight(), level().getMaxBuildHeight() - 1, Integer.MIN_VALUE);
        }

        int minY = level().getMinBuildHeight();
        int maxY = level().getMaxBuildHeight() - 1;
        int heightmapY = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        boolean heightmapValid = heightmapY > minY + 1 && heightmapY <= maxY + 1;
        if (!heightmapValid) {
            shockwaveHeightmapInvalid++;
        }
        int startY = heightmapValid ? Mth.clamp(heightmapY + SHOCKWAVE_SURFACE_SCAN_PADDING, minY, maxY) : maxY;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos(blockX, startY, blockZ);
        CollisionContext context = CollisionContext.empty();

        for (int y = startY; y >= minY; y--) {
            mutablePos.set(blockX, y, blockZ);
            BlockState state = level().getBlockState(mutablePos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            if (!state.getCollisionShape(level(), mutablePos, context).isEmpty()) {
                shockwaveFoundSurfaceTopDown++;
                return new SurfaceSample(y + 1.0D, true, false, heightmapValid, false, false, blockX, blockZ, heightmapY, startY, y);
            }
        }

        if (heightmapY > minY + 1) {
            shockwaveFallbackHeightmap++;
            return new SurfaceSample(heightmapY, false, false, heightmapValid, true, false, blockX, blockZ, heightmapY, startY, Integer.MIN_VALUE);
        }

        shockwaveFallbackEntityY++;
        return new SurfaceSample(fallbackY, false, false, heightmapValid, false, true, blockX, blockZ, heightmapY, startY, Integer.MIN_VALUE);
    }

    private void maybeLogShockwaveSurfaceSample(int index, int count, double worldX, double worldZ, SurfaceSample surface, double spawnY) {
        if (!DEBUG_SHOCKWAVE_VISUALS || tickCount % 20 != 0 || index != 0) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke shockwave surface sample: id={} tick={} sample=1/{} bx={} bz={} worldX={} worldZ={} heightmapY={} startY={} foundY={} surfaceTopY={} spawnY={} heightmapValid={} fallbackHeightmap={} fallbackEntityY={} chunkMissing={}",
                getId(),
                tickCount,
                count,
                surface.blockX(),
                surface.blockZ(),
                worldX,
                worldZ,
                surface.heightmapY(),
                surface.startY(),
                surface.foundY(),
                surface.surfaceY(),
                spawnY,
                surface.heightmapValid(),
                surface.usedHeightmapFallback(),
                surface.usedEntityFallback(),
                surface.chunkMissing()
        );
    }

    private void logShockwaveVisualDebug(boolean shockwaveVisualTick) {
        if (!DEBUG_SHOCKWAVE_VISUALS || tickCount % 20 != 0) {
            return;
        }

        SkyesNuclearTech.LOGGER.info(
                "Nuke shockwave client debug: id={} tick={} client={} radius={} max={} intervalTick={} methodCalls={} conditionPasses={} attempted={} added={} skipped={} surfaceFound={} surfaceNotFound={} chunkMissing={} invalidY={} heightmapInvalid={} foundTopDown={} fallbackHeightmap={} fallbackEntityY={} shockwaveCloudlets={} totalCloudlets={} spawnCloud={}",
                getId(),
                tickCount,
                level().isClientSide,
                getShockwaveRadius(),
                getShockwaveMaxRadius(),
                shockwaveVisualTick,
                shockwaveSpawnMethodCalls,
                shockwaveSpawnConditionPasses,
                shockwaveCloudletsAttempted,
                shockwaveCloudletsAdded,
                shockwaveCloudletsSkipped,
                shockwaveSurfaceFound,
                shockwaveSurfaceNotFound,
                shockwaveChunkMissing,
                shockwaveInvalidY,
                shockwaveHeightmapInvalid,
                shockwaveFoundSurfaceTopDown,
                shockwaveFallbackHeightmap,
                shockwaveFallbackEntityY,
                shockwaveCloudletCount(),
                cloudlets.size(),
                shouldSpawnCloud()
        );
    }

    private record SurfaceSample(
            double surfaceY,
            boolean found,
            boolean chunkMissing,
            boolean heightmapValid,
            boolean usedHeightmapFallback,
            boolean usedEntityFallback,
            int blockX,
            int blockZ,
            int heightmapY,
            int startY,
            int foundY
    ) {
    }

    private int nonShockwaveCloudletCount() {
        int count = 0;
        for (NuclearCloudlet cloudlet : cloudlets) {
            if (!cloudlet.isShockwaveVisual()) {
                count++;
            }
        }
        return count;
    }

    private void removeOldestNonShockwaveCloudlet() {
        for (Iterator<NuclearCloudlet> iterator = cloudlets.iterator(); iterator.hasNext(); ) {
            if (!iterator.next().isShockwaveVisual()) {
                iterator.remove();
                return;
            }
        }
    }

    private void removeOldestCloudletForShockwave() {
        int oldestShockwaveIndex = -1;
        int oldestShockwaveAge = -1;
        for (int index = 0; index < cloudlets.size(); index++) {
            NuclearCloudlet cloudlet = cloudlets.get(index);
            if (!cloudlet.isShockwaveVisual()) {
                cloudlets.remove(index);
                return;
            }
            if (cloudlet.type() == NuclearCloudletType.SHOCKWAVE && cloudlet.age() > oldestShockwaveAge) {
                oldestShockwaveAge = cloudlet.age();
                oldestShockwaveIndex = index;
            }
        }
        if (oldestShockwaveIndex >= 0) {
            cloudlets.remove(oldestShockwaveIndex);
        }
    }

    private int shockwaveCloudletCount() {
        int count = 0;
        for (NuclearCloudlet cloudlet : cloudlets) {
            if (cloudlet.type() == NuclearCloudletType.SHOCKWAVE) {
                count++;
            }
        }
        return count;
    }

    private void tickClientShockwaveArrivalSound() {
        try {
            Class<?> soundClient = Class.forName("com.skyeshade.skyent.client.effect.NukeShockwaveSoundClient");
            soundClient.getMethod("tick", NuclearExplosionEntity.class).invoke(null, this);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to tick client nuke shockwave arrival sound", exception);
        }
    }

    private void tickShockwaveServer() {
        double currentRadius = getShockwaveRadius();
        double maxRadius = getShockwaveMaxRadius();
        if (currentRadius <= 0.0D || currentRadius > maxRadius + SHOCKWAVE_DAMAGE_BAND_WIDTH) {
            return;
        }

        double previousRadius = Math.max(0.0D, (tickCount - 1) * SHOCKWAVE_SPEED_BLOCKS_PER_TICK);
        damageEntitiesInWavefront(previousRadius, currentRadius);
    }

    private void damageEntitiesInWavefront(double previousRadius, double currentRadius) {
        double searchRadius = Math.min(currentRadius + SHOCKWAVE_DAMAGE_BAND_WIDTH, getShockwaveMaxRadius() + SHOCKWAVE_DAMAGE_BAND_WIDTH);
        AABB search = new AABB(
                getX() - searchRadius,
                getY() - 128.0D,
                getZ() - searchRadius,
                getX() + searchRadius,
                getY() + 256.0D,
                getZ() + searchRadius
        );

        Entity source = sourceUuid == null || level().getServer() == null
                ? null
                : level().getServer().getPlayerList().getPlayer(sourceUuid);
        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, search, entity -> !entity.isRemoved())) {
            if (shockwaveDamagedEntities.contains(entity.getUUID())) {
                continue;
            }
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
                continue;
            }

            double distance = horizontalDistanceFromCenter(entity.position());
            if (distance <= previousRadius || distance > currentRadius + SHOCKWAVE_DAMAGE_BAND_WIDTH || distance > getShockwaveMaxRadius()) {
                continue;
            }

            float falloff = 1.0F - (float) Mth.clamp(distance / getShockwaveMaxRadius(), 0.0D, 1.0D) * 0.5F;
            entity.hurt(level().damageSources().explosion(this, source), 1000.0F * falloff);
            Vec3 knockback = entity.position().subtract(position());
            Vec3 horizontal = new Vec3(knockback.x, 0.0D, knockback.z);
            if (horizontal.lengthSqr() > 1.0E-6D) {
                Vec3 direction = horizontal.normalize();
                double strength = 3.0D * falloff;
                entity.push(direction.x * strength, 0.65D * falloff, direction.z * strength);
                entity.hurtMarked = true;
            }
            shockwaveDamagedEntities.add(entity.getUUID());
        }
    }

    private double horizontalDistanceFromCenter(Vec3 position) {
        double dx = position.x - getX();
        double dz = position.z - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double getShockwaveRadius() {
        return tickCount * SHOCKWAVE_SPEED_BLOCKS_PER_TICK;
    }

    private double getShockwaveMaxRadius() {
        return getRadius() * SHOCKWAVE_MAX_RADIUS_MULTIPLIER;
    }

    public List<NuclearCloudlet> getCloudlets() {
        return cloudlets;
    }

    public List<NuclearMushroomCloudSimulation.MushroomCloudlet> getMushroomCloudlets() {
        return mushroomCloudSimulation == null ? List.of() : mushroomCloudSimulation.cloudlets();
    }

    public long getVisualSeed() {
        long seed = entityData.get(DATA_VISUAL_SEED);
        return seed == 0L ? getUUID().getLeastSignificantBits() : seed;
    }

    public boolean shouldSpawnCloud() {
        return entityData.get(DATA_SPAWN_CLOUD);
    }

    public boolean shouldFlashSky() {
        return entityData.get(DATA_FLASH_SKY);
    }

    public float getRadius() {
        return entityData.get(DATA_RADIUS);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        strength = compound.getFloat("Strength");
        if (strength <= 0.0F) {
            strength = VANILLA_EXPLOSION_STRENGTH;
        }
        radius = compound.contains("Radius") ? compound.getFloat("Radius") : DEFAULT_NUKE_RADIUS;
        entityData.set(DATA_RADIUS, radius);
        destroyBlocks = !compound.contains("DestroyBlocks") || compound.getBoolean("DestroyBlocks");
        entityData.set(DATA_SPAWN_CLOUD, !compound.contains("SpawnCloud") || compound.getBoolean("SpawnCloud"));
        entityData.set(DATA_FLASH_SKY, !compound.contains("FlashSky") || compound.getBoolean("FlashSky"));
        playSounds = !compound.contains("PlaySounds") || compound.getBoolean("PlaySounds");
        explosionDone = compound.getBoolean("ExplosionDone");
        entityData.set(DATA_VISUAL_SEED, compound.contains("VisualSeed") ? compound.getLong("VisualSeed") : level().random.nextLong());
        if (compound.hasUUID("SourceUuid")) {
            sourceUuid = compound.getUUID("SourceUuid");
        } else {
            sourceUuid = null;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putFloat("Strength", strength);
        compound.putFloat("Radius", getRadius());
        compound.putBoolean("DestroyBlocks", destroyBlocks);
        compound.putBoolean("SpawnCloud", shouldSpawnCloud());
        compound.putBoolean("FlashSky", shouldFlashSky());
        compound.putBoolean("PlaySounds", playSounds);
        compound.putBoolean("ExplosionDone", explosionDone);
        compound.putLong("VisualSeed", getVisualSeed());
        if (sourceUuid != null) {
            compound.putUUID("SourceUuid", sourceUuid);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 512.0D * 512.0D;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        double horizontalRange = getShockwaveMaxRadius() + 96.0D;
        return new AABB(
                getX() - horizontalRange,
                getY() - 16.0D,
                getZ() - horizontalRange,
                getX() + horizontalRange,
                getY() + 240.0D,
                getZ() + horizontalRange
        );
    }

    public enum NuclearCloudletType {
        SHOCKWAVE
    }

    public static final class NuclearCloudlet {
        private final NuclearCloudletType type;
        private final int lifetime;
        private final float startSize;
        private final float growSize;
        private final long seed;
        private double x;
        private double y;
        private double z;
        private double velocityX;
        private double velocityY;
        private double velocityZ;
        private double accelerationX;
        private double accelerationZ;
        private double prevX;
        private double prevY;
        private double prevZ;
        private int age;

        private NuclearCloudlet(NuclearCloudletType type, double x, double y, double z, int lifetime, float startSize, float growSize, long seed) {
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
        }

        private NuclearCloudlet(
                NuclearCloudletType type,
                double x,
                double y,
                double z,
                int lifetime,
                float startSize,
                float growSize,
                long seed,
                double velocityX,
                double velocityY,
                double velocityZ,
                double accelerationX,
                double accelerationZ
        ) {
            this(type, x, y, z, lifetime, startSize, growSize, seed);
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.velocityZ = velocityZ;
            this.accelerationX = accelerationX;
            this.accelerationZ = accelerationZ;
        }

        private void tick() {
            prevX = x;
            prevY = y;
            prevZ = z;
            velocityX += accelerationX;
            velocityZ += accelerationZ;
            x += velocityX;
            y += velocityY;
            z += velocityZ;
            velocityX *= 0.96D;
            velocityY *= 0.96D;
            velocityZ *= 0.96D;

            age++;
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
            return Mth.lerp(progress, startSize, growSize);
        }

        public float alpha(float partialTick) {
            float progress = Mth.clamp((age + partialTick) / (float) lifetime, 0.0F, 1.0F);
            if (type == NuclearCloudletType.SHOCKWAVE) {
                return progress > 0.72F ? Mth.lerp((progress - 0.72F) / 0.28F, 0.95F, 0.0F) : 0.95F;
            }
            return progress > 0.68F ? Mth.lerp((progress - 0.68F) / 0.32F, 0.86F, 0.0F) : 0.86F;
        }

        public boolean isShockwaveVisual() {
            return type == NuclearCloudletType.SHOCKWAVE;
        }

        public NuclearCloudletType type() {
            return type;
        }

        public int age() {
            return age;
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
            float progress = Mth.clamp((age + partialTick) / (float) lifetime, 0.0F, 1.0F);
            float red = Mth.lerp(progress, 0.18F, 0.07F);
            float green = Mth.lerp(progress, 0.16F, 0.065F);
            float blue = Mth.lerp(progress, 0.14F, 0.06F);
            return Math.round(Mth.clamp(component == 0 ? red : component == 1 ? green : blue, 0.0F, 1.0F) * 255.0F);
        }
    }
}
