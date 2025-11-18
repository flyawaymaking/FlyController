package com.flyaway.flycontroller.managers;

import com.flyaway.flycontroller.FlyPlugin;
import com.flyaway.flycontroller.utils.TimeFormatter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.Map;

public class ActionBarManager {
    private final FlyPlugin plugin;
    private final ConfigManager configManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ActionBarManager(FlyPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    public void sendFlightTime(Player player, long endTime) {
        long remainingTime = endTime - System.currentTimeMillis();
        if (remainingTime <= 0) {
            return;
        }
        long minutes = remainingTime / 60000;
        long seconds = (remainingTime % 60000) / 1000;
        String timeString = configManager.getMessage("action-bar", Map.of(
                "minutes", String.valueOf(minutes),
                "seconds", String.valueOf(seconds)
        ));
        player.sendActionBar(miniMessage.deserialize(timeString));
    }
}
