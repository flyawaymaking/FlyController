package com.flyaway.flycontroller.commands;

import com.flyaway.flycontroller.FlyPlugin;
import com.flyaway.flycontroller.managers.ConfigManager;
import com.flyaway.flycontroller.managers.FlightManager;
import com.flyaway.flycontroller.managers.PlayerManager;
import com.flyaway.flycontroller.models.FlightData;
import com.flyaway.flycontroller.models.FlightTier;
import com.flyaway.flycontroller.utils.TimeFormatter;
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
    private final ConfigManager configManager;
    private final PlayerManager playerManager;
    private final FlightManager flightManager;

    public MFlyCommand(FlyPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.playerManager = plugin.getPlayerManager();
        this.flightManager = plugin.getFlightManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            playerManager.sendMessage(sender, configManager.getMessage("only-players"));
            return true;
        }

        if (!player.hasPermission("flycontroller.mfly")) {
            playerManager.sendMessage(player, configManager.getMessage("no-permission"));
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
                    playerManager.sendMessage(player, configManager.getMessage("mfly-deposit-usage"));
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[1]);
                    if (amount <= 0) {
                        playerManager.sendMessage(player, configManager.getMessage("amount-must-be-positive"));
                        return true;
                    }
                    flightManager.depositMoney(player, amount);
                } catch (NumberFormatException e) {
                    playerManager.sendMessage(player, configManager.getMessage("amount-must-be-number"));
                }
                break;

            case "activate":
                flightManager.activateFlight(player);
                break;

            case "continue":
                flightManager.continueFlight(player);
                break;

            case "reload":
                if (sender.hasPermission("flycontroller.admin")) {
                    plugin.reloadConfiguration();
                    playerManager.sendMessage(sender, configManager.getMessage("config-reloaded"));
                } else {
                    playerManager.sendMessage(sender, configManager.getMessage("no-permission"));
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
        int currentLevel = flightManager.calculateFlightLevel(data.getBalance());

        // Получаем сохраненное время
        Long pausedTime = flightManager.getPausedFlightTime(player.getUniqueId());
        if (pausedTime == null || pausedTime <= 0) {
            pausedTime = data.getPausedTime();
        }

        String activeTime = TimeFormatter.formatTime(flightManager.getRemainingFlightTime(player), configManager);
        String pausedTimeStr = TimeFormatter.formatTime(pausedTime, configManager);
        String cooldownTimeStr = TimeFormatter.formatTime(data.getCooldownEnd() - System.currentTimeMillis(), configManager);

        StringBuilder infoMessage = new StringBuilder(configManager.getMessage("mfly-info", Map.of(
                "{balance}", data.getBalance() + currencySymbol,
                "{level}", String.valueOf(currentLevel)
        )));

        double amountForNextLevel = getAmountForNextLevel(data, plugin);
        if (amountForNextLevel > 0) {
            infoMessage.append("\n").append(configManager.getMessage("mfly-info-next-level", Map.of("{amount}", amountForNextLevel + currencySymbol)));
        } else if (currentLevel >= flightManager.getMaxFlightLevel()) {
            infoMessage.append("\n").append(configManager.getMessage("mfly-info-max-level"));
        }

        infoMessage.append("\n").append(configManager.getMessage("mfly-info-levels"));
        for (FlightTier tier : configManager.getFlightTiers().values()) {
            String status = data.getBalance() >= tier.getMinAmount() ? "✓" : "✗";
            String timeFormatted = TimeFormatter.formatTime(tier.getDuration() * 1000L, configManager);

            Map<String, String> tierPlaceholders = Map.of(
                    "status", status,
                    "level", String.valueOf(tier.getLevel()),
                    "time", timeFormatted,
                    "cost", String.valueOf(tier.getMinAmount()),
                    "currency", currencySymbol
            );

            String levelLine = configManager.getMessage("mfly-info-level-format", tierPlaceholders);

            if (currentLevel == tier.getLevel()) {
                levelLine = configManager.getMessage("mfly-info-level-current",
                        Map.of("level_info", levelLine));
            }

            infoMessage.append("\n").append(levelLine);
        }

        if (flightManager.getRemainingFlightTime(player) > 0) {
            infoMessage.append("\n").append(configManager.getMessage("mfly-info-active-flight",
                    Map.of("time", activeTime)));
        }

        if (pausedTime > 0) {
            infoMessage.append("\n").append(configManager.getMessage("mfly-info-paused-flight",
                    Map.of("time", pausedTimeStr)));
            infoMessage.append("\n").append(configManager.getMessage("mfly-info-paused-hint"));
        }

        if (data.getCooldownEnd() > System.currentTimeMillis()) {
            infoMessage.append("\n").append(configManager.getMessage("mfly-info-cooldown",
                    Map.of("time", cooldownTimeStr)));
        }

        infoMessage.append("\n").append(configManager.getMessage("mfly-info-speeds"));
        StringBuilder speedsInfo = new StringBuilder();
        for (Integer speed : configManager.getFlightSpeeds().keySet()) {
            String speedName = getSpeedName(speed);
            speedsInfo.append(speed).append(" (").append(speedName).append("), ");
        }
        if (!speedsInfo.isEmpty()) {
            speedsInfo.setLength(speedsInfo.length() - 2);
            infoMessage.append("\n").append(speedsInfo.toString());
        }
        infoMessage.append("\n").append(configManager.getMessage("mfly-info-speeds-hint"));

        infoMessage.append("\n").append(configManager.getMessage("mfly-info-commands"));
        if (currentLevel < flightManager.getMaxFlightLevel()) {
            infoMessage.append("\n").append(configManager.getMessage("mfly-info-help-deposit"));
        }
        infoMessage.append("\n").append(configManager.getMessage("mfly-info-help-activate"));
        if (pausedTime > 0) {
            infoMessage.append("\n").append(configManager.getMessage("mfly-info-help-continue"));
        }
        if (player.hasPermission("flycontroller.admin")) {
            infoMessage.append("\n").append(configManager.getMessage("mfly-info-help-reload"));
        }

        playerManager.sendMessage(player, infoMessage.toString());
    }

    private void showCommandHelp(Player player) {
        String helpMessage = configManager.getMessage("mfly-help");
        if (player.hasPermission("flycontroller.admin")) {
            helpMessage += "\n" + configManager.getMessage("mfly-help-reload");
        }
        playerManager.sendMessage(player, helpMessage);
    }

    private double getAmountForNextLevel(FlightData data, FlyPlugin plugin) {
        double currentBalance = data.getBalance();
        int currentLevel = flightManager.calculateFlightLevel(currentBalance);
        int nextLevel = currentLevel + 1;

        FlightTier nextTier = configManager.getFlightTiers().get(nextLevel);
        if (nextTier == null) {
            return 0;
        }

        return Math.max(0, nextTier.getMinAmount() - currentBalance);
    }

    private String getSpeedName(int speed) {
        String speedName = configManager.getMessage("flyspeed-names." + speed);
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
