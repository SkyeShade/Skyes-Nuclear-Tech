package com.skyeshade.skyent.content.radiation;

import com.skyeshade.skyent.registry.ModItems;
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
            return RadiationConstants.URANIUM_ORE_RADIATION_STRENGTH;
        }
        if (stack.is(ModItems.RAW_URANIUM.get())) {
            return RAW_URANIUM_RADIATION_STRENGTH;
        }
        if (stack.is(ModItems.URANIUM_INGOT.get())) {
            return URANIUM_INGOT_RADIATION_STRENGTH;
        }
        if (stack.is(ModItems.URANIUM_BLOCK.get())) {
            return RadiationConstants.URANIUM_BLOCK_RADIATION_STRENGTH;
        }
        if (stack.is(ModItems.CORIUM_BLOCK.get())) {
            return RadiationConstants.CORIUM_BLOCK_RADIATION_STRENGTH;
        }
        return 0.0D;
    }

    public static double getStackRadiation(ItemStack stack) {
        return getItemRadiationPerItem(stack) * stack.getCount();
    }

    public static double calculateInventoryRadiation(LivingEntity entity) {
        if (entity instanceof Player player) {
            double radiation = 0.0D;
            for (ItemStack stack : player.getInventory().items) {
                radiation += getStackRadiation(stack);
            }
            for (ItemStack stack : player.getInventory().armor) {
                radiation += getStackRadiation(stack);
            }
            for (ItemStack stack : player.getInventory().offhand) {
                radiation += getStackRadiation(stack);
            }
            return radiation;
        }

        double radiation = getStackRadiation(entity.getMainHandItem()) + getStackRadiation(entity.getOffhandItem());
        for (ItemStack stack : entity.getArmorSlots()) {
            radiation += getStackRadiation(stack);
        }
        return radiation;
    }

    public static String formatRadiation(double value) {
        if (value == Math.rint(value)) {
            return Integer.toString((int) value);
        }

        return Double.toString(value);
    }
}
