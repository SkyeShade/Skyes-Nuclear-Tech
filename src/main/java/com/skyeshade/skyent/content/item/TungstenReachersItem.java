package com.skyeshade.skyent.content.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class TungstenReachersItem extends Item {
    public TungstenReachersItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.skyent.tungsten_reachers.1").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.skyent.tungsten_reachers.2").withStyle(ChatFormatting.GRAY));
    }
}
