package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class HotItemUtil {
    public static final double AMBIENT_TEMPERATURE_C = 21.0D;
    private static final String TEMPERATURE_TAG = "SkyentTemperatureC";

    private HotItemUtil() {
    }

    public static double getTemperature(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return AMBIENT_TEMPERATURE_C;
        }

        CompoundTag tag = customData.copyTag();
        return tag.contains(TEMPERATURE_TAG) ? tag.getDouble(TEMPERATURE_TAG) : AMBIENT_TEMPERATURE_C;
    }

    public static boolean hasTemperature(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }

        return customData.copyTag().contains(TEMPERATURE_TAG);
    }

    public static void setTemperature(ItemStack stack, double temperature) {
        if (stack.isEmpty()) {
            return;
        }

        if (temperature <= AMBIENT_TEMPERATURE_C) {
            clearTemperature(stack);
            return;
        }

        CompoundTag tag = getOrCreateCustomTag(stack);
        tag.putDouble(TEMPERATURE_TAG, temperature);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void clearTemperature(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return;
        }

        CompoundTag tag = customData.copyTag();
        tag.remove(TEMPERATURE_TAG);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean isHot(ItemStack stack) {
        return getTemperature(stack) > AMBIENT_TEMPERATURE_C;
    }

    public static double getForgingTemperature(ItemStack stack) {
        if (stack.is(ModItems.LEAD_INGOT.get())) {
            return 25.0D;
        }
        if (stack.is(ModItems.ALUMINUM_INGOT.get())) {
            return 400.0D;
        }
        if (stack.is(Items.GOLD_INGOT)) {
            return 650.0D;
        }
        if (stack.is(Items.COPPER_INGOT)) {
            return 800.0D;
        }
        if (stack.is(ModItems.COBALT_BRONZE_INGOT.get())) {
            return 1000.0D;
        }
        if (stack.is(ModItems.CUPRONICKEL_INGOT.get())) {
            return 950.0D;
        }
        if (stack.is(ModItems.COBALT_INGOT.get())) {
            return 1250.0D;
        }
        if (stack.is(ModItems.NICKEL_INGOT.get())) {
            return 1150.0D;
        }
        if (stack.is(ModItems.URANIUM_INGOT.get())) {
            return 600.0D;
        }
        if (stack.is(Items.IRON_INGOT)) {
            return 1200.0D;
        }
        if (stack.is(ModItems.STEEL_INGOT.get())) {
            return 1100.0D;
        }
        if (stack.is(ModItems.TITANIUM_INGOT.get())) {
            return 950.0D;
        }
        if (stack.is(ModItems.TUNGSTEN_INGOT.get())) {
            return 1600.0D;
        }
        return Double.POSITIVE_INFINITY;
    }

    public static boolean isForgeableIngot(ItemStack stack) {
        return !stack.isEmpty() && Double.isFinite(getForgingTemperature(stack));
    }

    public static boolean isForgeReady(ItemStack stack) {
        return isForgeableIngot(stack) && getTemperature(stack) >= getForgingTemperature(stack);
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltipComponents) {
        if (!isHot(stack)) {
            return;
        }

        double temperature = getTemperature(stack);
        tooltipComponents.add(Component.translatable("tooltip.skyent.temperature", Math.round(temperature)).withStyle(ChatFormatting.GOLD));
        if (isForgeReady(stack)) {
            tooltipComponents.add(Component.translatable("tooltip.skyent.forgeable").withStyle(ChatFormatting.YELLOW));
        }
    }

    private static CompoundTag getOrCreateCustomTag(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null ? new CompoundTag() : customData.copyTag();
    }
}
