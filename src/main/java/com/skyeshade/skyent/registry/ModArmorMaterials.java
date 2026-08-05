package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModArmorMaterials {
    private static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(
            Registries.ARMOR_MATERIAL,
            SkyesNuclearTech.MOD_ID
    );

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> STEEL = ARMOR_MATERIALS.register(
            "steel",
            () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), defense -> {
                        defense.put(ArmorItem.Type.BOOTS, 3);
                        defense.put(ArmorItem.Type.LEGGINGS, 6);
                        defense.put(ArmorItem.Type.CHESTPLATE, 8);
                        defense.put(ArmorItem.Type.HELMET, 3);
                        defense.put(ArmorItem.Type.BODY, 11);
                    }),
                    10,
                    SoundEvents.ARMOR_EQUIP_DIAMOND,
                    () -> Ingredient.of(ModItems.STEEL_INGOT.get()),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "steel"))),
                    2.0F,
                    0.0F
            )
    );

    private ModArmorMaterials() {
    }

    public static void register(IEventBus modEventBus) {
        ARMOR_MATERIALS.register(modEventBus);
    }
}
