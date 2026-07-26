package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.client.model.SkyentModelData;
import com.skyeshade.skyent.client.render.HeatingChamberLighting;
import com.skyeshade.skyent.content.block.MVChemicalReactorBlock;
import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorDirectTransfer;
import com.skyeshade.skyent.content.conveyor.ConveyorGateSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorInsertionUtil;
import com.skyeshade.skyent.content.conveyor.ConveyorLogicConstants;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.content.energy.RJEnergyInfo;
import com.skyeshade.skyent.content.energy.RJStorage;
import com.skyeshade.skyent.content.menu.MVChemicalReactorMenu;
import com.skyeshade.skyent.content.recipe.ChemicalReactorRecipe;
import com.skyeshade.skyent.content.recipe.ChemicalReactorRecipes;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModSounds;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.sounds.SoundSource;
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

public class MVChemicalReactorBlockEntity extends BlockEntity implements MenuProvider, RJEnergyInfo {
    public static final int INPUT_SLOT_COUNT = 3;
    public static final int OUTPUT_SLOT_COUNT = 3;
    public static final int FIRST_INPUT_SLOT = 0;
    public static final int FIRST_OUTPUT_SLOT = FIRST_INPUT_SLOT + INPUT_SLOT_COUNT;
    public static final int INVENTORY_SLOT_COUNT = INPUT_SLOT_COUNT + OUTPUT_SLOT_COUNT;
    public static final int TANK_CAPACITY_MB = 16_000;
    public static final int INPUT_TANK_COUNT = 2;
    public static final int OUTPUT_TANK_COUNT = 2;
    public static final int ENERGY_CAPACITY_RJ = 512_000;
    public static final int MAX_INPUT_RJ_PER_TICK = 512;
    public static final ElectricalTier REQUIRED_TIER = ElectricalTier.MV;
    public static final double RUNNING_CURRENT_AMPS = 1.0D;

    private static final int LIGHT_CHECK_INTERVAL_TICKS = 40;
    private static final float REACTOR_LOOP_VOLUME = 0.8F;
    private static final float REACTOR_LOOP_PITCH = 1.0F;
    private static final String TAG_STORED_RJ = "StoredRJ";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_MAX_PROGRESS = "MaxProgress";
    private static final String TAG_CURRENT_ENERGY_USAGE = "CurrentEnergyUsage";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_INPUT_TANK_1 = "InputTank1";
    private static final String TAG_INPUT_TANK_2 = "InputTank2";
    private static final String TAG_OUTPUT_TANK_1 = "OutputTank1";
    private static final String TAG_OUTPUT_TANK_2 = "OutputTank2";

    private static final int DATA_ENERGY_LOW = 0;
    private static final int DATA_ENERGY_HIGH = 1;
    private static final int DATA_MAX_ENERGY_LOW = 2;
    private static final int DATA_MAX_ENERGY_HIGH = 3;
    private static final int DATA_PROGRESS = 4;
    private static final int DATA_MAX_PROGRESS = 5;
    private static final int DATA_CURRENT_ENERGY_USAGE = 6;
    private static final int DATA_INPUT_1_AMOUNT_LOW = 7;
    private static final int DATA_INPUT_1_AMOUNT_HIGH = 8;
    private static final int DATA_INPUT_1_CAPACITY_LOW = 9;
    private static final int DATA_INPUT_1_CAPACITY_HIGH = 10;
    private static final int DATA_INPUT_1_FLUID_LOW = 11;
    private static final int DATA_INPUT_1_FLUID_HIGH = 12;
    private static final int DATA_INPUT_2_AMOUNT_LOW = 13;
    private static final int DATA_INPUT_2_AMOUNT_HIGH = 14;
    private static final int DATA_INPUT_2_CAPACITY_LOW = 15;
    private static final int DATA_INPUT_2_CAPACITY_HIGH = 16;
    private static final int DATA_INPUT_2_FLUID_LOW = 17;
    private static final int DATA_INPUT_2_FLUID_HIGH = 18;
    private static final int DATA_OUTPUT_1_AMOUNT_LOW = 19;
    private static final int DATA_OUTPUT_1_AMOUNT_HIGH = 20;
    private static final int DATA_OUTPUT_1_CAPACITY_LOW = 21;
    private static final int DATA_OUTPUT_1_CAPACITY_HIGH = 22;
    private static final int DATA_OUTPUT_1_FLUID_LOW = 23;
    private static final int DATA_OUTPUT_1_FLUID_HIGH = 24;
    private static final int DATA_OUTPUT_2_AMOUNT_LOW = 25;
    private static final int DATA_OUTPUT_2_AMOUNT_HIGH = 26;
    private static final int DATA_OUTPUT_2_CAPACITY_LOW = 27;
    private static final int DATA_OUTPUT_2_CAPACITY_HIGH = 28;
    private static final int DATA_OUTPUT_2_FLUID_LOW = 29;
    private static final int DATA_OUTPUT_2_FLUID_HIGH = 30;
    private static final int DATA_COUNT = 31;

