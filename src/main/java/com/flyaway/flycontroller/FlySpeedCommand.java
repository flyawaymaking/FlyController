package com.flyaway.flycontroller;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FlySpeedCommand implements CommandExecutor, TabCompleter {
    private final FlyPlugin plugin;

    public FlySpeedCommand(FlyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, plugin.getConfigManager().getMessage("only-players"));
            return true;
        }

        if (!player.hasPermission("flycontroller.flyspeed")) {
            plugin.sendMessage(player, plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (!plugin.isWorldAllowed(player.getWorld())) {
            plugin.sendMessage(player, plugin.getConfigManager().getMessage("flyspeed-not-allowed-world"));
            return true;
        }

        if (args.length == 0) {
            Map<String, String> placeholders = Map.of(
                    "speeds", getAvailableSpeedsString()
            );
            plugin.sendMessage(player, plugin.getConfigManager().getMessage("flyspeed-usage", placeholders));
            return true;
        }

        try {
            int speed = Integer.parseInt(args[0]);

            Map<Integer, Float> availableSpeeds = plugin.getFlySpeeds();

            if (!availableSpeeds.containsKey(speed)) {
                Map<String, String> placeholders = Map.of(
                        "speed", String.valueOf(speed),
                        "speeds", getAvailableSpeedsString()
                );
                plugin.sendMessage(player, plugin.getConfigManager().getMessage("flyspeed-not-available", placeholders));
                return true;
            }

            float flySpeed = availableSpeeds.get(speed);
            player.setFlySpeed(flySpeed);

            String speedName = getSpeedName(speed);
            Map<String, String> placeholders = Map.of(
                    "speed_name", speedName,
                    "speed_level", String.valueOf(speed)
            );
            plugin.sendMessage(player, plugin.getConfigManager().getMessage("flyspeed-set", placeholders));

        } catch (NumberFormatException e) {
            Map<String, String> placeholders = Map.of(
                    "speeds", getAvailableSpeedsString()
            );
            plugin.sendMessage(player, plugin.getConfigManager().getMessage("flyspeed-invalid-number", placeholders));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (sender.hasPermission("flycontroller.flyspeed") && args.length == 1) {
            for (Integer speed : plugin.getFlySpeeds().keySet()) {
                completions.add(speed.toString());
            }
        }

        return completions;
    }

    private String getAvailableSpeedsString() {
        Map<Integer, Float> speeds = plugin.getFlySpeeds();
        List<String> speedList = new ArrayList<>();

        for (Map.Entry<Integer, Float> entry : speeds.entrySet()) {
            int speedLevel = entry.getKey();
            String speedName = getSpeedName(speedLevel);
            speedList.add(speedLevel + " (" + speedName + ")");
        }

        return String.join(", ", speedList);
    }

    private String getSpeedName(int speed) {
        String speedName = plugin.getConfigManager().getMessage("flyspeed-names." + speed);
        if (speedName.startsWith("message.flyspeed-names.")) {
            return speed + "lvl";
        }
        return speedName;
    }
}
