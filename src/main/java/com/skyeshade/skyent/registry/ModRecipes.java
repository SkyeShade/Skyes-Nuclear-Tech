package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.recipe.BrickBlastFurnaceRecipe;
import com.skyeshade.skyent.content.recipe.CentrifugeRecipe;
import com.skyeshade.skyent.content.recipe.ChemicalReactorRecipe;
import com.skyeshade.skyent.content.recipe.MVAssemblerRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, SkyesNuclearTech.MOD_ID);
    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, SkyesNuclearTech.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BrickBlastFurnaceRecipe>> BRICK_BLAST_FURNACE_SERIALIZER =
            RECIPE_SERIALIZERS.register("brick_blast_furnace", BrickBlastFurnaceRecipe.Serializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MVAssemblerRecipe>> MV_ASSEMBLER_SERIALIZER =
            RECIPE_SERIALIZERS.register("mv_assembler", MVAssemblerRecipe.Serializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ChemicalReactorRecipe>> CHEMICAL_REACTOR_SERIALIZER =
            RECIPE_SERIALIZERS.register("chemical_reactor", ChemicalReactorRecipe.Serializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CentrifugeRecipe>> CENTRIFUGE_SERIALIZER =
            RECIPE_SERIALIZERS.register("centrifuge", CentrifugeRecipe.Serializer::new);

    public static final DeferredHolder<RecipeType<?>, RecipeType<BrickBlastFurnaceRecipe>> BRICK_BLAST_FURNACE_TYPE =
            RECIPE_TYPES.register("brick_blast_furnace", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return SkyesNuclearTech.MOD_ID + ":brick_blast_furnace";
                }
            });

    public static final DeferredHolder<RecipeType<?>, RecipeType<MVAssemblerRecipe>> MV_ASSEMBLER_TYPE =
            RECIPE_TYPES.register("mv_assembler", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return SkyesNuclearTech.MOD_ID + ":mv_assembler";
                }
            });

    public static final DeferredHolder<RecipeType<?>, RecipeType<ChemicalReactorRecipe>> CHEMICAL_REACTOR_TYPE =
            RECIPE_TYPES.register("chemical_reactor", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return SkyesNuclearTech.MOD_ID + ":chemical_reactor";
                }
            });

    public static final DeferredHolder<RecipeType<?>, RecipeType<CentrifugeRecipe>> CENTRIFUGE_TYPE =
            RECIPE_TYPES.register("centrifuge", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return SkyesNuclearTech.MOD_ID + ":centrifuge";
                }
            });

    private ModRecipes() {
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.register(modEventBus);
        RECIPE_TYPES.register(modEventBus);
    }
}
