package com.skyeshade.skyent.content.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class TooltipBlockItem extends BlockItem {
    private final String firstLineKey;
    private final String secondLineKey;

    public TooltipBlockItem(Block block, Item.Properties properties, String firstLineKey, String secondLineKey) {
        super(block, properties);
        this.firstLineKey = firstLineKey;
        this.secondLineKey = secondLineKey;
    }

    public TooltipBlockItem(Block block, Item.Properties properties, String firstLineKey) {
        this(block, properties, firstLineKey, null);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(firstLineKey));

        if (secondLineKey != null && !secondLineKey.isBlank()) {
            tooltipComponents.add(Component.translatable(secondLineKey));
        }
    }
}