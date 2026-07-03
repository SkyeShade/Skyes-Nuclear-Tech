package com.skyeshade.skyent.client.renderer.blockentity;

import com.skyeshade.skyent.registry.ModItems;
import net.minecraft.world.item.ItemStack;

public final class ForgeStageRenderUtil {
    private static final int MANUAL_STAGE_1_HIT = 2;
    private static final int MANUAL_STAGE_2_HIT = 4;

    private ForgeStageRenderUtil() {
    }

    public static ItemStack getManualPlateForgeRenderStack(ItemStack input, int hitsDone, boolean finished) {
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (finished) {
            return input;
        }
        if (hitsDone >= MANUAL_STAGE_2_HIT) {
            return new ItemStack(ModItems.HOT_PLATE_FORGING_STAGE_2.get());
        }
        if (hitsDone >= MANUAL_STAGE_1_HIT) {
            return new ItemStack(ModItems.HOT_PLATE_FORGING_STAGE_1.get());
        }
        return input;
    }

    public static ItemStack getSteamHammerPlateForgeRenderStack(ItemStack input, int strikesDone, boolean finished) {
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (finished) {
            return input;
        }
        return switch (strikesDone) {
            case 1 -> new ItemStack(ModItems.HOT_PLATE_FORGING_STAGE_1.get());
            case 2 -> new ItemStack(ModItems.HOT_PLATE_FORGING_STAGE_2.get());
            default -> input;
        };
    }
}
