package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ForgingAnvilBlockEntity extends BlockEntity {
    private ItemStack input = ItemStack.EMPTY;
    private int strikes;
    private boolean finished;

    public ForgingAnvilBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FORGING_ANVIL.get(), pos, blockState);
    }

    public boolean hasInput() {
        return !input.isEmpty();
    }

    public ItemStack getInput() {
        return input;
    }

    public int getStrikes() {
        return strikes;
    }

    public boolean isFinished() {
        return finished && !input.isEmpty();
    }

    public void setInput(ItemStack stack) {
        input = stack.copyWithCount(1);
        strikes = 0;
        finished = false;
        setChangedAndSync();
    }

    public ItemStack removeInput() {
        ItemStack removed = input.copy();
        clearInput();
        return removed;
    }

    public void clearInput() {
        input = ItemStack.EMPTY;
        strikes = 0;
        finished = false;
        setChangedAndSync();
    }

    public void setFinishedOutput(ItemStack stack) {
        input = stack.copyWithCount(1);
        strikes = 3;
        finished = true;
        setChangedAndSync();
    }

    public int incrementStrikes() {
        strikes++;
        setChangedAndSync();
        return strikes;
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!input.isEmpty()) {
            tag.put("Input", input.save(registries));
        }
        tag.putInt("Strikes", strikes);
        tag.putBoolean("Finished", finished);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input = tag.contains("Input") ? ItemStack.parseOptional(registries, tag.getCompound("Input")) : ItemStack.EMPTY;
        strikes = Math.max(0, tag.getInt("Strikes"));
        finished = tag.getBoolean("Finished") && !input.isEmpty();
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
