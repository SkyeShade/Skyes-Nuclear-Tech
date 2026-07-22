package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.block.BrickBlastFurnaceBlock;
import com.skyeshade.skyent.content.menu.BrickBlastFurnaceMenu;
import com.skyeshade.skyent.content.recipe.BrickBlastFurnaceRecipe;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class BrickBlastFurnaceBlockEntity extends BlockEntity implements MenuProvider {
    public static final int FUEL_SLOT = 0;
    public static final int TOP_INPUT_SLOT = 1;
    public static final int BOTTOM_INPUT_SLOT = 2;
    public static final int OUTPUT_SLOT = 3;
    public static final int MAX_FUEL_HEAT = 32_000;
    public static final int HEAT_USAGE_PER_TICK = 1;
    public static final int STEEL_RECIPE_TIME = 600;
    private static final int INVENTORY_SLOT_COUNT = 4;

    private static final int DATA_FUEL_HEAT = 0;
    private static final int DATA_MAX_FUEL_HEAT = 1;
    private static final int DATA_PROGRESS = 2;
    private static final int DATA_MAX_PROGRESS = 3;
    private static final int DATA_ACTIVE = 4;
    private static final int DATA_COUNT = 5;

    private int fuelHeat;
    private int progress;
    private int maxProgress = STEEL_RECIPE_TIME;
    private boolean active;

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case FUEL_SLOT -> isFuel(stack);
                case TOP_INPUT_SLOT, BOTTOM_INPUT_SLOT -> !stack.isEmpty();
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_FUEL_HEAT -> fuelHeat;
                case DATA_MAX_FUEL_HEAT -> MAX_FUEL_HEAT;
                case DATA_PROGRESS -> progress;
                case DATA_MAX_PROGRESS -> maxProgress;
                case DATA_ACTIVE -> active ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_FUEL_HEAT -> fuelHeat = Mth.clamp(value, 0, MAX_FUEL_HEAT);
                case DATA_PROGRESS -> progress = value;
                case DATA_MAX_PROGRESS -> maxProgress = value;
                case DATA_ACTIVE -> active = value != 0;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public BrickBlastFurnaceBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.BRICK_BLAST_FURNACE.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BrickBlastFurnaceBlockEntity furnace) {
        boolean wasActive = furnace.active;
        furnace.active = false;
        boolean changed = furnace.consumeFuelItemIfPossible();

        FurnaceRecipe recipe = furnace.findRecipe(level);
        if (recipe == null || !furnace.canOutputRecipe(recipe)) {
            if (furnace.progress != 0) {
                furnace.progress = 0;
                changed = true;
            }
        } else if (furnace.fuelHeat >= HEAT_USAGE_PER_TICK) {
            furnace.fuelHeat -= HEAT_USAGE_PER_TICK;
            furnace.progress++;
            furnace.active = true;
            changed = true;

            if (furnace.progress >= furnace.maxProgress) {
                furnace.completeRecipe(recipe);
                furnace.progress = 0;
            }
        }

        if (wasActive != furnace.active) {
            level.setBlock(pos, state.setValue(BrickBlastFurnaceBlock.LIT, furnace.active), Block.UPDATE_CLIENTS);
            changed = true;
        }

        if (changed) {
            setChanged(level, pos, state);
        }
    }

    public static boolean isFuel(ItemStack stack) {
        return getFuelHeat(stack) > 0;
    }

    public static boolean isTopInput(ItemStack stack) {
        return !stack.isEmpty();
    }

    public static boolean isBottomInput(ItemStack stack) {
        return !stack.isEmpty();
    }

    public static int getFuelHeat(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }

        if (stack.is(Items.LAVA_BUCKET)) {
            return MAX_FUEL_HEAT;
        }

        return stack.getBurnTime(RecipeType.SMELTING);
    }

    private boolean consumeFuelItemIfPossible() {
        ItemStack fuel = inventory.getStackInSlot(FUEL_SLOT);
        int heat = getFuelHeat(fuel);
        int remainingCapacity = MAX_FUEL_HEAT - fuelHeat;
        if (heat <= 0 || heat > remainingCapacity) {
            return false;
        }

        ItemStack remainder = fuel.getCraftingRemainingItem();
        fuel.shrink(1);
        if (!remainder.isEmpty() && fuel.isEmpty()) {
            inventory.setStackInSlot(FUEL_SLOT, remainder);
        } else if (!remainder.isEmpty() && level != null) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), remainder);
        }

        fuelHeat += heat;
        return true;
    }

    @Nullable
    private FurnaceRecipe findRecipe(Level level) {
        ItemStack top = inventory.getStackInSlot(TOP_INPUT_SLOT);
        ItemStack bottom = inventory.getStackInSlot(BOTTOM_INPUT_SLOT);

        for (var holder : level.getRecipeManager().getAllRecipesFor(ModRecipes.BRICK_BLAST_FURNACE_TYPE.get())) {
            BrickBlastFurnaceRecipe recipe = holder.value();
            if (matches(top, recipe.getFirstInput(), recipe.getFirstInputCount())
                    && matches(bottom, recipe.getSecondInput(), recipe.getSecondInputCount())) {
                return new FurnaceRecipe(
                        TOP_INPUT_SLOT,
                        recipe.getFirstInputCount(),
                        BOTTOM_INPUT_SLOT,
                        recipe.getSecondInputCount(),
                        recipe.getResult()
                );
            }
            if (matches(top, recipe.getSecondInput(), recipe.getSecondInputCount())
                    && matches(bottom, recipe.getFirstInput(), recipe.getFirstInputCount())) {
                return new FurnaceRecipe(
                        TOP_INPUT_SLOT,
                        recipe.getSecondInputCount(),
                        BOTTOM_INPUT_SLOT,
                        recipe.getFirstInputCount(),
                        recipe.getResult()
                );
            }
        }
        return null;
    }

    private static boolean matches(ItemStack stack, Ingredient ingredient, int count) {
        return ingredient.test(stack) && stack.getCount() >= count;
    }

    private boolean canOutputRecipe(FurnaceRecipe recipe) {
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        ItemStack result = recipe.output();
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void completeRecipe(FurnaceRecipe recipe) {
        inventory.extractItem(recipe.firstSlot(), recipe.firstCount(), false);
        inventory.extractItem(recipe.secondSlot(), recipe.secondCount(), false);

        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) {
            inventory.setStackInSlot(OUTPUT_SLOT, recipe.output().copy());
        } else {
            output.grow(recipe.output().getCount());
            inventory.setStackInSlot(OUTPUT_SLOT, output);
        }
    }

    private record FurnaceRecipe(int firstSlot, int firstCount, int secondSlot, int secondCount, ItemStack output) {
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        return new AutomationItemHandler(side);
    }

    public ContainerData getData() {
        return data;
    }

    public int getFuelHeat() {
        return fuelHeat;
    }

    public int getMaxFuelHeat() {
        return MAX_FUEL_HEAT;
    }

    public int getProgress() {
        return progress;
    }

    public int getMaxProgress() {
        return maxProgress;
    }

    public boolean isActive() {
        return active;
    }

    public int getRedstoneSignal() {
        return Mth.floor((float) inventory.getStackInSlot(OUTPUT_SLOT).getCount() / inventory.getSlotLimit(OUTPUT_SLOT) * 15.0F);
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
        return Component.translatable("container.skyent.brick_blast_furnace");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BrickBlastFurnaceMenu(containerId, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("FuelHeat", fuelHeat);
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuelHeat = Mth.clamp(tag.getInt("FuelHeat"), 0, MAX_FUEL_HEAT);
        progress = tag.getInt("Progress");
        maxProgress = tag.contains("MaxProgress") ? tag.getInt("MaxProgress") : STEEL_RECIPE_TIME;
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
    }

    private final class AutomationItemHandler implements IItemHandler {
        @Nullable
        private final Direction side;

        private AutomationItemHandler(@Nullable Direction side) {
            this.side = side;
        }

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
            if (stack.isEmpty() || !canInsertIntoSlotFromSide(slot, stack)) {
                return stack;
            }

            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (side != Direction.DOWN || slot != OUTPUT_SLOT) {
                return ItemStack.EMPTY;
            }

            return inventory.extractItem(OUTPUT_SLOT, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return canInsertIntoSlotFromSide(slot, stack);
        }

        private boolean canInsertIntoSlotFromSide(int slot, ItemStack stack) {
            if (!inventory.isItemValid(slot, stack)) {
                return false;
            }

            if (side == null) {
                return slot != OUTPUT_SLOT;
            }

            Direction facing = getBlockState().getValue(BrickBlastFurnaceBlock.FACING);
            Direction back = facing.getOpposite();
            Direction left = facing.getCounterClockWise();
            Direction right = facing.getClockWise();

            return switch (slot) {
                case FUEL_SLOT -> side == facing || side == back;
                case TOP_INPUT_SLOT -> side == Direction.UP;
                case BOTTOM_INPUT_SLOT -> side == left || side == right;
                default -> false;
            };
        }
    }
}
