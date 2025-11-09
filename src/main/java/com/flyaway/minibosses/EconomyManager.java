package com.flyaway.minibosses;

import org.bukkit.entity.Player;
import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.Currency;

import java.util.Locale;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public class EconomyManager {
    private final MiniBosses plugin;
    private Currency currency;
    private boolean economyEnabled = false;

    public EconomyManager(MiniBosses plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    public void setupEconomy() {
        // Проверяем, установлен ли CoinsEngine
        if (plugin.getServer().getPluginManager().getPlugin("CoinsEngine") == null) {
            plugin.getLogger().warning("CoinsEngine не найден! Экономика отключена.");
            economyEnabled = false;
            return;
        }

        try {
            // Получаем имя валюты из конфига
            String currencyName = plugin.getConfigManager().getBuyCurrency();
            this.currency = CoinsEngineAPI.getCurrency(currencyName);

            if (this.currency == null) {
                plugin.getLogger().warning("Валюта '" + currencyName + "' не найдена в CoinsEngine!");
                economyEnabled = false;
            } else {
                plugin.getLogger().info("Успешно подключена валюта: " + currency.getName());
                economyEnabled = true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при инициализации экономики: " + e.getMessage());
            economyEnabled = false;
        }
    }

    public boolean hasEnoughMoney(Player player, double amount) {
        if (!economyEnabled || currency == null) {
            player.sendMessage("§cСистема экономики недоступна!");
            return false;
        }

        try {
            double balance = CoinsEngineAPI.getBalance(player, currency);
            boolean hasMoney = balance >= amount;

            if (!hasMoney) {
                player.sendMessage("§cНедостаточно средств! Нужно: " + formatMoney(amount) + ", у вас: " + formatMoney(balance));
            }

            return hasMoney;
        } catch (Exception e) {
            player.sendMessage("§cОшибка при проверке баланса!");
            plugin.getLogger().warning("Ошибка при проверке баланса игрока " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public void addMoney(Player player, double amount) {
        if (!economyEnabled || currency == null) return;

        try {
            CoinsEngineAPI.addBalance(player, currency, amount);
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при начислении средств игроку " + player.getName() + ": " + e.getMessage());
        }
    }

    public double getBalance(Player player) {
        if (!economyEnabled || currency == null) return 0;

        try {
            return CoinsEngineAPI.getBalance(player, currency);
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при получении баланса игрока " + player.getName() + ": " + e.getMessage());
            return 0;
        }
    }

    public boolean removeMoney(Player player, double amount) {
        if (!economyEnabled || currency == null) return false;

        // Проверяем, достаточно ли денег перед списанием
        if (!hasEnoughMoney(player, amount)) return false;

        try {
            CoinsEngineAPI.removeBalance(player, currency, amount);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при списании средств с игрока " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public String formatMoney(double amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');  // Пробел как разделитель тысяч
        symbols.setDecimalSeparator('.');   // Точка как разделитель дробной части

        DecimalFormat formatter = new DecimalFormat("#,##0.00", symbols);
        return formatter.format(amount) + currency.getSymbol();
    }

    public boolean isEconomyAvailable() {
        return economyEnabled && currency != null;
    }

    public void reload() {
        setupEconomy();
    }
}
