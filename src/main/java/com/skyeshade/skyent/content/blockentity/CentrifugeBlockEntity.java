package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.client.model.SkyentModelData;
import com.skyeshade.skyent.client.render.HeatingChamberLighting;
import com.skyeshade.skyent.content.block.CentrifugeBlock;
import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorDirectTransfer;
import com.skyeshade.skyent.content.conveyor.ConveyorGateSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorInsertionUtil;
import com.skyeshade.skyent.content.conveyor.ConveyorLogicConstants;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.content.energy.RJEnergyInfo;
import com.skyeshade.skyent.content.energy.RJStorage;
import com.skyeshade.skyent.content.menu.CentrifugeMenu;
import com.skyeshade.skyent.content.recipe.CentrifugeRecipe;
import com.skyeshade.skyent.content.recipe.CentrifugeRecipes;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModSounds;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class CentrifugeBlockEntity extends BlockEntity implements MenuProvider, RJEnergyInfo {
    public static final int INPUT_SLOT_COUNT = 9;
    public static final int OUTPUT_SLOT_COUNT = 9;
    public static final int FIRST_INPUT_SLOT = 0;
    public static final int FIRST_OUTPUT_SLOT = FIRST_INPUT_SLOT + INPUT_SLOT_COUNT;
    public static final int INVENTORY_SLOT_COUNT = INPUT_SLOT_COUNT + OUTPUT_SLOT_COUNT;
    public static final int TANK_CAPACITY_MB = 16_000;
    public static final int ENERGY_CAPACITY_RJ = 512_000;
    public static final int MAX_INPUT_RJ_PER_TICK = 512;
    public static final ElectricalTier REQUIRED_TIER = ElectricalTier.MV;
    public static final double RUNNING_CURRENT_AMPS = 1.0D;

    private static final int LIGHT_CHECK_INTERVAL_TICKS = 40;
    private static final float MOTOR_LOOP_VOLUME = 0.95F;
    private static final float MOTOR_LOOP_PITCH = 0.8F;
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_INPUT_TANK = "InputTank";
    private static final String TAG_OUTPUT_TANK = "OutputTank";
    private static final String TAG_STORED_RJ = "StoredRJ";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_MAX_PROGRESS = "MaxProgress";
    private static final String TAG_CURRENT_ENERGY_USAGE = "CurrentEnergyUsage";
    private static final String TAG_RUNNING = "Running";

    private static final int DATA_ENERGY_LOW = 0;
    private static final int DATA_ENERGY_HIGH = 1;
    private static final int DATA_MAX_ENERGY_LOW = 2;
    private static final int DATA_MAX_ENERGY_HIGH = 3;
    private static final int DATA_PROGRESS = 4;
    private static final int DATA_MAX_PROGRESS = 5;
    private static final int DATA_CURRENT_ENERGY_USAGE = 6;
    private static final int DATA_INPUT_AMOUNT_LOW = 7;
    private static final int DATA_INPUT_AMOUNT_HIGH = 8;
    private static final int DATA_INPUT_CAPACITY_LOW = 9;
    private static final int DATA_INPUT_CAPACITY_HIGH = 10;
    private static final int DATA_INPUT_FLUID_LOW = 11;
    private static final int DATA_INPUT_FLUID_HIGH = 12;
    private static final int DATA_OUTPUT_AMOUNT_LOW = 13;
    private static final int DATA_OUTPUT_AMOUNT_HIGH = 14;
    private static final int DATA_OUTPUT_CAPACITY_LOW = 15;
    private static final int DATA_OUTPUT_CAPACITY_HIGH = 16;
    private static final int DATA_OUTPUT_FLUID_LOW = 17;
    private static final int DATA_OUTPUT_FLUID_HIGH = 18;
    private static final int DATA_COUNT = 19;

    private final CentrifugeItemStackHandler inventory = new CentrifugeItemStackHandler();
    private final FluidTank inputTank = createTank();
    private final FluidTank outputTank = createTank();
    private final RJStorage rjStorage = new RJStorage(ENERGY_CAPACITY_RJ);
    private final Map<AutomationHandlerKey, IItemHandler> automationItemHandlers = new HashMap<>();
    private final Map<AutomationHandlerKey, IFluidHandler> automationFluidHandlers = new HashMap<>();
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> low(rjStorage.getStoredRJ());
                case DATA_ENERGY_HIGH -> high(rjStorage.getStoredRJ());
                case DATA_MAX_ENERGY_LOW -> low(rjStorage.getCapacityRJ());
                case DATA_MAX_ENERGY_HIGH -> high(rjStorage.getCapacityRJ());
                case DATA_PROGRESS -> progress;
                case DATA_MAX_PROGRESS -> maxProgress;
                case DATA_CURRENT_ENERGY_USAGE -> currentEnergyUsage;
                case DATA_INPUT_AMOUNT_LOW -> low(inputTank.getFluidAmount());
                case DATA_INPUT_AMOUNT_HIGH -> high(inputTank.getFluidAmount());
                case DATA_INPUT_CAPACITY_LOW -> low(inputTank.getCapacity());
                case DATA_INPUT_CAPACITY_HIGH -> high(inputTank.getCapacity());
                case DATA_INPUT_FLUID_LOW -> low(fluidId(inputTank));
                case DATA_INPUT_FLUID_HIGH -> high(fluidId(inputTank));
                case DATA_OUTPUT_AMOUNT_LOW -> low(outputTank.getFluidAmount());
                case DATA_OUTPUT_AMOUNT_HIGH -> high(outputTank.getFluidAmount());
                case DATA_OUTPUT_CAPACITY_LOW -> low(outputTank.getCapacity());
                case DATA_OUTPUT_CAPACITY_HIGH -> high(outputTank.getCapacity());
                case DATA_OUTPUT_FLUID_LOW -> low(fluidId(outputTank));
                case DATA_OUTPUT_FLUID_HIGH -> high(fluidId(outputTank));
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_ENERGY_LOW -> rjStorage.setStoredRJ((rjStorage.getStoredRJ() & 0xFFFF0000) | (value & 0xFFFF));
                case DATA_ENERGY_HIGH -> rjStorage.setStoredRJ((rjStorage.getStoredRJ() & 0xFFFF) | ((value & 0xFFFF) << 16));
                case DATA_PROGRESS -> progress = value;
                case DATA_MAX_PROGRESS -> maxProgress = value;
                case DATA_CURRENT_ENERGY_USAGE -> currentEnergyUsage = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    private int progress;
    private int maxProgress;
    private int currentEnergyUsage;
    private boolean running;
    private int cachedSharedPackedLight = -1;
    private int lightCheckTicks;

    public CentrifugeBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.CENTRIFUGE.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CentrifugeBlockEntity centrifuge) {
        if (level.isClientSide) {
            return;
        }

        int previousUsage = centrifuge.currentEnergyUsage;
        int previousProgress = centrifuge.progress;
        int previousMaxProgress = centrifuge.maxProgress;
        boolean previousRunning = centrifuge.running;
        centrifuge.currentEnergyUsage = 0;
        centrifuge.running = false;
        boolean changed = centrifuge.tryAutoOutputFluid();
        changed |= centrifuge.tryAutoOutputItems();

        RecipeHolder<CentrifugeRecipe> recipeHolder = centrifuge.findMatchingRecipe();
        if (recipeHolder == null) {
            centrifuge.progress = 0;
            centrifuge.maxProgress = 0;
        } else {
            CentrifugeRecipe recipe = recipeHolder.value();
            centrifuge.maxProgress = recipe.processTime();
            if (centrifuge.canOutput(recipe) && centrifuge.rjStorage.getStoredRJ() >= recipe.energyPerTick()) {
                centrifuge.rjStorage.consumeRJ(recipe.energyPerTick());
                centrifuge.currentEnergyUsage = recipe.energyPerTick();
                centrifuge.running = true;
                centrifuge.progress++;
                if (centrifuge.progress >= recipe.processTime()) {
                    centrifuge.completeRecipe(recipe);
                    centrifuge.progress = 0;
                }
            }
        }

        if (changed
                || previousUsage != centrifuge.currentEnergyUsage
                || previousProgress != centrifuge.progress
                || previousMaxProgress != centrifuge.maxProgress
                || previousRunning != centrifuge.running) {
            centrifuge.setChangedAndSync();
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CentrifugeBlockEntity centrifuge) {
        if (!level.isClientSide) {
            return;
        }

        centrifuge.tickClientMotorLoop();
        centrifuge.lightCheckTicks++;
        if (centrifuge.lightCheckTicks >= LIGHT_CHECK_INTERVAL_TICKS) {
            centrifuge.lightCheckTicks = 0;
            centrifuge.refreshSharedLight(false);
        }
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public ContainerData getData() {
        return data;
    }

    public FluidStack getTankFluid(int tankIndex) {
        return tankIndex == 0 ? inputTank.getFluid() : tankIndex == 1 ? outputTank.getFluid() : FluidStack.EMPTY;
    }

    public int getTankAmount(int tankIndex) {
        return tankIndex == 0 ? inputTank.getFluidAmount() : tankIndex == 1 ? outputTank.getFluidAmount() : 0;
    }

    public int getTankCapacity(int tankIndex) {
        return tankIndex == 0 ? inputTank.getCapacity() : tankIndex == 1 ? outputTank.getCapacity() : 0;
    }

    @Nullable
    public IItemHandler getAutomationItemHandler(BlockPos queriedPos, @Nullable Direction side) {
        if (!isInputItemPort(queriedPos, side) && !isOutputItemPort(queriedPos, side)) {
            return null;
        }
        AutomationHandlerKey key = new AutomationHandlerKey(queriedPos.immutable(), side);
        return automationItemHandlers.computeIfAbsent(key, ignored -> new AutomationItemHandler(queriedPos.immutable(), side));
    }

    @Nullable
    public IFluidHandler getAutomationFluidHandler(BlockPos queriedPos, @Nullable Direction side) {
        if (isInputFluidPort(queriedPos, side)) {
            AutomationHandlerKey key = new AutomationHandlerKey(queriedPos.immutable(), side);
            return automationFluidHandlers.computeIfAbsent(key, ignored -> new SingleTankFluidHandler(inputTank, true, false));
        }
        if (isOutputFluidPort(queriedPos, side)) {
            AutomationHandlerKey key = new AutomationHandlerKey(queriedPos.immutable(), side);
            return automationFluidHandlers.computeIfAbsent(key, ignored -> new SingleTankFluidHandler(outputTank, false, true));
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
        return rjStorage.getCapacityRJ();
    }

    @Override
    public int getCurrentUsageRJPerTick() {
        return currentEnergyUsage;
    }

    @Override
    public String getVoltageTierName() {
        return REQUIRED_TIER.displayName();
    }

    public boolean isRunning() {
        return running;
    }

    public int getSharedPackedLight() {
        if (level == null && cachedSharedPackedLight < 0) {
            return 0;
        }
        return cachedSharedPackedLight >= 0 ? cachedSharedPackedLight : computePackedLight(level);
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.skyent.centrifuge");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CentrifugeMenu(containerId, playerInventory, this, data);
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        tag.put(TAG_INPUT_TANK, inputTank.writeToNBT(registries, new CompoundTag()));
        tag.put(TAG_OUTPUT_TANK, outputTank.writeToNBT(registries, new CompoundTag()));
        tag.putInt(TAG_STORED_RJ, rjStorage.getStoredRJ());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_MAX_PROGRESS, maxProgress);
        tag.putInt(TAG_CURRENT_ENERGY_USAGE, currentEnergyUsage);
        tag.putBoolean(TAG_RUNNING, running);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeAndMigrate(registries, tag.getCompound(TAG_INVENTORY));
        inputTank.readFromNBT(registries, tag.getCompound(TAG_INPUT_TANK));
        outputTank.readFromNBT(registries, tag.getCompound(TAG_OUTPUT_TANK));
        rjStorage.setStoredRJ(tag.getInt(TAG_STORED_RJ));
        progress = tag.getInt(TAG_PROGRESS);
        maxProgress = tag.getInt(TAG_MAX_PROGRESS);
        currentEnergyUsage = Math.max(0, tag.getInt(TAG_CURRENT_ENERGY_USAGE));
        running = tag.getBoolean(TAG_RUNNING);
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
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    private int computePackedLight(Level level) {
        Direction facing = getFacing();
        return HeatingChamberLighting.computeMaxPackedLight(
                level,
                worldPosition,
                facing,
                CentrifugeBlock.SIZE_X,
                CentrifugeBlock.SIZE_Y,
                CentrifugeBlock.SIZE_Z,
                CentrifugeBlock::localToWorld
        );
    }

    private Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(CentrifugeBlock.FACING) ? state.getValue(CentrifugeBlock.FACING) : Direction.NORTH;
    }

    private boolean isInputItemPort(BlockPos queriedPos, @Nullable Direction side) {
        return isPort(queriedPos, side, 2, 0, 2, getFacing().getClockWise());
    }

    private boolean isOutputItemPort(BlockPos queriedPos, @Nullable Direction side) {
        return isPort(queriedPos, side, 0, 0, 0, getFacing().getCounterClockWise());
    }

    private boolean isInputFluidPort(BlockPos queriedPos, @Nullable Direction side) {
        return isPort(queriedPos, side, 2, 0, 0, getFacing().getClockWise());
    }

    private boolean isOutputFluidPort(BlockPos queriedPos, @Nullable Direction side) {
        return isPort(queriedPos, side, 0, 0, 2, getFacing().getCounterClockWise());
    }

    private BlockPos outputFluidPortPos() {
        return CentrifugeBlock.localToWorld(worldPosition, getFacing(), 0, 0, 2);
    }

    private BlockPos outputItemPortPos() {
        return CentrifugeBlock.localToWorld(worldPosition, getFacing(), 0, 0, 0);
    }

    private boolean isOwnCentrifugeBlock(BlockPos pos) {
        if (level == null) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.CENTRIFUGE.get()) && !state.is(ModBlocks.CENTRIFUGE_PART.get())) {
            return false;
        }
        return CentrifugeBlock.getMasterPos(state, pos).equals(worldPosition);
    }

    private boolean isPort(BlockPos queriedPos, @Nullable Direction side, int localX, int localY, int localZ, Direction worldSide) {
        return side == worldSide && queriedPos.equals(CentrifugeBlock.localToWorld(worldPosition, getFacing(), localX, localY, localZ));
    }

    @Nullable
    private RecipeHolder<CentrifugeRecipe> findMatchingRecipe() {
        if (level == null) {
            return null;
        }

        for (RecipeHolder<CentrifugeRecipe> holder : CentrifugeRecipes.all(level)) {
            CentrifugeRecipe recipe = holder.value();
            if (matchesItemInputs(recipe) && matchesFluidInputs(recipe)) {
                return holder;
            }
        }
        return null;
    }

    private boolean matchesItemInputs(CentrifugeRecipe recipe) {
        boolean[] used = new boolean[INPUT_SLOT_COUNT];
        for (CentrifugeRecipe.CountedIngredient input : recipe.itemInputs()) {
            boolean matched = false;
            for (int slot = FIRST_INPUT_SLOT; slot < FIRST_OUTPUT_SLOT; slot++) {
                int localSlot = slot - FIRST_INPUT_SLOT;
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!used[localSlot] && input.ingredient().test(stack) && stack.getCount() >= input.count()) {
                    used[localSlot] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesFluidInputs(CentrifugeRecipe recipe) {
        List<CentrifugeRecipe.FluidIngredient> fluidInputs = recipe.fluidInputs();
        if (fluidInputs.isEmpty()) {
            return true;
        }
        CentrifugeRecipe.FluidIngredient input = fluidInputs.getFirst();
        FluidStack stored = inputTank.getFluid();
        return !stored.isEmpty() && stored.is(input.fluid()) && stored.getAmount() >= input.amount();
    }

    private boolean canOutput(CentrifugeRecipe recipe) {
        ItemStackHandler copy = copyInventory();
        for (CentrifugeRecipe.ItemOutput output : recipe.itemOutputs()) {
            if (output.chance() <= 0.0D || output.stack().isEmpty()) {
                continue;
            }
            ItemStack remaining = insertItemOutput(copy, output.stack().copy());
            if (!remaining.isEmpty()) {
                return false;
            }
        }

        for (FluidStack output : recipe.fluidOutputs()) {
            if (output.isEmpty() || outputTank.fill(output.copy(), IFluidHandler.FluidAction.SIMULATE) < output.getAmount()) {
                return false;
            }
        }
        return true;
    }

    private void completeRecipe(CentrifugeRecipe recipe) {
        consumeInputs(recipe);
        for (CentrifugeRecipe.ItemOutput output : recipe.itemOutputs()) {
            if (output.chance() <= 0.0D || output.stack().isEmpty() || level.random.nextDouble() > output.chance()) {
                continue;
            }
            insertItemOutput(inventory, output.stack().copy());
        }
        for (FluidStack output : recipe.fluidOutputs()) {
            if (!output.isEmpty()) {
                outputTank.fill(output.copy(), IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    private void consumeInputs(CentrifugeRecipe recipe) {
        boolean[] used = new boolean[INPUT_SLOT_COUNT];
        for (CentrifugeRecipe.CountedIngredient input : recipe.itemInputs()) {
            for (int slot = FIRST_INPUT_SLOT; slot < FIRST_OUTPUT_SLOT; slot++) {
                int localSlot = slot - FIRST_INPUT_SLOT;
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!used[localSlot] && input.ingredient().test(stack) && stack.getCount() >= input.count()) {
                    stack.shrink(input.count());
                    used[localSlot] = true;
                    break;
                }
            }
        }

        for (CentrifugeRecipe.FluidIngredient input : recipe.fluidInputs()) {
            inputTank.drain(input.stack(), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private ItemStackHandler copyInventory() {
        ItemStackHandler copy = new ItemStackHandler(INVENTORY_SLOT_COUNT);
        for (int slot = 0; slot < INVENTORY_SLOT_COUNT; slot++) {
            copy.setStackInSlot(slot, inventory.getStackInSlot(slot).copy());
        }
        return copy;
    }

    private static ItemStack insertItemOutput(ItemStackHandler handler, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = FIRST_OUTPUT_SLOT; slot < INVENTORY_SLOT_COUNT && !remaining.isEmpty(); slot++) {
            remaining = insertIntoHandlerSlot(handler, slot, remaining);
        }
        return remaining;
    }

    private static ItemStack insertIntoHandlerSlot(ItemStackHandler handler, int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack existing = handler.getStackInSlot(slot);
        if (existing.isEmpty()) {
            int inserted = Math.min(stack.getCount(), Math.min(handler.getSlotLimit(slot), stack.getMaxStackSize()));
            ItemStack placed = stack.copy();
            placed.setCount(inserted);
            handler.setStackInSlot(slot, placed);
            ItemStack remainder = stack.copy();
            remainder.shrink(inserted);
            return remainder;
        }

        if (!ItemStack.isSameItemSameComponents(existing, stack)) {
            return stack;
        }

        int inserted = Math.min(stack.getCount(), Math.min(handler.getSlotLimit(slot), existing.getMaxStackSize()) - existing.getCount());
        if (inserted <= 0) {
            return stack;
        }

        ItemStack merged = existing.copy();
        merged.grow(inserted);
        handler.setStackInSlot(slot, merged);
        ItemStack remainder = stack.copy();
        remainder.shrink(inserted);
        return remainder;
    }

    private boolean tryAutoOutputFluid() {
        if (level == null || level.isClientSide || outputTank.getFluidAmount() <= 0) {
            return false;
        }

        Direction outputSide = getFacing().getCounterClockWise();
        BlockPos portPos = outputFluidPortPos();
        BlockPos targetPos = portPos.relative(outputSide);
        if (isOwnCentrifugeBlock(targetPos)) {
            return false;
        }

        IFluidHandler target = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, outputSide.getOpposite());
        if (target == null) {
            return false;
        }

        FluidStack offered = outputTank.drain(outputTank.getFluidAmount(), IFluidHandler.FluidAction.SIMULATE);
        if (offered.isEmpty()) {
            return false;
        }

        int accepted = target.fill(offered, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            return false;
        }

        FluidStack drained = outputTank.drain(Math.min(accepted, offered.getAmount()), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return false;
        }

        int inserted = target.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (inserted < drained.getAmount()) {
            FluidStack remainder = drained.copy();
            remainder.setAmount(drained.getAmount() - inserted);
            outputTank.fill(remainder, IFluidHandler.FluidAction.EXECUTE);
        }
        return inserted > 0;
    }

    private boolean tryAutoOutputItems() {
        if (level == null || level.isClientSide) {
            return false;
        }

        boolean changed = false;
        Direction outputSide = getFacing().getCounterClockWise();
        BlockPos portPos = outputItemPortPos();
        BlockPos targetPos = portPos.relative(outputSide);
        if (isOwnCentrifugeBlock(targetPos)) {
            return false;
        }

        for (int slot = FIRST_OUTPUT_SLOT; slot < INVENTORY_SLOT_COUNT; slot++) {
            ItemStack output = inventory.getStackInSlot(slot);
            if (output.isEmpty()) {
                continue;
            }

            ItemStack remainder = tryInsertItemOutput(targetPos, outputSide, output, true);
            if (remainder.getCount() >= output.getCount()) {
                continue;
            }

            ItemStack committedRemainder = tryInsertItemOutput(targetPos, outputSide, output, false);
            inventory.setStackInSlot(slot, committedRemainder);
            changed = true;
        }
        return changed;
    }

    private ItemStack tryInsertItemOutput(BlockPos targetPos, Direction outputDirection, ItemStack stack, boolean simulate) {
        var directRemainder = ConveyorDirectTransfer.tryInsert(level, targetPos, stack, outputDirection.getOpposite(), simulate);
        if (directRemainder.isPresent()) {
            return directRemainder.get();
        }

        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, outputDirection.getOpposite());
        if (handler != null) {
            return ConveyorInsertionUtil.insertIntoHandler(handler, stack, simulate);
        }

        BlockState targetState = level.getBlockState(targetPos);
        if (!(targetState.getBlock() instanceof ConveyorBeltSurface surface)) {
            return stack;
        }
        if (targetState.getBlock() instanceof ConveyorGateSurface gate
                && !gate.skyent$canConveyorItemEnter(level, targetPos, targetState, outputDirection.getOpposite())) {
            return stack;
        }

        Vec3 outputStart = new Vec3(
                targetPos.getX() + 0.5D - outputDirection.getStepX() * 0.45D,
                targetPos.getY() + ConveyorLogicConstants.ITEM_PATH_Y_OFFSET,
                targetPos.getZ() + 0.5D - outputDirection.getStepZ() * 0.45D
        );
        Vec3 spawnPos = surface.getClosestSnappingPosition(level, targetPos, outputStart);
        if (!hasRoomAt(spawnPos)) {
            return stack;
        }

        if (!simulate) {
            ConveyorMovingItemEntity entity = new ConveyorMovingItemEntity(level, spawnPos.x, spawnPos.y, spawnPos.z, stack.copy());
            level.addFreshEntity(entity);
        }
        return ItemStack.EMPTY;
    }

    private boolean hasRoomAt(Vec3 position) {
        AABB searchBox = new AABB(position, position).inflate(ConveyorMovingItemEntity.ITEM_SPACING_DISTANCE);
        return level.getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, entity -> !entity.isRemoved()).isEmpty();
    }

    private Vec3 getMachineCenter() {
        Direction facing = getFacing();
        Vec3 sum = Vec3.ZERO;
        int count = 0;
        for (int x = 0; x < CentrifugeBlock.SIZE_X; x++) {
            for (int y = 0; y < CentrifugeBlock.SIZE_Y; y++) {
                for (int z = 0; z < CentrifugeBlock.SIZE_Z; z++) {
                    sum = sum.add(Vec3.atCenterOf(CentrifugeBlock.localToWorld(worldPosition, facing, x, y, z)));
                    count++;
                }
            }
        }
        return sum.scale(1.0D / Math.max(1, count));
    }

    private void tickClientMotorLoop() {
        if (level == null || !level.isClientSide) {
            return;
        }
        if (running) {
            startClientMotorLoop(level, worldPosition, getMachineCenter());
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
        return "centrifuge_motor:" + pos.asLong();
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
                        (BooleanSupplier) () -> level.getBlockEntity(pos) instanceof CentrifugeBlockEntity centrifuge && centrifuge.isRunning()
                );
            } else {
                Method method = managerClass.getMethod(methodName, clientLevelClass, String.class, net.minecraft.sounds.SoundEvent.class);
                method.invoke(null, level, motorLoopKey(pos), ModSounds.HEAVY_ELECTRIC_MOTOR_LOOP.get());
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to update Centrifuge client loop sound", exception);
        }
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private FluidTank createTank() {
        return new FluidTank(TANK_CAPACITY_MB) {
            @Override
            protected void onContentsChanged() {
                setChangedAndSync();
            }
        };
    }

    private static int fluidId(FluidTank tank) {
        FluidStack fluid = tank.getFluid();
        return fluid.isEmpty() ? 0 : BuiltInRegistries.FLUID.getId(fluid.getFluid());
    }

    private static int low(int value) {
        return value & 0xFFFF;
    }

    private static int high(int value) {
        return value >>> 16;
    }

    private record AutomationHandlerKey(BlockPos queriedPos, @Nullable Direction side) {
    }

    private final class AutomationItemHandler implements IItemHandler {
        private final BlockPos queriedPos;
        @Nullable
        private final Direction side;

        private AutomationItemHandler(BlockPos queriedPos, @Nullable Direction side) {
            this.queriedPos = queriedPos;
            this.side = side;
        }

        @Override
        public int getSlots() {
            return INPUT_SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            int mappedSlot = mapAutomationSlot(slot);
            return mappedSlot >= 0 ? inventory.getStackInSlot(mappedSlot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int mappedSlot = mapAutomationSlot(slot);
            if (!isInputItemPort(queriedPos, side) || mappedSlot < FIRST_INPUT_SLOT || mappedSlot >= FIRST_OUTPUT_SLOT) {
                return stack;
            }
            return inventory.insertItem(mappedSlot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int mappedSlot = mapAutomationSlot(slot);
            if (!isOutputItemPort(queriedPos, side) || mappedSlot < FIRST_OUTPUT_SLOT || mappedSlot >= INVENTORY_SLOT_COUNT) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(mappedSlot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            int mappedSlot = mapAutomationSlot(slot);
            return mappedSlot >= 0 ? inventory.getSlotLimit(mappedSlot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            int mappedSlot = mapAutomationSlot(slot);
            return isInputItemPort(queriedPos, side) && mappedSlot >= 0 && inventory.isItemValid(mappedSlot, stack);
        }

        private int mapAutomationSlot(int slot) {
            if (slot < 0 || slot >= INPUT_SLOT_COUNT) {
                return -1;
            }
            if (isInputItemPort(queriedPos, side)) {
                return FIRST_INPUT_SLOT + slot;
            }
            if (isOutputItemPort(queriedPos, side)) {
                return FIRST_OUTPUT_SLOT + slot;
            }
            return -1;
        }
    }

    private final class CentrifugeItemStackHandler extends ItemStackHandler {
        private CentrifugeItemStackHandler() {
            super(INVENTORY_SLOT_COUNT);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= FIRST_INPUT_SLOT && slot < FIRST_OUTPUT_SLOT;
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (slot < FIRST_OUTPUT_SLOT) {
                progress = 0;
            }
            setChanged();
        }

        private void deserializeAndMigrate(HolderLookup.Provider registries, CompoundTag tag) {
            deserializeNBT(registries, tag);
            if (getSlots() != INVENTORY_SLOT_COUNT) {
                setSize(INVENTORY_SLOT_COUNT);
            }
        }
    }

    private static final class SingleTankFluidHandler implements IFluidHandler {
        private final FluidTank tank;
        private final boolean allowFill;
        private final boolean allowDrain;

        private SingleTankFluidHandler(FluidTank tank, boolean allowFill, boolean allowDrain) {
            this.tank = tank;
            this.allowFill = allowFill;
            this.allowDrain = allowDrain;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tankIndex) {
            return tankIndex == 0 ? tank.getFluidInTank(0) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tankIndex) {
            return tankIndex == 0 ? tank.getTankCapacity(0) : 0;
        }

        @Override
        public boolean isFluidValid(int tankIndex, FluidStack stack) {
            return tankIndex == 0 && allowFill && tank.isFluidValid(0, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return allowFill ? tank.fill(resource, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return allowDrain ? tank.drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return allowDrain ? tank.drain(maxDrain, action) : FluidStack.EMPTY;
        }
    }
}
