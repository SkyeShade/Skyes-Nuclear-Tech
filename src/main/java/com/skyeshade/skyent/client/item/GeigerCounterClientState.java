package com.skyeshade.skyent.client.item;

import com.skyeshade.skyent.content.item.GeigerNeedleUtil;

public final class GeigerCounterClientState {
    private static double targetExposureMillisievertsPerSecond;
    private static double displayedExposureMillisievertsPerSecond;
    private static double radiationSickness;
    private static float targetNeedleValue;
    private static float displayedNeedleValue;

    private GeigerCounterClientState() {
    }

    public static void clientTick() {
        displayedExposureMillisievertsPerSecond += (targetExposureMillisievertsPerSecond - displayedExposureMillisievertsPerSecond) * 0.15D;
        targetNeedleValue = GeigerNeedleUtil.exposureToNeedleValue(displayedExposureMillisievertsPerSecond);
        displayedNeedleValue += (targetNeedleValue - displayedNeedleValue) * 0.15F;
    }

    public static void setExposureMillisievertsPerSecond(double exposureMillisievertsPerSecond) {
        targetExposureMillisievertsPerSecond = Math.max(0.0D, exposureMillisievertsPerSecond);
    }

    public static void setRadiationSickness(double radiationSickness) {
        GeigerCounterClientState.radiationSickness = Math.max(0.0D, radiationSickness);
    }

    public static float getNeedleValue() {
        return displayedNeedleValue;
    }

    public static double getRadiationSickness() {
        return radiationSickness;
    }

    public static double getDisplayedExposureMillisievertsPerSecond() {
        return displayedExposureMillisievertsPerSecond;
    }

    public static double getTargetExposureMillisievertsPerSecond() {
        return targetExposureMillisievertsPerSecond;
    }

}
