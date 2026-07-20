package com.skyeshade.skyent.content.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyeshade.skyent.registry.ModRecipes;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public final class MVAssemblerRecipe implements Recipe<RecipeInput> {
    private final List<CountedIngredient> ingredients;
    private final ItemStack result;
    private final int processTime;
    private final int energyPerTick;

    public MVAssemblerRecipe(List<CountedIngredient> ingredients, ItemStack result, int processTime, int energyPerTick) {
        this.ingredients = List.copyOf(ingredients);
        this.result = result;
        this.processTime = Math.max(1, processTime);
        this.energyPerTick = Math.max(0, energyPerTick);
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= ingredients.size();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        for (CountedIngredient ingredient : ingredients) {
            list.add(ingredient.ingredient());
        }
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.MV_ASSEMBLER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.MV_ASSEMBLER_TYPE.get();
    }

    public List<CountedIngredient> countedIngredients() {
        return ingredients;
    }

    public ItemStack result() {
        return result.copy();
    }

    public int processTime() {
        return processTime;
    }

    public int energyPerTick() {
        return energyPerTick;
    }

    public record CountedIngredient(Ingredient ingredient, int count) {
        private static final Codec<CountedIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(CountedIngredient::ingredient),
                Codec.INT.optionalFieldOf("count", 1).forGetter(CountedIngredient::count)
        ).apply(instance, CountedIngredient::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, CountedIngredient> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC,
                CountedIngredient::ingredient,
                ByteBufCodecs.VAR_INT,
                CountedIngredient::count,
                CountedIngredient::new
        );

        public CountedIngredient {
            count = Math.max(1, count);
        }
    }

    public static final class Serializer implements RecipeSerializer<MVAssemblerRecipe> {
        private static final MapCodec<MVAssemblerRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                CountedIngredient.CODEC.listOf().fieldOf("ingredients").forGetter(MVAssemblerRecipe::countedIngredients),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(MVAssemblerRecipe::result),
                Codec.INT.optionalFieldOf("process_time", 200).forGetter(MVAssemblerRecipe::processTime),
                Codec.INT.optionalFieldOf("energy_per_tick", 64).forGetter(MVAssemblerRecipe::energyPerTick)
        ).apply(instance, MVAssemblerRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, MVAssemblerRecipe> STREAM_CODEC = StreamCodec.composite(
                CountedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()),
                MVAssemblerRecipe::countedIngredients,
                ItemStack.STREAM_CODEC,
                MVAssemblerRecipe::result,
                ByteBufCodecs.VAR_INT,
                MVAssemblerRecipe::processTime,
                ByteBufCodecs.VAR_INT,
                MVAssemblerRecipe::energyPerTick,
                MVAssemblerRecipe::new
        );

        @Override
        public MapCodec<MVAssemblerRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MVAssemblerRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
