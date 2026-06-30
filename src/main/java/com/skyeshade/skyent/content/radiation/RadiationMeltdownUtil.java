package com.skyeshade.skyent.content.radiation;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RadiationMeltdownUtil {
    public static final TagKey<Block> CAN_BECOME_MOLTEN_CORIUM = BlockTags.create(ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "can_become_molten_corium"));

    private static final boolean DEBUG_MELTDOWN = false;
    private static final int MELTDOWN_SCAN_RADIUS = 4;
    private static final double MELTDOWN_RADIATION_THRESHOLD = 1000.0D;

    private RadiationMeltdownUtil() {
    }

    public static void tryTriggerMeltdown(ServerLevel level, BlockPos sourcePos, RandomSource random) {
        BlockState sourceState = level.getBlockState(sourcePos);
        if (!canBecomeMoltenCorium(sourceState)) {
            return;
        }

        List<BlockPos> participants = new ArrayList<>();
        double totalStrength = 0.0D;
        double sumX = 0.0D;
        double sumY = 0.0D;
        double sumZ = 0.0D;

        for (BlockPos scanPos : BlockPos.betweenClosed(
                sourcePos.offset(-MELTDOWN_SCAN_RADIUS, -MELTDOWN_SCAN_RADIUS, -MELTDOWN_SCAN_RADIUS),
                sourcePos.offset(MELTDOWN_SCAN_RADIUS, MELTDOWN_SCAN_RADIUS, MELTDOWN_SCAN_RADIUS)
        )) {
            if (sourcePos.distSqr(scanPos) > MELTDOWN_SCAN_RADIUS * MELTDOWN_SCAN_RADIUS || !level.hasChunkAt(scanPos)) {
                continue;
            }

            BlockState state = level.getBlockState(scanPos);
            if (!canBecomeMoltenCorium(state)) {
                continue;
            }

            double value = meltdownValue(state);
            totalStrength += value;
            participants.add(scanPos.immutable());
            sumX += scanPos.getX();
            sumY += scanPos.getY();
            sumZ += scanPos.getZ();
        }

        if (DEBUG_MELTDOWN) {
            SkyesNuclearTech.LOGGER.info(
                    "Meltdown scan at {} found {} participants with value {} / {}",
                    sourcePos,
                    participants.size(),
                    totalStrength,
                    MELTDOWN_RADIATION_THRESHOLD
            );
        }

        if (totalStrength < MELTDOWN_RADIATION_THRESHOLD || participants.isEmpty()) {
            return;
        }

        BlockPos center = BlockPos.containing(
                sumX / participants.size(),
                sumY / participants.size(),
                sumZ / participants.size()
        );
        BlockPos meltPos = participants.stream()
                .min(Comparator.comparingDouble(pos -> pos.distSqr(center)))
                .orElse(sourcePos);
        if (DEBUG_MELTDOWN) {
            SkyesNuclearTech.LOGGER.info("Meltdown triggered from {} at {}", sourcePos, meltPos);
        }

        level.setBlock(meltPos, ModBlocks.MOLTEN_CORIUM_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
    }

    public static boolean canBecomeMoltenCorium(BlockState state) {
        return state.is(ModBlocks.URANIUM_BLOCK.get())
                || state.is(CAN_BECOME_MOLTEN_CORIUM);
    }

    private static double meltdownValue(BlockState state) {
        if (state.is(ModBlocks.URANIUM_BLOCK.get())) {
            return RadiationBlockProfiles.getRadiationStrength(state);
        }
        return 0.0D;
    }
}
