package com.skyeshade.skyent.content.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class TooltipBlockItem extends BlockItem {
    private final List<String> lineKeys;
    private final ChatFormatting style;

    public TooltipBlockItem(Block block, Item.Properties properties, String firstLineKey, String secondLineKey) {
        this(block, properties, secondLineKey == null ? new String[] {firstLineKey} : new String[] {firstLineKey, secondLineKey});
    }

    public TooltipBlockItem(Block block, Item.Properties properties, String firstLineKey) {
        this(block, properties, new String[] {firstLineKey});
    }

    public TooltipBlockItem(Block block, Item.Properties properties, String... lineKeys) {
        this(block, properties, ChatFormatting.GRAY, lineKeys);
    }

    public TooltipBlockItem(Block block, Item.Properties properties, ChatFormatting style, String... lineKeys) {
        super(block, properties);
        this.lineKeys = List.of(lineKeys);
        this.style = style;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        for (String lineKey : lineKeys) {
            if (lineKey != null && !lineKey.isBlank()) {
                tooltipComponents.add(Component.translatable(lineKey).withStyle(style));
            }
        }
    }
}
