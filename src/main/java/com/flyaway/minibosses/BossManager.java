package com.flyaway.minibosses;

import com.flyaway.minibosses.boss.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class BossManager {
    private final MiniBosses plugin;
    private BukkitTask autoSpawnTask;
    private final Random random = new Random();

    private static boolean wgInitialized = false;
    private static boolean wgAvailable = false;

    // Кэшированные классы и методы WorldGuard
    private static Class<?> wgClass, bukkitAdapterClass, flagsClass, regionAssociableClass, flagClass, stateEnumClass;
    private static Method getInstanceMethod, getPlatformMethod, getRegionContainerMethod, createQueryMethod,
            adaptMethod, getApplicableRegionsMethod, queryValueMethod;
    private static Field mobSpawningFlagField;
    private static Object denyStateValue;

    public BossManager(MiniBosses plugin) {
        this.plugin = plugin;
    }

    private void initWorldGuardReflection() {
        if (wgInitialized) return;
        wgInitialized = true;

        try {
            wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            regionAssociableClass = Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable");
            flagClass = Class.forName("com.sk89q.worldguard.protection.flags.Flag");
            stateEnumClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag$State");

            getInstanceMethod = wgClass.getMethod("getInstance");
            getPlatformMethod = wgClass.getMethod("getPlatform");
            getRegionContainerMethod = getPlatformMethod.getReturnType().getMethod("getRegionContainer");
            createQueryMethod = getRegionContainerMethod.getReturnType().getMethod("createQuery");
            adaptMethod = bukkitAdapterClass.getMethod("adapt", Location.class);

            // Для получения applicable regions
            Class<?> regionQueryClass = createQueryMethod.getReturnType();
            Class<?> weLocationClass = adaptMethod.getReturnType();
            getApplicableRegionsMethod = regionQueryClass.getMethod("getApplicableRegions", weLocationClass);

            // Для проверки флага MOB_SPAWNING
            mobSpawningFlagField = flagsClass.getField("MOB_SPAWNING");

            // Для вызова queryValue
            Class<?> regionResultSetClass = getApplicableRegionsMethod.getReturnType();
            queryValueMethod = regionResultSetClass.getMethod("queryValue", regionAssociableClass, flagClass);

            // Значение StateFlag.State.DENY
            denyStateValue = Enum.valueOf((Class<Enum>) stateEnumClass.asSubclass(Enum.class), "DENY");

            wgAvailable = true;

        } catch (Throwable e) {
            wgAvailable = false;
            plugin.getLogger().warning("WorldGuard не найден или структура изменилась, защита отключена: " + e.getMessage());
        }
    }

    // Методы спавна боссов
    public void spawnEnderLord(Location location) {
        spawnEnderLord(location, true);
    }

    public void spawnEnderLord(Location location, boolean needSetCooldown) {
        new EnderLordBoss(plugin, location);
        if (needSetCooldown) plugin.getBossCooldowns().put("ender", System.currentTimeMillis());
        plugin.getLogger().info("Создан Повелитель Энда в " + location);
    }

    public void spawnNetherInferno(Location location) {
        spawnNetherInferno(location, true);
    }

    public void spawnNetherInferno(Location location, boolean needSetCooldown) {
        new NetherInfernoBoss(plugin, location);
        if (needSetCooldown) plugin.getBossCooldowns().put("nether", System.currentTimeMillis());
        plugin.getLogger().info("Создан Инфернальный Ифрит в " + location);
    }

    public void spawnForestGuardian(Location location) {
        spawnForestGuardian(location, true);
    }

    public void spawnForestGuardian(Location location, boolean needSetCooldown) {
        new ForestGuardianBoss(plugin, location);
        if (needSetCooldown) plugin.getBossCooldowns().put("forest", System.currentTimeMillis());
        plugin.getLogger().info("Создан Защитник Леса в " + location);
    }

    public void spawnDesertSandlord(Location location) {
        spawnDesertSandlord(location, true);
    }

    public void spawnDesertSandlord(Location location, boolean needSetCooldown) {
        new DesertSandlordBoss(plugin, location);
        if (needSetCooldown) plugin.getBossCooldowns().put("desert", System.currentTimeMillis());
        plugin.getLogger().info("Создан Повелитель Песков в " + location);
    }

    // Умный спавн с проверкой условий
    public boolean trySpawnBossNearPlayer(Player player, String bossType) {
        Location playerLoc = player.getLocation();

        if (player.hasPermission("minibosses.ignore")) {
            return false;
        }

        if (isInsideProtectedRegion(playerLoc)) {
            return false;
        }

        if (hasActiveBossNearby(playerLoc)) {
            return false;
        }

        if (!isCooldownExpired(bossType)) {
            return false;
        }

        Location spawnLocation = findSpawnLocation(playerLoc);
        if (spawnLocation == null) {
            return false;
        }

        switch (bossType.toLowerCase()) {
            case "ender":
                if (canSpawnEnderBoss(playerLoc)) {
                    if (!plugin.getConfigManager().isEnderBossEnabled()) {
                        plugin.getLogger().warning("Попытка спавна отключенного босса Энда");
                        return false;
                    }
                    spawnEnderLord(spawnLocation);
                    return true;
                }
                break;
            case "nether":
                if (canSpawnNetherBoss(playerLoc)) {
                    if (!plugin.getConfigManager().isNetherBossEnabled()) {
                        plugin.getLogger().warning("Попытка спавна отключенного босса Ада");
                        return false;
                    }
                    spawnNetherInferno(spawnLocation);
                    return true;
                }
                break;
            case "forest":
                if (canSpawnForestBoss(playerLoc)) {
                    if (!plugin.getConfigManager().isForestBossEnabled()) {
                        plugin.getLogger().warning("Попытка спавна отключенного босса Леса");
                        return false;
                    }
                    spawnForestGuardian(spawnLocation);
                    return true;
                }
                break;
            case "desert":
                if (canSpawnDesertBoss(playerLoc)) {
                    if (!plugin.getConfigManager().isDesertBossEnabled()) {
                        plugin.getLogger().warning("Попытка спавна отключенного босса Пустыни");
                        return false;
                    }
                    spawnDesertSandlord(spawnLocation);
                    return true;
                }
                break;
        }

        return false;
    }

    // Проверки условий спавна
    private boolean canSpawnEnderBoss(Location location) {
        return location.getWorld().getEnvironment() == World.Environment.THE_END &&
               canSpawnMobsAtLocation(location);
    }

    private boolean canSpawnNetherBoss(Location location) {
        if (location.getWorld().getEnvironment() != World.Environment.NETHER) return false;
        return isInNetherFortress(location) && canSpawnMobsAtLocation(location);
    }

    private boolean canSpawnForestBoss(Location location) {
        if (location.getWorld().getEnvironment() != World.Environment.NORMAL) return false;
        return isInForest(location) && canSpawnMobsAtLocation(location);
    }

    private boolean canSpawnDesertBoss(Location location) {
        if (location.getWorld().getEnvironment() != World.Environment.NORMAL) return false;
        return isInDesert(location) && canSpawnMobsAtLocation(location);
    }

    // Проверки биомов и локаций
    private boolean isInNetherFortress(Location location) {
        Material blockType = location.getBlock().getType();
        return blockType == Material.NETHER_BRICKS ||
               blockType == Material.NETHER_BRICK_FENCE ||
               blockType.toString().contains("NETHER_BRICK");
    }

    private boolean isInForest(Location location) {
        Biome biome = location.getBlock().getBiome();
        return biome.toString().contains("FOREST") ||
               biome == Biome.TAIGA ||
               biome == Biome.BIRCH_FOREST ||
               biome == Biome.DARK_FOREST;
    }

    private boolean isInDesert(Location location) {
        Biome biome = location.getBlock().getBiome();
        return biome == Biome.DESERT ||
               biome == Biome.BADLANDS ||
               biome == Biome.ERODED_BADLANDS ||
               biome == Biome.WOODED_BADLANDS;
    }

    private boolean hasActiveBossNearby(Location location) {
        // Проверяем живых мобов с меткой мини-босса в радиусе
        int radius = plugin.getConfigManager().getNoBossSpawnRadius();
        return location.getWorld().getNearbyEntities(location, radius, radius, radius).stream()
                .anyMatch(entity -> entity instanceof LivingEntity && plugin.isBoss((LivingEntity) entity));
    }

    private boolean isCooldownExpired(String bossType) {
        Long lastSpawn = plugin.getBossCooldowns().get(bossType);
        if (lastSpawn == null) return true;

        long currentTime = System.currentTimeMillis();
        long cooldownMs = getCooldownForBoss(bossType) * 1000L;

        return (currentTime - lastSpawn) >= cooldownMs;
    }

    private long getCooldownForBoss(String bossType) {
        switch (bossType) {
            case "ender": return plugin.getConfigManager().getEnderBossCooldown();
            case "nether": return plugin.getConfigManager().getNetherBossCooldown();
            case "forest": return plugin.getConfigManager().getForestBossCooldown();
            case "desert": return plugin.getConfigManager().getDesertBossCooldown();
            default: return 300;
        }
    }

    public Location findSpawnLocation(Location center) {
        for (int i = 0; i < 10; i++) {
            int x = center.getBlockX() + random.nextInt(30) - 15;
            int z = center.getBlockZ() + random.nextInt(30) - 15;
            int y = center.getWorld().getHighestBlockYAt(x, z);

            Location testLoc = new Location(center.getWorld(), x + 0.5, y + 1, z + 0.5);

            if (canSpawnMobsAtLocation(testLoc)) {
                return testLoc;
            }
        }
        return null;
    }

    private boolean canSpawnMobsAtLocation(Location location) {
        // Базовая проверка безопасности локации
        if (isInsideProtectedRegion(location)) return false;
        Block blockBelow = location.clone().subtract(0, 1, 0).getBlock();
        Block blockAt = location.getBlock();

        return blockBelow.getType().isOccluding() &&
               blockBelow.getType().isCollidable() &&
               !blockBelow.isLiquid() &&
               blockAt.isPassable();
    }

    public boolean isInsideProtectedRegion(Location location) {
        if (!isWorldGuardEnabled()) {
            return false;
        }

        if (!wgInitialized) {
            initWorldGuardReflection();
        }

        if (!wgAvailable) {
            return false;
        }

        try {
            Object wgInstance = getInstanceMethod.invoke(null);
            Object platform = getPlatformMethod.invoke(wgInstance);
            Object regionContainer = getRegionContainerMethod.invoke(platform);
            Object query = createQueryMethod.invoke(regionContainer);
            Object weLocation = adaptMethod.invoke(null, location);
            Object regionSet = getApplicableRegionsMethod.invoke(query, weLocation);

            // Получаем список регионов, в которых находится точка
            Collection<?> applicableRegions = (Collection<?>) regionSet.getClass().getMethod("getRegions").invoke(regionSet);
            return !applicableRegions.isEmpty();

        } catch (Throwable e) {
            plugin.getLogger().warning("Ошибка при проверке приватной территории (WorldGuard): " + e.getMessage());
            return false;
        }
    }

    private boolean isWorldGuardEnabled() {
        return Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    }

    // Задачи
    public void startTasks() {
        startAutoSpawner();
    }

    public void stopAllTasks() {
        if (autoSpawnTask != null && !autoSpawnTask.isCancelled()) {
            autoSpawnTask.cancel();
        }
    }

    private void startAutoSpawner() {
        if (autoSpawnTask != null && !autoSpawnTask.isCancelled()) {
            autoSpawnTask.cancel();
        }

        autoSpawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getConfigManager().isAutoSpawnEnabled()) return;

                for (World world : Bukkit.getWorlds()) {
                    for (Player player : world.getPlayers()) {
                        tryAutoSpawnForPlayer(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, plugin.getConfigManager().getSpawnCheckInterval() * 20L);
    }

    private void tryAutoSpawnForPlayer(Player player) {
        Location playerLoc = player.getLocation();

        // Проверяем каждого босса с его шансом
        if (plugin.getConfigManager().isEnderBossEnabled() &&
            canSpawnEnderBoss(playerLoc) &&
            random.nextDouble() * 100 <= plugin.getConfigManager().getEnderBossChance()) {
            trySpawnBossNearPlayer(player, "ender");
        }

        if (plugin.getConfigManager().isNetherBossEnabled() &&
            canSpawnNetherBoss(playerLoc) &&
            random.nextDouble() * 100 <= plugin.getConfigManager().getNetherBossChance()) {
            trySpawnBossNearPlayer(player, "nether");
        }

        if (plugin.getConfigManager().isForestBossEnabled() &&
            canSpawnForestBoss(playerLoc) &&
            random.nextDouble() * 100 <= plugin.getConfigManager().getForestBossChance()) {
            trySpawnBossNearPlayer(player, "forest");
        }

        if (plugin.getConfigManager().isDesertBossEnabled() &&
            canSpawnDesertBoss(playerLoc) &&
            random.nextDouble() * 100 <= plugin.getConfigManager().getDesertBossChance()) {
            trySpawnBossNearPlayer(player, "desert");
        }
    }

    // Утилиты
    public int getActiveBossCount() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (plugin.isBoss(entity)) {
                    count++;
                }
            }
        }
        return count;
    }

    public void cleanupAllBosses() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (plugin.isBoss(entity)) {
                    entity.remove();
                    plugin.unmarkAsBoss(entity);
                }
            }
        }
        plugin.getBossCooldowns().clear();
    }
}
