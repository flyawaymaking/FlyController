package com.flyaway.flycontroller;

import org.bukkit.entity.Player;
import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.Currency;

import java.util.Map;

public class EconomyManager {
    private final FlyPlugin plugin;
    private final Currency currency;
    private final ConfigManager configManager;
    private String currencyName;

    public EconomyManager(FlyPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        currencyName = configManager.getCurrency();
        this.currency = CoinsEngineAPI.getCurrency(currencyName);

        if (this.currency == null) {
            plugin.getLogger().warning("Валюта '" + currencyName + "' не найдена в CoinsEngine!");
        }
    }

    public String getCurrencyName() {
        return currency != null ? currency.getName() : plugin.getConfigManager().getCurrency();
    }

    public boolean hasEnoughMoney(Player player, double amount) {
        if (currency == null) {
            plugin.sendMessage(player, configManager.getMessage("economy-unavailable", Map.of("currency", currencyName)));
            return false;
        }

        try {
            double balance = CoinsEngineAPI.getBalance(player, currency);
            boolean hasMoney = balance >= amount;

            if (!hasMoney) {
                plugin.sendMessage(player, configManager.getMessage("economy-not-enough", Map.of(
                        "need", amount + getCurrencySymbol(),
                        "balance", balance + getCurrencySymbol())
                ));
            }

            return hasMoney;
        } catch (Exception e) {
            plugin.sendMessage(player, configManager.getMessage("economy-error"));
            plugin.getLogger().warning("Ошибка при проверке баланса: " + e.getMessage());
            return false;
        }
    }

    public boolean withdrawMoney(Player player, double amount) {
        if (currency == null) {
            plugin.sendMessage(player, configManager.getMessage("economy-unavailable", Map.of("currency", currencyName)));
            return false;
        }

        try {
            if (!hasEnoughMoney(player, amount)) {
                return false;
            }

            CoinsEngineAPI.removeBalance(player, currency, amount);
            return true;
        } catch (Exception e) {
            plugin.sendMessage(player, configManager.getMessage("economy-error"));
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
