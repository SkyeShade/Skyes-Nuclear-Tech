package com.skyeshade.skyent.content.fluid;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.compat.PortableTankCompat;
import com.skyeshade.skyent.registry.ModFluids;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;

public final class SafeFluidItemUtil {
    public static final boolean DEBUG_FLUID_CONTAINER_FILL = false;
    private static final int FILL_ACCEPTANCE_PROBE_MB = 1_000;
    private static final Set<ResourceLocation> LOGGED_BROKEN_CONTAINERS = ConcurrentHashMap.newKeySet();

    private SafeFluidItemUtil() {
    }

    public static Optional<FluidStack> safeGetFluidContained(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        try {
            return FluidUtil.getFluidContained(stack);
        } catch (RuntimeException exception) {
            logBrokenContainer(stack, "reading fluid contents", exception);
            return Optional.empty();
        }
    }

    public static boolean safeHasFluidHandler(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        try {
            return FluidUtil.getFluidHandler(stack).isPresent();
        } catch (RuntimeException exception) {
            logBrokenContainer(stack, "querying fluid handler", exception);
            return false;
        }
    }

    public static boolean hasFluidHandler(ItemStack stack) {
        return safeHasFluidHandler(stack);
    }

    public static Optional<IFluidHandlerItem> safeGetFluidHandler(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        try {
            return FluidUtil.getFluidHandler(stack);
        } catch (RuntimeException exception) {
            logBrokenContainer(stack, "querying fluid handler", exception);
            return Optional.empty();
        }
    }

    public static FluidActionResult safeTryEmptyContainer(ItemStack container, IFluidHandler fluidDestination, int maxAmount, @Nullable Player player, boolean doDrain) {
        if (container.isEmpty()) {
            return FluidActionResult.FAILURE;
        }

        try {
            return FluidUtil.tryEmptyContainer(container, fluidDestination, maxAmount, player, doDrain);
        } catch (RuntimeException exception) {
            logBrokenContainer(container, "emptying fluid container", exception);
            return FluidActionResult.FAILURE;
        }
    }

    public static FluidActionResult safeTryFillContainer(ItemStack container, IFluidHandler fluidSource, int maxAmount, @Nullable Player player, boolean doFill) {
        if (container.isEmpty()) {
            return FluidActionResult.FAILURE;
        }

        try {
            return FluidUtil.tryFillContainer(container, fluidSource, maxAmount, player, doFill);
        } catch (RuntimeException exception) {
            logBrokenContainer(container, "filling fluid container", exception);
            return FluidActionResult.FAILURE;
        }
    }

    public static boolean containsFluid(ItemStack stack, Fluid fluid) {
        return safeGetFluidContained(stack)
                .filter(fluidStack -> !fluidStack.isEmpty() && fluidStack.is(fluid))
                .isPresent();
    }

    public static boolean containsWater(ItemStack stack) {
        return containsFluid(stack, Fluids.WATER);
    }

    public static boolean containsSteam(ItemStack stack) {
        return containsFluid(stack, ModFluids.STEAM.get());
    }

    public static boolean containsAnyFluid(ItemStack stack) {
        return safeGetFluidContained(stack)
                .filter(fluidStack -> !fluidStack.isEmpty())
                .isPresent();
    }

    public static boolean isEmptyFluidContainer(ItemStack stack) {
        return safeGetFluidHandler(stack)
                .map(handler -> isHandlerEmpty(handler, stack))
                .orElse(false);
    }

    public static boolean canAcceptFluid(ItemStack stack, FluidStack fluid) {
        if (stack.isEmpty() || fluid.isEmpty()) {
            return false;
        }

        ItemStack workingStack = stack.copy();
        return safeGetFluidHandler(workingStack)
                .map(handler -> safeFill(handler, copyWithAmount(fluid, Math.max(FILL_ACCEPTANCE_PROBE_MB, fluid.getAmount())), IFluidHandler.FluidAction.SIMULATE, workingStack) > 0)
                .orElse(false);
    }

    public static boolean canAcceptFluid(ItemStack stack, Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return false;
        }

