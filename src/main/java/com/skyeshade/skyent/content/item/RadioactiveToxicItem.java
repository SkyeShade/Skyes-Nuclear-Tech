package com.skyeshade.skyent.content.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class RadioactiveToxicItem extends Item {
    public RadioactiveToxicItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        RadioactiveTooltip.append(stack, tooltipComponents);
        ToxicityTooltip.append(stack, tooltipComponents);
    }
}
