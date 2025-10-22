package com.flyaway.minibosses;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class MiniBosses extends JavaPlugin {
    private ConfigManager configManager;
    private BossManager bossManager;
    private PlayerDataManager playerDataManager;
    private EconomyManager economyManager;
    private final Map<String, Long> bossCooldowns = new HashMap<>();

    // Ключи для Persistent Data
    private final NamespacedKey bossTypeKey;

    public MiniBosses() {
        this.bossTypeKey = new NamespacedKey(this, "miniboss_type");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        bossManager = new BossManager(this);
        playerDataManager = new PlayerDataManager(this);
        economyManager = new EconomyManager(this);

        getServer().getPluginManager().registerEvents(new BossListener(this), this);

        // Регистрируем CommandHandler с TabCompleter
        CommandHandler commandHandler = new CommandHandler(this);
        getCommand("minibosses").setExecutor(commandHandler);
        getCommand("minibosses").setTabCompleter(commandHandler);

        bossManager.startTasks();
        getLogger().info("MiniBosses включен!");
    }

    @Override
    public void onDisable() {
        bossManager.stopAllTasks();
        bossCooldowns.clear();
    }

    public void reloadPlugin() {
        getBossManager().stopAllTasks();
        getConfigManager().reload();
        getBossManager().startTasks();
        getPlayerDataManager().setupPlayerData();
        getEconomyManager().setupEconomy();
        getLogger().info("Конфигурация MiniBosses перезагружена!");
    }

    // Методы для работы с Persistent Data
    public boolean isBoss(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(bossTypeKey, PersistentDataType.STRING);
    }

    public String getBossType(LivingEntity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return pdc.get(bossTypeKey, PersistentDataType.STRING);
    }

    public void markAsBoss(LivingEntity entity, String bossType) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(bossTypeKey, PersistentDataType.STRING, bossType);
    }

    public void unmarkAsBoss(LivingEntity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.remove(bossTypeKey);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public BossManager getBossManager() {
        return bossManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public Map<String, Long> getBossCooldowns() {
        return bossCooldowns;
    }

    public NamespacedKey getBossTypeKey() {
        return bossTypeKey;
    }
}
