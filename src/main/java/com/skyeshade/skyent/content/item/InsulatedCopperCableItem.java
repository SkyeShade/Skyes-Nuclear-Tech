package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.content.energy.InsulatedCopperCableConstants;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class InsulatedCopperCableItem extends BlockItem {
    public InsulatedCopperCableItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.literal("Max Current: " + formatCurrent(InsulatedCopperCableConstants.MAX_CURRENT_AMPS) + " A").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal("Resistance: " + formatDecimal(InsulatedCopperCableConstants.RESISTANCE_PER_BLOCK)).withStyle(ChatFormatting.GRAY));
    }

    private static String formatCurrent(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : formatDecimal(value);
    }

    private static String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
