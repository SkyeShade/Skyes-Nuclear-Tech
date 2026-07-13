package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.registry.ModItems;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

public final class HotMetalItems {
    private HotMetalItems() {
    }

    public static boolean hasHotVariant(ItemStack stack) {
        return !stack.isEmpty() && getHotItem(stack.getItem()) != null;
    }

    public static ItemStack toHotVariant(ItemStack stack) {
        Item hotItem = getHotItem(stack.getItem());
        if (stack.isEmpty() || hotItem == null) {
            return stack;
        }
        return stack.transmuteCopy(hotItem, stack.getCount());
    }

    public static ItemStack toHotVariantIfForgeReady(ItemStack stack) {
        if (!hasHotVariant(stack) || !HotItemUtil.isForgeReady(stack)) {
            return stack;
        }
        return toHotVariant(stack);
    }

    public static boolean isHotVariant(ItemStack stack) {
        return !stack.isEmpty() && isHotVariant(stack.getItem());
    }

    public static boolean isHotVariant(Item item) {
        return getNormalItem(item) != null;
    }

    public static ItemStack toNormalVariant(ItemStack stack) {
        Item normalItem = getNormalItem(stack.getItem());
        if (stack.isEmpty() || normalItem == null) {
            return stack;
        }

        ItemStack normal = stack.transmuteCopy(normalItem, stack.getCount());
        HotItemUtil.clearTemperature(normal);
        return normal;
    }

    @Nullable
    public static Item getHotItem(Item normalItem) {
        if (normalItem == Items.IRON_INGOT) return ModItems.HOT_IRON_INGOT.get();
        if (normalItem == Items.COPPER_INGOT) return ModItems.HOT_COPPER_INGOT.get();
        if (normalItem == Items.GOLD_INGOT) return ModItems.HOT_GOLD_INGOT.get();
        if (normalItem == ModItems.STEEL_INGOT.get()) return ModItems.HOT_STEEL_INGOT.get();
        if (normalItem == ModItems.COBALT_INGOT.get()) return ModItems.HOT_COBALT_INGOT.get();
        if (normalItem == ModItems.NICKEL_INGOT.get()) return ModItems.HOT_NICKEL_INGOT.get();
        if (normalItem == ModItems.ALUMINUM_INGOT.get()) return ModItems.HOT_ALUMINUM_INGOT.get();
        if (normalItem == ModItems.TITANIUM_INGOT.get()) return ModItems.HOT_TITANIUM_INGOT.get();
        if (normalItem == ModItems.TUNGSTEN_INGOT.get()) return ModItems.HOT_TUNGSTEN_INGOT.get();
        if (normalItem == ModItems.URANIUM_INGOT.get()) return ModItems.HOT_URANIUM_INGOT.get();
        if (normalItem == ModItems.COBALT_BRONZE_INGOT.get()) return ModItems.HOT_COBALT_BRONZE_INGOT.get();
        if (normalItem == ModItems.CUPRONICKEL_INGOT.get()) return ModItems.HOT_CUPRONICKEL_INGOT.get();
        if (normalItem == ModItems.IRON_ROD.get()) return ModItems.HOT_IRON_ROD.get();
        if (normalItem == ModItems.COPPER_ROD.get()) return ModItems.HOT_COPPER_ROD.get();
        if (normalItem == ModItems.TIN_ROD.get()) return ModItems.HOT_TIN_ROD.get();
        if (normalItem == ModItems.STEEL_ROD.get()) return ModItems.HOT_STEEL_ROD.get();
        if (normalItem == ModItems.ALUMINUM_ROD.get()) return ModItems.HOT_ALUMINUM_ROD.get();
        if (normalItem == ModItems.TITANIUM_ROD.get()) return ModItems.HOT_TITANIUM_ROD.get();
        if (normalItem == ModItems.TUNGSTEN_ROD.get()) return ModItems.HOT_TUNGSTEN_ROD.get();
        if (normalItem == ModItems.COBALT_ROD.get()) return ModItems.HOT_COBALT_ROD.get();
        if (normalItem == ModItems.NICKEL_ROD.get()) return ModItems.HOT_NICKEL_ROD.get();
        if (normalItem == ModItems.COBALT_BRONZE_ROD.get()) return ModItems.HOT_COBALT_BRONZE_ROD.get();
        if (normalItem == ModItems.CUPRONICKEL_ROD.get()) return ModItems.HOT_CUPRONICKEL_ROD.get();
        return null;
    }

