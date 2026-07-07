package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.block.ConveyorExporterBlock;
import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorInsertionUtil;
import com.skyeshade.skyent.content.menu.ConveyorExporterMenu;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class ConveyorExporterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int FILTER_SLOTS = 15;
    private static final int BASIC_EXPORT_INTERVAL_TICKS = 5;
    private static final int BASIC_EXPORT_AMOUNT = 1;
    private static final int DATA_WHITELIST = 0;
    private static final int DATA_COUNT = 1;
    private static final String TAG_FILTER = "Filter";
    private static final String TAG_WHITELIST = "Whitelist";

    private final ItemStackHandler filter = new ItemStackHandler(FILTER_SLOTS) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private boolean whitelist = true;
    private int exportCooldown;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == DATA_WHITELIST && whitelist ? 1 : 0;
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_WHITELIST) {
                whitelist = value != 0;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ConveyorExporterBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.CONVEYOR_EXPORTER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ConveyorExporterBlockEntity exporter) {
        if (exporter.exportCooldown > 0) {
            exporter.exportCooldown--;
            return;
        }

        exporter.exportCooldown = BASIC_EXPORT_INTERVAL_TICKS - 1;
        if (exporter.tryExport()) {
            setChanged(level, pos, state);
        }
    }

    private boolean tryExport() {
        if (level == null || level.isClientSide || !getBlockState().hasProperty(ConveyorExporterBlock.FACING)) {
            return false;
        }

        Direction facing = getBlockState().getValue(ConveyorExporterBlock.FACING);
        BlockPos frontPos = worldPosition.relative(facing);
        BlockState frontState = level.getBlockState(frontPos);
        if (!(frontState.getBlock() instanceof ConveyorBeltSurface)) {
            return false;
        }

        IItemHandler conveyor = level.getCapability(Capabilities.ItemHandler.BLOCK, frontPos, facing.getOpposite());
        if (conveyor == null) {
            return false;
        }

        IItemHandler source = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(facing.getOpposite()), facing);
        if (source == null) {
            return false;
        }

        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack candidate = source.extractItem(slot, BASIC_EXPORT_AMOUNT, true);
            if (candidate.isEmpty() || !matchesFilter(candidate)) {
                continue;
            }

            ItemStack simulatedRemainder = ConveyorInsertionUtil.insertIntoHandler(conveyor, candidate, true);
            if (!simulatedRemainder.isEmpty()) {
                continue;
            }

            ItemStack extracted = source.extractItem(slot, BASIC_EXPORT_AMOUNT, false);
            if (extracted.isEmpty()) {
                continue;
            }

            ItemStack remainder = ConveyorInsertionUtil.insertIntoHandler(conveyor, extracted, false);
            if (!remainder.isEmpty()) {
                source.insertItem(slot, remainder, false);
            }
            return remainder.getCount() != extracted.getCount();
        }

        return false;
    }

    public boolean matchesFilter(ItemStack stack) {
        boolean hasAnyFilter = false;
        boolean matchesAnyFilter = false;
        for (int slot = 0; slot < filter.getSlots(); slot++) {
            ItemStack filterStack = filter.getStackInSlot(slot);
            if (filterStack.isEmpty()) {
                continue;
            }

            hasAnyFilter = true;
            if (ItemStack.isSameItemSameComponents(filterStack, stack)) {
                matchesAnyFilter = true;
                break;
            }
        }

        if (!hasAnyFilter) {
            return true;
        }

        return whitelist ? matchesAnyFilter : !matchesAnyFilter;
    }

    public ItemStackHandler getFilter() {
        return filter;
    }

    public boolean isWhitelist() {
        return whitelist;
    }

    public void setFilterSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= FILTER_SLOTS) {
            return;
        }

        ItemStack ghost = stack.copy();
        if (!ghost.isEmpty()) {
            ghost.setCount(1);
        }
        filter.setStackInSlot(slot, ghost);
    }

    public void toggleFilterMode() {
        whitelist = !whitelist;
        setChanged();
    }

    public ContainerData getData() {
        return data;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.skyent.conveyor_exporter");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ConveyorExporterMenu(containerId, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_FILTER, filter.serializeNBT(registries));
        tag.putBoolean(TAG_WHITELIST, whitelist);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        filter.deserializeNBT(registries, tag.getCompound(TAG_FILTER));
        whitelist = !tag.contains(TAG_WHITELIST) || tag.getBoolean(TAG_WHITELIST);
    }
}
