package com.flyaway.flycontroller;

public class FlightData {
    private double balance; // Баланс на счету полётов (внесённые деньги)
    private int maxUnlockedLevel;
    private long cooldownEnd;
    private boolean flightActive;
    private long flightEndTime;
    private long pausedTime; // Сохранённое время в миллисекундах

    public FlightData() {
        this.balance = 0;
        this.maxUnlockedLevel = 0;
        this.cooldownEnd = 0;
        this.flightActive = false;
        this.flightEndTime = 0;
        this.pausedTime = 0;
    }

    // Геттеры и сеттеры
    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public int getMaxUnlockedLevel() {
        return maxUnlockedLevel;
    }

    public void setMaxUnlockedLevel(int maxUnlockedLevel) {
        this.maxUnlockedLevel = maxUnlockedLevel;
    }

    public long getCooldownEnd() {
        return cooldownEnd;
    }

    public void setCooldownEnd(long cooldownEnd) {
        this.cooldownEnd = cooldownEnd;
    }

    public boolean isFlightActive() {
        return flightActive;
    }

    public void setFlightActive(boolean flightActive) {
        this.flightActive = flightActive;
    }

    public long getFlightEndTime() {
        return flightEndTime;
    }

    public void setFlightEndTime(long flightEndTime) {
        this.flightEndTime = flightEndTime;
    }

    public long getPausedTime() {
        return pausedTime;
    }

    public void setPausedTime(long pausedTime) {
        this.pausedTime = pausedTime;
    }
}
