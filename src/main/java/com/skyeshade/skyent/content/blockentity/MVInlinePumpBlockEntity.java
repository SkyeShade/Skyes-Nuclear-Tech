package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.block.MediumTankBlock;
import com.skyeshade.skyent.content.block.BasicFluidDuctBlock;
import com.skyeshade.skyent.content.block.MVInlinePumpBlock;
import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.content.energy.RJEnergyInfo;
import com.skyeshade.skyent.content.energy.RJStorage;
import com.skyeshade.skyent.content.fluid.SafeFluidItemUtil;
import com.skyeshade.skyent.content.item.SteelFluidBarrelItem;
import com.skyeshade.skyent.content.menu.MVInlinePumpMenu;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModSounds;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class MVInlinePumpBlockEntity extends BlockEntity implements MenuProvider, RJEnergyInfo {
    public static final int ENERGY_CAPACITY_RJ = 64_000;
    public static final ElectricalTier REQUIRED_TIER = ElectricalTier.MV;
    public static final int REQUIRED_VOLTAGE = REQUIRED_TIER.voltage();
    public static final double MAX_INPUT_AMPS = 0.25D;
    public static final int MAX_INPUT_RJ_PER_TICK = (int) Math.round(REQUIRED_VOLTAGE * MAX_INPUT_AMPS);
    public static final int ENERGY_USAGE_RJ_PER_ACTIVE_TICK = MAX_INPUT_RJ_PER_TICK;
    public static final int TANK_CAPACITY_MB = 32_000;
    public static final int MAX_TRANSFER_MB_PER_TICK = 1_000;
    public static final int MIN_TRANSFER_MB_PER_TICK = 50;
    private static final int MAX_INPUT_NETWORK_DUCTS = 256;
    private static final String TAG_STORED_RJ = "StoredRJ";
    private static final float MOTOR_LOOP_VOLUME = 0.75F;
    private static final float MOTOR_LOOP_PITCH = 0.8F;

    public static final int DUMP_INPUT_SLOT = 0;
    public static final int DUMP_OUTPUT_SLOT = 1;
    public static final int FILL_INPUT_SLOT = 2;
    public static final int FILL_OUTPUT_SLOT = 3;
    private static final int INVENTORY_SLOT_COUNT = 4;

    private static final int DATA_ENERGY_LOW = 0;
    private static final int DATA_ENERGY_HIGH = 1;
    private static final int DATA_MAX_ENERGY_LOW = 2;
    private static final int DATA_MAX_ENERGY_HIGH = 3;
    private static final int DATA_CURRENT_ENERGY_USAGE = 4;
    private static final int DATA_FLUID_AMOUNT_LOW = 5;
    private static final int DATA_FLUID_AMOUNT_HIGH = 6;
    private static final int DATA_FLUID_CAPACITY_LOW = 7;
    private static final int DATA_FLUID_CAPACITY_HIGH = 8;
    private static final int DATA_FLUID_ID_LOW = 9;
    private static final int DATA_FLUID_ID_HIGH = 10;
    private static final int DATA_ACTIVE = 11;
    private static final int DATA_CONTAINER_REVISION = 12;
    private static final int DATA_CURRENT_TRANSFER_MB_PER_TICK = 13;
    private static final int DATA_COUNT = 14;

    private int currentEnergyUsage;
    private int currentTransferMbPerTick;
    private int inputMovedThisTick;
    private int outputMovedThisTick;
    private boolean active;
    private int containerRevision;
    private final Set<ServerPlayer> viewers = new HashSet<>();

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case DUMP_INPUT_SLOT -> isFilledFluidContainer(stack);
                case FILL_INPUT_SLOT -> canFillContainerFromTank(stack);
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            containerRevision++;
            setChanged();
        }

        @Override
        public int getSlotLimit(int slot) {
            return switch (slot) {
                case DUMP_INPUT_SLOT, DUMP_OUTPUT_SLOT, FILL_INPUT_SLOT, FILL_OUTPUT_SLOT -> 16;
                default -> super.getSlotLimit(slot);
            };
        }
    };

    private final RJStorage rjStorage = new RJStorage(ENERGY_CAPACITY_RJ);
    private final FluidTank fluidTank = new FluidTank(TANK_CAPACITY_MB) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return canAcceptFluid(stack);
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final IItemHandler automationItemHandler = new AutomationItemHandler();
    private final IFluidHandler inputFluidHandler = new SidedFluidHandler(true, false);
    private final IFluidHandler outputFluidHandler = new SidedFluidHandler(false, true);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            int stored = rjStorage.getStoredRJ();
            int fluidAmount = fluidTank.getFluidAmount();
            int fluidCapacity = fluidTank.getCapacity();
            int fluidId = getFluidId();

            return switch (index) {
                case DATA_ENERGY_LOW -> low(stored);
                case DATA_ENERGY_HIGH -> high(stored);
                case DATA_MAX_ENERGY_LOW -> low(rjStorage.getCapacityRJ());
                case DATA_MAX_ENERGY_HIGH -> high(rjStorage.getCapacityRJ());
                case DATA_CURRENT_ENERGY_USAGE -> currentEnergyUsage;
                case DATA_FLUID_AMOUNT_LOW -> low(fluidAmount);
                case DATA_FLUID_AMOUNT_HIGH -> high(fluidAmount);
                case DATA_FLUID_CAPACITY_LOW -> low(fluidCapacity);
                case DATA_FLUID_CAPACITY_HIGH -> high(fluidCapacity);
                case DATA_FLUID_ID_LOW -> low(fluidId);
                case DATA_FLUID_ID_HIGH -> high(fluidId);
                case DATA_ACTIVE -> active ? 1 : 0;
                case DATA_CONTAINER_REVISION -> containerRevision;
                case DATA_CURRENT_TRANSFER_MB_PER_TICK -> currentTransferMbPerTick;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_ENERGY_LOW -> rjStorage.setStoredRJ(combine(value, data.get(DATA_ENERGY_HIGH)));
                case DATA_ENERGY_HIGH -> rjStorage.setStoredRJ(combine(data.get(DATA_ENERGY_LOW), value));
                case DATA_CURRENT_ENERGY_USAGE -> currentEnergyUsage = value;
                case DATA_ACTIVE -> active = value != 0;
                case DATA_CONTAINER_REVISION -> containerRevision = value;
                case DATA_CURRENT_TRANSFER_MB_PER_TICK -> currentTransferMbPerTick = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public MVInlinePumpBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.MV_INLINE_PUMP.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MVInlinePumpBlockEntity pump) {
        int previousUsage = pump.currentEnergyUsage;
        int previousTransfer = pump.currentTransferMbPerTick;
        boolean wasActive = pump.active;
        pump.currentEnergyUsage = 0;
        pump.currentTransferMbPerTick = 0;
        pump.inputMovedThisTick = 0;
        pump.outputMovedThisTick = 0;
        pump.active = false;
        boolean changed = false;

        if (pump.tryDumpContainer()) {
            changed = true;
        }
        if (pump.tryFillContainer()) {
            changed = true;
        }
        if (pump.pushToOutputSide()) {
            changed = true;
        }
        if (pump.pullFromInputSide()) {
            changed = true;
        }
        if (pump.inputMovedThisTick > 0 || pump.outputMovedThisTick > 0) {
            pump.consumeActiveTickEnergy();
            pump.currentTransferMbPerTick = pump.outputMovedThisTick > 0 ? pump.outputMovedThisTick : pump.inputMovedThisTick;
            pump.active = true;
        }
        if (previousUsage != pump.currentEnergyUsage || previousTransfer != pump.currentTransferMbPerTick || wasActive != pump.active) {
            changed = true;
        }

        if (changed) {
            setChanged(level, pos, state);
            if (previousUsage != pump.currentEnergyUsage || previousTransfer != pump.currentTransferMbPerTick || wasActive != pump.active) {
                pump.syncToClient();
            }
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, MVInlinePumpBlockEntity pump) {
        if (!level.isClientSide) {
            return;
        }

        pump.tickClientMotorLoop();
    }

    public static boolean isFilledFluidContainer(ItemStack stack) {
        if (SteelFluidBarrelItem.isSteelFluidBarrel(stack)) {
            return stack.getCount() == 1
                    ? SafeFluidItemUtil.containsAnyFluid(stack)
                    : SteelFluidBarrelItem.isFullBarrel(stack);
        }
        if (stack.getCount() > 1) {
            return false;
        }
        return SafeFluidItemUtil.containsAnyFluid(stack);
    }

    public boolean canFillContainerFromTank(ItemStack stack) {
        if (fluidTank.getFluidAmount() <= 0) {
            return false;
        }
        if (SteelFluidBarrelItem.isSteelFluidBarrel(stack)) {
            return stack.getCount() == 1
                    ? SafeFluidItemUtil.canAcceptFluidForSlot(stack, fluidTank.getFluid().getFluid())
                    : SteelFluidBarrelItem.isEmptyBarrel(stack);
        }
        if (stack.getCount() > 1) {
            return false;
        }
        return SafeFluidItemUtil.canAcceptFluidForSlot(stack, fluidTank.getFluid().getFluid());
    }

    private boolean pullFromInputSide() {
        if (level == null || fluidTank.getSpace() <= 0 || rjStorage.getStoredRJ() < ENERGY_USAGE_RJ_PER_ACTIVE_TICK) {
            return false;
        }

        Direction direction = MVInlinePumpBlock.worldDirectionForLocalSide(Direction.SOUTH, getFacing());
        int remainingTransfer = Math.min(MAX_TRANSFER_MB_PER_TICK, fluidTank.getSpace());
        int moved = 0;
        for (SourceEndpoint source : getInputFluidSources(direction)) {
            if (remainingTransfer <= 0 || fluidTank.getSpace() <= 0) {
                break;
            }

            int inserted = pullFromSource(source.handler(), Math.min(remainingTransfer, fluidTank.getSpace()));
            if (inserted > 0) {
                moved += inserted;
                remainingTransfer -= inserted;
            }
        }

        if (moved <= 0) {
            return false;
        }

        inputMovedThisTick += moved;
        return true;
    }

    private int pullFromSource(IFluidHandler source, int maxTransfer) {
        FluidStack available = source.drain(maxTransfer, IFluidHandler.FluidAction.SIMULATE);
        if (available.isEmpty() || !canAcceptFluid(available)) {
            return 0;
        }

        int accepted = fluidTank.fill(available, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            return 0;
        }

        FluidStack drained = source.drain(copyWithAmount(available, accepted), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return 0;
        }

        int inserted = fluidTank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (inserted < drained.getAmount()) {
            FluidStack remainder = copyWithAmount(drained, drained.getAmount() - inserted);
            source.fill(remainder, IFluidHandler.FluidAction.EXECUTE);
        }

        return inserted;
    }

    private List<SourceEndpoint> getInputFluidSources(Direction direction) {
        if (level == null) {
            return List.of();
        }

        BlockPos sourcePos = worldPosition.relative(direction);
        BlockState sourceState = level.getBlockState(sourcePos);
        if (sourceState.is(ModBlocks.BASIC_FLUID_DUCT.get())) {
            return BasicFluidDuctBlock.canUseSide(sourceState, direction.getOpposite())
                    ? collectInputNetworkSources(sourcePos)
                    : List.of();
        }

        return directSource(sourcePos, direction.getOpposite()).map(List::of).orElseGet(List::of);
    }

    private List<SourceEndpoint> collectInputNetworkSources(BlockPos startDuctPos) {
        if (level == null) {
            return List.of();
        }

        Set<BlockPos> network = collectInputDuctNetwork(startDuctPos);
        List<SourceEndpoint> sources = new ArrayList<>();
        for (BlockPos ductPos : network) {
            BlockState ductState = level.getBlockState(ductPos);
            for (Direction direction : Direction.values()) {
                if (!BasicFluidDuctBlock.canUseSide(ductState, direction)) {
                    continue;
                }

                BlockPos sourcePos = ductPos.relative(direction);
                if (sourcePos.equals(worldPosition) || network.contains(sourcePos)) {
                    continue;
                }

                directSource(sourcePos, direction.getOpposite()).ifPresent(sources::add);
            }
        }

        sources.sort(Comparator
                .comparingLong((SourceEndpoint source) -> source.pos().asLong())
                .thenComparing(source -> source.side().ordinal()));
        return sources;
    }

    private Set<BlockPos> collectInputDuctNetwork(BlockPos startDuctPos) {
        Set<BlockPos> network = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(startDuctPos);

        while (!queue.isEmpty() && network.size() < MAX_INPUT_NETWORK_DUCTS) {
            BlockPos ductPos = queue.removeFirst();
            if (!network.add(ductPos) || level == null) {
                continue;
            }

            BlockState ductState = level.getBlockState(ductPos);
            if (!ductState.is(ModBlocks.BASIC_FLUID_DUCT.get())) {
                continue;
            }

            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = ductPos.relative(direction);
                if (network.contains(neighborPos)) {
                    continue;
                }

                BlockState neighborState = level.getBlockState(neighborPos);
                if (BasicFluidDuctBlock.canConnectDucts(ductState, direction, neighborState)) {
                    queue.add(neighborPos);
                }
            }
        }

        return network;
    }

    private Optional<SourceEndpoint> directSource(BlockPos sourcePos, Direction sourceSide) {
        if (level == null) {
            return Optional.empty();
        }

        BlockState sourceState = level.getBlockState(sourcePos);
        if (isMediumTankState(sourceState)) {
            return MediumTankBlock.getMasterBlockEntity(level, sourceState, sourcePos)
                    .map(tank -> tank.getPumpExtractionFluidHandler(sourceState, sourceSide))
                    .map(handler -> new SourceEndpoint(sourcePos, sourceSide, handler));
        }

        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, sourcePos, sourceSide);
        return handler == null ? Optional.empty() : Optional.of(new SourceEndpoint(sourcePos, sourceSide, handler));
    }

    private static boolean isMediumTankState(BlockState state) {
        return state.is(ModBlocks.MEDIUM_TANK.get()) || state.is(ModBlocks.MEDIUM_TANK_PART.get());
    }

    private record SourceEndpoint(BlockPos pos, Direction side, IFluidHandler handler) {
    }

    private boolean pushToOutputSide() {
        if (level == null || fluidTank.getFluidAmount() <= 0 || rjStorage.getStoredRJ() < ENERGY_USAGE_RJ_PER_ACTIVE_TICK) {
            return false;
        }

        Direction direction = MVInlinePumpBlock.worldDirectionForLocalSide(Direction.NORTH, getFacing());
        IFluidHandler receiver = level.getCapability(
                Capabilities.FluidHandler.BLOCK,
                worldPosition.relative(direction),
                direction.getOpposite()
        );
        if (receiver == null) {
            return false;
        }

        int outputRate = getCurrentOutputTransferRateMbPerTick();
        if (outputRate <= 0) {
            return false;
        }

        FluidStack offered = fluidTank.drain(Math.min(outputRate, fluidTank.getFluidAmount()), IFluidHandler.FluidAction.SIMULATE);
        int accepted = receiver.fill(offered, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            return false;
        }

        FluidStack drained = fluidTank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
        int inserted = receiver.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (inserted < drained.getAmount()) {
            fluidTank.fill(copyWithAmount(drained, drained.getAmount() - inserted), IFluidHandler.FluidAction.EXECUTE);
        }

        if (inserted <= 0) {
            return false;
        }

        outputMovedThisTick += inserted;
        return true;
    }

    private void consumeActiveTickEnergy() {
        int consumed = rjStorage.consumeRJ(ENERGY_USAGE_RJ_PER_ACTIVE_TICK);
        currentEnergyUsage = consumed;
    }

    public static int outputTransferRateMbPerTick(int amount, int capacity) {
        if (amount <= 0) {
            return 0;
        }
        if (amount >= capacity) {
            return MAX_TRANSFER_MB_PER_TICK;
        }

        double fill = (double) amount / (double) Math.max(1, capacity);
        int rate = MIN_TRANSFER_MB_PER_TICK + (int) Math.floor(fill * (MAX_TRANSFER_MB_PER_TICK - MIN_TRANSFER_MB_PER_TICK));
        return Mth.clamp(rate, MIN_TRANSFER_MB_PER_TICK, MAX_TRANSFER_MB_PER_TICK);
    }

    public int getCurrentOutputTransferRateMbPerTick() {
        return outputTransferRateMbPerTick(fluidTank.getFluidAmount(), fluidTank.getCapacity());
    }

    public int getCurrentTransferMbPerTick() {
        return currentTransferMbPerTick;
    }

    private Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(MVInlinePumpBlock.FACING) ? state.getValue(MVInlinePumpBlock.FACING) : Direction.NORTH;
    }

    private boolean tryDumpContainer() {
        ItemStack input = inventory.getStackInSlot(DUMP_INPUT_SLOT);
        if (input.isEmpty()) {
            return false;
        }

        if (tryDrainStackedSteelBarrel(DUMP_INPUT_SLOT, DUMP_OUTPUT_SLOT, fluidTank, stack -> !stack.isEmpty() && canAcceptFluid(stack))) {
            return true;
        }
        if (SteelFluidBarrelItem.isSteelFluidBarrel(input) && input.getCount() > 1) {
            return false;
        }

        SafeFluidItemUtil.TransferResult result = SafeFluidItemUtil.drainContainerIntoTank(
                input,
                fluidTank,
                stack -> !stack.isEmpty() && canAcceptFluid(stack),
                fluidTank.getSpace()
        );
        if (!result.transferred()) {
            return false;
        }

        ItemStack container = result.container();
        if (SafeFluidItemUtil.isEmptyFluidContainer(container) && canPlaceOutput(DUMP_OUTPUT_SLOT, container)) {
            forceSetItemSlot(DUMP_INPUT_SLOT, ItemStack.EMPTY);
            placeOutput(DUMP_OUTPUT_SLOT, container);
        } else {
            forceSetItemSlot(DUMP_INPUT_SLOT, container);
        }
        return true;
    }

    private boolean tryDrainStackedSteelBarrel(int inputSlot, int outputSlot, IFluidHandler targetTank, Predicate<FluidStack> acceptedFluid) {
        ItemStack input = inventory.getStackInSlot(inputSlot);
        if (!SteelFluidBarrelItem.isSteelFluidBarrel(input) || input.getCount() <= 1) {
            return false;
        }

        FluidStack fluid = SteelFluidBarrelItem.getContainedFluid(input);
        if (!SteelFluidBarrelItem.isFullBarrel(input) || !acceptedFluid.test(fluid)) {
            return false;
        }

        FluidStack fullFluid = fluid.copy();
        fullFluid.setAmount(SteelFluidBarrelItem.CAPACITY_MB);
        if (targetTank.fill(fullFluid, IFluidHandler.FluidAction.SIMULATE) != SteelFluidBarrelItem.CAPACITY_MB) {
            return false;
        }

        ItemStack emptyBarrel = SteelFluidBarrelItem.createEmptyBarrel(1);
        if (!canPlaceOutput(outputSlot, emptyBarrel)) {
            return false;
        }

        int filled = targetTank.fill(fullFluid, IFluidHandler.FluidAction.EXECUTE);
        if (filled != SteelFluidBarrelItem.CAPACITY_MB) {
            return false;
        }

        ItemStack remaining = input.copy();
        remaining.shrink(1);
        forceSetItemSlot(inputSlot, remaining);
        placeOutput(outputSlot, emptyBarrel);
        return true;
    }

    private boolean tryFillContainer() {
        ItemStack input = inventory.getStackInSlot(FILL_INPUT_SLOT);
        if (input.isEmpty() || fluidTank.getFluidAmount() <= 0) {
            return false;
        }

        if (tryFillStackedSteelBarrel(FILL_INPUT_SLOT, FILL_OUTPUT_SLOT, fluidTank, copyWithAmount(fluidTank.getFluid(), SteelFluidBarrelItem.CAPACITY_MB))) {
            return true;
        }
        if (SteelFluidBarrelItem.isSteelFluidBarrel(input) && input.getCount() > 1) {
            return false;
        }

        FluidStack fluid = copyWithAmount(fluidTank.getFluid(), 1);
        SafeFluidItemUtil.TransferResult result = SafeFluidItemUtil.fillContainerFromTank(
                input,
                fluidTank,
                stack -> !stack.isEmpty() && stack.is(fluid.getFluid()),
                fluidTank.getFluidAmount()
        );
        if (!result.transferred()) {
            return false;
        }

        ItemStack container = result.container();
        if (SafeFluidItemUtil.isFluidContainerFull(container, fluid) && canPlaceOutput(FILL_OUTPUT_SLOT, container)) {
            forceSetItemSlot(FILL_INPUT_SLOT, ItemStack.EMPTY);
            placeOutput(FILL_OUTPUT_SLOT, container);
        } else {
            forceSetItemSlot(FILL_INPUT_SLOT, container);
        }
        return true;
    }

    private boolean tryFillStackedSteelBarrel(int inputSlot, int outputSlot, IFluidHandler sourceTank, FluidStack fullFluid) {
        ItemStack input = inventory.getStackInSlot(inputSlot);
        if (!SteelFluidBarrelItem.isSteelFluidBarrel(input) || input.getCount() <= 1) {
            return false;
        }
        if (!SteelFluidBarrelItem.isEmptyBarrel(input)) {
            return false;
        }

        FluidStack simulatedDrain = sourceTank.drain(fullFluid, IFluidHandler.FluidAction.SIMULATE);
        if (simulatedDrain.getAmount() < SteelFluidBarrelItem.CAPACITY_MB) {
            return false;
        }

        ItemStack fullBarrel = SteelFluidBarrelItem.createFilledBarrel(fullFluid, 1);
        if (!canPlaceOutput(outputSlot, fullBarrel)) {
            return false;
        }

        FluidStack drained = sourceTank.drain(fullFluid, IFluidHandler.FluidAction.EXECUTE);
        if (drained.getAmount() != SteelFluidBarrelItem.CAPACITY_MB) {
            if (!drained.isEmpty()) {
                sourceTank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            }
            return false;
        }

        ItemStack remaining = input.copy();
        remaining.shrink(1);
        forceSetItemSlot(inputSlot, remaining);
        placeOutput(outputSlot, fullBarrel);
        return true;
    }

    private boolean canAcceptFluid(FluidStack stack) {
        return stack.isEmpty() || fluidTank.getFluid().isEmpty() || fluidTank.getFluid().is(stack.getFluid());
    }

    private static FluidStack copyWithAmount(FluidStack stack, int amount) {
        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }

    private boolean canPlaceOutput(int slot, ItemStack result) {
        if (result.isEmpty()) {
            return true;
        }

        ItemStack output = inventory.getStackInSlot(slot);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void placeOutput(int slot, ItemStack result) {
        if (result.isEmpty()) {
            return;
        }

        ItemStack output = inventory.getStackInSlot(slot);
        if (output.isEmpty()) {
            forceSetItemSlot(slot, result);
        } else {
            ItemStack merged = output.copy();
            merged.grow(result.getCount());
            forceSetItemSlot(slot, merged);
        }
    }

    private void forceSetItemSlot(int slot, ItemStack stack) {
        inventory.setStackInSlot(slot, ItemStack.EMPTY);
        if (!stack.isEmpty()) {
            inventory.setStackInSlot(slot, stack.copy());
        }
        containerRevision++;
        setChanged();
        syncContainerSlotToViewers(slot, stack);
    }

    private void syncContainerSlotToViewers(int handlerSlot, ItemStack stack) {
        for (ServerPlayer viewer : Set.copyOf(viewers)) {
            if (viewer.containerMenu instanceof MVInlinePumpMenu menu && menu.getBlockEntity() == this) {
                menu.syncHandlerSlot(viewer, handlerSlot, stack);
            } else {
                viewers.remove(viewer);
            }
        }
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IItemHandler getAutomationItemHandler() {
        return automationItemHandler;
    }

    @Nullable
    public IFluidHandler getAutomationFluidHandler(@Nullable Direction side) {
        BlockState state = getBlockState();
        if (MVInlinePumpBlock.isFluidInputSide(state, side)) {
            return inputFluidHandler;
        }
        if (MVInlinePumpBlock.isFluidOutputSide(state, side)) {
            return outputFluidHandler;
        }
        return null;
    }

    public int getAvailableRJCapacity() {
        return Math.min(rjStorage.getAvailableRJCapacity(), MAX_INPUT_RJ_PER_TICK);
    }

    public int receiveRJ(ElectricalTier tier, int maxAmount, boolean simulate) {
        if (tier != REQUIRED_TIER) {
            return 0;
        }

        int received = rjStorage.receiveRJ(Math.min(maxAmount, MAX_INPUT_RJ_PER_TICK), simulate);
        if (received > 0 && !simulate) {
            setChanged();
        }

        return received;
    }

    @Override
    public int getEnergyStoredRJ() {
        return rjStorage.getStoredRJ();
    }

    @Override
    public int getEnergyCapacityRJ() {
        return ENERGY_CAPACITY_RJ;
    }

    @Override
    public int getCurrentUsageRJPerTick() {
        return currentEnergyUsage;
    }

    @Override
    public String getVoltageTierName() {
        return REQUIRED_TIER.displayName();
    }

    public ContainerData getData() {
        return data;
    }

    public boolean isActive() {
        return active;
    }

    public void addViewer(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            viewers.add(serverPlayer);
        }
    }

    public void removeViewer(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            viewers.remove(serverPlayer);
        }
    }

    public FluidStack getFluidInTank() {
        return fluidTank.getFluid();
    }

    public int getFluidAmount() {
        return fluidTank.getFluidAmount();
    }

    public int getFluidCapacity() {
        return fluidTank.getCapacity();
    }

    public int getRedstoneSignal() {
        return Mth.floor((float) fluidTank.getFluidAmount() / fluidTank.getCapacity() * 15.0F);
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.skyent.mv_inline_pump");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MVInlinePumpMenu(containerId, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_STORED_RJ, rjStorage.getStoredRJ());
        tag.put("FluidTank", fluidTank.writeToNBT(registries, new CompoundTag()));
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putBoolean("Active", active);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rjStorage.setStoredRJ(tag.getInt(TAG_STORED_RJ));
        fluidTank.readFromNBT(registries, tag.getCompound("FluidTank"));
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        active = tag.getBoolean("Active");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        loadAdditional(pkt.getTag(), lookupProvider);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide) {
            stopClientMotorLoop(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            stopClientMotorLoop(level, worldPosition);
        }
        super.setRemoved();
    }

    private void syncToClient() {
        if (level == null || level.isClientSide) {
            return;
        }

        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    private void tickClientMotorLoop() {
        if (level == null || !level.isClientSide) {
            return;
        }
        if (active) {
            startClientMotorLoop(level, worldPosition, worldPosition.getCenter());
        } else {
            stopClientMotorLoop(level, worldPosition);
        }
    }

    private static void startClientMotorLoop(Level level, BlockPos pos, Vec3 center) {
        invokeClientLoopMethod("startOrUpdateNamedLoop", level, pos, center);
    }

    private static void stopClientMotorLoop(Level level, BlockPos pos) {
        invokeClientLoopMethod("stopNamedLoop", level, pos, Vec3.ZERO);
    }

    private static String motorLoopKey(BlockPos pos) {
        return "mv_inline_pump_motor:" + pos.asLong();
    }

    private static void invokeClientLoopMethod(String methodName, Level level, BlockPos pos, Vec3 center) {
        if (!level.isClientSide) {
            return;
        }
        try {
            Class<?> clientLevelClass = Class.forName("net.minecraft.client.multiplayer.ClientLevel");
            if (!clientLevelClass.isInstance(level)) {
                return;
            }
            Class<?> managerClass = Class.forName("com.skyeshade.skyent.client.sound.MachineSoundManager");
            if ("startOrUpdateNamedLoop".equals(methodName)) {
                Method method = managerClass.getMethod(
                        methodName,
                        clientLevelClass,
                        String.class,
                        net.minecraft.sounds.SoundEvent.class,
                        SoundSource.class,
                        Supplier.class,
                        float.class,
                        float.class,
                        BooleanSupplier.class
                );
                method.invoke(
                        null,
                        level,
                        motorLoopKey(pos),
                        ModSounds.HEAVY_ELECTRIC_MOTOR_LOOP.get(),
                        SoundSource.BLOCKS,
                        (Supplier<Vec3>) () -> center,
                        MOTOR_LOOP_VOLUME,
                        MOTOR_LOOP_PITCH,
                        (BooleanSupplier) () -> level.getBlockEntity(pos) instanceof MVInlinePumpBlockEntity pump && pump.isActive()
                );
            } else {
                Method method = managerClass.getMethod(methodName, clientLevelClass, String.class, net.minecraft.sounds.SoundEvent.class);
                method.invoke(null, level, motorLoopKey(pos), ModSounds.HEAVY_ELECTRIC_MOTOR_LOOP.get());
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to update MV Inline Pump client loop sound", exception);
        }
    }

    private int getFluidId() {
        FluidStack fluid = fluidTank.getFluid();
        return fluid.isEmpty() ? 0 : BuiltInRegistries.FLUID.getId(fluid.getFluid());
    }

    private static int low(int value) {
        return value & 0xFFFF;
    }

    private static int high(int value) {
        return value >>> 16;
    }

    private static int combine(int low, int high) {
        return (low & 0xFFFF) | ((high & 0xFFFF) << 16);
    }

    private final class AutomationItemHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return INVENTORY_SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (isFilledFluidContainer(stack)) {
                return inventory.insertItem(DUMP_INPUT_SLOT, stack, simulate);
            }
            if (canFillContainerFromTank(stack)) {
                return inventory.insertItem(FILL_INPUT_SLOT, stack, simulate);
            }
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != DUMP_OUTPUT_SLOT && slot != FILL_OUTPUT_SLOT) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return inventory.isItemValid(slot, stack);
        }
    }

    private final class SidedFluidHandler implements IFluidHandler {
        private final boolean allowFill;
        private final boolean allowDrain;

        private SidedFluidHandler(boolean allowFill, boolean allowDrain) {
            this.allowFill = allowFill;
            this.allowDrain = allowDrain;
        }

        @Override
        public int getTanks() {
            return fluidTank.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return fluidTank.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return fluidTank.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return allowFill && fluidTank.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return allowFill ? fluidTank.fill(resource, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return allowDrain ? fluidTank.drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return allowDrain ? fluidTank.drain(maxDrain, action) : FluidStack.EMPTY;
        }
    }
}