    @Nullable
    public static Item getNormalItem(Item hotItem) {
        if (hotItem == ModItems.HOT_IRON_INGOT.get()) return Items.IRON_INGOT;
        if (hotItem == ModItems.HOT_COPPER_INGOT.get()) return Items.COPPER_INGOT;
        if (hotItem == ModItems.HOT_GOLD_INGOT.get()) return Items.GOLD_INGOT;
        if (hotItem == ModItems.HOT_STEEL_INGOT.get()) return ModItems.STEEL_INGOT.get();
        if (hotItem == ModItems.HOT_COBALT_INGOT.get()) return ModItems.COBALT_INGOT.get();
        if (hotItem == ModItems.HOT_NICKEL_INGOT.get()) return ModItems.NICKEL_INGOT.get();
        if (hotItem == ModItems.HOT_ALUMINUM_INGOT.get()) return ModItems.ALUMINUM_INGOT.get();
        if (hotItem == ModItems.HOT_TITANIUM_INGOT.get()) return ModItems.TITANIUM_INGOT.get();
        if (hotItem == ModItems.HOT_TUNGSTEN_INGOT.get()) return ModItems.TUNGSTEN_INGOT.get();
        if (hotItem == ModItems.HOT_URANIUM_INGOT.get()) return ModItems.URANIUM_INGOT.get();
        if (hotItem == ModItems.HOT_COBALT_BRONZE_INGOT.get()) return ModItems.COBALT_BRONZE_INGOT.get();
        if (hotItem == ModItems.HOT_CUPRONICKEL_INGOT.get()) return ModItems.CUPRONICKEL_INGOT.get();
        if (hotItem == ModItems.HOT_IRON_ROD.get()) return ModItems.IRON_ROD.get();
        if (hotItem == ModItems.HOT_COPPER_ROD.get()) return ModItems.COPPER_ROD.get();
        if (hotItem == ModItems.HOT_TIN_ROD.get()) return ModItems.TIN_ROD.get();
        if (hotItem == ModItems.HOT_STEEL_ROD.get()) return ModItems.STEEL_ROD.get();
        if (hotItem == ModItems.HOT_ALUMINUM_ROD.get()) return ModItems.ALUMINUM_ROD.get();
        if (hotItem == ModItems.HOT_TITANIUM_ROD.get()) return ModItems.TITANIUM_ROD.get();
        if (hotItem == ModItems.HOT_TUNGSTEN_ROD.get()) return ModItems.TUNGSTEN_ROD.get();
        if (hotItem == ModItems.HOT_COBALT_ROD.get()) return ModItems.COBALT_ROD.get();
        if (hotItem == ModItems.HOT_NICKEL_ROD.get()) return ModItems.NICKEL_ROD.get();
        if (hotItem == ModItems.HOT_COBALT_BRONZE_ROD.get()) return ModItems.COBALT_BRONZE_ROD.get();
        if (hotItem == ModItems.HOT_CUPRONICKEL_ROD.get()) return ModItems.CUPRONICKEL_ROD.get();
        return null;
    }

    public static Item getLookupItem(Item item) {
        Item normalItem = getNormalItem(item);
        return normalItem == null ? item : normalItem;
    }

    public static List<Item> getNormalHeatingInputs() {
        return List.of(
                Items.IRON_INGOT,
                Items.COPPER_INGOT,
                Items.GOLD_INGOT,
                ModItems.STEEL_INGOT.get(),
                ModItems.COBALT_INGOT.get(),
                ModItems.NICKEL_INGOT.get(),
                ModItems.ALUMINUM_INGOT.get(),
                ModItems.TITANIUM_INGOT.get(),
                ModItems.TUNGSTEN_INGOT.get(),
                ModItems.URANIUM_INGOT.get(),
                ModItems.COBALT_BRONZE_INGOT.get(),
                ModItems.CUPRONICKEL_INGOT.get(),
                ModItems.IRON_ROD.get(),
                ModItems.COPPER_ROD.get(),
                ModItems.TIN_ROD.get(),
                ModItems.STEEL_ROD.get(),
                ModItems.ALUMINUM_ROD.get(),
                ModItems.TITANIUM_ROD.get(),
                ModItems.TUNGSTEN_ROD.get(),
                ModItems.COBALT_ROD.get(),
                ModItems.NICKEL_ROD.get(),
                ModItems.COBALT_BRONZE_ROD.get(),
                ModItems.CUPRONICKEL_ROD.get()
        );
    }
}
