package com.skyeshade.skyent.content.energy;

import net.minecraft.util.Mth;

public final class RJStorage {
    private final int capacity;
    private int stored;

    public RJStorage(int capacity) {
        this.capacity = capacity;
    }

    public int getStoredRJ() {
        return stored;
    }

    public int getCapacityRJ() {
        return capacity;
    }

    public int getAvailableRJCapacity() {
        return capacity - stored;
    }

    public int receiveRJ(int maxAmount, boolean simulate) {
        int received = Math.min(getAvailableRJCapacity(), Math.max(0, maxAmount));
        if (received > 0 && !simulate) {
            stored += received;
        }

        return received;
    }

    public int extractRJ(int maxAmount, boolean simulate) {
        int extracted = Math.min(stored, Math.max(0, maxAmount));
        if (extracted > 0 && !simulate) {
            stored -= extracted;
        }

        return extracted;
    }

    public int consumeRJ(int amount) {
        return extractRJ(amount, false);
    }

    public int setStoredRJ(int amount) {
        int previous = stored;
        stored = Mth.clamp(amount, 0, capacity);
        return stored - previous;
    }
}
