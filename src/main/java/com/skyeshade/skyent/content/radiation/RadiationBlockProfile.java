package com.skyeshade.skyent.content.radiation;

public record RadiationBlockProfile(
        double radiationStrength,
        int environmentalRange,
        int entityRange,
        double transmission,
        boolean hasCustomTransmission,
        boolean radioactive,
        boolean showShieldingTooltip
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private double radiationStrength;
        private int environmentalRange;
        private int entityRange;
        private double transmission = 0.80D;
        private boolean hasCustomTransmission;
        private boolean radioactive;
        private boolean showShieldingTooltip;

        private Builder() {
        }

        public Builder radiation(double strength, int environmentalRange, int entityRange) {
            this.radiationStrength = strength;
            this.environmentalRange = environmentalRange;
            this.entityRange = entityRange;
            this.radioactive = strength > 0.0D;
            return this;
        }

        public Builder transmission(double transmission) {
            this.transmission = transmission;
            this.hasCustomTransmission = true;
            return this;
        }

        public Builder showShieldingTooltip() {
            this.showShieldingTooltip = true;
            return this;
        }

        public RadiationBlockProfile build() {
            return new RadiationBlockProfile(
                    radiationStrength,
                    environmentalRange,
                    entityRange,
                    transmission,
                    hasCustomTransmission,
                    radioactive,
                    showShieldingTooltip
            );
        }
    }
}