        return canAcceptFluid(stack, new FluidStack(fluid, FILL_ACCEPTANCE_PROBE_MB));
    }

    public static boolean canAcceptFluidForSlot(ItemStack stack, Fluid fluid) {
        if (stack.isEmpty() || fluid == Fluids.EMPTY) {
            return false;
        }

        ItemStack workingStack = ensureFluidContainerDataInitialized(stack.copy());
        Optional<IFluidHandlerItem> optionalHandler = safeGetFluidHandler(workingStack);
        if (optionalHandler.isEmpty()) {
            return false;
        }

        FillAttempt attempt = tryFill(optionalHandler.get(), new FluidStack(fluid, FILL_ACCEPTANCE_PROBE_MB), IFluidHandler.FluidAction.SIMULATE, workingStack);
        if (attempt.amount() > 0) {
            return true;
        }

        FluidContentsAttempt contained = tryGetFluidContained(workingStack);
        if (!contained.stack().isEmpty()) {
            return contained.stack().is(fluid);
        }

        return true;
    }

    public static boolean isFluidContainerFull(ItemStack stack, FluidStack fluid) {
        return safeGetFluidHandler(stack)
                .map(handler -> !isHandlerEmpty(handler, stack) && safeFill(handler, copyWithAmount(fluid, 1), IFluidHandler.FluidAction.SIMULATE, stack) <= 0)
                .orElse(false);
    }

    public static TransferResult drainContainerIntoTank(ItemStack container, IFluidHandler targetTank, Predicate<FluidStack> acceptedFluid, int maxTransfer) {
        if (container.isEmpty() || maxTransfer <= 0) {
            return TransferResult.unchanged(container);
        }

        ItemStack workingContainer = ensureFluidContainerDataInitialized(container.copy());
        Optional<IFluidHandlerItem> optionalHandler = safeGetFluidHandler(workingContainer);
        if (optionalHandler.isEmpty()) {
            return TransferResult.unchanged(container);
        }

        IFluidHandlerItem handler = optionalHandler.get();
        FluidStack simulatedDrain = safeDrain(handler, maxTransfer, IFluidHandler.FluidAction.SIMULATE, workingContainer);
        if (simulatedDrain.isEmpty() || !acceptedFluid.test(simulatedDrain)) {
            return TransferResult.unchanged(container);
        }

        int accepted = targetTank.fill(simulatedDrain, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            return TransferResult.unchanged(container);
        }

        FluidStack actualDrain = safeDrain(handler, copyWithAmount(simulatedDrain, accepted), IFluidHandler.FluidAction.EXECUTE, workingContainer);
        if (actualDrain.isEmpty()) {
            return TransferResult.unchanged(container);
        }

        int filled = targetTank.fill(actualDrain, IFluidHandler.FluidAction.EXECUTE);
        return new TransferResult(safeGetContainer(handler, workingContainer).copy(), filled > 0);
    }

    public static TransferResult fillContainerFromTank(ItemStack container, IFluidHandler sourceTank, Predicate<FluidStack> allowedFluid, int maxTransfer) {
        if (container.isEmpty() || maxTransfer <= 0) {
            debugFill("fillContainerFromTank skipped: item={} maxTransfer={}", itemId(container), maxTransfer);
            return TransferResult.unchanged(container);
        }

        ItemStack originalCopy = container.copy();
        Optional<IFluidHandlerItem> originalHandler = safeGetFluidHandler(originalCopy);
        ItemStack workingContainer = ensureFluidContainerDataInitialized(container.copy());
        Optional<IFluidHandlerItem> optionalHandler = safeGetFluidHandler(workingContainer);
        if (optionalHandler.isEmpty()) {
            debugFill("fillContainerFromTank skipped: item={} has no item fluid handler", itemId(container));
            return TransferResult.unchanged(container);
        }

        IFluidHandlerItem handler = optionalHandler.get();
        FluidStack available = sourceTank.drain(maxTransfer, IFluidHandler.FluidAction.SIMULATE);
        debugFill("fillContainerFromTank called: item={} source={} available={} maxTransfer={}",
                itemId(container), fluidId(available), available.getAmount(), maxTransfer);
        if (available.isEmpty() || !allowedFluid.test(available)) {
            debugFill("fillContainerFromTank skipped: source fluid not available/allowed item={} source={} amount={}",
                    itemId(container), fluidId(available), available.getAmount());
            return TransferResult.unchanged(container);
        }

        debugHandler("original", originalHandler.orElse(null), originalCopy, available);
        debugHandler("initialized", handler, workingContainer, available);

        int accepted = safeFill(handler, available, IFluidHandler.FluidAction.SIMULATE, workingContainer);
        debugFill("fillContainerFromTank simulated item fill: item={} fluid={} accepted={}",
                itemId(container), fluidId(available), accepted);
        if (accepted <= 0) {
            TransferResult fallback = tryFillContainerWithFluidUtil(container, sourceTank, available, maxTransfer);
            if (fallback.transferred()) {
                return fallback;
            }
            TransferResult portableTankFallback = tryFillPortableTankCompat(container, sourceTank, available, maxTransfer);
            if (portableTankFallback.transferred()) {
                return portableTankFallback;
            }
            debugFill("fillContainerFromTank failed: handler exists but rejects simulated fill item={} fluid={} handler={}",
                    itemId(container), fluidId(available), handler.getClass().getName());
            return TransferResult.unchanged(container);
        }
        accepted = Math.min(accepted, available.getAmount());

        FluidStack simulatedDrain = sourceTank.drain(accepted, IFluidHandler.FluidAction.SIMULATE);
        debugFill("fillContainerFromTank simulated source drain: item={} fluid={} requested={} drained={}",
                itemId(container), fluidId(available), accepted, simulatedDrain.getAmount());
        if (simulatedDrain.isEmpty()) {
            return TransferResult.unchanged(container);
        }

        FluidStack drained = sourceTank.drain(simulatedDrain.getAmount(), IFluidHandler.FluidAction.EXECUTE);
        debugFill("fillContainerFromTank actual source drain: item={} fluid={} drained={}",
                itemId(container), fluidId(drained), drained.getAmount());
        if (drained.isEmpty()) {
            return TransferResult.unchanged(container);
        }

        int filled = safeFill(handler, drained, IFluidHandler.FluidAction.EXECUTE, workingContainer);
        debugFill("fillContainerFromTank actual item fill: item={} fluid={} inserted={}",
                itemId(container), fluidId(drained), filled);
        if (filled <= 0) {
            sourceTank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            return TransferResult.unchanged(container);
        }
        if (filled < drained.getAmount()) {
            sourceTank.fill(copyWithAmount(drained, drained.getAmount() - filled), IFluidHandler.FluidAction.EXECUTE);
        }
        ItemStack updated = safeGetContainer(handler, workingContainer).copy();
        debugFill("fillContainerFromTank result: item={} updatedItem={} changed={}",
                itemId(container), itemId(updated), !ItemStack.isSameItemSameComponents(container, updated) || container.getCount() != updated.getCount());
        return new TransferResult(updated, true);
    }

    private static TransferResult tryFillContainerWithFluidUtil(ItemStack container, IFluidHandler sourceTank, FluidStack available, int maxTransfer) {
        debugFill("FluidUtil fallback simulate: item={} fluid={} amount={}",
                itemId(container), fluidId(available), Math.min(maxTransfer, available.getAmount()));
        FluidActionResult simulated = safeTryFillContainer(container.copy(), sourceTank, Math.min(maxTransfer, available.getAmount()), null, false);
        debugFill("FluidUtil fallback simulated result: item={} success={} resultItem={}",
                itemId(container), simulated.isSuccess(), simulated.isSuccess() ? itemId(simulated.getResult()) : itemId(ItemStack.EMPTY));
        if (!simulated.isSuccess()) {
            return TransferResult.unchanged(container);
        }

        FluidActionResult filled = safeTryFillContainer(container.copy(), sourceTank, Math.min(maxTransfer, available.getAmount()), null, true);
        debugFill("FluidUtil fallback execute result: item={} success={} resultItem={}",
                itemId(container), filled.isSuccess(), filled.isSuccess() ? itemId(filled.getResult()) : itemId(ItemStack.EMPTY));
        if (!filled.isSuccess()) {
            return TransferResult.unchanged(container);
        }

        return new TransferResult(filled.getResult().copy(), true);
    }

    private static TransferResult tryFillPortableTankCompat(ItemStack container, IFluidHandler sourceTank, FluidStack available, int maxTransfer) {
        Optional<ItemStack> primed = PortableTankCompat.primeEmptyPortableTank(container, available);
        if (primed.isEmpty()) {
            return TransferResult.unchanged(container);
        }

        ItemStack workingContainer = primed.get();
        Optional<IFluidHandlerItem> optionalHandler = safeGetFluidHandler(workingContainer);
        if (optionalHandler.isEmpty()) {
            debugFill("PortableTankCompat fill failed: primed item={} has no handler", itemId(workingContainer));
            return TransferResult.unchanged(container);
        }

        IFluidHandlerItem handler = optionalHandler.get();
        int accepted = safeFill(handler, copyWithAmount(available, Math.min(maxTransfer, available.getAmount())), IFluidHandler.FluidAction.SIMULATE, workingContainer);
        debugFill("PortableTankCompat simulated fill: item={} fluid={} accepted={}",
                itemId(container), fluidId(available), accepted);
        if (accepted <= 0) {
            return TransferResult.unchanged(container);
        }

        FluidStack drained = sourceTank.drain(Math.min(accepted, available.getAmount()), IFluidHandler.FluidAction.EXECUTE);
        debugFill("PortableTankCompat source drain: item={} fluid={} drained={}",
                itemId(container), fluidId(drained), drained.getAmount());
        if (drained.isEmpty()) {
            return TransferResult.unchanged(container);
        }

        int inserted = safeFill(handler, drained, IFluidHandler.FluidAction.EXECUTE, workingContainer);
        debugFill("PortableTankCompat actual fill: item={} fluid={} inserted={}",
                itemId(container), fluidId(drained), inserted);
        if (inserted <= 0) {
            sourceTank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            return TransferResult.unchanged(container);
        }
        if (inserted < drained.getAmount()) {
            sourceTank.fill(copyWithAmount(drained, drained.getAmount() - inserted), IFluidHandler.FluidAction.EXECUTE);
        }

        ItemStack updated = safeGetContainer(handler, workingContainer).copy();
        debugFill("PortableTankCompat result: item={} inserted={} updatedChanged={}",
                itemId(container), inserted, !ItemStack.isSameItemSameComponents(container, updated) || container.getCount() != updated.getCount());
        return new TransferResult(updated, true);
    }

    private static boolean isHandlerEmpty(IFluidHandler handler, ItemStack stack) {
        try {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                if (!handler.getFluidInTank(tank).isEmpty()) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException exception) {
            logBrokenContainer(stack, "checking fluid container emptiness", exception);
            return false;
        }
    }

    private static ItemStack safeGetContainer(IFluidHandlerItem handler, ItemStack fallback) {
        try {
            return handler.getContainer();
        } catch (RuntimeException exception) {
            logBrokenContainer(fallback, "reading fluid container result", exception);
            return fallback;
        }
    }

    private static FluidStack safeDrain(IFluidHandler handler, int maxDrain, IFluidHandler.FluidAction action, ItemStack stack) {
        try {
            return handler.drain(maxDrain, action);
        } catch (RuntimeException exception) {
            logBrokenContainer(stack, "draining fluid container", exception);
            return FluidStack.EMPTY;
        }
    }

    private static FluidStack safeDrain(IFluidHandler handler, FluidStack resource, IFluidHandler.FluidAction action, ItemStack stack) {
        try {
            return handler.drain(resource, action);
        } catch (RuntimeException exception) {
            logBrokenContainer(stack, "draining fluid container", exception);
            return FluidStack.EMPTY;
        }
    }

    private static int safeFill(IFluidHandler handler, FluidStack resource, IFluidHandler.FluidAction action, ItemStack stack) {
        return tryFill(handler, resource, action, stack).amount();
    }

    private static FillAttempt tryFill(IFluidHandler handler, FluidStack resource, IFluidHandler.FluidAction action, ItemStack stack) {
        try {
            return new FillAttempt(handler.fill(resource, action), false);
        } catch (RuntimeException exception) {
            logBrokenContainer(stack, "filling fluid container", exception);
            return new FillAttempt(0, true);
        }
    }

    private static FluidContentsAttempt tryGetFluidContained(ItemStack stack) {
        try {
            return new FluidContentsAttempt(FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY), false);
        } catch (RuntimeException exception) {
            logBrokenContainer(stack, "reading fluid contents", exception);
            return new FluidContentsAttempt(FluidStack.EMPTY, true);
        }
    }

    private static ItemStack ensureFluidContainerDataInitialized(ItemStack stack) {
        if (!stack.isEmpty() && !stack.has(DataComponents.CUSTOM_DATA)) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
        }

        return stack;
    }

    private static FluidStack copyWithAmount(FluidStack stack, int amount) {
        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }

    public static void debugFill(String message, Object... args) {
        if (DEBUG_FLUID_CONTAINER_FILL) {
            SkyesNuclearTech.LOGGER.info("[FluidContainerFill] " + message, args);
        }
    }

    private static void debugHandler(String label, @Nullable IFluidHandlerItem handler, ItemStack stack, FluidStack sourceFluid) {
        if (!DEBUG_FLUID_CONTAINER_FILL) {
            return;
        }
        if (handler == null) {
            debugFill("{} handler: item={} no handler", label, itemId(stack));
            return;
        }

        debugFill("{} handler: item={} class={}", label, itemId(stack), handler.getClass().getName());
        int tanks = safeGetTankCount(handler, stack, label);
        debugFill("{} handler tanks: item={} tanks={}", label, itemId(stack), tanks);
        for (int tank = 0; tank < tanks; tank++) {
            FluidStack contained = safeGetFluidInTank(handler, tank, stack, label);
            int capacity = safeGetTankCapacity(handler, tank, stack, label);
            boolean valid = safeIsFluidValid(handler, tank, sourceFluid, stack, label);
            debugFill("{} handler tank {}: item={} fluid={} amount={} capacity={} validForSource={}",
                    label, tank, itemId(stack), fluidId(contained), contained.getAmount(), capacity, valid);
        }
    }

    private static int safeGetTankCount(IFluidHandler handler, ItemStack stack, String label) {
        try {
            return handler.getTanks();
        } catch (RuntimeException exception) {
            logBrokenContainer(stack, label + " reading tank count", exception);
            return 0;
        }
    }

    private static FluidStack safeGetFluidInTank(IFluidHandler handler, int tank, ItemStack stack, String label) {
        try {
            return handler.getFluidInTank(tank);
        } catch (RuntimeException exception) {
            logBrokenContainer(stack, label + " reading tank fluid", exception);
            return FluidStack.EMPTY;
        }
    }

    private static int safeGetTankCapacity(IFluidHandler handler, int tank, ItemStack stack, String label) {
        try {
            return handler.getTankCapacity(tank);
        } catch (RuntimeException exception) {
            logBrokenContainer(stack, label + " reading tank capacity", exception);
            return 0;
        }
    }

    private static boolean safeIsFluidValid(IFluidHandler handler, int tank, FluidStack fluid, ItemStack stack, String label) {
        try {
            return handler.isFluidValid(tank, fluid);
        } catch (RuntimeException exception) {
            logBrokenContainer(stack, label + " checking fluid validity", exception);
            return false;
        }
    }

    private static ResourceLocation itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private static ResourceLocation fluidId(FluidStack stack) {
        return stack.isEmpty() ? ResourceLocation.withDefaultNamespace("empty") : BuiltInRegistries.FLUID.getKey(stack.getFluid());
    }

    public record TransferResult(ItemStack container, boolean transferred) {
        private static TransferResult unchanged(ItemStack container) {
            return new TransferResult(container, false);
        }
    }

    private record FillAttempt(int amount, boolean threw) {
    }

    private record FluidContentsAttempt(FluidStack stack, boolean threw) {
    }

    private static void logBrokenContainer(ItemStack stack, String action, RuntimeException exception) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (LOGGED_BROKEN_CONTAINERS.add(itemId)) {
            SkyesNuclearTech.LOGGER.warn("Ignoring broken fluid container capability on item {} while {}", itemId, action, exception);
        }
    }
}
