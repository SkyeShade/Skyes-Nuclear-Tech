package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.block.CoalForgeBedType;
import com.skyeshade.skyent.content.block.CoalForgeBlock;
import com.skyeshade.skyent.content.item.HotMetalItems;
import com.skyeshade.skyent.content.item.HotItemUtil;
import com.skyeshade.skyent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class CoalForgeBlockEntity extends BlockEntity {
    public static final int INGOT_SLOT_COUNT = 4;
    public static final int COAL_LAYER_BURN_TICKS = 2400;
    public static final int CHARCOAL_LAYER_BURN_TICKS = 2000;
    public static final double MAX_TEMPERATURE_C = 1200.0D;
    private static final double INGOT_HEAT_PER_TICK_C = 1.2D;

    private int burnTicksRemaining;
    private int originalFuelLayers;

    private final ItemStackHandler ingots = new ItemStackHandler(INGOT_SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return HotItemUtil.isForgeableIngot(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChangedAndSync();
        }
    };

    public CoalForgeBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.COAL_FORGE.get(), pos, blockState);
        originalFuelLayers = blockState.hasProperty(CoalForgeBlock.LAYERS) ? blockState.getValue(CoalForgeBlock.LAYERS) : 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CoalForgeBlockEntity forge) {
        boolean changed = false;
        if (state.getValue(CoalForgeBlock.BED_TYPE) == CoalForgeBedType.COAL
                && state.getValue(CoalForgeBlock.LIT)
                && state.getValue(CoalForgeBlock.LAYERS) > 0) {
            if (forge.burnTicksRemaining > 0) {
                forge.burnTicksRemaining--;
                forge.heatIngots();
                changed = true;
            }

            if (forge.burnTicksRemaining <= 0) {
                int ashLayers = Math.max(1, forge.originalFuelLayers);
                level.setBlock(pos, CoalForgeBlock.withBedState(state, CoalForgeBedType.ASH, ashLayers, false), Block.UPDATE_CLIENTS);
                forge.burnTicksRemaining = 0;
                changed = true;
            }
        }

        if (changed) {
            setChanged(level, pos, state);
            forge.sync();
        }
    }

    public boolean addFuelLayer(ItemStack fuel) {
        if (level == null || !CoalForgeBlock.isFuelBedAccepting(getBlockState())) {
            return false;
        }

        int burnTicks = getFuelBurnTicks(fuel);
        if (burnTicks <= 0) {
            return false;
        }

        BlockState state = getBlockState();
        int layers = state.getValue(CoalForgeBlock.LAYERS);
        if (layers >= CoalForgeBlock.MAX_LAYERS) {
            return false;
        }

        int newLayers = layers + 1;
        originalFuelLayers = newLayers;
        burnTicksRemaining += burnTicks;
        level.setBlock(worldPosition, CoalForgeBlock.withBedState(state, CoalForgeBedType.COAL, newLayers, state.getValue(CoalForgeBlock.LIT)), Block.UPDATE_CLIENTS);
        setChangedAndSync();
        return true;
    }

    public boolean light() {
        if (level == null) {
            return false;
        }

        BlockState state = getBlockState();
        if (state.getValue(CoalForgeBlock.BED_TYPE) != CoalForgeBedType.COAL
                || state.getValue(CoalForgeBlock.LAYERS) <= 0
                || state.getValue(CoalForgeBlock.LIT)
                || burnTicksRemaining <= 0) {
            return false;
        }

        level.setBlock(worldPosition, CoalForgeBlock.withBedState(state, CoalForgeBedType.COAL, state.getValue(CoalForgeBlock.LAYERS), true), Block.UPDATE_CLIENTS);
        setChangedAndSync();
        return true;
    }

    public boolean removeCoalLayer() {
        if (level == null) {
            return false;
        }

        BlockState state = getBlockState();
        if (state.getValue(CoalForgeBlock.BED_TYPE) != CoalForgeBedType.COAL || state.getValue(CoalForgeBlock.LAYERS) <= 0) {
            return false;
        }

        int layers = state.getValue(CoalForgeBlock.LAYERS);
        int newLayers = layers - 1;
        int layerBurn = originalFuelLayers > 0 ? Math.max(1, burnTicksRemaining / originalFuelLayers) : COAL_LAYER_BURN_TICKS;
        burnTicksRemaining = Math.max(0, burnTicksRemaining - layerBurn);
        originalFuelLayers = newLayers;
        boolean remainsLit = state.getValue(CoalForgeBlock.LIT) && newLayers > 0 && burnTicksRemaining > 0;
        level.setBlock(worldPosition, CoalForgeBlock.withBedState(state, newLayers == 0 ? CoalForgeBedType.EMPTY : CoalForgeBedType.COAL, newLayers, remainsLit), Block.UPDATE_CLIENTS);
        Containers.dropItemStack(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.75D, worldPosition.getZ() + 0.5D, new ItemStack(Items.COAL));
        setChangedAndSync();
        return true;
    }

    public boolean removeAshLayer() {
        if (level == null) {
            return false;
        }

        BlockState state = getBlockState();
        if (state.getValue(CoalForgeBlock.BED_TYPE) != CoalForgeBedType.ASH || state.getValue(CoalForgeBlock.LAYERS) <= 0) {
            return false;
        }

        int newLayers = state.getValue(CoalForgeBlock.LAYERS) - 1;
        // TODO: Add an ash item later for fertilizer, chemistry, cement, or lye chains.
        level.setBlock(worldPosition, CoalForgeBlock.withBedState(state, newLayers == 0 ? CoalForgeBedType.EMPTY : CoalForgeBedType.ASH, newLayers, false), Block.UPDATE_CLIENTS);
        setChangedAndSync();
        return true;
    }

    public boolean insertIngot(ItemStack heldStack) {
        if (!HotItemUtil.isForgeableIngot(heldStack)) {
            return false;
        }

        for (int slot = 0; slot < ingots.getSlots(); slot++) {
            if (ingots.getStackInSlot(slot).isEmpty()) {
                ItemStack inserted = heldStack.copyWithCount(1);
                ingots.setStackInSlot(slot, inserted);
                return true;
            }
        }
        return false;
    }

    public ItemStack removeLastIngot() {
        for (int slot = ingots.getSlots() - 1; slot >= 0; slot--) {
            ItemStack stack = ingots.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                ItemStack removed = stack.copy();
                ingots.setStackInSlot(slot, ItemStack.EMPTY);
                return removed;
            }
        }
        return ItemStack.EMPTY;
    }

    public int findExtractionSlot() {
        int forgeableSlot = findForgeableSlot();
        if (forgeableSlot >= 0) {
            return forgeableSlot;
        }

        for (int slot = ingots.getSlots() - 1; slot >= 0; slot--) {
            if (!ingots.getStackInSlot(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    public ItemStack getIngotStack(int slot) {
        return slot >= 0 && slot < ingots.getSlots() ? ingots.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    public ItemStack extractIngot(int slot, int amount) {
        if (slot < 0 || slot >= ingots.getSlots() || amount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack extracted = ingots.extractItem(slot, amount, false);
        if (!extracted.isEmpty()) {
            setChangedAndSync();
        }
        return extracted;
    }

    public boolean insertSingleIngotStack(ItemStack stack) {
        if (stack.isEmpty() || !HotItemUtil.isForgeableIngot(stack)) {
            return false;
        }

        for (int slot = 0; slot < ingots.getSlots(); slot++) {
            if (ingots.getStackInSlot(slot).isEmpty()) {
                ingots.setStackInSlot(slot, stack.copyWithCount(1));
                return true;
            }
        }
        return false;
    }

    public int findForgeableSlot() {
        for (int slot = 0; slot < ingots.getSlots(); slot++) {
            ItemStack stack = ingots.getStackInSlot(slot);
            if (HotItemUtil.isForgeReady(stack)) {
                return slot;
            }
        }
        return -1;
    }

    public boolean hasForgeableOutput() {
        return findForgeableSlot() >= 0;
    }

    public ItemStack removeForgeableOutput() {
        int slot = findForgeableSlot();
        if (slot < 0) {
            return ItemStack.EMPTY;
        }

        ItemStack removed = ingots.extractItem(slot, 1, false);
        setChangedAndSync();
        return removed;
    }

    public ItemStackHandler getIngots() {
        return ingots;
    }

    public int getBurnTicksRemaining() {
        return burnTicksRemaining;
    }

    public int getOriginalFuelLayers() {
        return originalFuelLayers;
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < ingots.getSlots(); slot++) {
            ItemStack stack = ingots.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }

        BlockState state = getBlockState();
        if (state.getValue(CoalForgeBlock.BED_TYPE) == CoalForgeBedType.COAL) {
            for (int layer = 0; layer < state.getValue(CoalForgeBlock.LAYERS); layer++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.COAL));
            }
        }
    }

    private void heatIngots() {
        for (int slot = 0; slot < ingots.getSlots(); slot++) {
            ItemStack stack = ingots.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            double temperature = HotItemUtil.getTemperature(stack);
            double targetTemperature = Math.min(MAX_TEMPERATURE_C, HotItemUtil.getForgingTemperature(stack));
            if (Double.isFinite(targetTemperature) && temperature < targetTemperature) {
                HotItemUtil.setTemperature(stack, Math.min(targetTemperature, temperature + INGOT_HEAT_PER_TICK_C));
                ingots.setStackInSlot(slot, HotMetalItems.toHotVariantIfForgeReady(stack));
            }
        }
    }

    private static int getFuelBurnTicks(ItemStack stack) {
        if (stack.is(Items.COAL)) {
            return COAL_LAYER_BURN_TICKS;
        }
        if (stack.is(Items.CHARCOAL)) {
            return CHARCOAL_LAYER_BURN_TICKS;
        }
        // TODO: Accept coal coke here once that material exists.
        return 0;
    }

    private void setChangedAndSync() {
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("BurnTicksRemaining", burnTicksRemaining);
        tag.putInt("OriginalFuelLayers", originalFuelLayers);
        tag.put("Ingots", ingots.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        burnTicksRemaining = Math.max(0, tag.getInt("BurnTicksRemaining"));
        originalFuelLayers = Mth.clamp(tag.getInt("OriginalFuelLayers"), 0, CoalForgeBlock.MAX_LAYERS);
        ingots.deserializeNBT(registries, tag.getCompound("Ingots"));
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
}
