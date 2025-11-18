package com.flyaway.flycontroller.commands;

import com.flyaway.flycontroller.FlyPlugin;
import com.flyaway.flycontroller.managers.ConfigManager;
import com.flyaway.flycontroller.managers.FlightManager;
import com.flyaway.flycontroller.managers.PlayerManager;
import com.flyaway.flycontroller.models.FlightData;
import com.flyaway.flycontroller.models.FlightTier;
import com.flyaway.flycontroller.utils.NumberFormatter;
import com.flyaway.flycontroller.utils.TimeFormatter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

        return handleCommand(player, args);
    }

    private boolean handleCommand(Player player, String[] args) {
        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info":
                showFlightInfo(player);
                break;

            case "deposit":
                return handleDeposit(player, args);

            case "activate":
                flightManager.activateFlight(player);
                break;

            case "continue":
                flightManager.continueFlight(player);
                break;

            case "reload":
                return handleReload(player);

            default:
                showCommandHelp(player);
                break;
        }

        return true;
    }

    private boolean handleDeposit(Player player, String[] args) {
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
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (sender.hasPermission("flycontroller.admin")) {
            plugin.reloadConfiguration();
            playerManager.sendMessage(sender, configManager.getMessage("config-reloaded"));
        } else {
            playerManager.sendMessage(sender, configManager.getMessage("no-permission"));
        }
        return true;
    }

    private void showFlightInfo(Player player) {
        UUID playerId = player.getUniqueId();
        FlightData data = plugin.getDataManager().loadPlayerData(playerId);
        String currencySymbol = plugin.getEconomyManager().getCurrencySymbol();
        int currentLevel = flightManager.calculateFlightLevel(data.getBalance());

        FlightTimeInfo timeInfo = getFlightTimeInfo(player, data, playerId);

        StringBuilder infoMessage = buildBaseInfo(player, data, currencySymbol, currentLevel, timeInfo);

        appendLevelsInfo(infoMessage, data, currencySymbol, currentLevel);
        appendFlightStatus(infoMessage, timeInfo, data);
        appendSpeedsInfo(infoMessage);

        playerManager.sendMessage(player, infoMessage.toString());
    }

    private static class FlightTimeInfo {
        final long remainingTime;
        final long pausedTime;
        final long cooldownTime;

        FlightTimeInfo(long remainingTime, long pausedTime, long cooldownTime) {
            this.remainingTime = remainingTime;
            this.pausedTime = pausedTime;
            this.cooldownTime = cooldownTime;
        }
    }

    private FlightTimeInfo getFlightTimeInfo(Player player, FlightData data, UUID playerId) {
        long remainingTime = flightManager.getRemainingFlightTime(player);
        long pausedTime = flightManager.getPausedFlightTime(playerId);

        if (pausedTime <= 0) {
            pausedTime = data.getPausedTime();
        }

        long cooldownTime = Math.max(0, data.getCooldownEnd() - System.currentTimeMillis());

        return new FlightTimeInfo(remainingTime, pausedTime, cooldownTime);
    }

    private StringBuilder buildBaseInfo(Player player, FlightData data, String currencySymbol, int currentLevel, FlightTimeInfo timeInfo) {
        StringBuilder infoMessage = new StringBuilder();

        infoMessage.append(configManager.getMessage("mfly-info", Map.of(
                "balance", NumberFormatter.formatWithCurrency(data.getBalance(), currencySymbol),
                "level", String.valueOf(currentLevel)
        )));

        double amountForNextLevel = getAmountForNextLevel(data);
        if (amountForNextLevel > 0) {
            infoMessage.append("\n").append(configManager.getMessage("mfly-info-next-level",
                    Map.of("amount", NumberFormatter.formatWithCurrency(amountForNextLevel, currencySymbol))));
        } else if (currentLevel >= flightManager.getMaxFlightLevel()) {
            infoMessage.append("\n").append(configManager.getMessage("mfly-info-max-level"));
        }

        return infoMessage;
    }

    private void appendLevelsInfo(StringBuilder infoMessage, FlightData data, String currencySymbol, int currentLevel) {
        infoMessage.append("\n").append(configManager.getMessage("mfly-info-levels"));

        configManager.getFlightTiers().values().stream()
                .sorted((t1, t2) -> Integer.compare(t1.getLevel(), t2.getLevel()))
                .forEach(tier -> appendTierInfo(infoMessage, data, currencySymbol, currentLevel, tier));
    }

    private void appendTierInfo(StringBuilder infoMessage, FlightData data, String currencySymbol, int currentLevel, FlightTier tier) {
        String status = data.getBalance() >= tier.getMinAmount() ?
                configManager.getMessage("mfly-info-unlocked") :
                configManager.getMessage("mfly-info-locked");

        String timeFormatted = TimeFormatter.formatTime(tier.getDuration() * 1000L, configManager);

        Map<String, String> tierPlaceholders = Map.of(
                "status", status,
                "level", String.valueOf(tier.getLevel()),
                "time", timeFormatted,
                "cost", NumberFormatter.format(tier.getMinAmount()),
                "currency", currencySymbol
        );

        String levelLine = configManager.getMessage("mfly-info-level-format", tierPlaceholders);

        if (currentLevel == tier.getLevel()) {
            levelLine = configManager.getMessage("mfly-info-level-current",
                    Map.of("level_info", levelLine));
        }

        infoMessage.append("\n").append(levelLine);
    }

    private void appendFlightStatus(StringBuilder infoMessage, FlightTimeInfo timeInfo, FlightData data) {
        if (timeInfo.remainingTime > 0) {
            String activeTime = TimeFormatter.formatTime(timeInfo.remainingTime, configManager);
            infoMessage.append("\n").append(configManager.getMessage("mfly-info-active-flight",
                    Map.of("time", activeTime)));
        }

        if (timeInfo.pausedTime > 0) {
            String pausedTimeStr = TimeFormatter.formatTime(timeInfo.pausedTime, configManager);
            infoMessage.append("\n").append(configManager.getMessage("mfly-info-paused-flight",
                    Map.of("time", pausedTimeStr)));
            infoMessage.append("\n").append(configManager.getMessage("mfly-info-paused-hint"));
        }

        if (timeInfo.cooldownTime > 0) {
            String cooldownTimeStr = TimeFormatter.formatTime(timeInfo.cooldownTime, configManager);
            infoMessage.append("\n").append(configManager.getMessage("mfly-info-cooldown",
                    Map.of("time", cooldownTimeStr)));
        }
    }

    private void appendSpeedsInfo(StringBuilder infoMessage) {
        infoMessage.append("\n").append(configManager.getMessage("mfly-info-speeds"));

        StringBuilder speedsInfo = new StringBuilder();
        configManager.getFlightSpeeds().keySet().stream()
                .sorted()
                .forEach(speed -> {
                    if (!speedsInfo.isEmpty()) {
                        speedsInfo.append(", ");
                    }
                    speedsInfo.append(speed).append(" (").append(getSpeedName(speed)).append(")");
                });

        if (!speedsInfo.isEmpty()) {
            infoMessage.append("\n").append(speedsInfo.toString());
        }
        infoMessage.append("\n").append(configManager.getMessage("mfly-info-speeds-hint"));
    }

    private void showCommandHelp(Player player) {
        String helpMessage = configManager.getMessage("mfly-help");
        if (player.hasPermission("flycontroller.admin")) {
            helpMessage += "\n" + configManager.getMessage("mfly-help-reload");
        }
        playerManager.sendMessage(player, helpMessage);
    }

    private double getAmountForNextLevel(FlightData data) {
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
            return speed + "lvl";
        }
        return speedName;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> commands = new ArrayList<>();
            commands.add("info");
            commands.add("deposit");
            commands.add("activate");
            commands.add("continue");

            if (sender.hasPermission("flycontroller.admin")) {
                commands.add("reload");
            }

            // Фильтрация по введённому тексту
            String input = args[0].toLowerCase();
            for (String cmd : commands) {
                if (cmd.startsWith(input)) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2 && "deposit".equalsIgnoreCase(args[0])) {
            completions.add("<amount>");
        }

        return completions;
    }
}
