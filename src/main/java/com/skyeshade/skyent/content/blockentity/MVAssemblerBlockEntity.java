package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.client.model.SkyentModelData;
import com.skyeshade.skyent.client.render.HeatingChamberLighting;
import com.skyeshade.skyent.client.render.MVAssemblerLightRefreshTracker;
import com.skyeshade.skyent.content.block.MVAssemblerBlock;
import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorDirectTransfer;
import com.skyeshade.skyent.content.conveyor.ConveyorGateSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorInsertionUtil;
import com.skyeshade.skyent.content.conveyor.ConveyorLogicConstants;
import com.skyeshade.skyent.content.conveyor.MachineConveyorOutput;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.content.energy.RJEnergyInfo;
import com.skyeshade.skyent.content.energy.RJStorage;
import com.skyeshade.skyent.content.menu.MVAssemblerMenu;
import com.skyeshade.skyent.content.recipe.MVAssemblerRecipe;
import com.skyeshade.skyent.content.recipe.MVAssemblerRecipes;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModSounds;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class MVAssemblerBlockEntity extends BlockEntity implements MenuProvider, RJEnergyInfo {
    public static final int INPUT_SLOT_COUNT = 12;
    public static final int OUTPUT_SLOT = 12;
    public static final int INVENTORY_SLOT_COUNT = 13;
    public static final int ENERGY_CAPACITY_RJ = 512_000;
    public static final int MAX_INPUT_RJ_PER_TICK = 512;
    public static final ElectricalTier REQUIRED_TIER = ElectricalTier.MV;
    public static final double RUNNING_CURRENT_AMPS = 1.0D;
    private static final int OUTPUT_LOCAL_X = 1;
    private static final int OUTPUT_LOCAL_Y = 0;
    private static final int OUTPUT_LOCAL_Z = MVAssemblerBlock.SIZE_Z - 1;
    private static final float MOTOR_LOOP_VOLUME = 0.8F;
    private static final float MOTOR_LOOP_PITCH = 1.75F;
    private static final int LIGHT_CHECK_INTERVAL_TICKS = 40;
    private static final String TAG_STORED_RJ = "StoredRJ";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_SELECTED_RECIPE = "SelectedRecipe";
    private static final String TAG_CURRENT_ENERGY_USAGE = "CurrentEnergyUsage";
    private static final String TAG_STATUS = "Status";
    private static final int DATA_ENERGY_LOW = 0;
    private static final int DATA_ENERGY_HIGH = 1;
    private static final int DATA_MAX_ENERGY_LOW = 2;
    private static final int DATA_MAX_ENERGY_HIGH = 3;
    private static final int DATA_PROGRESS = 4;
    private static final int DATA_MAX_PROGRESS = 5;
    private static final int DATA_CURRENT_ENERGY_USAGE = 6;
    private static final int DATA_SELECTED_RECIPE_INDEX = 7;
    private static final int DATA_STATUS = 8;
    private static final int DATA_COUNT = 9;

    private final AssemblerItemStackHandler inventory = new AssemblerItemStackHandler();
    private final RJStorage rjStorage = new RJStorage(ENERGY_CAPACITY_RJ);
    private final Map<MachineAutomationHandlerKey, IItemHandler> automationItemHandlers = new HashMap<>();
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> rjStorage.getStoredRJ() & 0xFFFF;
                case DATA_ENERGY_HIGH -> rjStorage.getStoredRJ() >>> 16;
                case DATA_MAX_ENERGY_LOW -> rjStorage.getCapacityRJ() & 0xFFFF;
                case DATA_MAX_ENERGY_HIGH -> rjStorage.getCapacityRJ() >>> 16;
                case DATA_PROGRESS -> progress;
                case DATA_MAX_PROGRESS -> getMaxProgress();
                case DATA_CURRENT_ENERGY_USAGE -> currentEnergyUsage;
                case DATA_SELECTED_RECIPE_INDEX -> getSelectedRecipeIndex();
                case DATA_STATUS -> status.code;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_ENERGY_LOW -> rjStorage.setStoredRJ((rjStorage.getStoredRJ() & 0xFFFF0000) | (value & 0xFFFF));
                case DATA_ENERGY_HIGH -> rjStorage.setStoredRJ((rjStorage.getStoredRJ() & 0xFFFF) | ((value & 0xFFFF) << 16));
                case DATA_PROGRESS -> progress = value;
                case DATA_CURRENT_ENERGY_USAGE -> currentEnergyUsage = value;
                case DATA_STATUS -> status = Status.byCode(value);
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    @Nullable
    private ResourceLocation selectedRecipeId;
    private int progress;
    private int currentEnergyUsage;
    private Status status = Status.IDLE;
    private int cachedSharedPackedLight = -1;
    private int lightCheckTicks;

    public MVAssemblerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.MV_ASSEMBLER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MVAssemblerBlockEntity assembler) {
        if (level.isClientSide) {
            return;
        }

        int previousUsage = assembler.currentEnergyUsage;
        Status previousStatus = assembler.status;
        assembler.currentEnergyUsage = 0;
        boolean changed = assembler.tryAutoOutput();

        RecipeHolder<MVAssemblerRecipe> recipeHolder = assembler.getSelectedRecipe();
        if (recipeHolder == null) {
            assembler.status = assembler.selectedRecipeId == null ? Status.IDLE : Status.MISSING_RECIPE;
            assembler.resetProgressIfNeeded();
        } else {
            MVAssemblerRecipe recipe = recipeHolder.value();
            if (!assembler.hasIngredients(recipe)) {
                assembler.status = Status.MISSING_INGREDIENTS;
                assembler.resetProgressIfNeeded();
            } else if (!assembler.canOutput(recipe)) {
                assembler.status = Status.OUTPUT_BLOCKED;
            } else if (assembler.rjStorage.getStoredRJ() < recipe.energyPerTick()) {
                assembler.status = Status.MISSING_POWER;
            } else {
                assembler.rjStorage.consumeRJ(recipe.energyPerTick());
                assembler.currentEnergyUsage = recipe.energyPerTick();
                assembler.progress++;
                assembler.status = Status.ASSEMBLING;
                if (assembler.progress >= recipe.processTime()) {
                    assembler.consumeIngredients(recipe);
                    assembler.mergeOutput(recipe.result());
                    assembler.progress = 0;
                }
            }
        }

        if (previousUsage != assembler.currentEnergyUsage || previousStatus != assembler.status || changed) {
            assembler.setChangedAndSync();
        } else if (assembler.currentEnergyUsage > 0) {
            setChanged(level, pos, state);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, MVAssemblerBlockEntity assembler) {
        if (!level.isClientSide) {
            return;
        }

        assembler.lightCheckTicks++;
        if (assembler.lightCheckTicks >= LIGHT_CHECK_INTERVAL_TICKS) {
            assembler.lightCheckTicks = 0;
            assembler.refreshSharedLight(false);
        }
        assembler.tickClientMotorLoop();
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        return getAutomationItemHandler(worldPosition, side);
    }

    public IItemHandler getAutomationItemHandler(BlockPos queriedPos, @Nullable Direction side) {
        MachineAutomationHandlerKey key = new MachineAutomationHandlerKey(queriedPos.immutable(), side);
        return automationItemHandlers.computeIfAbsent(key, ignored -> new AutomationItemHandler(queriedPos.immutable(), side));
    }

    public ContainerData getData() {
        return data;
    }

    public void selectRecipe(ResourceLocation recipeId) {
        if (level == null || MVAssemblerRecipes.byId(level, recipeId).isEmpty()) {
            return;
        }

        if (!recipeId.equals(selectedRecipeId)) {
            selectedRecipeId = recipeId;
            progress = 0;
            setChangedAndSync();
        }
    }

    @Nullable
    public RecipeHolder<MVAssemblerRecipe> getSelectedRecipe() {
        if (level == null || selectedRecipeId == null) {
            return null;
        }
        return MVAssemblerRecipes.byId(level, selectedRecipeId).orElse(null);
    }

    public int getSelectedRecipeIndex() {
        if (level == null || selectedRecipeId == null) {
            return -1;
        }
        return MVAssemblerRecipes.indexOf(level, selectedRecipeId);
    }

    public int countMatchingInput(MVAssemblerRecipe.CountedIngredient countedIngredient) {
        int count = 0;
        for (int slot = 0; slot < INPUT_SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (countedIngredient.ingredient().test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public boolean hasIngredients(MVAssemblerRecipe recipe) {
        for (MVAssemblerRecipe.CountedIngredient ingredient : recipe.countedIngredients()) {
            if (countMatchingInput(ingredient) < ingredient.count()) {
                return false;
            }
        }
        return true;
    }

    public String getStatusText() {
        return status.label;
    }

    public boolean isRunning() {
        return status == Status.ASSEMBLING;
    }

    public int getProgress() {
        return progress;
    }

    public int getMaxProgress() {
        RecipeHolder<MVAssemblerRecipe> recipe = getSelectedRecipe();
        return recipe == null ? 0 : recipe.value().processTime();
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
        return Component.translatable("container.skyent.mv_assembler");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MVAssemblerMenu(containerId, playerInventory, this, data);
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
        if (level != null && level.isClientSide) {
            MVAssemblerLightRefreshTracker.register(worldPosition);
        }
        refreshSharedLight(true);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide) {
            MVAssemblerLightRefreshTracker.unregister(worldPosition);
            stopClientMotorLoop(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            MVAssemblerLightRefreshTracker.unregister(worldPosition);
            stopClientMotorLoop(level, worldPosition);
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_STORED_RJ, rjStorage.getStoredRJ());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_CURRENT_ENERGY_USAGE, currentEnergyUsage);
        tag.putInt(TAG_STATUS, status.code);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        if (selectedRecipeId != null) {
            tag.putString(TAG_SELECTED_RECIPE, selectedRecipeId.toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rjStorage.setStoredRJ(tag.getInt(TAG_STORED_RJ));
        progress = tag.getInt(TAG_PROGRESS);
        currentEnergyUsage = Math.max(0, tag.getInt(TAG_CURRENT_ENERGY_USAGE));
        status = Status.byCode(tag.getInt(TAG_STATUS));
        inventory.deserializeAndMigrate(registries, tag.getCompound(TAG_INVENTORY));
        selectedRecipeId = tag.contains(TAG_SELECTED_RECIPE) ? ResourceLocation.tryParse(tag.getString(TAG_SELECTED_RECIPE)) : null;
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

    private boolean canOutput(MVAssemblerRecipe recipe) {
        ItemStack result = recipe.result();
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        return output.isEmpty()
                || ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void consumeIngredients(MVAssemblerRecipe recipe) {
        for (MVAssemblerRecipe.CountedIngredient ingredient : recipe.countedIngredients()) {
            int remaining = ingredient.count();
            for (int slot = 0; slot < INPUT_SLOT_COUNT && remaining > 0; slot++) {
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

    private boolean tryAutoOutput() {
        if (level == null || level.isClientSide) {
            return false;
        }

        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) {
            return false;
        }

        Direction facing = getFacing();
        BlockPos portPos = getOutputFaceBlockPos(facing);
        BlockPos targetPos = portPos.relative(facing);
        if (isOwnAssemblerBlock(targetPos)) {
            return false;
        }
        ItemStack remainder = tryInsertOutput(targetPos, facing, output, true);
        if (remainder.getCount() >= output.getCount()) {
            return false;
        }

        ItemStack committedRemainder = tryInsertOutput(targetPos, facing, output, false);
        inventory.setStackInSlot(OUTPUT_SLOT, committedRemainder);
        return true;
    }

    private ItemStack tryInsertOutput(BlockPos targetPos, Direction outputDirection, ItemStack stack, boolean simulate) {
        return MachineConveyorOutput.tryInsert(level, targetPos, outputDirection, stack, simulate);
    }

    private void resetProgressIfNeeded() {
        if (progress != 0) {
            progress = 0;
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
                MVAssemblerBlock.SIZE_X,
                MVAssemblerBlock.SIZE_Y,
                MVAssemblerBlock.SIZE_Z,
                MVAssemblerBlock::localToWorld
        );
    }

    private Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(MVAssemblerBlock.FACING) ? state.getValue(MVAssemblerBlock.FACING) : Direction.NORTH;
    }

    private BlockPos getOutputFaceBlockPos(Direction facing) {
        return MVAssemblerBlock.localToWorld(
                worldPosition,
                facing,
                OUTPUT_LOCAL_X,
                OUTPUT_LOCAL_Y,
                OUTPUT_LOCAL_Z
        );
    }

    private boolean isOutputAccess(BlockPos queriedPos, @Nullable Direction side) {
        Direction facing = getFacing();
        return side == facing && queriedPos.equals(getOutputFaceBlockPos(facing));
    }

    private boolean isOwnAssemblerBlock(BlockPos pos) {
        if (level == null) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.MV_ASSEMBLER.get()) && !state.is(ModBlocks.MV_ASSEMBLER_PART.get())) {
            return false;
        }
        return MVAssemblerBlock.getMasterPos(state, pos).equals(worldPosition);
    }

    private boolean hasRoomAt(Vec3 position) {
        if (level == null) {
            return false;
        }
        AABB searchBox = new AABB(position, position).inflate(ConveyorMovingItemEntity.ITEM_SPACING_DISTANCE);
        return level.getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, entity -> !entity.isRemoved()).isEmpty();
    }

    private Vec3 getMachineCenter() {
        Direction facing = getFacing();
        Vec3 sum = Vec3.ZERO;
        int count = 0;
        for (int x = 0; x < MVAssemblerBlock.SIZE_X; x++) {
            for (int y = 0; y < MVAssemblerBlock.SIZE_Y; y++) {
                for (int z = 0; z < MVAssemblerBlock.SIZE_Z; z++) {
                    sum = sum.add(Vec3.atCenterOf(MVAssemblerBlock.localToWorld(worldPosition, facing, x, y, z)));
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
        if (isRunning()) {
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
        return "mv_assembler_motor:" + pos.asLong();
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
                        (BooleanSupplier) () -> level.getBlockEntity(pos) instanceof MVAssemblerBlockEntity assembler && assembler.isRunning()
                );
            } else {
                Method method = managerClass.getMethod(methodName, clientLevelClass, String.class, net.minecraft.sounds.SoundEvent.class);
                method.invoke(null, level, motorLoopKey(pos), ModSounds.HEAVY_ELECTRIC_MOTOR_LOOP.get());
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to update MV Assembler client loop sound", exception);
        }
    }

    public enum Status {
        IDLE(0, "Idle"),
        MISSING_RECIPE(1, "Missing recipe"),
        MISSING_INGREDIENTS(2, "Missing ingredients"),
        MISSING_POWER(3, "Missing power"),
        ASSEMBLING(4, "Assembling"),
        OUTPUT_BLOCKED(5, "Output blocked");

        private static final Map<Integer, Status> BY_CODE = new HashMap<>();

        static {
            for (Status status : values()) {
                BY_CODE.put(status.code, status);
            }
        }

        private final int code;
        private final String label;

        Status(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public static Status byCode(int code) {
            return BY_CODE.getOrDefault(code, IDLE);
        }

        public String label() {
            return label;
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
            return INVENTORY_SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= INVENTORY_SLOT_COUNT) {
                return ItemStack.EMPTY;
            }
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= INPUT_SLOT_COUNT) {
                return stack;
            }
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != OUTPUT_SLOT || !isOutputAccess(queriedPos, side)) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(OUTPUT_SLOT, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= 0 && slot < INVENTORY_SLOT_COUNT ? inventory.getSlotLimit(slot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < INPUT_SLOT_COUNT && inventory.isItemValid(slot, stack);
        }
    }

    private final class AssemblerItemStackHandler extends ItemStackHandler {
        private AssemblerItemStackHandler() {
            super(INVENTORY_SLOT_COUNT);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < INPUT_SLOT_COUNT;
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (slot < INPUT_SLOT_COUNT) {
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
}
