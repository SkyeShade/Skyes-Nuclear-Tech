package com.skyeshade.skyent.content.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyeshade.skyent.registry.ModRecipes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
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

public final class ElectricBlastFurnaceRecipe implements Recipe<RecipeInput> {
    private final ElectricBlastFurnaceMode mode;
    private final List<CountedIngredient> ingredients;
    private final ItemStack result;
    private final int processTime;
    private final int energyPerTick;

    public ElectricBlastFurnaceRecipe(ElectricBlastFurnaceMode mode, List<CountedIngredient> ingredients, ItemStack result, int processTime, int energyPerTick) {
        this.mode = mode;
        this.ingredients = List.copyOf(ingredients.stream().limit(12).toList());
        this.result = result.copy();
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
        return ModRecipes.ELECTRIC_BLAST_FURNACE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.ELECTRIC_BLAST_FURNACE_TYPE.get();
    }

    public ElectricBlastFurnaceMode mode() {
        return mode;
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

    public int distinctIngredientTypeCount() {
        Set<String> ids = new HashSet<>();
        for (CountedIngredient ingredient : ingredients) {
            ItemStack[] stacks = ingredient.ingredient().getItems();
            if (stacks.length > 0 && !stacks[0].isEmpty()) {
                ids.add(BuiltInRegistries.ITEM.getKey(stacks[0].getItem()).toString());
            }
        }
        return ids.size();
    }

    public int totalIngredientCount() {
        int total = 0;
        for (CountedIngredient ingredient : ingredients) {
            total += ingredient.count();
        }
        return total;
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

    public static final class Serializer implements RecipeSerializer<ElectricBlastFurnaceRecipe> {
        private static final MapCodec<ElectricBlastFurnaceRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ElectricBlastFurnaceMode.CODEC.fieldOf("mode").forGetter(ElectricBlastFurnaceRecipe::mode),
                CountedIngredient.CODEC.listOf().fieldOf("ingredients").forGetter(ElectricBlastFurnaceRecipe::countedIngredients),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ElectricBlastFurnaceRecipe::result),
                Codec.INT.optionalFieldOf("process_time", 400).forGetter(ElectricBlastFurnaceRecipe::processTime),
                Codec.INT.optionalFieldOf("energy_per_tick", 192).forGetter(ElectricBlastFurnaceRecipe::energyPerTick)
        ).apply(instance, ElectricBlastFurnaceRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, ElectricBlastFurnaceRecipe> STREAM_CODEC = StreamCodec.composite(
                ElectricBlastFurnaceMode.STREAM_CODEC,
                ElectricBlastFurnaceRecipe::mode,
                CountedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()),
                ElectricBlastFurnaceRecipe::countedIngredients,
                ItemStack.STREAM_CODEC,
                ElectricBlastFurnaceRecipe::result,
                ByteBufCodecs.VAR_INT,
                ElectricBlastFurnaceRecipe::processTime,
                ByteBufCodecs.VAR_INT,
                ElectricBlastFurnaceRecipe::energyPerTick,
                ElectricBlastFurnaceRecipe::new
        );

        @Override
        public MapCodec<ElectricBlastFurnaceRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ElectricBlastFurnaceRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }

    public static Comparator<net.minecraft.world.item.crafting.RecipeHolder<ElectricBlastFurnaceRecipe>> alloyPriorityComparator() {
        return Comparator
                .comparingInt((net.minecraft.world.item.crafting.RecipeHolder<ElectricBlastFurnaceRecipe> holder) -> holder.value().distinctIngredientTypeCount()).reversed()
                .thenComparing(Comparator.comparingInt((net.minecraft.world.item.crafting.RecipeHolder<ElectricBlastFurnaceRecipe> holder) -> holder.value().totalIngredientCount()).reversed())
                .thenComparing(holder -> holder.id().toString());
    }
}
