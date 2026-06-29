package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.content.radiation.RadioactiveSource;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class RadioactiveBlockItem extends BlockItem {
    public RadioactiveBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (getBlock() instanceof RadioactiveSource radioactiveSource) {
            tooltipComponents.add(Component.translatable("tooltip.skyent.radioactive").withStyle(ChatFormatting.YELLOW));
            tooltipComponents.add(Component.translatable(
                    "tooltip.skyent.radiation_strength",
                    formatRadiation(radioactiveSource.getRadiationStrength())
            ).withStyle(ChatFormatting.YELLOW));
        }
    }

    private static String formatRadiation(double value) {
        if (value == Math.rint(value)) {
            return Integer.toString((int) value);
        }

        return Double.toString(value);
    }
}
