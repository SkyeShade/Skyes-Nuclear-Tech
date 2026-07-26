package com.skyeshade.skyent.content.radiation;

import com.skyeshade.skyent.registry.ModItems;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.content.item.SteelTongsItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public final class RadiationItemValues {
    public static final double RAW_URANIUM_RADIATION_STRENGTH = 1.0D;
    public static final double URANIUM_INGOT_RADIATION_STRENGTH = 5.0D;
    public static final double URANIUM_POWDER_RADIATION_STRENGTH = 10.0D;
    public static final double YELLOWCAKE_RADIATION_STRENGTH = 5.0D;

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
        if (stack.is(ModItems.URANIUM_POWDER.get())) {
            return URANIUM_POWDER_RADIATION_STRENGTH;
        }
        if (stack.is(ModItems.YELLOWCAKE.get())) {
            return YELLOWCAKE_RADIATION_STRENGTH;
        }
        if (stack.is(ModItems.URANIUM_BLOCK.get())) {
            return RadiationBlockProfiles.getRadiationStrength(ModBlocks.URANIUM_BLOCK.get());
        }
        if (stack.is(ModItems.CORIUM_BLOCK.get())) {
            return RadiationBlockProfiles.getRadiationStrength(ModBlocks.CORIUM_BLOCK.get());
        }
        if (stack.is(ModItems.RADIOACTIVE_SCRAP_METAL.get())) {
            return RadiationBlockProfiles.getRadiationStrength(ModBlocks.RADIOACTIVE_SCRAP_METAL.get());
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            return RadiationBlockProfiles.getRadiationStrength(blockItem.getBlock());
        }
        return 0.0D;
    }

    public static double getStackRadiation(ItemStack stack) {
        return getItemRadiationPerItem(stack) * stack.getCount();
    }

    public static double calculateInventoryRadiation(LivingEntity entity) {
        return calculateInventorySelfDose(entity);
    }

    public static double calculateInventorySelfDose(LivingEntity entity) {
        if (entity instanceof Player player) {
            double radiation = 0.0D;
            for (ItemStack stack : player.getInventory().items) {
                radiation += getStackSelfDose(stack, player.registryAccess());
            }
            for (ItemStack stack : player.getInventory().armor) {
                radiation += getStackSelfDose(stack, player.registryAccess());
            }
            for (ItemStack stack : player.getInventory().offhand) {
                radiation += getStackSelfDose(stack, player.registryAccess());
            }
            return radiation;
        }

        double radiation = getStackSelfDose(entity.getMainHandItem(), entity.registryAccess())
                + getStackSelfDose(entity.getOffhandItem(), entity.registryAccess());
        for (ItemStack stack : entity.getArmorSlots()) {
            radiation += getStackSelfDose(stack, entity.registryAccess());
        }
        return radiation;
    }

    public static double calculateInventoryEmissionStrength(LivingEntity entity) {
        if (entity instanceof Player player) {
            double radiation = 0.0D;
            for (ItemStack stack : player.getInventory().items) {
                radiation += getStackEmissionStrength(stack, player.registryAccess());
            }
            for (ItemStack stack : player.getInventory().armor) {
                radiation += getStackEmissionStrength(stack, player.registryAccess());
            }
            for (ItemStack stack : player.getInventory().offhand) {
                radiation += getStackEmissionStrength(stack, player.registryAccess());
            }
            return radiation;
        }

        double radiation = getStackEmissionStrength(entity.getMainHandItem(), entity.registryAccess())
                + getStackEmissionStrength(entity.getOffhandItem(), entity.registryAccess());
        for (ItemStack stack : entity.getArmorSlots()) {
            radiation += getStackEmissionStrength(stack, entity.registryAccess());
        }
        return radiation;
    }

    private static double getStackSelfDose(ItemStack stack, HolderLookup.Provider registries) {
        double radiation = getStackRadiation(stack);
        if (SteelTongsItem.isTongs(stack)) {
            double heldRadiation = getStackRadiation(SteelTongsItem.getHeldStack(stack, registries));
            radiation += reduceTongsHeldRadiation(heldRadiation);
        }
        return radiation;
    }

    private static double getStackEmissionStrength(ItemStack stack, HolderLookup.Provider registries) {
        double radiation = getStackRadiation(stack);
        if (SteelTongsItem.isTongs(stack)) {
            radiation += getStackRadiation(SteelTongsItem.getHeldStack(stack, registries));
        }
        return radiation;
    }

    private static double reduceTongsHeldRadiation(double rawDose) {
        if (rawDose <= 0.0D) {
            return 0.0D;
        }
        return Math.sqrt(rawDose);
    }

    public static String formatRadiation(double value) {
        if (value == Math.rint(value)) {
            return Integer.toString((int) value);
        }

        return Double.toString(value);
    }
}
