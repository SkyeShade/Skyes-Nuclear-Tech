package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class WrenchUtil {
    public static final TagKey<Item> SKYENT_WRENCH = tag(SkyesNuclearTech.MOD_ID, "tools/wrench");
    public static final TagKey<Item> SKYENT_WRENCHES = tag(SkyesNuclearTech.MOD_ID, "tools/wrenches");
    public static final TagKey<Item> COMMON_WRENCH = tag("c", "tools/wrench");
    public static final TagKey<Item> COMMON_WRENCHES = tag("c", "tools/wrenches");
    public static final TagKey<Item> FORGE_WRENCH = tag("forge", "tools/wrench");
    public static final TagKey<Item> FORGE_WRENCHES = tag("forge", "tools/wrenches");

    private WrenchUtil() {
    }

    public static boolean isWrench(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(ModItems.WRENCH.get())
                || stack.is(SKYENT_WRENCH)
                || stack.is(SKYENT_WRENCHES)
                || stack.is(COMMON_WRENCH)
                || stack.is(COMMON_WRENCHES)
                || stack.is(FORGE_WRENCH)
                || stack.is(FORGE_WRENCHES));
    }

    private static TagKey<Item> tag(String namespace, String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
