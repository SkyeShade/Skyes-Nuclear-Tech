package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.content.block.BasicFluidDuctBlock;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockState state = level.getBlockState(context.getClickedPos());
        if (context.getPlayer() != null && state.getBlock() instanceof BasicFluidDuctBlock) {
            if (context.getPlayer().isShiftKeyDown()) {
                return BasicFluidDuctBlock.pickupWithWrench(level, context.getClickedPos(), context.getPlayer());
            }

            BlockHitResult hitResult = new BlockHitResult(
                    context.getClickLocation(),
                    context.getClickedFace(),
                    context.getClickedPos(),
                    context.isInside()
            );
            return BasicFluidDuctBlock.useWrench(
                    state,
                    level,
                    context.getClickedPos(),
                    context.getPlayer(),
                    hitResult
            );
        }

        return InteractionResult.PASS;
    }
}
