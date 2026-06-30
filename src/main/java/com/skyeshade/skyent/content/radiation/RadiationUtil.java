package com.skyeshade.skyent.content.radiation;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.network.RadiationRayBatchPayload;
import com.skyeshade.skyent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

public final class RadiationUtil {
    private static final boolean DEBUG_RADIATION = false;
    private static final boolean DEBUG_RADIATION_RANGE = false;
    private static final double BASE_ENVIRONMENT_INTERACTION_CHANCE = 0.75D;
    private static final double ENVIRONMENT_INTERACTION_CHANCE_MULTIPLIER = 10.0D;
    private static final double FULL_ENVIRONMENTAL_STRENGTH = 50.0D;
    private static final double RADIATION_RANGE_EPSILON = 1.0E-6D;
    private static final int MIN_ENVIRONMENT_ATTEMPTS_PER_RANDOM_TICK = 1;
    private static final int MAX_ENVIRONMENT_ATTEMPTS_PER_RANDOM_TICK = 8;
    private static final int MAX_FULL_RAY_ATTEMPTS_PER_RANDOM_TICK = 16;
    private static final int MIN_ENVIRONMENT_CONVERSIONS_PER_RANDOM_TICK = 1;
    private static final int MAX_ENVIRONMENT_CONVERSIONS_PER_RANDOM_TICK = 3;
    private static final int MAX_FULL_RAY_CONVERSIONS_PER_RANDOM_TICK = 8;
    private static final int MAX_TARGET_SELECTION_TRIES = 8;
    private static final double CHEAP_DISTANCE_BIAS_POWER = 2.0D;
    private static final int ENVIRONMENT_VERTICAL_TARGET_RADIUS = 2;
    private static final double RAY_PATH_STEP = 0.25D;
    private static final double MIN_RAY_MULTIPLIER = 0.01D;

    private RadiationUtil() {
    }

    public static void applyCheapEnvironmentalRadiation(ServerLevel level, BlockPos sourcePos, double strength, int range, RandomSource random) {
        if (strength <= 0.0D || range <= 0) {
            return;
        }

        DebugRayCollector debugRays = DebugRayCollector.create(level, sourcePos, strength, range);
        int attempts = environmentAttempts(strength);
        int maxConversions = environmentConversions(strength);
        int conversions = 0;
        for (int attempt = 0; attempt < attempts && conversions < maxConversions; attempt++) {
            BlockPos targetPos = pickBiasedTarget(sourcePos, range, random, CHEAP_DISTANCE_BIAS_POWER);
            if (attemptCheapEnvironmentalInteraction(level, sourcePos, targetPos, strength, range, random, debugRays)) {
                conversions++;
            }
        }

        debugRays.send(level);
    }

    public static void applyFullEnvironmentalRadiation(ServerLevel level, BlockPos sourcePos, double strength, int range, RandomSource random) {
        applyFullEnvironmentalRadiation(level, sourcePos, strength, range, fullRayAttempts(strength), fullRayConversions(strength), random);
    }

    public static void applyFullEnvironmentalRadiation(ServerLevel level, BlockPos sourcePos, double strength, int range, int attempts, int maxConversions, RandomSource random) {
        applyFullEnvironmentalRadiation(level, sourcePos, Vec3.atCenterOf(sourcePos), strength, range, attempts, maxConversions, random);
    }

    public static void applyFullEnvironmentalRadiation(ServerLevel level, Vec3 sourceCenter, double strength, int range, int attempts, int maxConversions, RandomSource random) {
        applyFullEnvironmentalRadiation(level, BlockPos.containing(sourceCenter), sourceCenter, strength, range, attempts, maxConversions, random);
    }

    private static void applyFullEnvironmentalRadiation(ServerLevel level, BlockPos sourcePos, Vec3 sourceCenter, double strength, int range, int attempts, int maxConversions, RandomSource random) {
        if (strength <= 0.0D || range <= 0) {
            return;
        }

        DebugRayCollector debugRays = DebugRayCollector.create(level, sourcePos, sourceCenter, strength, range);
        attempts = Mth.clamp(attempts, 0, MAX_FULL_RAY_ATTEMPTS_PER_RANDOM_TICK);
        maxConversions = Mth.clamp(maxConversions, 0, MAX_FULL_RAY_CONVERSIONS_PER_RANDOM_TICK);
        int conversions = 0;
        for (int attempt = 0; attempt < attempts && conversions < maxConversions; attempt++) {
            conversions += attemptFullRayEnvironmentalInteraction(level, sourcePos, sourceCenter, randomDirection(random), strength, range, random, debugRays, maxConversions - conversions);
        }

        debugRays.send(level);
    }

