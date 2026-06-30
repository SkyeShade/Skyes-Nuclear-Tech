package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.content.radiation.RadiationItemValues;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class RadioactiveTooltip {
    private RadioactiveTooltip() {
    }

    public static void append(ItemStack stack, List<Component> tooltipComponents) {
        double perItemRadiation = RadiationItemValues.getItemRadiationPerItem(stack);
        if (perItemRadiation <= 0.0D) {
            return;
        }

        tooltipComponents.add(Component.translatable("tooltip.skyent.radioactive").withStyle(ChatFormatting.YELLOW));
        if (stack.getCount() > 1) {
            tooltipComponents.add(Component.translatable(
                    "tooltip.skyent.radiation_strength_each",
                    RadiationItemValues.formatRadiation(perItemRadiation)
            ).withStyle(ChatFormatting.YELLOW));
            tooltipComponents.add(Component.translatable(
                    "tooltip.skyent.stack_radiation_strength",
                    RadiationItemValues.formatRadiation(RadiationItemValues.getStackRadiation(stack))
            ).withStyle(ChatFormatting.YELLOW));
        } else {
            tooltipComponents.add(Component.translatable(
                    "tooltip.skyent.radiation_strength",
                    RadiationItemValues.formatRadiation(perItemRadiation)
            ).withStyle(ChatFormatting.YELLOW));
        }
    }
}
