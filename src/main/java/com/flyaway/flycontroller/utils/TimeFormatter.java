package com.flyaway.flycontroller.utils;

import com.flyaway.flycontroller.managers.ConfigManager;

import java.util.Map;

public class TimeFormatter {
    public static String formatTime(long milliseconds, ConfigManager configManager) {
        if (milliseconds <= 0) return "0";
        long minutes = milliseconds / 60000;
        long seconds = (milliseconds % 60000) / 1000;

        Map<String, String> timePlaceholders = Map.of(
                "minutes", String.valueOf(minutes),
                "seconds", String.valueOf(seconds)
        );
        return configManager.getMessage("time-format", timePlaceholders);
    }
}
