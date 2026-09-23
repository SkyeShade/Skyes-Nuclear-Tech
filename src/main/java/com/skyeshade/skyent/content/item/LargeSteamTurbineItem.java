package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.content.block.LargeSteamTurbineBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class LargeSteamTurbineItem extends BlockItem {
    public LargeSteamTurbineItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return LargeSteamTurbineBlock.place(context, state);
    }
}
