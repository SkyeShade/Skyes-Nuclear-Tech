package com.skyeshade.skyent.compat;

import com.skyeshade.skyent.content.fluid.SafeFluidItemUtil;
import java.lang.reflect.Field;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

public final class PortableTankCompat {
    private static final String PORTABLE_TANKS_NAMESPACE = "portabletanks";
    private static final String BASE_BLOCK_CLASS = "com.supermartijn642.core.block.BaseBlock";
    private static final String TILE_DATA_FIELD = "TILE_DATA";
    private static DataComponentType<CompoundTag> tileDataComponent;
    private static boolean tileDataLookupAttempted;

    private PortableTankCompat() {
    }

    public static Optional<ItemStack> primeEmptyPortableTank(ItemStack original, FluidStack fluid) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(original.getItem());
        SafeFluidItemUtil.debugFill("PortableTankCompat considered: item={} fluid={} amount={}",
                itemId, fluid.isEmpty() ? "empty" : BuiltInRegistries.FLUID.getKey(fluid.getFluid()), fluid.getAmount());
        if (original.isEmpty() || fluid.isEmpty() || !PORTABLE_TANKS_NAMESPACE.equals(itemId.getNamespace())) {
            return Optional.empty();
        }

        ItemStack working = original.copy();
        Optional<IFluidHandlerItem> optionalHandler = SafeFluidItemUtil.safeGetFluidHandler(working);
        if (optionalHandler.isEmpty()) {
            SafeFluidItemUtil.debugFill("PortableTankCompat skipped: item={} no handler", itemId);
            return Optional.empty();
        }

        IFluidHandlerItem handler = optionalHandler.get();
        if (!isEmptyValidPortableTank(handler, working, fluid)) {
            return Optional.empty();
        }

        DataComponentType<CompoundTag> tileData = getTileDataComponent();
        if (tileData == null) {
            SafeFluidItemUtil.debugFill("PortableTankCompat skipped: item={} TILE_DATA component unavailable", itemId);
            return Optional.empty();
        }

        if (!working.has(tileData)) {
            working.set(tileData, new CompoundTag());
        }

        SafeFluidItemUtil.debugFill("PortableTankCompat primed: item={} added empty BaseBlock.TILE_DATA={}", itemId, !original.has(tileData));
        return Optional.of(working);
    }

    private static boolean isEmptyValidPortableTank(IFluidHandlerItem handler, ItemStack stack, FluidStack fluid) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        try {
            if (handler.getTanks() <= 0) {
                SafeFluidItemUtil.debugFill("PortableTankCompat skipped: item={} no tanks", itemId);
                return false;
            }
            FluidStack contained = handler.getFluidInTank(0);
            int capacity = handler.getTankCapacity(0);
            boolean valid = handler.isFluidValid(0, fluid);
            SafeFluidItemUtil.debugFill("PortableTankCompat tank state: item={} empty={} capacity={} valid={}",
                    itemId, contained.isEmpty(), capacity, valid);
            return contained.isEmpty() && capacity > 0 && valid;
        } catch (RuntimeException exception) {
            SafeFluidItemUtil.debugFill("PortableTankCompat skipped: item={} tank inspection threw {}", itemId, exception.toString());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<CompoundTag> getTileDataComponent() {
        if (tileDataLookupAttempted) {
            return tileDataComponent;
        }

        tileDataLookupAttempted = true;
        try {
            Class<?> baseBlockClass = Class.forName(BASE_BLOCK_CLASS);
            Field field = baseBlockClass.getField(TILE_DATA_FIELD);
            Object value = field.get(null);
            if (value instanceof DataComponentType<?> componentType) {
                tileDataComponent = (DataComponentType<CompoundTag>) componentType;
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            SafeFluidItemUtil.debugFill("PortableTankCompat TILE_DATA lookup failed: {}", exception.toString());
        }
        return tileDataComponent;
    }
}