    private static BlockState getEnvironmentalRadiationConversion(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK)) {
            return ModBlocks.DEAD_GRASS.get().defaultBlockState();
        }
        if (isShortVegetation(state)) {
            return ModBlocks.DEAD_SHORT_GRASS.get().defaultBlockState();
        }
        if (isTallVegetation(state)) {
            return ModBlocks.DEAD_TALL_GRASS.get().defaultBlockState();
        }
        if (state.is(Blocks.OAK_LEAVES)) {
            return copyLeafProperties(state, ModBlocks.DEAD_OAK_LEAVES.get().defaultBlockState());
        }
        if (state.is(Blocks.BIRCH_LEAVES)) {
            return copyLeafProperties(state, ModBlocks.DEAD_BIRCH_LEAVES.get().defaultBlockState());
        }
        if (state.is(Blocks.SPRUCE_LEAVES)) {
            return copyLeafProperties(state, ModBlocks.DEAD_SPRUCE_LEAVES.get().defaultBlockState());
        }
        if (state.is(Blocks.JUNGLE_LEAVES)) {
            return copyLeafProperties(state, ModBlocks.DEAD_JUNGLE_LEAVES.get().defaultBlockState());
        }
        if (state.is(Blocks.ACACIA_LEAVES)) {
            return copyLeafProperties(state, ModBlocks.DEAD_ACACIA_LEAVES.get().defaultBlockState());
        }
        if (state.is(Blocks.DARK_OAK_LEAVES)) {
            return copyLeafProperties(state, ModBlocks.DEAD_DARK_OAK_LEAVES.get().defaultBlockState());
        }
        if (state.is(Blocks.MANGROVE_LEAVES)) {
            return copyLeafProperties(state, ModBlocks.DEAD_MANGROVE_LEAVES.get().defaultBlockState());
        }
        if (state.is(Blocks.CHERRY_LEAVES)) {
            return copyLeafProperties(state, ModBlocks.DEAD_CHERRY_LEAVES.get().defaultBlockState());
        }
        if (state.is(Blocks.AZALEA_LEAVES)) {
            return copyLeafProperties(state, ModBlocks.DEAD_AZALEA_LEAVES.get().defaultBlockState());
        }
        if (state.is(Blocks.FLOWERING_AZALEA_LEAVES)) {
            return copyLeafProperties(state, ModBlocks.DEAD_FLOWERING_AZALEA_LEAVES.get().defaultBlockState());
        }
        return null;
    }

    private static boolean applyEnvironmentalRadiationConversion(ServerLevel level, BlockPos pos, BlockState state, int flags) {
        if (state.is(Blocks.GRASS_BLOCK)) {
            level.setBlock(pos, ModBlocks.DEAD_GRASS.get().defaultBlockState(), flags);
            convertVegetationAboveDeadGrass(level, pos, flags);
            return true;
        }
        if (isTallVegetation(state)) {
            convertTallVegetation(level, pos, state, flags);
            return true;
        }
        if (isShortVegetation(state)) {
            level.setBlock(pos, ModBlocks.DEAD_SHORT_GRASS.get().defaultBlockState(), flags);
            return true;
        }

        BlockState conversionState = getEnvironmentalRadiationConversion(state);
        if (conversionState == null) {
            return false;
        }

        level.setBlock(pos, conversionState, flags);
        return true;
    }

    private static void convertVegetationAboveDeadGrass(ServerLevel level, BlockPos deadGrassPos, int flags) {
        BlockPos abovePos = deadGrassPos.above();
        if (!level.hasChunkAt(abovePos)) {
            return;
        }

        BlockState aboveState = level.getBlockState(abovePos);
        if (isTallVegetation(aboveState)) {
            convertTallVegetation(level, abovePos, aboveState, flags);
        } else if (isShortVegetation(aboveState)) {
            level.setBlock(abovePos, ModBlocks.DEAD_SHORT_GRASS.get().defaultBlockState(), flags);
        }
    }

    private static void convertTallVegetation(ServerLevel level, BlockPos pos, BlockState state, int flags) {
        BlockPos lowerPos = pos;
        BlockPos upperPos = pos.above();
        if (state.hasProperty(DoublePlantBlock.HALF) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
            lowerPos = pos.below();
            upperPos = pos;
        }

        if (level.hasChunkAt(lowerPos)) {
            level.setBlock(lowerPos, ModBlocks.DEAD_TALL_GRASS.get().defaultBlockState(), flags);
        }
        if (level.hasChunkAt(upperPos)) {
            level.setBlock(upperPos, Blocks.AIR.defaultBlockState(), flags);
        }
    }

    private static boolean isShortVegetation(BlockState state) {
        return state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.DANDELION)
                || state.is(Blocks.POPPY)
                || state.is(Blocks.BLUE_ORCHID)
                || state.is(Blocks.ALLIUM)
                || state.is(Blocks.AZURE_BLUET)
                || state.is(Blocks.RED_TULIP)
                || state.is(Blocks.ORANGE_TULIP)
                || state.is(Blocks.WHITE_TULIP)
                || state.is(Blocks.PINK_TULIP)
                || state.is(Blocks.OXEYE_DAISY)
                || state.is(Blocks.CORNFLOWER)
                || state.is(Blocks.LILY_OF_THE_VALLEY)
                || state.is(Blocks.TORCHFLOWER);
    }

    private static boolean isTallVegetation(BlockState state) {
        return state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.SUNFLOWER)
                || state.is(Blocks.LILAC)
                || state.is(Blocks.ROSE_BUSH)
                || state.is(Blocks.PEONY);
    }

    private static BlockState copyLeafProperties(BlockState source, BlockState target) {
        if (source.hasProperty(LeavesBlock.DISTANCE) && target.hasProperty(LeavesBlock.DISTANCE)) {
            target = target.setValue(LeavesBlock.DISTANCE, source.getValue(LeavesBlock.DISTANCE));
        }
        if (source.hasProperty(LeavesBlock.PERSISTENT) && target.hasProperty(LeavesBlock.PERSISTENT)) {
            target = target.setValue(LeavesBlock.PERSISTENT, source.getValue(LeavesBlock.PERSISTENT));
        }
        if (source.hasProperty(LeavesBlock.WATERLOGGED) && target.hasProperty(LeavesBlock.WATERLOGGED)) {
            target = target.setValue(LeavesBlock.WATERLOGGED, source.getValue(LeavesBlock.WATERLOGGED));
        }
        return target;
    }

    private static BlockPos pickBiasedTarget(BlockPos sourcePos, int range, RandomSource random, double biasPower) {
        for (int attempt = 0; attempt < MAX_TARGET_SELECTION_TRIES; attempt++) {
            Vec3 offset = randomDirection(random).scale(range * Math.pow(random.nextDouble(), biasPower));
            BlockPos targetPos = BlockPos.containing(
                    sourcePos.getX() + 0.5D + offset.x,
                    sourcePos.getY() + 0.5D + clampVerticalOffset(offset.y),
                    sourcePos.getZ() + 0.5D + offset.z
            );
            if (isWithinRadiationRange(sourcePos, targetPos, range)) {
                debugSelectedTarget(sourcePos, targetPos, range, true);
                return targetPos;
            }
        }

        return randomInSphereTarget(sourcePos, range, random);
    }

    private static BlockPos randomInSphereTarget(BlockPos sourcePos, int range, RandomSource random) {
        while (true) {
            BlockPos targetPos = sourcePos.offset(
                    random.nextInt(range * 2 + 1) - range,
                    random.nextInt(ENVIRONMENT_VERTICAL_TARGET_RADIUS * 2 + 1) - ENVIRONMENT_VERTICAL_TARGET_RADIUS,
                    random.nextInt(range * 2 + 1) - range
            );
            if (isWithinRadiationRange(sourcePos, targetPos, range)) {
                debugSelectedTarget(sourcePos, targetPos, range, true);
                return targetPos;
            }
        }
    }

    private static double clampVerticalOffset(double y) {
        return Mth.clamp(y, -ENVIRONMENT_VERTICAL_TARGET_RADIUS, ENVIRONMENT_VERTICAL_TARGET_RADIUS);
    }

    private static Vec3 randomDirection(RandomSource random) {
        double theta = random.nextDouble() * Math.PI * 2.0D;
        double y = random.nextDouble() * 2.0D - 1.0D;
        double horizontal = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
        return new Vec3(Math.cos(theta) * horizontal, y, Math.sin(theta) * horizontal);
    }

    private static boolean attemptCheapEnvironmentalInteraction(ServerLevel level, BlockPos sourcePos, BlockPos targetPos, double strength, int range, RandomSource random, DebugRayCollector debugRays) {
        if (!level.hasChunkAt(targetPos)) {
            debugRays.record(targetPos, Vec3.atCenterOf(targetPos), 0.0D, false, false, false, 0, 0);
            return false;
        }

        BlockState targetState = level.getBlockState(targetPos);
        BlockState conversionState = getEnvironmentalRadiationConversion(targetState);
        if (conversionState == null) {
            debugRays.record(targetPos, Vec3.atCenterOf(targetPos), 0.0D, false, false, false, 0, 0);
            return false;
        }

        double finalChance = interactionChance(sourcePos, targetPos, strength, range);
        boolean affected = false;
        if (finalChance > 0.0D && random.nextDouble() < finalChance) {
            affected = applyEnvironmentalRadiationConversion(level, targetPos, targetState, Block.UPDATE_CLIENTS);
            if (DEBUG_RADIATION) {
                SkyesNuclearTech.LOGGER.info("Cheap radiation converted vegetation at {}", targetPos);
            }
        }

        debugRays.record(targetPos, Vec3.atCenterOf(targetPos), finalChance, false, true, affected, 1, affected ? 1 : 0);
        return affected;
    }

    private static int attemptFullRayEnvironmentalInteraction(ServerLevel level, BlockPos sourcePos, Vec3 direction, double strength, int range, RandomSource random, DebugRayCollector debugRays, int remainingConversions) {
        return attemptFullRayEnvironmentalInteraction(level, sourcePos, Vec3.atCenterOf(sourcePos), direction, strength, range, random, debugRays, remainingConversions);
    }

    private static int attemptFullRayEnvironmentalInteraction(ServerLevel level, BlockPos sourcePos, Vec3 sourceCenter, Vec3 direction, double strength, int range, RandomSource random, DebugRayCollector debugRays, int remainingConversions) {
        Vec3 normalizedDirection = direction.lengthSqr() < 1.0E-6D ? direction : direction.normalize();
        BlockPos rayTargetPos = BlockPos.containing(sourceCenter.add(normalizedDirection.scale(range)));
        RayInteractionResult result = traceEnvironmentalRay(level, sourcePos, sourceCenter, normalizedDirection, strength, range, random, remainingConversions);
        debugRays.record(rayTargetPos, result.end, result.lastChance, result.blocked, true, result.conversions > 0, result.convertibleHits, result.conversions);
        return result.conversions;
    }

    private static RayInteractionResult traceEnvironmentalRay(ServerLevel level, BlockPos sourcePos, Vec3 direction, double strength, int range, RandomSource random, int remainingConversions) {
        return traceEnvironmentalRay(level, sourcePos, Vec3.atCenterOf(sourcePos), direction, strength, range, random, remainingConversions);
    }

    private static RayInteractionResult traceEnvironmentalRay(ServerLevel level, BlockPos sourcePos, Vec3 sourceCenter, Vec3 direction, double strength, int range, RandomSource random, int remainingConversions) {
        Vec3 start = sourceCenter;
        if (direction.lengthSqr() < 1.0E-6D) {
            return new RayInteractionResult(start, false, 0.0D, 0, 0);
        }

        direction = direction.normalize();
        Vec3 maxEnd = start.add(direction.scale(range));
        Set<BlockPos> visited = new HashSet<>();
        int steps = Mth.ceil(range / RAY_PATH_STEP);
        int conversions = 0;
        int convertibleHits = 0;
        double lastChance = 0.0D;
        double rayMultiplier = 1.0D;

        for (int step = 1; step <= steps; step++) {
            double distance = Math.min(range, step * RAY_PATH_STEP);
            Vec3 point = start.add(direction.scale(distance));
            BlockPos currentPos = BlockPos.containing(point);
            if (!visited.add(currentPos) || currentPos.equals(sourcePos)) {
                continue;
            }

            if (!level.hasChunkAt(currentPos)) {
                return new RayInteractionResult(point, true, lastChance, convertibleHits, conversions);
            }

            BlockState currentState = level.getBlockState(currentPos);
            BlockState conversionState = getEnvironmentalRadiationConversion(currentState);
            if (conversionState != null) {
                convertibleHits++;
                lastChance = interactionChance(sourceCenter, currentPos, strength, range) * rayMultiplier;
                if (conversions < remainingConversions && lastChance > 0.0D && random.nextDouble() < lastChance) {
                    if (applyEnvironmentalRadiationConversion(level, currentPos, currentState, Block.UPDATE_CLIENTS)) {
                        conversions++;
                    }
                    if (DEBUG_RADIATION) {
                        SkyesNuclearTech.LOGGER.info("Full ray radiation converted vegetation at {}", currentPos);
                    }
                    if (conversions >= remainingConversions) {
                        return new RayInteractionResult(maxEnd, false, lastChance, convertibleHits, conversions);
                    }
                }
            }

            rayMultiplier *= environmentalRadiationTransmission(currentState, level, currentPos);
            if (rayMultiplier <= MIN_RAY_MULTIPLIER) {
                return new RayInteractionResult(Vec3.atCenterOf(currentPos), true, lastChance, convertibleHits, conversions);
            }
        }

        return new RayInteractionResult(maxEnd, false, lastChance, convertibleHits, conversions);
    }

    public static double environmentalRadiationTransmission(BlockState state, ServerLevel level, BlockPos pos) {
        if (state.isAir()) {
            return 1.0D;
        }
        if (state.is(Blocks.GRASS_BLOCK) || state.is(ModBlocks.DEAD_GRASS.get()) || state.is(Blocks.DIRT)) {
            return 0.95D;
        }
        if (state.getBlock() instanceof LeavesBlock) {
            return 0.98D;
        }
        if (state.getFluidState().is(Fluids.WATER)) {
            return 0.85D;
        }
        OptionalDouble customTransmission = RadiationBlockProfiles.getCustomTransmission(state);
        if (customTransmission.isPresent()) {
            return customTransmission.getAsDouble();
        }
        if (state.is(BlockTags.LOGS)) {
            return 0.90D;
        }
        if (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)) {
            return 0.75D;
        }
        if (state.is(Blocks.IRON_BLOCK)) {
            return 0.50D;
        }
        if (!state.isSolidRender(level, pos)) {
            return 1.0D;
        }
        return 0.80D;
    }

    private static double interactionChance(BlockPos sourcePos, BlockPos targetPos, double strength, int range) {
        return interactionChance(Vec3.atCenterOf(sourcePos), targetPos, strength, range);
    }

    private static double interactionChance(Vec3 sourceCenter, BlockPos targetPos, double strength, int range) {
        double distance = sourceCenter.distanceTo(Vec3.atCenterOf(targetPos));
        double closeness = Mth.clamp(1.0D - distance / range, 0.0D, 1.0D);
        return Mth.clamp(BASE_ENVIRONMENT_INTERACTION_CHANCE * ENVIRONMENT_INTERACTION_CHANCE_MULTIPLIER * strengthFactor(strength) * closeness * closeness, 0.0D, 1.0D);
    }

    private static double distanceBetweenCenters(BlockPos first, BlockPos second) {
        return Vec3.atCenterOf(first).distanceTo(Vec3.atCenterOf(second));
    }

    private static boolean isWithinRadiationRange(BlockPos sourcePos, BlockPos targetPos, int range) {
        return distanceBetweenCenters(sourcePos, targetPos) <= range + RADIATION_RANGE_EPSILON;
    }

    private static void debugSelectedTarget(BlockPos sourcePos, BlockPos targetPos, int range, boolean selected) {
        if (DEBUG_RADIATION_RANGE) {
            SkyesNuclearTech.LOGGER.info(
                    "Radiation target source={} target={} distance={} range={} withinRange={} selected={}",
                    sourcePos,
                    targetPos,
                    distanceBetweenCenters(sourcePos, targetPos),
                    range,
                    isWithinRadiationRange(sourcePos, targetPos, range),
                    selected
            );
        }
    }

    private static double strengthFactor(double strength) {
        return Math.min(1.0D, strength / FULL_ENVIRONMENTAL_STRENGTH);
    }

    private static int environmentAttempts(double strength) {
        double factor = strengthFactor(strength);
        int attempts = MIN_ENVIRONMENT_ATTEMPTS_PER_RANDOM_TICK + Mth.floor(factor * (MAX_ENVIRONMENT_ATTEMPTS_PER_RANDOM_TICK - MIN_ENVIRONMENT_ATTEMPTS_PER_RANDOM_TICK));
        return Mth.clamp(attempts, MIN_ENVIRONMENT_ATTEMPTS_PER_RANDOM_TICK, MAX_ENVIRONMENT_ATTEMPTS_PER_RANDOM_TICK);
    }

    private static int environmentConversions(double strength) {
        double factor = strengthFactor(strength);
        int conversions = MIN_ENVIRONMENT_CONVERSIONS_PER_RANDOM_TICK + Mth.floor(factor * (MAX_ENVIRONMENT_CONVERSIONS_PER_RANDOM_TICK - MIN_ENVIRONMENT_CONVERSIONS_PER_RANDOM_TICK));
        return Mth.clamp(conversions, MIN_ENVIRONMENT_CONVERSIONS_PER_RANDOM_TICK, MAX_ENVIRONMENT_CONVERSIONS_PER_RANDOM_TICK);
    }

    private static int fullRayAttempts(double strength) {
        if (strength <= FULL_ENVIRONMENTAL_STRENGTH) {
            return environmentAttempts(strength);
        }

        double hotFactor = Math.log10(strength / FULL_ENVIRONMENTAL_STRENGTH + 1.0D);
        int attempts = MAX_ENVIRONMENT_ATTEMPTS_PER_RANDOM_TICK + Mth.floor(hotFactor * 16.0D);
        return Mth.clamp(attempts, MAX_ENVIRONMENT_ATTEMPTS_PER_RANDOM_TICK, MAX_FULL_RAY_ATTEMPTS_PER_RANDOM_TICK);
    }

    private static int fullRayConversions(double strength) {
        if (strength <= FULL_ENVIRONMENTAL_STRENGTH) {
            return environmentConversions(strength);
        }

        double hotFactor = Math.log10(strength / FULL_ENVIRONMENTAL_STRENGTH + 1.0D);
        int conversions = MAX_ENVIRONMENT_CONVERSIONS_PER_RANDOM_TICK + Mth.floor(hotFactor * 8.0D);
        return Mth.clamp(conversions, MAX_ENVIRONMENT_CONVERSIONS_PER_RANDOM_TICK, MAX_FULL_RAY_CONVERSIONS_PER_RANDOM_TICK);
    }

    private static final class DebugRayCollector {
        private final Vec3 sourceCenter;
        private final BlockPos sourcePos;
        private final double strength;
        private final int range;
        private final boolean enabled;
        private final List<RadiationRayBatchPayload.Ray> rays;

        private DebugRayCollector(Vec3 sourceCenter, BlockPos sourcePos, double strength, int range, boolean enabled) {
            this.sourceCenter = sourceCenter;
            this.sourcePos = sourcePos;
            this.strength = strength;
            this.range = range;
            this.enabled = enabled;
            this.rays = enabled ? new ArrayList<>() : List.of();
        }

        private static DebugRayCollector create(ServerLevel level, BlockPos sourcePos, double strength, int range) {
            Vec3 sourceCenter = Vec3.atCenterOf(sourcePos);
            return create(level, sourcePos, sourceCenter, strength, range);
        }

        private static DebugRayCollector create(ServerLevel level, BlockPos sourcePos, Vec3 sourceCenter, double strength, int range) {
            return new DebugRayCollector(sourceCenter, sourcePos, strength, range, RadiationDebugRays.hasNearbyEnabledPlayers(level, sourceCenter));
        }

        private void record(BlockPos targetPos, Vec3 end, double finalChance, boolean blocked, boolean validTarget, boolean affectedBlock, int convertibleHits, int convertedCount) {
            if (!enabled || rays.size() >= RadiationRayBatchPayload.MAX_RAYS_PER_BATCH) {
                return;
            }

            rays.add(new RadiationRayBatchPayload.Ray(
                    sourceCenter,
                    end,
                    sourcePos,
                    targetPos,
                    strength,
                    range,
                    finalChance,
                    blocked,
                    validTarget,
                    affectedBlock,
                    convertibleHits,
                    convertedCount
            ));
        }

        private void send(ServerLevel level) {
            RadiationDebugRays.send(level, sourceCenter, rays);
        }
    }

    private record RayInteractionResult(Vec3 end, boolean blocked, double lastChance, int convertibleHits, int conversions) {
    }
}
