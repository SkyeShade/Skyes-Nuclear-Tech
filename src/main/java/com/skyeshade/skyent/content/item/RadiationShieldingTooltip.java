package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.content.radiation.RadiationBlockProfile;
import com.skyeshade.skyent.content.radiation.RadiationBlockProfiles;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public final class RadiationShieldingTooltip {
    private RadiationShieldingTooltip() {
    }

    public static void append(ItemStack stack, List<Component> tooltipComponents) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return;
        }

        RadiationBlockProfile profile = RadiationBlockProfiles.get(blockItem.getBlock());
        if (!profile.showShieldingTooltip() || !profile.hasCustomTransmission()) {
            return;
        }

        double shieldFraction = 1.0D - profile.transmission();
        if (shieldFraction < 0.10D) {
            return;
        }

        tooltipComponents.add(severity(shieldFraction).withStyle(ChatFormatting.DARK_GREEN));
    }

    private static MutableComponent severity(double shieldFraction) {
        if (shieldFraction >= 0.95D) {
            return Component.translatable("tooltip.skyent.radiation_shielding.extreme");
        }
        if (shieldFraction >= 0.70D) {
            return Component.translatable("tooltip.skyent.radiation_shielding.high");
        }
        if (shieldFraction >= 0.35D) {
            return Component.translatable("tooltip.skyent.radiation_shielding.medium");
        }
        return Component.translatable("tooltip.skyent.radiation_shielding.low");
    }
}
