package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.client.model.SkyentModelData;
import com.skyeshade.skyent.client.render.HeatingChamberLighting;
import com.skyeshade.skyent.content.block.MediumTankBlock;
import com.skyeshade.skyent.content.fluid.SafeFluidItemUtil;
import com.skyeshade.skyent.content.item.SteelFluidBarrelItem;
import com.skyeshade.skyent.content.menu.MediumTankMenu;
import com.skyeshade.skyent.registry.ModBlockEntities;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public class MediumTankBlockEntity extends BlockEntity implements MenuProvider {
    public static final int TANK_CAPACITY_MB = 512_000;

    public static final int DUMP_INPUT_SLOT = 0;
    public static final int DUMP_OUTPUT_SLOT = 1;
    public static final int FILL_INPUT_SLOT = 2;
    public static final int FILL_OUTPUT_SLOT = 3;
    private static final int INVENTORY_SLOT_COUNT = 4;

    private static final int DATA_FLUID_AMOUNT_LOW = 0;
    private static final int DATA_FLUID_AMOUNT_HIGH = 1;
    private static final int DATA_FLUID_CAPACITY_LOW = 2;
    private static final int DATA_FLUID_CAPACITY_HIGH = 3;
    private static final int DATA_FLUID_ID_LOW = 4;
    private static final int DATA_FLUID_ID_HIGH = 5;
    private static final int DATA_CONTAINER_REVISION = 6;
    private static final int DATA_COUNT = 7;

    private int cachedSharedPackedLight = -1;
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
                case DUMP_INPUT_SLOT -> 16;
                case DUMP_OUTPUT_SLOT, FILL_INPUT_SLOT, FILL_OUTPUT_SLOT -> 16;
                default -> super.getSlotLimit(slot);
            };
        }
    };

    private final FluidTank fluidTank = new FluidTank(TANK_CAPACITY_MB) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return canAcceptFluid(stack);
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
            syncFluidToClient();
        }
    };
    private final IItemHandler automationItemHandler = new AutomationItemHandler();
    private final IFluidHandler passivePortFluidHandler = new SidedFluidHandler(true, false);
    private final IFluidHandler pumpExtractionFluidHandler = new SidedFluidHandler(true, true);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            int fluidAmount = fluidTank.getFluidAmount();
            int fluidCapacity = fluidTank.getCapacity();
            int fluidId = getFluidId();

            return switch (index) {
                case DATA_FLUID_AMOUNT_LOW -> low(fluidAmount);
                case DATA_FLUID_AMOUNT_HIGH -> high(fluidAmount);
                case DATA_FLUID_CAPACITY_LOW -> low(fluidCapacity);
                case DATA_FLUID_CAPACITY_HIGH -> high(fluidCapacity);
                case DATA_FLUID_ID_LOW -> low(fluidId);
                case DATA_FLUID_ID_HIGH -> high(fluidId);
                case DATA_CONTAINER_REVISION -> containerRevision;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_CONTAINER_REVISION) {
                containerRevision = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public MediumTankBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.MEDIUM_TANK.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MediumTankBlockEntity tank) {
        boolean changed = false;

        if (tank.tryDumpContainer()) {
            changed = true;
        }
        if (tank.tryFillContainer()) {
            changed = true;
        }

        if (changed) {
            setChanged(level, pos, state);
        }
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
            if (viewer.containerMenu instanceof MediumTankMenu menu && menu.getBlockEntity() == this) {
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

    public IFluidHandler getAutomationFluidHandler() {
        return passivePortFluidHandler;
    }

    @Nullable
    public IFluidHandler getAutomationFluidHandler(Direction side) {
        return MediumTankBlock.isValidPipeConnection(getBlockState(), side) ? passivePortFluidHandler : null;
    }

    @Nullable
    public IFluidHandler getPumpExtractionFluidHandler(BlockState queriedState, @Nullable Direction side) {
        return MediumTankBlock.isValidPipeConnection(queriedState, side) ? pumpExtractionFluidHandler : null;
    }

    public ContainerData getData() {
        return data;
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
        return Component.translatable("container.skyent.medium_tank");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MediumTankMenu(containerId, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("FluidTank", fluidTank.writeToNBT(registries, new CompoundTag()));
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fluidTank.readFromNBT(registries, tag.getCompound("FluidTank"));
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
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
    public ModelData getModelData() {
        if (level == null && cachedSharedPackedLight < 0) {
            return ModelData.EMPTY;
        }

        int packedLight = cachedSharedPackedLight >= 0 ? cachedSharedPackedLight : computePackedLight(level);
        return ModelData.of(SkyentModelData.SHARED_PACKED_LIGHT, packedLight);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshSharedLight(true);
    }

    public void refreshSharedLight(boolean forceRenderUpdate) {
        if (level == null || !level.isClientSide) {
            return;
        }

        int packedLight = computePackedLight(level);
        if (!forceRenderUpdate && packedLight == cachedSharedPackedLight) {
            return;
        }

        cachedSharedPackedLight = packedLight;
        requestModelDataUpdate();
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    private int computePackedLight(Level level) {
        Direction facing = getBlockState().hasProperty(MediumTankBlock.FACING)
                ? getBlockState().getValue(MediumTankBlock.FACING)
                : Direction.NORTH;
        return HeatingChamberLighting.computeMaxPackedLight(
                level,
                worldPosition,
                facing,
                MediumTankBlock.SIZE_X,
                MediumTankBlock.SIZE_Y,
                MediumTankBlock.SIZE_Z,
                MediumTankBlock::localToWorld
        );
    }

    private void syncFluidToClient() {
        if (level == null || level.isClientSide) {
            return;
        }

        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
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