    private final ReactorItemStackHandler inventory = new ReactorItemStackHandler();
    private final FluidTank[] inputTanks = {createTank(), createTank()};
    private final FluidTank[] outputTanks = {createTank(), createTank()};
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
                case DATA_INPUT_1_AMOUNT_LOW -> low(inputTanks[0].getFluidAmount());
                case DATA_INPUT_1_AMOUNT_HIGH -> high(inputTanks[0].getFluidAmount());
                case DATA_INPUT_1_CAPACITY_LOW -> low(inputTanks[0].getCapacity());
                case DATA_INPUT_1_CAPACITY_HIGH -> high(inputTanks[0].getCapacity());
                case DATA_INPUT_1_FLUID_LOW -> low(fluidId(inputTanks[0]));
                case DATA_INPUT_1_FLUID_HIGH -> high(fluidId(inputTanks[0]));
                case DATA_INPUT_2_AMOUNT_LOW -> low(inputTanks[1].getFluidAmount());
                case DATA_INPUT_2_AMOUNT_HIGH -> high(inputTanks[1].getFluidAmount());
                case DATA_INPUT_2_CAPACITY_LOW -> low(inputTanks[1].getCapacity());
                case DATA_INPUT_2_CAPACITY_HIGH -> high(inputTanks[1].getCapacity());
                case DATA_INPUT_2_FLUID_LOW -> low(fluidId(inputTanks[1]));
                case DATA_INPUT_2_FLUID_HIGH -> high(fluidId(inputTanks[1]));
                case DATA_OUTPUT_1_AMOUNT_LOW -> low(outputTanks[0].getFluidAmount());
                case DATA_OUTPUT_1_AMOUNT_HIGH -> high(outputTanks[0].getFluidAmount());
                case DATA_OUTPUT_1_CAPACITY_LOW -> low(outputTanks[0].getCapacity());
                case DATA_OUTPUT_1_CAPACITY_HIGH -> high(outputTanks[0].getCapacity());
                case DATA_OUTPUT_1_FLUID_LOW -> low(fluidId(outputTanks[0]));
                case DATA_OUTPUT_1_FLUID_HIGH -> high(fluidId(outputTanks[0]));
                case DATA_OUTPUT_2_AMOUNT_LOW -> low(outputTanks[1].getFluidAmount());
                case DATA_OUTPUT_2_AMOUNT_HIGH -> high(outputTanks[1].getFluidAmount());
                case DATA_OUTPUT_2_CAPACITY_LOW -> low(outputTanks[1].getCapacity());
                case DATA_OUTPUT_2_CAPACITY_HIGH -> high(outputTanks[1].getCapacity());
                case DATA_OUTPUT_2_FLUID_LOW -> low(fluidId(outputTanks[1]));
                case DATA_OUTPUT_2_FLUID_HIGH -> high(fluidId(outputTanks[1]));
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
    private int cachedSharedPackedLight = -1;
    private int lightCheckTicks;

