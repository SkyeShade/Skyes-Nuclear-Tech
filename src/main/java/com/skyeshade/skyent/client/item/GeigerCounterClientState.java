package com.skyeshade.skyent.client.item;

public final class GeigerCounterClientState {
    private static double targetExposureMillisievertsPerSecond;
    private static double displayedExposureMillisievertsPerSecond;
    private static float targetNeedleValue;
    private static float displayedNeedleValue;

    private GeigerCounterClientState() {
    }

    public static void clientTick() {
        displayedExposureMillisievertsPerSecond += (targetExposureMillisievertsPerSecond - displayedExposureMillisievertsPerSecond) * 0.15D;
        targetNeedleValue = exposureToNeedleValue(displayedExposureMillisievertsPerSecond);
        displayedNeedleValue += (targetNeedleValue - displayedNeedleValue) * 0.15F;
    }

    public static void setExposureMillisievertsPerSecond(double exposureMillisievertsPerSecond) {
        targetExposureMillisievertsPerSecond = Math.max(0.0D, exposureMillisievertsPerSecond);
    }

    public static float getNeedleValue() {
        return displayedNeedleValue;
    }

    public static double getDisplayedExposureMillisievertsPerSecond() {
        return displayedExposureMillisievertsPerSecond;
    }

    private static float exposureToNeedleValue(double exposure) {
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
}
