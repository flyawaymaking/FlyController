package com.flyaway.flycontroller.managers;

import com.flyaway.flycontroller.FlyPlugin;
import org.bukkit.entity.Player;
import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.Currency;

import java.util.Map;

public class EconomyManager {
    private final FlyPlugin plugin;
    private Currency currency;
    private String currencyName;
    private final ConfigManager configManager;
    private final PlayerManager playerManager;

    public EconomyManager(FlyPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.playerManager = plugin.getPlayerManager();
        loadCurrency();
    }

    public void loadCurrency() {
        currencyName = configManager.getCurrency();
        this.currency = CoinsEngineAPI.getCurrency(currencyName);

        if (currency == null) {
            plugin.getLogger().warning("Валюта '" + currencyName + "' не найдена в CoinsEngine!");
        }
    }

    public void reload() {
        loadCurrency();
    }

    public String getCurrencyName() {
        return currency != null ? currency.getName() : configManager.getCurrency();
    }

    public boolean hasEnoughMoney(Player player, double amount) {
        if (currency == null) {
            playerManager.sendMessage(player, configManager.getMessage("economy-unavailable", Map.of("currency", currencyName)));
            return false;
        }

        try {
            double balance = CoinsEngineAPI.getBalance(player, currency);
            boolean hasMoney = balance >= amount;

            if (!hasMoney) {
                playerManager.sendMessage(player, configManager.getMessage("economy-not-enough", Map.of(
                        "need", amount + getCurrencySymbol(),
                        "balance", balance + getCurrencySymbol())
                ));
            }

            return hasMoney;
        } catch (Exception e) {
            playerManager.sendMessage(player, configManager.getMessage("economy-error"));
            plugin.getLogger().warning("Ошибка при проверке баланса: " + e.getMessage());
            return false;
        }
    }

    public boolean withdrawMoney(Player player, double amount) {
        if (currency == null) {
            playerManager.sendMessage(player, configManager.getMessage("economy-unavailable", Map.of("currency", currencyName)));
            return false;
        }

        try {
            if (!hasEnoughMoney(player, amount)) {
                return false;
            }

            CoinsEngineAPI.removeBalance(player, currency, amount);
            return true;
        } catch (Exception e) {
            playerManager.sendMessage(player, configManager.getMessage("economy-error"));
            plugin.getLogger().warning("Ошибка при списании денег: " + e.getMessage());
            return false;
        }
    }

    public String getCurrencySymbol() {
        return currency != null ? currency.getSymbol() : "";
    }

    public boolean isEconomyAvailable() {
        return currency != null;
    }
}