    public MVChemicalReactorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.MV_CHEMICAL_REACTOR.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MVChemicalReactorBlockEntity reactor) {
        if (level.isClientSide) {
            return;
        }

        int previousUsage = reactor.currentEnergyUsage;
        int previousProgress = reactor.progress;
        int previousMaxProgress = reactor.maxProgress;
        reactor.currentEnergyUsage = 0;
        boolean changed = reactor.tryAutoOutputFluids();
        changed |= reactor.tryAutoOutputItems();

        RecipeHolder<ChemicalReactorRecipe> recipeHolder = reactor.findMatchingRecipe();
        if (recipeHolder == null) {
            reactor.progress = 0;
            reactor.maxProgress = 0;
        } else {
            ChemicalReactorRecipe recipe = recipeHolder.value();
            reactor.maxProgress = recipe.processTime();
            if (reactor.rjStorage.getStoredRJ() >= recipe.energyPerTick()) {
                reactor.rjStorage.consumeRJ(recipe.energyPerTick());
                reactor.currentEnergyUsage = recipe.energyPerTick();
                reactor.progress++;
                if (reactor.progress >= recipe.processTime()) {
                    reactor.completeRecipe(recipe);
                    reactor.progress = 0;
                }
            }
        }

        if (changed
                || previousUsage != reactor.currentEnergyUsage
                || previousProgress != reactor.progress
                || previousMaxProgress != reactor.maxProgress) {
            reactor.setChangedAndSync();
        } else if (reactor.currentEnergyUsage > 0) {
            setChanged(level, pos, state);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, MVChemicalReactorBlockEntity reactor) {
        if (!level.isClientSide) {
            return;
        }

        reactor.lightCheckTicks++;
        if (reactor.lightCheckTicks >= LIGHT_CHECK_INTERVAL_TICKS) {
            reactor.lightCheckTicks = 0;
            reactor.refreshSharedLight(false);
        }
        reactor.tickClientReactorLoop();
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public ContainerData getData() {
        return data;
    }

    public FluidStack getTankFluid(int tankIndex) {
        FluidTank tank = tankByMenuIndex(tankIndex);
        return tank == null ? FluidStack.EMPTY : tank.getFluid();
    }

    public int getTankAmount(int tankIndex) {
        FluidTank tank = tankByMenuIndex(tankIndex);
        return tank == null ? 0 : tank.getFluidAmount();
    }

    public int getTankCapacity(int tankIndex) {
        FluidTank tank = tankByMenuIndex(tankIndex);
        return tank == null ? 0 : tank.getCapacity();
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
        int inputTank = inputFluidPortIndex(queriedPos, side);
        if (inputTank >= 0) {
            AutomationHandlerKey key = new AutomationHandlerKey(queriedPos.immutable(), side);
            return automationFluidHandlers.computeIfAbsent(key, ignored -> new SingleTankFluidHandler(inputTanks[inputTank], true, false));
        }

        int outputTank = outputFluidPortIndex(queriedPos, side);
        if (outputTank >= 0) {
            AutomationHandlerKey key = new AutomationHandlerKey(queriedPos.immutable(), side);
            return automationFluidHandlers.computeIfAbsent(key, ignored -> new SingleTankFluidHandler(outputTanks[outputTank], false, true));
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

    public int getProgress() {
        return progress;
    }

    public int getMaxProgress() {
        return maxProgress;
    }

    public boolean isRunning() {
        return currentEnergyUsage > 0;
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
        return Component.translatable("container.skyent.mv_chemical_reactor");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MVChemicalReactorMenu(containerId, playerInventory, this, data);
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
            stopClientReactorLoop(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            stopClientReactorLoop(level, worldPosition);
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_STORED_RJ, rjStorage.getStoredRJ());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_MAX_PROGRESS, maxProgress);
        tag.putInt(TAG_CURRENT_ENERGY_USAGE, currentEnergyUsage);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        tag.put(TAG_INPUT_TANK_1, inputTanks[0].writeToNBT(registries, new CompoundTag()));
        tag.put(TAG_INPUT_TANK_2, inputTanks[1].writeToNBT(registries, new CompoundTag()));
        tag.put(TAG_OUTPUT_TANK_1, outputTanks[0].writeToNBT(registries, new CompoundTag()));
        tag.put(TAG_OUTPUT_TANK_2, outputTanks[1].writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rjStorage.setStoredRJ(tag.getInt(TAG_STORED_RJ));
        progress = tag.getInt(TAG_PROGRESS);
        maxProgress = tag.getInt(TAG_MAX_PROGRESS);
        currentEnergyUsage = Math.max(0, tag.getInt(TAG_CURRENT_ENERGY_USAGE));
        inventory.deserializeAndMigrate(registries, tag.getCompound(TAG_INVENTORY));
        inputTanks[0].readFromNBT(registries, tag.getCompound(TAG_INPUT_TANK_1));
        inputTanks[1].readFromNBT(registries, tag.getCompound(TAG_INPUT_TANK_2));
        outputTanks[0].readFromNBT(registries, tag.getCompound(TAG_OUTPUT_TANK_1));
        outputTanks[1].readFromNBT(registries, tag.getCompound(TAG_OUTPUT_TANK_2));
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

    @Nullable
    private RecipeHolder<ChemicalReactorRecipe> findMatchingRecipe() {
        if (level == null) {
            return null;
        }

        for (RecipeHolder<ChemicalReactorRecipe> recipe : ChemicalReactorRecipes.all(level)) {
            if (canProcess(recipe.value())) {
                return recipe;
            }
        }
        return null;
    }

    private boolean canProcess(ChemicalReactorRecipe recipe) {
        return hasItemInputs(recipe)
                && hasFluidInputs(recipe)
                && canAcceptItemOutputs(recipe)
                && canAcceptFluidOutputs(recipe);
    }

    private boolean hasItemInputs(ChemicalReactorRecipe recipe) {
        for (ChemicalReactorRecipe.CountedIngredient ingredient : recipe.itemInputs()) {
            int count = 0;
            for (int slot = FIRST_INPUT_SLOT; slot < FIRST_OUTPUT_SLOT; slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (ingredient.ingredient().test(stack)) {
                    count += stack.getCount();
                }
            }
            if (count < ingredient.count()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasFluidInputs(ChemicalReactorRecipe recipe) {
        boolean[] usedTanks = new boolean[inputTanks.length];
        for (ChemicalReactorRecipe.FluidIngredient ingredient : recipe.fluidInputs()) {
            boolean matched = false;
            for (int tank = 0; tank < inputTanks.length; tank++) {
                if (usedTanks[tank]) {
                    continue;
                }
                FluidStack stored = inputTanks[tank].getFluid();
                if (!stored.isEmpty() && stored.is(ingredient.fluid()) && stored.getAmount() >= ingredient.amount()) {
                    usedTanks[tank] = true;
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

    private boolean canAcceptItemOutputs(ChemicalReactorRecipe recipe) {
        ItemStackHandler simulated = copyInventory();
        for (ItemStack output : recipe.itemOutputs()) {
            ItemStack remaining = output.copy();
            for (int slot = FIRST_OUTPUT_SLOT; slot < INVENTORY_SLOT_COUNT && !remaining.isEmpty(); slot++) {
                remaining = insertIntoHandlerSlot(simulated, slot, remaining);
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean canAcceptFluidOutputs(ChemicalReactorRecipe recipe) {
        List<FluidStack> outputs = recipe.fluidOutputs();
        if (outputs.size() > outputTanks.length) {
            return false;
        }
        for (int index = 0; index < outputs.size(); index++) {
            if (outputTanks[index].fill(outputs.get(index), IFluidHandler.FluidAction.SIMULATE) < outputs.get(index).getAmount()) {
                return false;
            }
        }
        return true;
    }

    private void completeRecipe(ChemicalReactorRecipe recipe) {
        consumeItemInputs(recipe);
        consumeFluidInputs(recipe);
        insertItemOutputs(recipe);
        insertFluidOutputs(recipe);
        setChangedAndSync();
    }

    private void consumeItemInputs(ChemicalReactorRecipe recipe) {
        for (ChemicalReactorRecipe.CountedIngredient ingredient : recipe.itemInputs()) {
            int remaining = ingredient.count();
            for (int slot = FIRST_INPUT_SLOT; slot < FIRST_OUTPUT_SLOT && remaining > 0; slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!ingredient.ingredient().test(stack)) {
                    continue;
                }
                int consumed = Math.min(remaining, stack.getCount());
                stack.shrink(consumed);
                remaining -= consumed;
            }
        }
    }

    private void consumeFluidInputs(ChemicalReactorRecipe recipe) {
        boolean[] usedTanks = new boolean[inputTanks.length];
        for (ChemicalReactorRecipe.FluidIngredient ingredient : recipe.fluidInputs()) {
            for (int tank = 0; tank < inputTanks.length; tank++) {
                if (usedTanks[tank]) {
                    continue;
                }
                FluidStack stored = inputTanks[tank].getFluid();
                if (!stored.isEmpty() && stored.is(ingredient.fluid()) && stored.getAmount() >= ingredient.amount()) {
                    inputTanks[tank].drain(new FluidStack(ingredient.fluid(), ingredient.amount()), IFluidHandler.FluidAction.EXECUTE);
                    usedTanks[tank] = true;
                    break;
                }
            }
        }
    }

    private void insertItemOutputs(ChemicalReactorRecipe recipe) {
        for (ItemStack output : recipe.itemOutputs()) {
            ItemStack remaining = output.copy();
            for (int slot = FIRST_OUTPUT_SLOT; slot < INVENTORY_SLOT_COUNT && !remaining.isEmpty(); slot++) {
                remaining = insertIntoHandlerSlot(inventory, slot, remaining);
            }
        }
    }

    private void insertFluidOutputs(ChemicalReactorRecipe recipe) {
        List<FluidStack> outputs = recipe.fluidOutputs();
        for (int index = 0; index < Math.min(outputs.size(), outputTanks.length); index++) {
            outputTanks[index].fill(outputs.get(index), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private ItemStackHandler copyInventory() {
        ItemStackHandler copy = new ItemStackHandler(INVENTORY_SLOT_COUNT);
        for (int slot = 0; slot < INVENTORY_SLOT_COUNT; slot++) {
            copy.setStackInSlot(slot, inventory.getStackInSlot(slot).copy());
        }
        return copy;
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

    private boolean tryAutoOutputFluids() {
        boolean changed = false;
        changed |= tryAutoOutputFluid(0);
        changed |= tryAutoOutputFluid(1);
        return changed;
    }

    private boolean tryAutoOutputFluid(int tankIndex) {
        if (level == null || level.isClientSide || tankIndex < 0 || tankIndex >= outputTanks.length || outputTanks[tankIndex].getFluidAmount() <= 0) {
            return false;
        }

        Direction outputSide = getFacing().getCounterClockWise();
        BlockPos portPos = outputFluidPortPos(tankIndex);
        BlockPos targetPos = portPos.relative(outputSide);
        if (isOwnReactorBlock(targetPos)) {
            return false;
        }

        IFluidHandler target = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, outputSide.getOpposite());
        if (target == null) {
            return false;
        }

        FluidStack offered = outputTanks[tankIndex].drain(outputTanks[tankIndex].getFluidAmount(), IFluidHandler.FluidAction.SIMULATE);
        if (offered.isEmpty()) {
            return false;
        }

        int accepted = target.fill(offered, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            return false;
        }

        FluidStack drained = outputTanks[tankIndex].drain(Math.min(accepted, offered.getAmount()), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return false;
        }

        int inserted = target.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (inserted < drained.getAmount()) {
            FluidStack remainder = drained.copy();
            remainder.setAmount(drained.getAmount() - inserted);
            outputTanks[tankIndex].fill(remainder, IFluidHandler.FluidAction.EXECUTE);
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
        if (isOwnReactorBlock(targetPos)) {
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
        for (int x = 0; x < MVChemicalReactorBlock.SIZE_X; x++) {
            for (int y = 0; y < MVChemicalReactorBlock.SIZE_Y; y++) {
                for (int z = 0; z < MVChemicalReactorBlock.SIZE_Z; z++) {
                    sum = sum.add(Vec3.atCenterOf(MVChemicalReactorBlock.localToWorld(worldPosition, facing, x, y, z)));
                    count++;
                }
            }
        }
        return sum.scale(1.0D / Math.max(1, count));
    }

    private void tickClientReactorLoop() {
        if (level == null || !level.isClientSide) {
            return;
        }
        if (isRunning()) {
            startClientReactorLoop(level, worldPosition, getMachineCenter());
        } else {
            stopClientReactorLoop(level, worldPosition);
        }
    }

    private static void startClientReactorLoop(Level level, BlockPos pos, Vec3 center) {
        invokeClientLoopMethod("startOrUpdateNamedLoop", level, pos, center);
    }

    private static void stopClientReactorLoop(Level level, BlockPos pos) {
        invokeClientLoopMethod("stopNamedLoop", level, pos, Vec3.ZERO);
    }

    private static String reactorLoopKey(BlockPos pos) {
        return "mv_chemical_reactor_running:" + pos.asLong();
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
                        reactorLoopKey(pos),
                        ModSounds.CHEMICAL_REACTOR.get(),
                        SoundSource.BLOCKS,
                        (Supplier<Vec3>) () -> center,
                        REACTOR_LOOP_VOLUME,
                        REACTOR_LOOP_PITCH,
                        (BooleanSupplier) () -> level.getBlockEntity(pos) instanceof MVChemicalReactorBlockEntity reactor && reactor.isRunning()
                );
            } else {
                Method method = managerClass.getMethod(methodName, clientLevelClass, String.class, net.minecraft.sounds.SoundEvent.class);
                method.invoke(null, level, reactorLoopKey(pos), ModSounds.CHEMICAL_REACTOR.get());
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to update Chemical Reactor client loop sound", exception);
        }
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private int computePackedLight(Level level) {
        Direction facing = getFacing();
        return HeatingChamberLighting.computeMaxPackedLight(
                level,
                worldPosition,
                facing,
                MVChemicalReactorBlock.SIZE_X,
                MVChemicalReactorBlock.SIZE_Y,
                MVChemicalReactorBlock.SIZE_Z,
                MVChemicalReactorBlock::localToWorld
        );
    }

    private Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(MVChemicalReactorBlock.FACING) ? state.getValue(MVChemicalReactorBlock.FACING) : Direction.NORTH;
    }

    private boolean isInputItemPort(BlockPos queriedPos, @Nullable Direction side) {
        return isPort(queriedPos, side, 2, 0, 2, getFacing().getClockWise());
    }

    private boolean isOutputItemPort(BlockPos queriedPos, @Nullable Direction side) {
        return isPort(queriedPos, side, 0, 0, 0, getFacing().getCounterClockWise());
    }

    private int inputFluidPortIndex(BlockPos queriedPos, @Nullable Direction side) {
        if (isPort(queriedPos, side, 2, 0, 0, getFacing().getClockWise())) {
            return 0;
        }
        return isPort(queriedPos, side, 2, 0, 1, getFacing().getClockWise()) ? 1 : -1;
    }

    private int outputFluidPortIndex(BlockPos queriedPos, @Nullable Direction side) {
        if (isPort(queriedPos, side, 0, 0, 2, getFacing().getCounterClockWise())) {
            return 0;
        }
        return isPort(queriedPos, side, 0, 0, 1, getFacing().getCounterClockWise()) ? 1 : -1;
    }

    private BlockPos outputFluidPortPos(int tankIndex) {
        return MVChemicalReactorBlock.localToWorld(worldPosition, getFacing(), 0, 0, tankIndex == 0 ? 2 : 1);
    }

    private BlockPos outputItemPortPos() {
        return MVChemicalReactorBlock.localToWorld(worldPosition, getFacing(), 0, 0, 0);
    }

    private boolean isOwnReactorBlock(BlockPos pos) {
        if (level == null) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.MV_CHEMICAL_REACTOR.get()) && !state.is(ModBlocks.MV_CHEMICAL_REACTOR_PART.get())) {
            return false;
        }
        return MVChemicalReactorBlock.getMasterPos(state, pos).equals(worldPosition);
    }

    private boolean isPort(BlockPos queriedPos, @Nullable Direction side, int localX, int localY, int localZ, Direction worldSide) {
        return side == worldSide && queriedPos.equals(MVChemicalReactorBlock.localToWorld(worldPosition, getFacing(), localX, localY, localZ));
    }

    @Nullable
    private FluidTank tankByMenuIndex(int tankIndex) {
        return switch (tankIndex) {
            case 0 -> inputTanks[0];
            case 1 -> inputTanks[1];
            case 2 -> outputTanks[0];
            case 3 -> outputTanks[1];
            default -> null;
        };
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

    private final class ReactorItemStackHandler extends ItemStackHandler {
        private ReactorItemStackHandler() {
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
