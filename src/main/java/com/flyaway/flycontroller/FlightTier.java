package com.flyaway.flycontroller;

public class FlightTier {
    private final int level;
    private final double minAmount; // Минимальная сумма для уровня
    private final int duration; // в секундах

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
