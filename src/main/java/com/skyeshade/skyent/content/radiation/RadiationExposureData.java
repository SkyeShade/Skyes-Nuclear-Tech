package com.skyeshade.skyent.content.radiation;

public class RadiationExposureData {
    private double currentEnvironmentalExposureMillisievertsPerSecond;
    private long lastExposureUpdateTick;

    public double getCurrentEnvironmentalExposureMillisievertsPerSecond() {
        return currentEnvironmentalExposureMillisievertsPerSecond;
    }

    public void setCurrentEnvironmentalExposureMillisievertsPerSecond(double currentEnvironmentalExposureMillisievertsPerSecond) {
        this.currentEnvironmentalExposureMillisievertsPerSecond = currentEnvironmentalExposureMillisievertsPerSecond;
    }

    public long getLastExposureUpdateTick() {
        return lastExposureUpdateTick;
    }

    public void setLastExposureUpdateTick(long lastExposureUpdateTick) {
        this.lastExposureUpdateTick = lastExposureUpdateTick;
    }
}
