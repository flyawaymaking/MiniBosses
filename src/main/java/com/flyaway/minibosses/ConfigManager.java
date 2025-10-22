package com.flyaway.minibosses;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.List;

public class ConfigManager {
    private final MiniBosses plugin;
    private FileConfiguration config;

    public ConfigManager(MiniBosses plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    // ========== ОБЩИЕ НАСТРОЙКИ ==========
    public boolean isAutoSpawnEnabled() { return config.getBoolean("auto-spawn.enabled", true); }
    public int getSpawnCheckInterval() { return config.getInt("auto-spawn.check-interval", 300); }
    public int getNoBossSpawnRadius() { return config.getInt("auto-spawn.no-boss-spawn-radius", 100); }
    public String getBuyCurrency() { return config.getString("buy-currency", "money"); }
    public long getBuyCooldown() {return config.getLong("buy-cooldown", 24 * 60 * 60 * 1000); } // 24 часа в миллисекундах

    // ========== БОСС ЭНДА ==========
    public boolean isEnderBossEnabled() { return config.getBoolean("ender-boss.enabled", true); }
    public double getEnderBossChance() { return config.getDouble("ender-boss.spawn-chance", 15.0); }
    public int getEnderBossCooldown() { return config.getInt("ender-boss.cooldown", 1200); }
    public double getEnderBossHealth() { return config.getDouble("ender-boss.health", 300.0); }
    public double getEnderAbilityChance() { return config.getDouble("ender-boss.ability-chance", 0.3); }
    public List<String> getEnderDeathCommands() { return config.getStringList("ender-boss.death-commands"); }

    // Помощники Энда
    public int getEnderHelperCount() { return config.getInt("ender-boss.helper-count", 4); }
    public double getEnderHelperHealth() { return config.getDouble("ender-boss.helper-health", 40.0); }

    // Способности Энда
    public int getEnderAbilityRadius() { return config.getInt("ender-boss.ability-radius", 15); }
    public int getEnderBlindnessDuration() { return config.getInt("ender-boss.blindness-duration", 60); }
    public int getEnderTeleportRadius() { return config.getInt("ender-boss.teleport-radius", 12); }

    // Награды Энда
    public int getEnderRewardEnderPearls() { return config.getInt("ender-boss.reward-ender-pearls", 8); }
    public int getEnderRewardEnderEyes() { return config.getInt("ender-boss.reward-ender-eyes", 2); }
    public int getEnderRewardChorusFruit() { return config.getInt("ender-boss.reward-chorus-fruit", 5); }
    public int getEnderRewardShulkerShells() { return config.getInt("ender-boss.reward-shulker-shells", 1); }
    public int getEnderRewardExp() { return config.getInt("ender-boss.reward-exp", 150); }

    // ========== БОСС АДА ==========
    public boolean isNetherBossEnabled() { return config.getBoolean("nether-boss.enabled", true); }
    public double getNetherBossChance() { return config.getDouble("nether-boss.spawn-chance", 20.0); }
    public int getNetherBossCooldown() { return config.getInt("nether-boss.cooldown", 900); }
    public double getNetherBossHealth() { return config.getDouble("nether-boss.health", 250.0); }
    public double getNetherAbilityChance() { return config.getDouble("nether-boss.ability-chance", 0.35); }
    public List<String> getNetherDeathCommands() { return config.getStringList("nether-boss.death-commands"); }

    // Помощники Ада
    public int getNetherHelperCount() { return config.getInt("nether-boss.helper-count", 3); }
    public int getNetherHelperSize() { return config.getInt("nether-boss.helper-size", 3); }

    // Способности Ада
    public int getNetherAbilityRadius() { return config.getInt("nether-boss.ability-radius", 10); }
    public int getNetherFireDuration() { return config.getInt("nether-boss.fire-duration", 100); }
    public double getNetherFireDamage() { return config.getDouble("nether-boss.fire-damage", 5.0); }
    public int getNetherFireballRadius() { return config.getInt("nether-boss.fireball-radius", 15); }
    public float getNetherFireballPower() { return (float) config.getDouble("nether-boss.fireball-power", 1.5); }
    public int getNetherTeleportRadius() { return config.getInt("nether-boss.teleport-radius", 20); }

    // Награды Ада
    public int getNetherRewardBlazeRods() { return config.getInt("nether-boss.reward-blaze-rods", 12); }
    public int getNetherRewardNetherBricks() { return config.getInt("nether-boss.reward-nether-bricks", 16); }
    public int getNetherRewardGoldNuggets() { return config.getInt("nether-boss.reward-gold-nuggets", 8); }
    public int getNetherRewardExp() { return config.getInt("nether-boss.reward-exp", 120); }

    // ========== БОСС ЛЕСА ==========
    public boolean isForestBossEnabled() { return config.getBoolean("forest-boss.enabled", true); }
    public double getForestBossChance() { return config.getDouble("forest-boss.spawn-chance", 25.0); }
    public int getForestBossCooldown() { return config.getInt("forest-boss.cooldown", 600); }
    public double getForestBossHealth() { return config.getDouble("forest-boss.health", 400.0); }
    public double getForestAbilityChance() { return config.getDouble("forest-boss.ability-chance", 0.25); }
    public List<String> getForestDeathCommands() { return config.getStringList("forest-boss.death-commands"); }

    // Помощники Леса
    public int getForestHelperCount() { return config.getInt("forest-boss.helper-count", 5); }
    public double getForestHelperHealth() { return config.getDouble("forest-boss.helper-health", 30.0); }

    // Способности Леса
    public int getForestAbilityRadius() { return config.getInt("forest-boss.ability-radius", 12); }
    public int getForestSlownessDuration() { return config.getInt("forest-boss.slowness-duration", 80); }
    public int getForestSlownessLevel() { return config.getInt("forest-boss.slowness-level", 2); }
    public int getForestWeaknessDuration() { return config.getInt("forest-boss.weakness-duration", 60); }
    public double getForestKnockbackPower() { return config.getDouble("forest-boss.knockback-power", 1.2); }
    public int getForestPullRadius() { return config.getInt("forest-boss.pull-radius", 10); }
    public double getForestPullPower() { return config.getDouble("forest-boss.pull-power", 1.5); }
    public int getForestPullWeaknessDuration() { return config.getInt("forest-boss.pull-weakness-duration", 60); }

    // Награды Леса
    public int getForestRewardIron() { return config.getInt("forest-boss.reward-iron", 16); }
    public int getForestRewardApples() { return config.getInt("forest-boss.reward-apples", 8); }
    public int getForestRewardLogs() { return config.getInt("forest-boss.reward-logs", 12); }
    public int getForestRewardExp() { return config.getInt("forest-boss.reward-exp", 200); }

    // ========== БОСС ПУСТЫНИ ==========
    public boolean isDesertBossEnabled() { return config.getBoolean("desert-boss.enabled", true); }
    public double getDesertBossChance() { return config.getDouble("desert-boss.spawn-chance", 18.0); }
    public int getDesertBossCooldown() { return config.getInt("desert-boss.cooldown", 800); }
    public double getDesertBossHealth() { return config.getDouble("desert-boss.health", 280.0); }
    public double getDesertAbilityChance() { return config.getDouble("desert-boss.ability-chance", 0.32); }
    public List<String> getDesertDeathCommands() { return config.getStringList("desert-boss.death-commands"); }

    // Помощники Пустыни
    public int getDesertHelperCount() { return config.getInt("desert-boss.helper-count", 4); }
    public double getDesertHelperHealth() { return config.getDouble("desert-boss.helper-health", 35.0); }

    // Способности Пустыни
    public int getDesertAbilityRadius() { return config.getInt("desert-boss.ability-radius", 12); }
    public int getDesertSandstormDuration() { return config.getInt("desert-boss.sandstorm-duration", 80); }
    public int getDesertHungerDuration() { return config.getInt("desert-boss.hunger-duration", 100); }
    public int getDesertHungerLevel() { return config.getInt("desert-boss.hunger-level", 1); }
    public double getDesertSandblastDamage() { return config.getDouble("desert-boss.sandblast-damage", 6.0); }
    public double getDesertArrowDamage() { return config.getDouble("desert-boss.arrow-damage", 4.0); }
    public int getDesertArrowSlownessDuration() { return config.getInt("desert-boss.arrow-slowness-duration", 60); }
    public int getDesertArrowSlownessLevel() { return config.getInt("desert-boss.arrow-slowness-level", 1); }

    // Награды Пустыни
    public int getDesertRewardGold() { return config.getInt("desert-boss.reward-gold", 12); }
    public int getDesertRewardCactus() { return config.getInt("desert-boss.reward-cactus", 8); }
    public int getDesertRewardSandstone() { return config.getInt("desert-boss.reward-sandstone", 16); }
    public int getDesertRewardRabbitHide() { return config.getInt("desert-boss.reward-rabbit-hide", 6); }
    public int getDesertRewardExp() { return config.getInt("desert-boss.reward-exp", 140); }

    // ========== УТИЛИТНЫЕ МЕТОДЫ ==========
    public double getAbilityChance(String bossType) {
        switch (bossType) {
            case "ender": return getEnderAbilityChance();
            case "nether": return getNetherAbilityChance();
            case "forest": return getForestAbilityChance();
            case "desert": return getDesertAbilityChance();
            default: return 0.3;
        }
    }

    public List<String> getDeathCommands(String bossType) {
        switch (bossType) {
            case "ender": return getEnderDeathCommands();
            case "nether": return getNetherDeathCommands();
            case "forest": return getForestDeathCommands();
            case "desert": return getDesertDeathCommands();
            default: return java.util.Collections.emptyList();
        }
    }

    public int getRewardExp(String bossType) {
        switch (bossType) {
            case "ender": return getEnderRewardExp();
            case "nether": return getNetherRewardExp();
            case "forest": return getForestRewardExp();
            case "desert": return getDesertRewardExp();
            default: return 100;
        }
    }

    public double getBuyPrice(String bossType) {
        switch (bossType) {
            case "ender": return config.getDouble("ender-boss.buy-price", 10000.0);
            case "nether": return config.getDouble("nether-boss.buy-price", 10000.0);
            case "forest": return config.getDouble("forest-boss.buy-price", 10000.0);
            case "desert": return config.getDouble("desert-boss.buy-price", 10000.0);
            default: return 10000.0;
        }
    }

    public boolean isBossBuyable(String bossType) {
        return config.getBoolean(bossType + "-boss.buy-enabled", true);
    }
}
