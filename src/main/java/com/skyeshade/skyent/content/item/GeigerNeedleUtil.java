package com.skyeshade.skyent.content.item;

import net.minecraft.util.Mth;

public final class GeigerNeedleUtil {
    public static final float NEEDLE_SAFE_ANGLE_DEGREES = 90.0F;
    public static final float NEEDLE_HOT_ANGLE_DEGREES = -90.0F;

    private GeigerNeedleUtil() {
    }

    public static float exposureToNeedleValue(double exposure) {
        if (exposure <= 0.0D) {
            return 0.0F;
        }

        if (exposure < 5.0D) {
            return (float) (0.28D * (Math.log10(1.0D + exposure) / Math.log10(1.0D + 5.0D)));
        }

        if (exposure < 100.0D) {
            double t = Math.log10(exposure / 5.0D) / Math.log10(100.0D / 5.0D);
            return (float) (0.28D + t * 0.44D);
        }

        if (exposure < 500.0D) {
            double t = Math.log10(exposure / 100.0D) / Math.log10(500.0D / 100.0D);
            return (float) (0.72D + t * 0.28D);
        }

        return 1.0F;
    }

    public static float exposureToNeedleDegrees(double exposure) {
        return valueToNeedleDegrees(exposureToNeedleValue(exposure));
    }

    public static float valueToNeedleDegrees(float value) {
        return Mth.lerp(Mth.clamp(value, 0.0F, 1.0F), NEEDLE_SAFE_ANGLE_DEGREES, NEEDLE_HOT_ANGLE_DEGREES);
    }

    public static float valueToRenderedNeedleDegrees(float value, float ticks) {
        float clampedValue = Mth.clamp(value, 0.0F, 1.0F);
        float angle = valueToNeedleDegrees(clampedValue);
        float jitter = Mth.sin(ticks * 1.37F) * Mth.clamp(clampedValue * 1.25F, 0.005F, 0.08F) * 20.0F;
        return angle + jitter;
    }
}
