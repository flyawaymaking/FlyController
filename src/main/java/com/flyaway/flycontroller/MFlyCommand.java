package com.flyaway.flycontroller;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MFlyCommand implements CommandExecutor, TabCompleter {
    private final FlyPlugin plugin;

    public MFlyCommand(FlyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, plugin.getConfigManager().getMessage("only-players"));
            return true;
        }

        if (!player.hasPermission("flycontroller.mfly")) {
            plugin.sendMessage(player, plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            showFlightInfo(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info":
                showFlightInfo(player);
                break;

            case "deposit":
                if (args.length < 2) {
                    plugin.sendMessage(player, plugin.getConfigManager().getMessage("mfly-deposit-usage"));
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[1]);
                    if (amount <= 0) {
                        plugin.sendMessage(player, plugin.getConfigManager().getMessage("amount-must-be-positive"));
                        return true;
                    }
                    plugin.depositMoney(player, amount);
                } catch (NumberFormatException e) {
                    plugin.sendMessage(player, plugin.getConfigManager().getMessage("amount-must-be-number"));
                }
                break;

            case "activate":
                plugin.activateFlight(player);
                break;

            case "continue":
                plugin.continueFlight(player);
                break;

            case "reload":
                if (sender.hasPermission("flycontroller.admin")) {
                    plugin.reloadConfiguration();
                    plugin.sendMessage(sender, plugin.getConfigManager().getMessage("config-reloaded"));
                } else {
                    plugin.sendMessage(sender, plugin.getConfigManager().getMessage("no-permission"));
                }
                break;

            default:
                showCommandHelp(player);
                break;
        }

        return true;
    }

    private void showFlightInfo(Player player) {
        FlightData data = plugin.getDataManager().loadPlayerData(player.getUniqueId());
        String currencySymbol = plugin.getEconomyManager().getCurrencySymbol();
        int currentLevel = plugin.calculateFlightLevel(data.getBalance());

        // Получаем сохраненное время
        Long pausedTime = plugin.getPausedFlightTime(player);
        if (pausedTime == null || pausedTime <= 0) {
            pausedTime = data.getPausedTime();
        }

        String activeTime = plugin.formatTime(plugin.getRemainingFlightTime(player));
        String pausedTimeStr = plugin.formatTime(pausedTime);
        String cooldownTimeStr = plugin.formatTime(data.getCooldownEnd() - System.currentTimeMillis());

        String infoMessage = plugin.getConfigManager().getMessage("mfly-info", Map.of(
                "{balance}", data.getBalance() + currencySymbol,
                "{level}", String.valueOf(currentLevel)
        ));

        double amountForNextLevel = getAmountForNextLevel(data, plugin);
        if (amountForNextLevel > 0) {
            infoMessage += "\n" + plugin.getConfigManager().getMessage("mfly-info-next-level", Map.of("{amount}", amountForNextLevel + currencySymbol));
        } else if (currentLevel >= plugin.getMaxFlightLevel()) {
            infoMessage += "\n" + plugin.getConfigManager().getMessage("mfly-info-max-level");
        }

        infoMessage += "\n" + plugin.getConfigManager().getMessage("mfly-info-levels");
        for (FlightTier tier : plugin.getFlightTiers().values()) {
            String status = data.getBalance() >= tier.getMinAmount() ? "✓" : "✗";
            String timeFormatted = plugin.formatTime(tier.getDuration() * 1000L);

            Map<String, String> tierPlaceholders = Map.of(
                    "status", status,
                    "level", String.valueOf(tier.getLevel()),
                    "time", timeFormatted,
                    "cost", String.valueOf(tier.getMinAmount()),
                    "currency", currencySymbol
            );

            String levelLine = plugin.getConfigManager().getMessage("mfly-info-level-format", tierPlaceholders);

            if (currentLevel == tier.getLevel()) {
                levelLine = plugin.getConfigManager().getMessage("mfly-info-level-current",
                        Map.of("level_info", levelLine));
            }

            infoMessage += "\n" + levelLine;
        }

        if (plugin.getRemainingFlightTime(player) > 0) {
            infoMessage += "\n" + plugin.getConfigManager().getMessage("mfly-info-active-flight",
                    Map.of("time", activeTime));
        }

        if (pausedTime > 0) {
            infoMessage += "\n" + plugin.getConfigManager().getMessage("mfly-info-paused-flight",
                    Map.of("time", pausedTimeStr));
            infoMessage += "\n" + plugin.getConfigManager().getMessage("mfly-info-paused-hint");
        }

        if (data.getCooldownEnd() > System.currentTimeMillis()) {
            infoMessage += "\n" + plugin.getConfigManager().getMessage("mfly-info-cooldown",
                    Map.of("time", cooldownTimeStr));
        }

        infoMessage += "\n" + plugin.getConfigManager().getMessage("mfly-info-speeds");
        StringBuilder speedsInfo = new StringBuilder();
        for (Integer speed : plugin.getFlySpeeds().keySet()) {
            String speedName = getSpeedName(speed);
            speedsInfo.append(speed).append(" (").append(speedName).append("), ");
        }
        if (!speedsInfo.isEmpty()) {
            speedsInfo.setLength(speedsInfo.length() - 2);
            infoMessage += "\n" + speedsInfo.toString();
        }
        infoMessage += "\n" + plugin.getConfigManager().getMessage("mfly-info-speeds-hint");

        infoMessage += "\n" + plugin.getConfigManager().getMessage("mfly-info-commands");
        if (currentLevel < plugin.getMaxFlightLevel()) {
            infoMessage += "\n" + plugin.getConfigManager().getMessage("mfly-info-help-deposit");
        }
        infoMessage += "\n" + plugin.getConfigManager().getMessage("mfly-info-help-activate");
        if (pausedTime > 0) {
            infoMessage += "\n" + plugin.getConfigManager().getMessage("mfly-info-help-continue");
        }
        if (player.hasPermission("flycontroller.admin")) {
            infoMessage += "\n" + plugin.getConfigManager().getMessage("mfly-info-help-reload");
        }

        plugin.sendMessage(player, infoMessage);
    }

    private void showCommandHelp(Player player) {
        String helpMessage = plugin.getConfigManager().getMessage("mfly-help");
        if (player.hasPermission("flycontroller.admin")) {
            helpMessage += "\n" + plugin.getConfigManager().getMessage("mfly-help-reload");
        }
        plugin.sendMessage(player, helpMessage);
    }

    private double getAmountForNextLevel(FlightData data, FlyPlugin plugin) {
        double currentBalance = data.getBalance();
        int currentLevel = plugin.calculateFlightLevel(currentBalance);
        int nextLevel = currentLevel + 1;

        FlightTier nextTier = plugin.getFlightTiers().get(nextLevel);
        if (nextTier == null) {
            return 0;
        }

        return Math.max(0, nextTier.getMinAmount() - currentBalance);
    }

    private String getSpeedName(int speed) {
        String speedName = plugin.getConfigManager().getMessage("flyspeed-names." + speed);
        if (speedName.startsWith("message.flyspeed-names.")) {
            return switch (speed) {
                case 1 -> "медленно";
                case 2 -> "нормально";
                case 3 -> "быстро";
                case 4 -> "очень быстро";
                default -> "уровень " + speed;
            };
        }
        return speedName;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("info");
            completions.add("deposit");
            completions.add("activate");
            completions.add("continue");
            if (sender.hasPermission("flycontroller.admin")) {
                completions.add("reload");
            }
        }

        return completions;
    }
}
