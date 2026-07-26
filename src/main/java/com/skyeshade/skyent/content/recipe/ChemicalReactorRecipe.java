package com.skyeshade.skyent.content.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyeshade.skyent.registry.ModRecipes;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

public final class ChemicalReactorRecipe implements Recipe<RecipeInput> {
    private final List<FluidIngredient> fluidInputs;
    private final List<CountedIngredient> itemInputs;
    private final List<FluidStack> fluidOutputs;
    private final List<ItemStack> itemOutputs;
    private final int processTime;
    private final int energyPerTick;

    public ChemicalReactorRecipe(
            List<FluidIngredient> fluidInputs,
            List<CountedIngredient> itemInputs,
            List<FluidStack> fluidOutputs,
            List<ItemStack> itemOutputs,
            int processTime,
            int energyPerTick
    ) {
        this.fluidInputs = List.copyOf(fluidInputs);
        this.itemInputs = List.copyOf(itemInputs);
        this.fluidOutputs = fluidOutputs.stream().map(FluidStack::copy).toList();
        this.itemOutputs = itemOutputs.stream().map(ItemStack::copy).toList();
        this.processTime = Math.max(1, processTime);
        this.energyPerTick = Math.max(0, energyPerTick);
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        return itemOutputs.isEmpty() ? ItemStack.EMPTY : itemOutputs.getFirst().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return itemOutputs.isEmpty() ? ItemStack.EMPTY : itemOutputs.getFirst().copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (CountedIngredient input : itemInputs) {
            ingredients.add(input.ingredient());
        }
        return ingredients;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.CHEMICAL_REACTOR_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.CHEMICAL_REACTOR_TYPE.get();
    }

    public List<FluidIngredient> fluidInputs() {
        return fluidInputs;
    }

    public List<CountedIngredient> itemInputs() {
        return itemInputs;
    }

    public List<FluidStack> fluidOutputs() {
        return fluidOutputs.stream().map(FluidStack::copy).toList();
    }

    public List<ItemStack> itemOutputs() {
        return itemOutputs.stream().map(ItemStack::copy).toList();
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

    public record FluidIngredient(Fluid fluid, int amount) {
        private static final Codec<FluidIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("fluid").forGetter(input -> BuiltInRegistries.FLUID.getKey(input.fluid())),
                Codec.INT.fieldOf("amount").forGetter(FluidIngredient::amount)
        ).apply(instance, (fluidId, amount) -> new FluidIngredient(BuiltInRegistries.FLUID.get(fluidId), amount)));

        private static final StreamCodec<RegistryFriendlyByteBuf, FluidIngredient> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC,
                input -> BuiltInRegistries.FLUID.getKey(input.fluid()),
                ByteBufCodecs.VAR_INT,
                FluidIngredient::amount,
                (fluidId, amount) -> new FluidIngredient(BuiltInRegistries.FLUID.get(fluidId), amount)
        );

        public FluidIngredient {
            amount = Math.max(1, amount);
            if (fluid == null) {
                fluid = Fluids.EMPTY;
            }
        }

        public FluidStack stack() {
            return new FluidStack(fluid, amount);
        }
    }

    private record FluidOutput(Fluid fluid, int amount) {
        private static final Codec<FluidOutput> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("fluid").forGetter(output -> BuiltInRegistries.FLUID.getKey(output.fluid())),
                Codec.INT.fieldOf("amount").forGetter(FluidOutput::amount)
        ).apply(instance, (fluidId, amount) -> new FluidOutput(BuiltInRegistries.FLUID.get(fluidId), amount)));

        private static final StreamCodec<RegistryFriendlyByteBuf, FluidOutput> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC,
                output -> BuiltInRegistries.FLUID.getKey(output.fluid()),
                ByteBufCodecs.VAR_INT,
                FluidOutput::amount,
                (fluidId, amount) -> new FluidOutput(BuiltInRegistries.FLUID.get(fluidId), amount)
        );

        private FluidStack stack() {
            return fluid == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(fluid, Math.max(1, amount));
        }
    }

    public static final class Serializer implements RecipeSerializer<ChemicalReactorRecipe> {
        private static final MapCodec<ChemicalReactorRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                FluidIngredient.CODEC.listOf().optionalFieldOf("fluid_inputs", List.of()).forGetter(ChemicalReactorRecipe::fluidInputs),
                CountedIngredient.CODEC.listOf().optionalFieldOf("item_inputs", List.of()).forGetter(ChemicalReactorRecipe::itemInputs),
                FluidOutput.CODEC.listOf().optionalFieldOf("fluid_outputs", List.of()).xmap(
                        outputs -> outputs.stream().map(FluidOutput::stack).filter(stack -> !stack.isEmpty()).toList(),
                        stacks -> stacks.stream().map(stack -> new FluidOutput(stack.getFluid(), stack.getAmount())).toList()
                ).forGetter(ChemicalReactorRecipe::fluidOutputs),
                ItemStack.STRICT_CODEC.listOf().optionalFieldOf("item_outputs", List.of()).forGetter(ChemicalReactorRecipe::itemOutputs),
                Codec.INT.optionalFieldOf("process_time", 200).forGetter(ChemicalReactorRecipe::processTime),
                Codec.INT.optionalFieldOf("energy_per_tick", 64).forGetter(ChemicalReactorRecipe::energyPerTick)
        ).apply(instance, ChemicalReactorRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, ChemicalReactorRecipe> STREAM_CODEC = StreamCodec.composite(
                FluidIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()),
                ChemicalReactorRecipe::fluidInputs,
                CountedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()),
                ChemicalReactorRecipe::itemInputs,
                FluidOutput.STREAM_CODEC.apply(ByteBufCodecs.list()).map(
                        outputs -> outputs.stream().map(FluidOutput::stack).filter(stack -> !stack.isEmpty()).toList(),
                        stacks -> stacks.stream().map(stack -> new FluidOutput(stack.getFluid(), stack.getAmount())).toList()
                ),
                ChemicalReactorRecipe::fluidOutputs,
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()),
                ChemicalReactorRecipe::itemOutputs,
                ByteBufCodecs.VAR_INT,
                ChemicalReactorRecipe::processTime,
                ByteBufCodecs.VAR_INT,
                ChemicalReactorRecipe::energyPerTick,
                ChemicalReactorRecipe::new
        );

        @Override
        public MapCodec<ChemicalReactorRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ChemicalReactorRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
