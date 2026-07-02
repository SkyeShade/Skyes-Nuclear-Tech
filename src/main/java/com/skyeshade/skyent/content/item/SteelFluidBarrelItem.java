package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

public class SteelFluidBarrelItem extends Item {
    public static final int CAPACITY_MB = 8_000;
    private static final String FLUID_TAG = "Fluid";
    private static final String AMOUNT_TAG = "Amount";

    public SteelFluidBarrelItem(Properties properties) {
        super(properties);
    }

    public static boolean isSteelFluidBarrel(ItemStack stack) {
        return stack.is(ModItems.STEEL_FLUID_BARREL.get());
    }

    public static boolean isEmptyBarrel(ItemStack stack) {
        return isSteelFluidBarrel(stack) && getStoredFluid(stack).isEmpty();
    }

    public static boolean isFullBarrel(ItemStack stack) {
        FluidStack fluid = getStoredFluid(stack);
        return isSteelFluidBarrel(stack) && !fluid.isEmpty() && fluid.getAmount() == CAPACITY_MB;
    }

    public static FluidStack getContainedFluid(ItemStack stack) {
        return getStoredFluid(stack);
    }

    public static ItemStack createEmptyBarrel(int count) {
        return new ItemStack(ModItems.STEEL_FLUID_BARREL.get(), count);
    }

    public static ItemStack createFilledBarrel(Fluid fluid) {
        return createFilledBarrel(new FluidStack(fluid, CAPACITY_MB));
    }

    public static ItemStack createFilledBarrel(FluidStack fluid) {
        if (fluid.isEmpty() || fluid.getFluid() == Fluids.EMPTY) {
            return ItemStack.EMPTY;
        }

        ItemStack barrel = new ItemStack(ModItems.STEEL_FLUID_BARREL.get());
        return FluidUtil.getFluidHandler(barrel)
                .map(handler -> fillBarrel(handler, fluid))
                .orElse(ItemStack.EMPTY);
    }

    public static ItemStack createFilledBarrel(FluidStack fluid, int count) {
        ItemStack barrel = createFilledBarrel(fluid);
        if (!barrel.isEmpty()) {
            barrel.setCount(count);
        }
        return barrel;
    }

    private static ItemStack fillBarrel(IFluidHandlerItem handler, FluidStack fluid) {
        FluidStack toFill = fluid.copy();
        toFill.setAmount(CAPACITY_MB);
        int filled = handler.fill(toFill, IFluidHandler.FluidAction.EXECUTE);
        return filled == CAPACITY_MB ? handler.getContainer().copy() : ItemStack.EMPTY;
    }

    public static FluidStack getStoredFluid(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return FluidStack.EMPTY;
        }

        CompoundTag tag = customData.copyTag();
        if (!tag.contains(FLUID_TAG) || !tag.contains(AMOUNT_TAG)) {
            return FluidStack.EMPTY;
        }

        ResourceLocation fluidId = ResourceLocation.tryParse(tag.getString(FLUID_TAG));
        if (fluidId == null) {
            return FluidStack.EMPTY;
        }

        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
        int amount = tag.getInt(AMOUNT_TAG);
        if (fluid == Fluids.EMPTY || amount <= 0) {
            return FluidStack.EMPTY;
        }

        return new FluidStack(fluid, Math.min(amount, CAPACITY_MB));
    }

    public static void setStoredFluid(ItemStack stack, FluidStack fluid) {
        if (stack.isEmpty()) {
            return;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData == null ? new CompoundTag() : customData.copyTag();
        if (fluid.isEmpty() || fluid.getAmount() <= 0) {
            tag.remove(FLUID_TAG);
            tag.remove(AMOUNT_TAG);
        } else {
            tag.putString(FLUID_TAG, BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString());
            tag.putInt(AMOUNT_TAG, Math.min(fluid.getAmount(), CAPACITY_MB));
        }

        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        FluidStack fluid = getStoredFluid(stack);
        if (fluid.isEmpty()) {
            tooltipComponents.add(Component.literal("Empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltipComponents.add(Component.literal("Contains: ")
                    .append(fluid.getHoverName())
                    .withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.literal("Amount: " + fluid.getAmount() + " / " + CAPACITY_MB + " mB")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltipComponents.add(Component.literal("Capacity: " + CAPACITY_MB + " mB").withStyle(ChatFormatting.DARK_GRAY));
    }
}
