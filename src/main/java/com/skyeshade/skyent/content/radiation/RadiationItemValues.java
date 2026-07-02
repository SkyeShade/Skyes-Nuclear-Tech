package com.skyeshade.skyent.content.radiation;

import com.skyeshade.skyent.registry.ModItems;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.content.item.TungstenReachersUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class RadiationItemValues {
    public static final double RAW_URANIUM_RADIATION_STRENGTH = 1.0D;
    public static final double URANIUM_INGOT_RADIATION_STRENGTH = 5.0D;

    private RadiationItemValues() {
    }

    public static double getItemRadiationPerItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        if (stack.is(ModItems.URANIUM_ORE.get()) || stack.is(ModItems.DEEPSLATE_URANIUM_ORE.get())) {
            return RadiationBlockProfiles.getRadiationStrength(ModBlocks.URANIUM_ORE.get());
        }
        if (stack.is(ModItems.RAW_URANIUM.get())) {
            return RAW_URANIUM_RADIATION_STRENGTH;
        }
        if (stack.is(ModItems.URANIUM_INGOT.get())) {
            return URANIUM_INGOT_RADIATION_STRENGTH;
        }
        if (stack.is(ModItems.URANIUM_BLOCK.get())) {
            return RadiationBlockProfiles.getRadiationStrength(ModBlocks.URANIUM_BLOCK.get());
        }
        if (stack.is(ModItems.CORIUM_BLOCK.get())) {
            return RadiationBlockProfiles.getRadiationStrength(ModBlocks.CORIUM_BLOCK.get());
        }
        return 0.0D;
    }

    public static double getStackRadiation(ItemStack stack) {
        return getItemRadiationPerItem(stack) * stack.getCount();
    }

    public static double calculateInventoryRadiation(LivingEntity entity) {
        if (entity instanceof Player player) {
            double radiation = 0.0D;
            int selectedSlot = player.getInventory().selected;
            for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                double stackRadiation = getStackRadiation(player.getInventory().items.get(slot));
                radiation += slot == selectedSlot
                        ? effectiveHandRadiation(player, InteractionHand.MAIN_HAND, stackRadiation)
                        : stackRadiation;
            }
            for (ItemStack stack : player.getInventory().armor) {
                radiation += getStackRadiation(stack);
            }
            radiation += effectiveHandRadiation(player, InteractionHand.OFF_HAND, getStackRadiation(player.getOffhandItem()));
            return radiation;
        }

        double radiation = effectiveHandRadiation(entity, InteractionHand.MAIN_HAND, getStackRadiation(entity.getMainHandItem()))
                + effectiveHandRadiation(entity, InteractionHand.OFF_HAND, getStackRadiation(entity.getOffhandItem()));
        for (ItemStack stack : entity.getArmorSlots()) {
            radiation += getStackRadiation(stack);
        }
        return radiation;
    }

    private static double effectiveHandRadiation(LivingEntity entity, InteractionHand hand, double radiation) {
        if (radiation <= 0.0D || !TungstenReachersUtil.isOppositeHandProtected(entity, hand)) {
            return radiation;
        }
        return TungstenReachersUtil.reduceOppositeHandRadiation(radiation);
    }

    public static String formatRadiation(double value) {
        if (value == Math.rint(value)) {
            return Integer.toString((int) value);
        }

        return Double.toString(value);
    }
}
