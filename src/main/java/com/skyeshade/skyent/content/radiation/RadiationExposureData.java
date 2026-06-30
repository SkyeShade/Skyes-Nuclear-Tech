package com.skyeshade.skyent.content.radiation;

public class RadiationExposureData {
    private double currentEnvironmentalExposureMillisievertsPerSecond;
    private double currentInventoryExposureMillisievertsPerSecond;
    private double currentTotalExposureMillisievertsPerSecond;
    private double radiationSickness;
    private long lastExposureUpdateTick;
    private long lastSicknessUpdateTick;
    private long lastSymptomTick;
    private long lastDebugOverlayTick;

    public double getCurrentEnvironmentalExposureMillisievertsPerSecond() {
        return currentEnvironmentalExposureMillisievertsPerSecond;
    }

    public void setCurrentEnvironmentalExposureMillisievertsPerSecond(double currentEnvironmentalExposureMillisievertsPerSecond) {
        this.currentEnvironmentalExposureMillisievertsPerSecond = currentEnvironmentalExposureMillisievertsPerSecond;
    }

    public double getCurrentInventoryExposureMillisievertsPerSecond() {
        return currentInventoryExposureMillisievertsPerSecond;
    }

    public void setCurrentInventoryExposureMillisievertsPerSecond(double currentInventoryExposureMillisievertsPerSecond) {
        this.currentInventoryExposureMillisievertsPerSecond = currentInventoryExposureMillisievertsPerSecond;
    }

    public double getCurrentTotalExposureMillisievertsPerSecond() {
        return currentTotalExposureMillisievertsPerSecond;
    }

    public void setCurrentTotalExposureMillisievertsPerSecond(double currentTotalExposureMillisievertsPerSecond) {
        this.currentTotalExposureMillisievertsPerSecond = currentTotalExposureMillisievertsPerSecond;
    }

    public double getRadiationSickness() {
        return radiationSickness;
    }

    public void setRadiationSickness(double radiationSickness) {
        this.radiationSickness = radiationSickness;
    }

    public long getLastExposureUpdateTick() {
        return lastExposureUpdateTick;
    }

    public void setLastExposureUpdateTick(long lastExposureUpdateTick) {
        this.lastExposureUpdateTick = lastExposureUpdateTick;
    }

    public long getLastSicknessUpdateTick() {
        return lastSicknessUpdateTick;
    }

    public void setLastSicknessUpdateTick(long lastSicknessUpdateTick) {
        this.lastSicknessUpdateTick = lastSicknessUpdateTick;
    }

    public long getLastSymptomTick() {
        return lastSymptomTick;
    }

    public void setLastSymptomTick(long lastSymptomTick) {
        this.lastSymptomTick = lastSymptomTick;
    }

    public long getLastDebugOverlayTick() {
        return lastDebugOverlayTick;
    }

    public void setLastDebugOverlayTick(long lastDebugOverlayTick) {
        this.lastDebugOverlayTick = lastDebugOverlayTick;
    }
}
