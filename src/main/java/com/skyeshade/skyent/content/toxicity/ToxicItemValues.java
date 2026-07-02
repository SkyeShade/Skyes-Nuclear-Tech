package com.skyeshade.skyent.content.toxicity;

import com.skyeshade.skyent.registry.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ToxicItemValues {
    public static final double LEAD_ORE_TOXICITY = 0.1D;
    public static final double RAW_LEAD_TOXICITY = 0.05D;
    public static final double LEAD_INGOT_TOXICITY = 1.0D;
    public static final double LEAD_PLATE_TOXICITY = 1.0D;
    public static final double LEAD_POWDER_TOXICITY = 2.0D;
    public static final double URANIUM_POWDER_TOXICITY = 2.0D;
    public static final double LEAD_BLOCK_TOXICITY = 10.0D;

    private ToxicItemValues() {
    }

    public static double getItemToxicityPerItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        if (stack.is(ModItems.LEAD_ORE.get())) {
            return LEAD_ORE_TOXICITY;
        }
        if (stack.is(ModItems.RAW_LEAD.get())) {
            return RAW_LEAD_TOXICITY;
        }
        if (stack.is(ModItems.LEAD_INGOT.get())) {
            return LEAD_INGOT_TOXICITY;
        }
        if (stack.is(ModItems.LEAD_PLATE.get())) {
            return LEAD_PLATE_TOXICITY;
        }
        if (stack.is(ModItems.LEAD_POWDER.get())) {
            return LEAD_POWDER_TOXICITY;
        }
        if (stack.is(ModItems.URANIUM_POWDER.get())) {
            return URANIUM_POWDER_TOXICITY;
        }
        if (stack.is(ModItems.LEAD_BLOCK.get())) {
            return LEAD_BLOCK_TOXICITY;
        }
        return 0.0D;
    }

    public static double getStackToxicity(ItemStack stack) {
        return getItemToxicityPerItem(stack) * stack.getCount();
    }

    public static double calculateInventoryToxicity(LivingEntity entity) {
        if (entity instanceof Player player) {
            double toxicity = 0.0D;
            for (ItemStack stack : player.getInventory().items) {
                toxicity += getStackToxicity(stack);
            }
            for (ItemStack stack : player.getInventory().armor) {
                toxicity += getStackToxicity(stack);
            }
            for (ItemStack stack : player.getInventory().offhand) {
                toxicity += getStackToxicity(stack);
            }
            return toxicity;
        }

        double toxicity = getStackToxicity(entity.getMainHandItem()) + getStackToxicity(entity.getOffhandItem());
        for (ItemStack stack : entity.getArmorSlots()) {
            toxicity += getStackToxicity(stack);
        }
        return toxicity;
    }

    public static String formatToxicity(double value) {
        return String.format("%.1f", value);
    }
}
