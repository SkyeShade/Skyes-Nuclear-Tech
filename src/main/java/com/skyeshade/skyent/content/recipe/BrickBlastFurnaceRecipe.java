package com.skyeshade.skyent.content.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyeshade.skyent.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public final class BrickBlastFurnaceRecipe implements Recipe<RecipeInput> {
    private final Ingredient firstInput;
    private final Ingredient secondInput;
    private final ItemStack result;

    public BrickBlastFurnaceRecipe(Ingredient firstInput, Ingredient secondInput, ItemStack result) {
        this.firstInput = firstInput;
        this.secondInput = secondInput;
        this.result = result;
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return input.size() >= 2 && firstInput.test(input.getItem(0)) && secondInput.test(input.getItem(1));
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(firstInput);
        ingredients.add(secondInput);
        return ingredients;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.BRICK_BLAST_FURNACE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.BRICK_BLAST_FURNACE_TYPE.get();
    }

    public Ingredient getFirstInput() {
        return firstInput;
    }

    public Ingredient getSecondInput() {
        return secondInput;
    }

    public ItemStack getResult() {
        return result.copy();
    }

    public static final class Serializer implements RecipeSerializer<BrickBlastFurnaceRecipe> {
        private static final MapCodec<BrickBlastFurnaceRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("input1").forGetter(BrickBlastFurnaceRecipe::getFirstInput),
                Ingredient.CODEC_NONEMPTY.fieldOf("input2").forGetter(BrickBlastFurnaceRecipe::getSecondInput),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(BrickBlastFurnaceRecipe::getResult)
        ).apply(instance, BrickBlastFurnaceRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, BrickBlastFurnaceRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC,
                BrickBlastFurnaceRecipe::getFirstInput,
                Ingredient.CONTENTS_STREAM_CODEC,
                BrickBlastFurnaceRecipe::getSecondInput,
                ItemStack.STREAM_CODEC,
                BrickBlastFurnaceRecipe::getResult,
                BrickBlastFurnaceRecipe::new
        );

        @Override
        public MapCodec<BrickBlastFurnaceRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, BrickBlastFurnaceRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
