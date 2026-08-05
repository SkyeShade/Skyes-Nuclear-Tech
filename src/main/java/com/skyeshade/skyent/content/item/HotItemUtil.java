package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class HotItemUtil {
    public static final double AMBIENT_TEMPERATURE_C = 21.0D;
    public static final TagKey<Item> PYROPHORIC = ItemTags.create(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "pyrophoric")
    );
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
        return HotMetalItems.isHotVariant(stack) || getTemperature(stack) > AMBIENT_TEMPERATURE_C;
    }

    public static boolean isPyrophoric(ItemStack stack) {
        return !stack.isEmpty() && stack.is(PYROPHORIC);
    }

    public static double getForgingTemperature(ItemStack stack) {
        Item item = HotMetalItems.getLookupItem(stack.getItem());
        if (item == ModItems.LEAD_INGOT.get()) {
            return 25.0D;
        }
        if (item == ModItems.TIN_ROD.get()) {
            return 250.0D;
        }
        if (item == ModItems.ALUMINUM_INGOT.get() || item == ModItems.ALUMINUM_ROD.get()) {
            return 400.0D;
        }
        if (item == Items.GOLD_INGOT) {
            return 650.0D;
        }
        if (item == Items.COPPER_INGOT || item == ModItems.COPPER_ROD.get()) {
            return 800.0D;
        }
        if (item == ModItems.COBALT_BRONZE_INGOT.get() || item == ModItems.COBALT_BRONZE_ROD.get()) {
            return 1000.0D;
        }
        if (item == ModItems.CUPRONICKEL_INGOT.get() || item == ModItems.CUPRONICKEL_ROD.get()) {
            return 950.0D;
        }
        if (item == ModItems.COBALT_INGOT.get() || item == ModItems.COBALT_ROD.get()) {
            return 1250.0D;
        }
        if (item == ModItems.NICKEL_INGOT.get() || item == ModItems.NICKEL_ROD.get()) {
            return 1150.0D;
        }
        if (item == ModItems.URANIUM_INGOT.get()) {
            return 600.0D;
        }
        if (item == Items.IRON_INGOT || item == ModItems.IRON_ROD.get()) {
            return 1200.0D;
        }
        if (item == ModItems.STEEL_INGOT.get() || item == ModItems.STEEL_ROD.get()) {
            return 1100.0D;
        }
        if (item == ModItems.TITANIUM_INGOT.get() || item == ModItems.TITANIUM_ROD.get()) {
            return 950.0D;
        }
        if (item == ModItems.TUNGSTEN_INGOT.get() || item == ModItems.TUNGSTEN_ROD.get()) {
            return 1600.0D;
        }
        if (item == ModItems.STAINLESS_STEEL_INGOT.get() || item == ModItems.STAINLESS_STEEL_ROD.get()) {
            return 1260.0D;
        }
        if (item == ModItems.CHROMIUM_INGOT.get()) {
            return 1100.0D;
        }
        if (item == ModItems.STAINLESS_STEEL_INGOT.get() || item == ModItems.STAINLESS_STEEL_ROD.get()) {
            return 1260.0D;
        }
        return Double.POSITIVE_INFINITY;
    }

    public static boolean isForgeableIngot(ItemStack stack) {
        return !stack.isEmpty() && Double.isFinite(getForgingTemperature(stack));
    }

    public static boolean isForgeReady(ItemStack stack) {
        return isForgeableIngot(stack) && (HotMetalItems.isHotVariant(stack) || getTemperature(stack) >= getForgingTemperature(stack));
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltipComponents) {
        if (!isHot(stack)) {
            return;
        }

        double temperature = HotMetalItems.isHotVariant(stack) && !hasTemperature(stack)
                ? getForgingTemperature(stack)
                : getTemperature(stack);
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
