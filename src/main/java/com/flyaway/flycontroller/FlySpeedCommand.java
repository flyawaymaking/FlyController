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
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда только для игроков!");
            return true;
        }

        Player player = (Player) sender;

        // ПРОВЕРКА ПРАВА ДОСТУПА
        if (!player.hasPermission("flycontroller.flyspeed")) {
            player.sendMessage("§cУ вас нет разрешения на использование этой команды!");
            return true;
        }

        // Проверка мира
        if (!plugin.isWorldAllowed(player.getWorld())) {
            player.sendMessage("§cКоманда /flyspeed доступна только в разрешённых мирах!");
            return true;
        }

        // Проверка аргументов
        if (args.length == 0) {
            player.sendMessage("§cИспользование: /flyspeed <скорость>");
            player.sendMessage("§6Доступные скорости: " + getAvailableSpeedsString());
            return true;
        }

        try {
            int speed = Integer.parseInt(args[0]);

            // Получаем доступные скорости из конфига
            Map<Integer, Float> availableSpeeds = plugin.getFlySpeeds();

            // Проверка доступности скорости
            if (!availableSpeeds.containsKey(speed)) {
                player.sendMessage("§cСкорость " + speed + " недоступна!");
                player.sendMessage("§6Доступные скорости: " + getAvailableSpeedsString());
                return true;
            }

            // Устанавливаем скорость полёта из конфига
            float flySpeed = availableSpeeds.get(speed);
            player.setFlySpeed(flySpeed);

            String speedName = getSpeedName(speed);
            player.sendMessage("§aСкорость полёта установлена на: §e" + speedName + "§a (" + speed + ")");

        } catch (NumberFormatException e) {
            player.sendMessage("§cСкорость должна быть числом!");
            player.sendMessage("§6Доступные скорости: " + getAvailableSpeedsString());
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (sender.hasPermission("flycontroller.flyspeed") && args.length == 1) {
            // Показываем доступные скорости из конфига
            for (Integer speed : plugin.getFlySpeeds().keySet()) {
                completions.add(speed.toString());
            }
        }

        return completions;
    }

    /**
     * Возвращает строку с доступными скоростями для сообщения
     */
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

    /**
     * Возвращает текстовое название скорости
     */
    private String getSpeedName(int speed) {
        // Можно добавить в конфиг названия скоростей, но пока оставим фиксированные
        switch (speed) {
            case 1: return "медленно";
            case 2: return "нормально";
            case 3: return "быстро";
            case 4: return "очень быстро";
            default: return "уровень " + speed;
        }
    }
}
