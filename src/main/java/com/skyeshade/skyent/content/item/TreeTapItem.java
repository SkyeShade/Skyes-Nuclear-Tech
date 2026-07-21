package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.content.block.ResinBearingRubberLogBlock;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

public class TreeTapItem extends Item {
    public TreeTapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.RESIN_BEARING_RUBBER_LOG.get())) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        if (!level.isClientSide) {
            ItemStack resin = new ItemStack(ModItems.RESIN.get(), 1 + level.random.nextInt(3));
            if (player == null || !player.getInventory().add(resin)) {
                Block.popResource(level, pos.relative(context.getClickedFace()), resin);
            }
            BlockState replacement = ModBlocks.RUBBER_LOG.get().defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
            level.setBlock(pos, replacement, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 0.8F, 1.15F);
            ItemStack stack = context.getItemInHand();
            if (player != null && !player.getAbilities().instabuild) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
