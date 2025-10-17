package com.flyaway.flycontroller;

import org.bukkit.entity.Player;
import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.Currency;

public class EconomyManager {
    private final FlyPlugin plugin;
    private Currency currency;

    public EconomyManager(FlyPlugin plugin) {
        this.plugin = plugin;
        // Получаем имя валюты из конфига
        String currencyName = plugin.getConfigManager().getCurrency();
        this.currency = CoinsEngineAPI.getCurrency(currencyName);

        if (this.currency == null) {
            plugin.getLogger().warning("Валюта '" + currencyName + "' не найдена в CoinsEngine!");
        } else {
            plugin.getLogger().info("Успешно подключена валюта: " + currency.getName());
        }
    }

    public String getCurrencyName() {
        return currency != null ? currency.getName() : plugin.getConfigManager().getCurrency();
    }

    public boolean hasEnoughMoney(Player player, double amount) {
        if (currency == null) {
            player.sendMessage("§cСистема экономики недоступна! Валюта 'money' не найдена.");
            return false;
        }

        try {
            double balance = CoinsEngineAPI.getBalance(player, currency);
            boolean hasMoney = balance >= amount;

            if (!hasMoney) {
                player.sendMessage("§cНедостаточно средств! Нужно: " + amount + getCurrencySymbol() + ", у вас: " + balance + getCurrencySymbol());
            }

            return hasMoney;
        } catch (Exception e) {
            player.sendMessage("§cОшибка при проверке баланса!");
            plugin.getLogger().warning("Ошибка при проверке баланса: " + e.getMessage());
            return false;
        }
    }

    public boolean withdrawMoney(Player player, double amount) {
        if (currency == null) {
            player.sendMessage("§cСистема экономики недоступна! Валюта 'money' не найдена.");
            return false;
        }

        try {
            // Проверяем, что у игрока достаточно денег
            if (!hasEnoughMoney(player, amount)) {
                return false;
            }

            // Списание денег через CoinsEngine
            CoinsEngineAPI.removeBalance(player, currency, amount);
            return true;
        } catch (Exception e) {
            player.sendMessage("§cОшибка при списании денег!");
            plugin.getLogger().warning("Ошибка при списании денег: " + e.getMessage());
            return false;
        }
    }

    public boolean depositMoney(Player player, double amount) {
        if (currency == null) {
            player.sendMessage("§cСистема экономики недоступна! Валюта 'money' не найдена.");
            return false;
        }

        try {
            // Начисление денег через CoinsEngine
            CoinsEngineAPI.addBalance(player, currency, amount);
            return true;
        } catch (Exception e) {
            player.sendMessage("§cОшибка при начислении денег!");
            plugin.getLogger().warning("Ошибка при начислении денег: " + e.getMessage());
            return false;
        }
    }

    public double getBalance(Player player) {
        if (currency == null) {
            return 0;
        }

        try {
            return CoinsEngineAPI.getBalance(player, currency);
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при получении баланса: " + e.getMessage());
            return 0;
        }
    }

    public String getCurrencySymbol() {
        return currency != null ? currency.getSymbol() : "";
    }

    public Currency getCurrency() {
        return currency;
    }

    public boolean isEconomyAvailable() {
        return currency != null;
    }
}
