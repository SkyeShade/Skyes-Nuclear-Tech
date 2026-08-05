package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.ElectricBlastFurnaceBlock;
import com.skyeshade.skyent.content.conveyor.MachineConveyorOutput;
import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.content.energy.RJEnergyInfo;
import com.skyeshade.skyent.content.energy.RJStorage;
import com.skyeshade.skyent.content.menu.ElectricBlastFurnaceMenu;
import com.skyeshade.skyent.content.recipe.ElectricBlastFurnaceMode;
import com.skyeshade.skyent.content.recipe.ElectricBlastFurnaceRecipe;
import com.skyeshade.skyent.content.recipe.ElectricBlastFurnaceRecipes;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModSounds;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class ElectricBlastFurnaceBlockEntity extends BlockEntity implements MenuProvider, RJEnergyInfo {
    public static final int ELECTRODE_SLOT_COUNT = 3;
    public static final int INPUT_SLOT_COUNT = 12;
    public static final int ELECTRODE_SLOT_0 = 0;
    public static final int FIRST_INPUT_SLOT = ELECTRODE_SLOT_0 + ELECTRODE_SLOT_COUNT;
    public static final int OUTPUT_SLOT = FIRST_INPUT_SLOT + INPUT_SLOT_COUNT;
    public static final int POWER_ITEM_SLOT = OUTPUT_SLOT + 1;
    public static final int INVENTORY_SLOT_COUNT = POWER_ITEM_SLOT + 1;
    public static final int ENERGY_CAPACITY_RJ = 512_000;
    public static final int MAX_INPUT_RJ_PER_TICK = 512;
    public static final ElectricalTier REQUIRED_TIER = ElectricalTier.MV;
    public static final double RUNNING_CURRENT_AMPS = 1.0D;
    private static final int SMELTING_ENERGY_PER_TICK = 192;
    private static final int MAX_BATCH_SIZE = 4;
    private static final float LOOP_VOLUME = 2.9F;
    private static final float LOOP_PITCH = 1.0F;
    private static final double PARTICLE_LOCAL_X = 1.5D;
    private static final double PARTICLE_LOCAL_Y = 3.0D;
    private static final double PARTICLE_LOCAL_Z = 1.5D;
    private static final double PARTICLE_SPREAD_XZ = 0.22D;
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_STORED_RJ = "StoredRJ";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_MAX_PROGRESS = "MaxProgress";
    private static final String TAG_CURRENT_ENERGY_USAGE = "CurrentEnergyUsage";
    private static final String TAG_RUNNING = "Running";
    private static final String TAG_MODE = "Mode";
    private static final int DATA_ENERGY_LOW = 0;
    private static final int DATA_ENERGY_HIGH = 1;
    private static final int DATA_MAX_ENERGY_LOW = 2;
    private static final int DATA_MAX_ENERGY_HIGH = 3;
    private static final int DATA_PROGRESS = 4;
    private static final int DATA_MAX_PROGRESS = 5;
    private static final int DATA_CURRENT_ENERGY_USAGE = 6;
    private static final int DATA_RUNNING = 7;
    private static final int DATA_MODE = 8;
    private static final int DATA_COUNT = 9;
    private static final TagKey<Item> EBF_ELECTRODES = ItemTags.create(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "ebf_electrodes")
    );

    private final EbfItemStackHandler inventory = new EbfItemStackHandler();
    private final RJStorage rjStorage = new RJStorage(ENERGY_CAPACITY_RJ);
    private final RecipeManager.CachedCheck<SingleRecipeInput, BlastingRecipe> blastingRecipeCheck = RecipeManager.createCheck(RecipeType.BLASTING);
    private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> smeltingRecipeCheck = RecipeManager.createCheck(RecipeType.SMELTING);
    private final Map<MachineAutomationHandlerKey, IItemHandler> automationItemHandlers = new HashMap<>();
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
                case DATA_RUNNING -> running ? 1 : 0;
                case DATA_MODE -> mode.code();
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
                case DATA_RUNNING -> running = value != 0;
                case DATA_MODE -> mode = ElectricBlastFurnaceMode.byCode(value);
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
    private ElectricBlastFurnaceMode mode = ElectricBlastFurnaceMode.SMELTING;

    public ElectricBlastFurnaceBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ELECTRIC_BLAST_FURNACE.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ElectricBlastFurnaceBlockEntity furnace) {
        if (level.isClientSide) {
            return;
        }

        int previousUsage = furnace.currentEnergyUsage;
        int previousProgress = furnace.progress;
        int previousMaxProgress = furnace.maxProgress;
        boolean previousRunning = furnace.running;
        furnace.currentEnergyUsage = 0;
        furnace.running = false;
        boolean changed = furnace.tryAutoOutput();

        ActiveOperation operation = furnace.findCurrentOperation();
        if (operation == null || !furnace.hasUsableElectrodes()) {
            furnace.resetProgress();
        } else {
            furnace.maxProgress = operation.processTime();
            if (furnace.rjStorage.getStoredRJ() >= operation.energyPerTick()) {
                furnace.rjStorage.consumeRJ(operation.energyPerTick());
                furnace.currentEnergyUsage = operation.energyPerTick();
                furnace.running = true;
                furnace.progress++;
                if (furnace.progress >= operation.processTime()) {
                    ActiveOperation completionOperation = furnace.findCurrentOperation();
                    if (completionOperation != null && furnace.hasUsableElectrodes()) {
                        furnace.completeOperation(completionOperation);
                    }
                    furnace.resetProgress();
                }
            }
        }

        if (changed
                || previousUsage != furnace.currentEnergyUsage
                || previousProgress != furnace.progress
                || previousMaxProgress != furnace.maxProgress
                || previousRunning != furnace.running) {
            furnace.setChangedAndSync();
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ElectricBlastFurnaceBlockEntity furnace) {
        if (level.isClientSide) {
            furnace.tickClientLoop();
            furnace.spawnRunningParticles();
        }
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public ContainerData getData() {
        return data;
    }

    @Nullable
    public IItemHandler getAutomationItemHandler(BlockPos queriedPos, @Nullable Direction side) {
        if (portFor(queriedPos, side) == EbfPort.NONE) {
            return null;
        }

        MachineAutomationHandlerKey key = new MachineAutomationHandlerKey(queriedPos.immutable(), side);
        return automationItemHandlers.computeIfAbsent(key, ignored -> new AutomationItemHandler(queriedPos.immutable(), side));
    }

    public void toggleMode() {
        mode = mode.toggled();
        resetProgress();
        setChangedAndSync();
    }

    public ElectricBlastFurnaceMode getMode() {
        return mode;
    }

    public boolean isRunning() {
        return running;
    }

    public int getElectrodeRenderCount() {
        int count = 0;
        for (int slot = ELECTRODE_SLOT_0; slot < FIRST_INPUT_SLOT; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (isValidElectrode(stack) && stack.getDamageValue() < stack.getMaxDamage()) {
                count++;
            }
        }
        return count;
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

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.skyent.electric_blast_furnace");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ElectricBlastFurnaceMenu(containerId, playerInventory, this, data);
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        tag.putInt(TAG_STORED_RJ, rjStorage.getStoredRJ());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_MAX_PROGRESS, maxProgress);
        tag.putInt(TAG_CURRENT_ENERGY_USAGE, currentEnergyUsage);
        tag.putBoolean(TAG_RUNNING, running);
        tag.putString(TAG_MODE, mode.serializedName());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeAndMigrate(registries, tag.getCompound(TAG_INVENTORY));
        rjStorage.setStoredRJ(tag.getInt(TAG_STORED_RJ));
        progress = tag.getInt(TAG_PROGRESS);
        maxProgress = tag.getInt(TAG_MAX_PROGRESS);
        currentEnergyUsage = Math.max(0, tag.getInt(TAG_CURRENT_ENERGY_USAGE));
        running = tag.getBoolean(TAG_RUNNING);
        if (tag.contains(TAG_MODE)) {
            try {
                mode = ElectricBlastFurnaceMode.bySerializedName(tag.getString(TAG_MODE));
            } catch (IllegalArgumentException ignored) {
                mode = ElectricBlastFurnaceMode.SMELTING;
            }
        }
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
            stopClientLoop(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            stopClientLoop(level, worldPosition);
        }
        super.setRemoved();
    }

    @Nullable
    private ActiveOperation findCurrentOperation() {
        if (level == null) {
            return null;
        }
        return mode == ElectricBlastFurnaceMode.SMELTING ? findSmeltingOperation() : findAlloyingOperation();
    }

    private void resetProgress() {
        progress = 0;
        maxProgress = 0;
    }

    @Nullable
    private ActiveOperation findSmeltingOperation() {
        if (level == null) {
            return null;
        }

        for (RecipeHolder<ElectricBlastFurnaceRecipe> holder : ElectricBlastFurnaceRecipes.all(level, ElectricBlastFurnaceMode.SMELTING)) {
            ElectricBlastFurnaceRecipe recipe = holder.value();
            int batchSize = maxRecipeBatchSize(recipe);
            if (batchSize > 0) {
                return new ActiveOperation(ElectricBlastFurnaceMode.SMELTING, holder.id(), batchSize, recipe.processTime(), recipe.energyPerTick());
            }
        }

        for (int slot = FIRST_INPUT_SLOT; slot < OUTPUT_SLOT; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            SmeltingRecipeMatch recipe = getCookingRecipeFor(stack);
            if (recipe == null) {
                continue;
            }

            int matchingInputs = countMatchingInputs(recipe.ingredient());
            int batchSize = maxBatchSizeForOutput(recipe.result(), Math.min(MAX_BATCH_SIZE, matchingInputs));
            if (batchSize > 0) {
                return new ActiveOperation(
                        ElectricBlastFurnaceMode.SMELTING,
                        recipe.recipeId(),
                        batchSize,
                        recipe.processTime(),
                        recipe.energyPerTick()
                );
            }
        }
        return null;
    }

    @Nullable
    private ActiveOperation findAlloyingOperation() {
        if (level == null) {
            return null;
        }
        for (RecipeHolder<ElectricBlastFurnaceRecipe> holder : ElectricBlastFurnaceRecipes.all(level, ElectricBlastFurnaceMode.ALLOYING)) {
            ElectricBlastFurnaceRecipe recipe = holder.value();
            int batchSize = maxRecipeBatchSize(recipe);
            if (batchSize > 0) {
                return new ActiveOperation(ElectricBlastFurnaceMode.ALLOYING, holder.id(), batchSize, recipe.processTime(), recipe.energyPerTick());
            }
        }
        return null;
    }

    @Nullable
    private ResolvedRecipe resolveRecipe(ActiveOperation operation) {
        if (level == null) {
            return null;
        }
        RecipeHolder<?> holder = level.getRecipeManager().byKey(operation.recipeId()).orElse(null);
        if (holder != null && holder.value() instanceof ElectricBlastFurnaceRecipe recipe && recipe.mode() == operation.mode()) {
            return new ResolvedRecipe(recipe);
        }

        if (operation.mode() == ElectricBlastFurnaceMode.SMELTING) {
            SmeltingRecipeMatch recipe = getCookingRecipeById(operation.recipeId());
            return recipe == null ? null : new ResolvedRecipe(recipe);
        }

        return null;
    }

    private void completeOperation(ActiveOperation operation) {
        ResolvedRecipe resolved = resolveRecipe(operation);
        if (resolved == null) {
            return;
        }
        if (resolved.ebfRecipe() != null) {
            ElectricBlastFurnaceRecipe recipe = resolved.ebfRecipe();
            consumeInputs(recipe, operation.batchSize());
            mergeOutput(batchedResult(recipe.result(), operation.batchSize()));
            damageElectrodes(operation.batchSize());
        } else if (operation.mode() == ElectricBlastFurnaceMode.SMELTING) {
            consumeMatchingInputs(resolved.smeltingRecipe().ingredient(), operation.batchSize());
            mergeOutput(batchedResult(resolved.smeltingRecipe().result(), operation.batchSize()));
            damageElectrodes(operation.batchSize());
        } else {
            return;
        }
    }

    @Nullable
    private SmeltingRecipeMatch getCookingRecipeFor(ItemStack stack) {
        if (stack.isEmpty() || level == null) {
            return null;
        }

        SingleRecipeInput input = new SingleRecipeInput(stack);
        RecipeHolder<BlastingRecipe> blastingRecipe = blastingRecipeCheck.getRecipeFor(input, level).orElse(null);
        if (blastingRecipe != null) {
            return createSmeltingRecipeMatch(blastingRecipe, input);
        }

        RecipeHolder<SmeltingRecipe> smeltingRecipe = smeltingRecipeCheck.getRecipeFor(input, level).orElse(null);
        return smeltingRecipe == null ? null : createSmeltingRecipeMatch(smeltingRecipe, input);
    }

    @Nullable
    private SmeltingRecipeMatch getCookingRecipeById(ResourceLocation recipeId) {
        if (level == null) {
            return null;
        }
        RecipeHolder<?> holder = level.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof AbstractCookingRecipe recipe)) {
            return null;
        }
        if (recipe.getType() != RecipeType.BLASTING && recipe.getType() != RecipeType.SMELTING) {
            return null;
        }
        ItemStack[] inputs = recipe.getIngredients().isEmpty() ? new ItemStack[0] : recipe.getIngredients().get(0).getItems();
        ItemStack sample = inputs.length == 0 ? ItemStack.EMPTY : inputs[0];
        return createSmeltingRecipeMatch(new RecipeHolder<>(holder.id(), recipe), new SingleRecipeInput(sample));
    }

    @Nullable
    private SmeltingRecipeMatch createSmeltingRecipeMatch(RecipeHolder<? extends AbstractCookingRecipe> holder, SingleRecipeInput input) {
        if (level == null || holder.value().getIngredients().isEmpty()) {
            return null;
        }

        ItemStack result = holder.value().assemble(input, level.registryAccess());
        if (result.isEmpty()) {
            return null;
        }
        return new SmeltingRecipeMatch(
                holder.id(),
                holder.value().getIngredients().get(0),
                result,
                inheritedSmeltingProcessTime(holder.value()),
                SMELTING_ENERGY_PER_TICK
        );
    }

    private boolean matchesIngredients(ElectricBlastFurnaceRecipe recipe, int batchSize) {
        for (ElectricBlastFurnaceRecipe.CountedIngredient ingredient : recipe.countedIngredients()) {
            if (countMatchingInputs(ingredient.ingredient()) < ingredient.count() * batchSize) {
                return false;
            }
        }
        return !recipe.countedIngredients().isEmpty();
    }

    private int countMatchingInputs(Ingredient ingredient) {
        int count = 0;
        for (int slot = FIRST_INPUT_SLOT; slot < OUTPUT_SLOT; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (ingredient.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private boolean hasUsableElectrodes() {
        for (int slot = ELECTRODE_SLOT_0; slot < FIRST_INPUT_SLOT; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!isValidElectrode(stack) || stack.getDamageValue() >= stack.getMaxDamage()) {
                return false;
            }
        }
        return true;
    }

    private boolean canOutputStack(ItemStack result) {
        if (result.getCount() > result.getMaxStackSize()) {
            return false;
        }
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        return output.isEmpty()
                || ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void consumeInputs(ElectricBlastFurnaceRecipe recipe, int batchSize) {
        for (ElectricBlastFurnaceRecipe.CountedIngredient ingredient : recipe.countedIngredients()) {
            consumeMatchingInputs(ingredient.ingredient(), ingredient.count() * batchSize);
        }
    }

    private void consumeMatchingInputs(Ingredient ingredient, int count) {
        int remaining = count;
        for (int slot = FIRST_INPUT_SLOT; slot < OUTPUT_SLOT && remaining > 0; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!ingredient.test(stack)) {
                continue;
            }
            int consumed = Math.min(remaining, stack.getCount());
            stack.shrink(consumed);
            remaining -= consumed;
        }
    }

    private void mergeOutput(ItemStack result) {
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) {
            inventory.setStackInSlot(OUTPUT_SLOT, result.copy());
        } else {
            ItemStack merged = output.copy();
            merged.grow(result.getCount());
            inventory.setStackInSlot(OUTPUT_SLOT, merged);
        }
    }

    private void damageElectrodes(int amount) {
        for (int slot = ELECTRODE_SLOT_0; slot < FIRST_INPUT_SLOT; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem()) {
                continue;
            }
            int damage = stack.getDamageValue() + Math.max(1, amount);
            if (damage >= stack.getMaxDamage()) {
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            } else {
                stack.setDamageValue(damage);
            }
        }
    }

    private int maxBatchSizeForOutput(ItemStack singleResult, int availableInputs) {
        for (int batchSize = Math.min(MAX_BATCH_SIZE, availableInputs); batchSize >= 1; batchSize--) {
            if (canOutputStack(batchedResult(singleResult, batchSize))) {
                return batchSize;
            }
        }
        return 0;
    }

    private int maxRecipeBatchSize(ElectricBlastFurnaceRecipe recipe) {
        for (int batchSize = MAX_BATCH_SIZE; batchSize >= 1; batchSize--) {
            if (matchesIngredients(recipe, batchSize) && canOutputStack(batchedResult(recipe.result(), batchSize))) {
                return batchSize;
            }
        }
        return 0;
    }

    private static ItemStack batchedResult(ItemStack singleResult, int batchSize) {
        ItemStack result = singleResult.copy();
        result.setCount(singleResult.getCount() * batchSize);
        return result;
    }

    public static int inheritedSmeltingProcessTime(AbstractCookingRecipe recipe) {
        return Math.max(1, (recipe.getCookingTime() + 3) / 4);
    }

    private boolean tryAutoOutput() {
        if (level == null || level.isClientSide) {
            return false;
        }
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) {
            return false;
        }

        Direction outputSide = outputPortSide();
        BlockPos portPos = outputPortPos();
        BlockPos targetPos = portPos.relative(outputSide);
        if (isOwnMachineBlock(targetPos)) {
            return false;
        }

        ItemStack remainder = tryInsertItemOutput(targetPos, outputSide, output, true);
        if (remainder.getCount() >= output.getCount()) {
            return false;
        }

        ItemStack committedRemainder = tryInsertItemOutput(targetPos, outputSide, output, false);
        inventory.setStackInSlot(OUTPUT_SLOT, committedRemainder);
        return true;
    }

    private ItemStack tryInsertItemOutput(BlockPos targetPos, Direction outputDirection, ItemStack stack, boolean simulate) {
        return MachineConveyorOutput.tryInsert(level, targetPos, outputDirection, stack, simulate);
    }

    private boolean isOwnMachineBlock(BlockPos pos) {
        if (level == null) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.ELECTRIC_BLAST_FURNACE.get()) && !state.is(ModBlocks.ELECTRIC_BLAST_FURNACE_PART.get())) {
            return false;
        }
        return ElectricBlastFurnaceBlock.getMasterPos(state, pos).equals(worldPosition);
    }

    private Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(ElectricBlastFurnaceBlock.FACING) ? state.getValue(ElectricBlastFurnaceBlock.FACING) : Direction.NORTH;
    }

    private EbfPort portFor(BlockPos queriedPos, @Nullable Direction side) {
        if (isPort(queriedPos, side, 1, 0, 2, outputPortSide())) {
            return EbfPort.OUTPUT_ITEMS;
        }
        if (isPort(queriedPos, side, 1, 0, 0, inputPortSide())) {
            return EbfPort.INPUT_ITEMS;
        }
        return EbfPort.NONE;
    }

    private boolean isPort(BlockPos queriedPos, @Nullable Direction side, int localX, int localY, int localZ, Direction worldSide) {
        return side == worldSide && queriedPos.equals(ElectricBlastFurnaceBlock.localToWorld(worldPosition, getFacing(), localX, localY, localZ));
    }

    private BlockPos outputPortPos() {
        return ElectricBlastFurnaceBlock.localToWorld(worldPosition, getFacing(), 1, 0, 2);
    }

    private Direction inputPortSide() {
        return getFacing().getOpposite();
    }

    private Direction outputPortSide() {
        return getFacing();
    }

    private Vec3 getMachineCenter() {
        Direction facing = getFacing();
        Vec3 sum = Vec3.ZERO;
        int count = 0;
        for (int x = 0; x < ElectricBlastFurnaceBlock.SIZE_X; x++) {
            for (int y = 0; y < ElectricBlastFurnaceBlock.SIZE_Y; y++) {
                for (int z = 0; z < ElectricBlastFurnaceBlock.SIZE_Z; z++) {
                    sum = sum.add(Vec3.atCenterOf(ElectricBlastFurnaceBlock.localToWorld(worldPosition, facing, x, y, z)));
                    count++;
                }
            }
        }
        return sum.scale(1.0D / Math.max(1, count));
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void tickClientLoop() {
        if (level == null || !level.isClientSide) {
            return;
        }
        if (isRunning()) {
            startClientLoop(level, worldPosition, getMachineCenter());
        } else {
            stopClientLoop(level, worldPosition);
        }
    }

    private void spawnRunningParticles() {
        if (level == null || !level.isClientSide || !isRunning()) {
            return;
        }

        Vec3 opening = localPointToWorld(PARTICLE_LOCAL_X, PARTICLE_LOCAL_Y, PARTICLE_LOCAL_Z);
        double x = opening.x + (level.random.nextDouble() - 0.5D) * PARTICLE_SPREAD_XZ;
        double z = opening.z + (level.random.nextDouble() - 0.5D) * PARTICLE_SPREAD_XZ;
        double y = opening.y + 0.03D + level.random.nextDouble() * 0.08D;
        if (level.random.nextFloat() < 0.35F) {
            level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0D, 0.035D + level.random.nextDouble() * 0.025D, 0.0D);
        }
        if (level.random.nextFloat() < 0.08F) {
            level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0D, 0.015D, 0.0D);
        }
    }

    private Vec3 localPointToWorld(double localX, double localY, double localZ) {
        Direction facing = getFacing();
        Direction right = facing.getClockWise();
        double rightOffset = localX - (ElectricBlastFurnaceBlock.CONTROLLER_LOCAL_X + 0.5D);
        double yOffset = localY - (ElectricBlastFurnaceBlock.CONTROLLER_LOCAL_Y + 0.5D);
        double forwardOffset = localZ - (ElectricBlastFurnaceBlock.CONTROLLER_LOCAL_Z + 0.5D);
        return new Vec3(
                worldPosition.getX() + 0.5D + right.getStepX() * rightOffset + facing.getStepX() * forwardOffset,
                worldPosition.getY() + 0.5D + yOffset,
                worldPosition.getZ() + 0.5D + right.getStepZ() * rightOffset + facing.getStepZ() * forwardOffset
        );
    }

    private static void startClientLoop(Level level, BlockPos pos, Vec3 center) {
        invokeClientLoopMethod("startOrUpdateNamedLoop", level, pos, center);
    }

    private static void stopClientLoop(Level level, BlockPos pos) {
        invokeClientLoopMethod("stopNamedLoop", level, pos, Vec3.ZERO);
    }

    private static String loopKey(BlockPos pos) {
        return "electric_blast_furnace:" + pos.asLong();
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
                        loopKey(pos),
                        ModSounds.ELECTRIC_BLAST_FURNACE.get(),
                        SoundSource.BLOCKS,
                        (Supplier<Vec3>) () -> center,
                        LOOP_VOLUME,
                        LOOP_PITCH,
                        (BooleanSupplier) () -> level.getBlockEntity(pos) instanceof ElectricBlastFurnaceBlockEntity furnace && furnace.isRunning()
                );
            } else {
                Method method = managerClass.getMethod(methodName, clientLevelClass, String.class, net.minecraft.sounds.SoundEvent.class);
                method.invoke(null, level, loopKey(pos), ModSounds.ELECTRIC_BLAST_FURNACE.get());
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to update Electric Blast Furnace client loop sound", exception);
        }
    }

    public static boolean isValidElectrode(ItemStack stack) {
        return stack.is(EBF_ELECTRODES);
    }

    private static int low(int value) {
        return value & 0xFFFF;
    }

    private static int high(int value) {
        return value >>> 16;
    }

    private enum EbfPort {
        NONE,
        INPUT_ITEMS,
        OUTPUT_ITEMS
    }

    private record ActiveOperation(ElectricBlastFurnaceMode mode, ResourceLocation recipeId, int batchSize, int processTime, int energyPerTick) {
    }

    private record SmeltingRecipeMatch(ResourceLocation recipeId, Ingredient ingredient, ItemStack result, int processTime, int energyPerTick) {
        SmeltingRecipeMatch {
            result = result.copy();
            processTime = Math.max(1, processTime);
            energyPerTick = Math.max(0, energyPerTick);
        }
    }

    private record ResolvedRecipe(@Nullable SmeltingRecipeMatch smeltingRecipe, @Nullable ElectricBlastFurnaceRecipe ebfRecipe) {
        private ResolvedRecipe(SmeltingRecipeMatch smeltingRecipe) {
            this(smeltingRecipe, null);
        }

        private ResolvedRecipe(ElectricBlastFurnaceRecipe ebfRecipe) {
            this(null, ebfRecipe);
        }
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
            return portFor(queriedPos, side) == EbfPort.OUTPUT_ITEMS ? 1 : INPUT_SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            int mappedSlot = mapAutomationSlot(slot);
            return mappedSlot >= 0 ? inventory.getStackInSlot(mappedSlot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int mappedSlot = mapAutomationSlot(slot);
            if (portFor(queriedPos, side) != EbfPort.INPUT_ITEMS || mappedSlot < FIRST_INPUT_SLOT || mappedSlot >= OUTPUT_SLOT) {
                return stack;
            }
            return inventory.insertItem(mappedSlot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int mappedSlot = mapAutomationSlot(slot);
            return portFor(queriedPos, side) == EbfPort.OUTPUT_ITEMS && mappedSlot == OUTPUT_SLOT
                    ? inventory.extractItem(mappedSlot, amount, simulate)
                    : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            int mappedSlot = mapAutomationSlot(slot);
            return mappedSlot >= 0 ? inventory.getSlotLimit(mappedSlot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            int mappedSlot = mapAutomationSlot(slot);
            return portFor(queriedPos, side) == EbfPort.INPUT_ITEMS && mappedSlot >= 0 && inventory.isItemValid(mappedSlot, stack);
        }

        private int mapAutomationSlot(int slot) {
            EbfPort port = portFor(queriedPos, side);
            if (port == EbfPort.INPUT_ITEMS) {
                return slot >= 0 && slot < INPUT_SLOT_COUNT ? FIRST_INPUT_SLOT + slot : -1;
            }
            if (port == EbfPort.OUTPUT_ITEMS) {
                return slot == 0 ? OUTPUT_SLOT : -1;
            }
            return -1;
        }
    }

    private final class EbfItemStackHandler extends ItemStackHandler {
        private EbfItemStackHandler() {
            super(INVENTORY_SLOT_COUNT);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot >= ELECTRODE_SLOT_0 && slot < FIRST_INPUT_SLOT) {
                return isValidElectrode(stack);
            }
            return slot >= FIRST_INPUT_SLOT && slot < OUTPUT_SLOT;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChangedAndSync();
        }

        private void deserializeAndMigrate(HolderLookup.Provider registries, CompoundTag tag) {
            deserializeNBT(registries, tag);
            if (getSlots() != INVENTORY_SLOT_COUNT) {
                setSize(INVENTORY_SLOT_COUNT);
            }
        }
    }
}
