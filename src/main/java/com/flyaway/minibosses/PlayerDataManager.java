package com.flyaway.minibosses;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class PlayerDataManager {
    private final MiniBosses plugin;
    private File dataFile;
    private FileConfiguration playerData;

    public PlayerDataManager(MiniBosses plugin) {
        this.plugin = plugin;
        setupPlayerData();
    }

    public void setupPlayerData() {
        dataFile = new File(plugin.getDataFolder(), "player_data.yml");

        // Создаем файл если его нет, но НЕ используем saveResource()
        if (!dataFile.exists()) {
            try {
                // Создаем папку плагина если её нет
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                // Создаем пустой файл
                dataFile.createNewFile();
                plugin.getLogger().info("Создан новый файл player_data.yml");
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось создать player_data.yml: " + e.getMessage());
                return;
            }
        }

        playerData = YamlConfiguration.loadConfiguration(dataFile);

        // Сохраняем файл с комментариями при первом создании
        if (playerData.getKeys(false).isEmpty()) {
            playerData.options().setHeader(Arrays.asList(
                    "Данные игроков для покупки боссов",
                    "Не редактируйте вручную!",
                    "Формат: UUID_игрока.last_buy_time: timestamp")
            );
            savePlayerData();
        }
    }

    public long getLastBuyTime(Player player) {
        return playerData.getLong(player.getUniqueId() + ".last_buy_time", 0);
    }

    public void setLastBuyTime(Player player, long time) {
        playerData.set(player.getUniqueId() + ".last_buy_time", time);
        savePlayerData();
    }

    public boolean canBuyBoss(Player player) {
        long lastBuyTime = getLastBuyTime(player);
        long currentTime = System.currentTimeMillis();
        long cooldown = plugin.getConfigManager().getBuyCooldown();

        return (currentTime - lastBuyTime) >= cooldown;
    }

    public String getCooldownRemaining(Player player) {
        long lastBuyTime = getLastBuyTime(player);
        long currentTime = System.currentTimeMillis();
        long cooldown = plugin.getConfigManager().getBuyCooldown();
        long remaining = cooldown - (currentTime - lastBuyTime);

        if (remaining <= 0) return "0 секунд";

        long hours = remaining / (60 * 60 * 1000);
        long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
        long seconds = (remaining % (60 * 1000)) / 1000;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private void savePlayerData() {
        try {
            playerData.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить player_data.yml: " + e.getMessage());
        }
    }

    public void reloadPlayerData() {
        playerData = YamlConfiguration.loadConfiguration(dataFile);
    }
}
