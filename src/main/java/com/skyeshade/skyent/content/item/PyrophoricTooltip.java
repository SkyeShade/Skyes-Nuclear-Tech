package com.skyeshade.skyent.content.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

public final class PyrophoricTooltip {
    private static final Style PYROPHORIC_STYLE = Style.EMPTY.withColor(TextColor.fromRgb(0xFFB23A));

    private PyrophoricTooltip() {
    }

    public static void append(ItemStack stack, List<Component> tooltipComponents) {
        if (!HotItemUtil.isPyrophoric(stack)) {
            return;
        }

        tooltipComponents.add(Component.translatable("tooltip.skyent.pyrophoric").withStyle(PYROPHORIC_STYLE));
    }
}
