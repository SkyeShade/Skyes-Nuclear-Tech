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
import net.minecraft.world.item.Item;
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

public final class CentrifugeRecipe implements Recipe<RecipeInput> {
    private final List<FluidIngredient> fluidInputs;
    private final List<CountedIngredient> itemInputs;
    private final List<FluidStack> fluidOutputs;
    private final List<ItemOutput> itemOutputs;
    private final int processTime;
    private final int energyPerTick;

    public CentrifugeRecipe(
            List<FluidIngredient> fluidInputs,
            List<CountedIngredient> itemInputs,
            List<FluidStack> fluidOutputs,
            List<ItemOutput> itemOutputs,
            int processTime,
            int energyPerTick
    ) {
        this.fluidInputs = List.copyOf(fluidInputs);
        this.itemInputs = List.copyOf(itemInputs);
        this.fluidOutputs = fluidOutputs.stream().limit(3).map(FluidStack::copy).toList();
        this.itemOutputs = itemOutputs.stream().limit(9).toList();
        this.processTime = Math.max(1, processTime);
        this.energyPerTick = Math.max(0, energyPerTick);
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        return itemOutputs.isEmpty() ? ItemStack.EMPTY : itemOutputs.getFirst().stack().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return itemOutputs.isEmpty() ? ItemStack.EMPTY : itemOutputs.getFirst().stack().copy();
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
        return ModRecipes.CENTRIFUGE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.CENTRIFUGE_TYPE.get();
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

    public List<ItemOutput> itemOutputs() {
        return itemOutputs;
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

    public record ItemOutput(ItemStack stack, double chance) {
        private static final Codec<ItemOutput> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(output -> BuiltInRegistries.ITEM.getKey(output.stack().getItem())),
                Codec.INT.optionalFieldOf("count", 1).forGetter(output -> output.stack().getCount()),
                Codec.DOUBLE.optionalFieldOf("chance", 1.0D).forGetter(ItemOutput::chance)
        ).apply(instance, ItemOutput::fromJson));

        private static final StreamCodec<RegistryFriendlyByteBuf, ItemOutput> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC,
                ItemOutput::stack,
                ByteBufCodecs.DOUBLE,
                ItemOutput::chance,
                ItemOutput::new
        );

        public ItemOutput {
            stack = stack.copy();
            chance = Math.clamp(chance, 0.0D, 1.0D);
        }

        private static ItemOutput fromJson(ResourceLocation id, int count, double chance) {
            Item item = BuiltInRegistries.ITEM.get(id);
            return new ItemOutput(new ItemStack(item, Math.max(1, count)), chance);
        }

        public boolean isGuaranteed() {
            return chance >= 1.0D;
        }
    }

    public static final class Serializer implements RecipeSerializer<CentrifugeRecipe> {
        private static final MapCodec<CentrifugeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                FluidIngredient.CODEC.listOf().optionalFieldOf("fluid_inputs", List.of()).forGetter(CentrifugeRecipe::fluidInputs),
                CountedIngredient.CODEC.listOf().optionalFieldOf("item_inputs", List.of()).forGetter(CentrifugeRecipe::itemInputs),
                FluidOutput.CODEC.listOf().optionalFieldOf("fluid_outputs", List.of()).xmap(
                        outputs -> outputs.stream().map(FluidOutput::stack).filter(stack -> !stack.isEmpty()).toList(),
                        stacks -> stacks.stream().map(stack -> new FluidOutput(stack.getFluid(), stack.getAmount())).toList()
                ).forGetter(CentrifugeRecipe::fluidOutputs),
                ItemOutput.CODEC.listOf().optionalFieldOf("item_outputs", List.of()).forGetter(CentrifugeRecipe::itemOutputs),
                Codec.INT.optionalFieldOf("process_time", 400).forGetter(CentrifugeRecipe::processTime),
                Codec.INT.optionalFieldOf("energy_per_tick", 128).forGetter(CentrifugeRecipe::energyPerTick)
        ).apply(instance, CentrifugeRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, CentrifugeRecipe> STREAM_CODEC = StreamCodec.composite(
                FluidIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()),
                CentrifugeRecipe::fluidInputs,
                CountedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()),
                CentrifugeRecipe::itemInputs,
                FluidOutput.STREAM_CODEC.apply(ByteBufCodecs.list()).map(
                        outputs -> outputs.stream().map(FluidOutput::stack).filter(stack -> !stack.isEmpty()).toList(),
                        stacks -> stacks.stream().map(stack -> new FluidOutput(stack.getFluid(), stack.getAmount())).toList()
                ),
                CentrifugeRecipe::fluidOutputs,
                ItemOutput.STREAM_CODEC.apply(ByteBufCodecs.list()),
                CentrifugeRecipe::itemOutputs,
                ByteBufCodecs.VAR_INT,
                CentrifugeRecipe::processTime,
                ByteBufCodecs.VAR_INT,
                CentrifugeRecipe::energyPerTick,
                CentrifugeRecipe::new
        );

        @Override
        public MapCodec<CentrifugeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CentrifugeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
