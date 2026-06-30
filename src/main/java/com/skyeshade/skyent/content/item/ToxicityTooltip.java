package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.content.toxicity.ToxicItemValues;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class ToxicityTooltip {
    private ToxicityTooltip() {
    }

    public static void append(ItemStack stack, List<Component> tooltipComponents) {
        double stackToxicity = ToxicItemValues.getStackToxicity(stack);
        if (stackToxicity <= 0.0D) {
            return;
        }

        tooltipComponents.add(Component.translatable(
                "tooltip.skyent.toxicity",
                severityTranslationKey(stackToxicity),
                ToxicItemValues.formatToxicity(stackToxicity)
        ).withStyle(ChatFormatting.GREEN));
    }

    private static Component severityTranslationKey(double stackToxicity) {
        if (stackToxicity >= 1000.0D) {
            return Component.translatable("tooltip.skyent.toxicity.extreme");
        }
        if (stackToxicity >= 100.0D) {
            return Component.translatable("tooltip.skyent.toxicity.high");
        }
        if (stackToxicity >= 10.0D) {
            return Component.translatable("tooltip.skyent.toxicity.medium");
        }
        return Component.translatable("tooltip.skyent.toxicity.low");
    }
}
