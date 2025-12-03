package com.flyaway.flycontroller.models;

public class FlightTier {
    private final int level;
    private final double minAmount;
    private final int duration;

    public FlightTier(int level, double minAmount, int duration) {
        this.level = level;
        this.minAmount = minAmount;
        this.duration = duration;
    }

    public int getLevel() {
        return level;
    }

    public double getMinAmount() {
        return minAmount;
    }

    public int getDuration() {
        return duration;
    }
}
