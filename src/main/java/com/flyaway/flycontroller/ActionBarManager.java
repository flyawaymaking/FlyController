package com.flyaway.flycontroller;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

public class ActionBarManager {
    private final FlyPlugin plugin;

    public ActionBarManager(FlyPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendFlightTime(Player player, long endTime) {
        long remainingTime = endTime - System.currentTimeMillis();
        if (remainingTime <= 0) {
            return;
        }

        long seconds = remainingTime / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        String timeString = String.format("§a⏰ §e%d:%02d §aосталось полёта", minutes, seconds);

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(timeString));
    }
}
