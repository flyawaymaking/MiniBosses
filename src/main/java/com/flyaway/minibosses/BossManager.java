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
import java.lang.reflect.Method;

public class BossManager {
    private final MiniBosses plugin;
    private BukkitTask autoSpawnTask;
    private final Random random = new Random();

    private static boolean wgInitialized = false;
    private static boolean wgAvailable = false;

    private static Method getInstanceMethod, getPlatformMethod, getRegionContainerMethod, createQueryMethod,
            adaptMethod, getApplicableRegionsMethod;
    private static Object denyStateValue;

    public BossManager(MiniBosses plugin) {
        this.plugin = plugin;
    }

    private void initWorldGuardReflection() {
        if (wgInitialized) return;
        wgInitialized = true;

        try {
            // Кэшированные классы и методы WorldGuard
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");

            getInstanceMethod = wgClass.getMethod("getInstance");
            getPlatformMethod = wgClass.getMethod("getPlatform");
            getRegionContainerMethod = getPlatformMethod.getReturnType().getMethod("getRegionContainer");
            createQueryMethod = getRegionContainerMethod.getReturnType().getMethod("createQuery");
            adaptMethod = bukkitAdapterClass.getMethod("adapt", Location.class);

            Class<?> regionQueryClass = createQueryMethod.getReturnType();
            Class<?> weLocationClass = adaptMethod.getReturnType();
            getApplicableRegionsMethod = regionQueryClass.getMethod("getApplicableRegions", weLocationClass);

            wgAvailable = true;

        } catch (Throwable e) {
            wgAvailable = false;
            plugin.getLogger().warning("WorldGuard не найден или структура изменилась, защита отключена: " + e.getMessage());
        }
    }

    public void spawnEnderLord(Location location, boolean needSetCooldown) {
        new EnderLordBoss(plugin, location);
        if (needSetCooldown) plugin.getBossCooldowns().put("ender", System.currentTimeMillis());
        plugin.getLogger().info("Создан Повелитель Энда в " + location);
    }

    public void spawnNetherInferno(Location location, boolean needSetCooldown) {
        new NetherInfernoBoss(plugin, location);
        if (needSetCooldown) plugin.getBossCooldowns().put("nether", System.currentTimeMillis());
        plugin.getLogger().info("Создан Инфернальный Ифрит в " + location);
    }

    public void spawnForestGuardian(Location location, boolean needSetCooldown) {
        new ForestGuardianBoss(plugin, location);
        if (needSetCooldown) plugin.getBossCooldowns().put("forest", System.currentTimeMillis());
        plugin.getLogger().info("Создан Защитник Леса в " + location);
    }

    public void spawnDesertSandlord(Location location, boolean needSetCooldown) {
        new DesertSandlordBoss(plugin, location);
        if (needSetCooldown) plugin.getBossCooldowns().put("desert", System.currentTimeMillis());
        plugin.getLogger().info("Создан Повелитель Песков в " + location);
    }

    public void trySpawnBossNearPlayer(Player player, String bossType) {
        Location playerLoc = player.getLocation();

        if (player.hasPermission("minibosses.ignore")) return;

        if (isInsideProtectedRegion(playerLoc)) return;

        if (hasActiveBossNearby(playerLoc)) return;

        if (!isCooldownExpired(bossType)) return;

        Location spawnLocation = findSpawnLocation(playerLoc);
        if (spawnLocation == null) return;

        switch (bossType.toLowerCase()) {
            case "ender":
                if (canSpawnEnderBoss(playerLoc)) {
                    if (!plugin.getConfigManager().isEnderBossEnabled()) {
                        plugin.getLogger().warning("Попытка спавна отключенного босса Энда");
                        return;
                    }
                    spawnEnderLord(spawnLocation, true);
                    return;
                }
                break;
            case "nether":
                if (canSpawnNetherBoss(playerLoc)) {
                    if (!plugin.getConfigManager().isNetherBossEnabled()) {
                        plugin.getLogger().warning("Попытка спавна отключенного босса Ада");
                        return;
                    }
                    spawnNetherInferno(spawnLocation, true);
                    return;
                }
                break;
            case "forest":
                if (canSpawnForestBoss(playerLoc)) {
                    if (!plugin.getConfigManager().isForestBossEnabled()) {
                        plugin.getLogger().warning("Попытка спавна отключенного босса Леса");
                        return;
                    }
                    spawnForestGuardian(spawnLocation, true);
                    return;
                }
                break;
            case "desert":
                if (canSpawnDesertBoss(playerLoc)) {
                    if (!plugin.getConfigManager().isDesertBossEnabled()) {
                        plugin.getLogger().warning("Попытка спавна отключенного босса Пустыни");
                        return;
                    }
                    spawnDesertSandlord(spawnLocation, true);
                    return;
                }
                break;
        }
    }

    // Проверки условий спавна
    private boolean canSpawnEnderBoss(Location location) {
        return location.getWorld().getEnvironment() == World.Environment.THE_END && canSpawnMobsAtLocation(location);
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
        long cooldownMs = plugin.getConfigManager().getCooldownForBoss(bossType) * 1000L;

        return (currentTime - lastSpawn) >= cooldownMs;
    }

    public Location findSpawnLocation(Location center) {
        World world = center.getWorld();
        boolean isNether = world.getEnvironment() == World.Environment.NETHER;

        for (int i = 0; i < 20; i++) {
            int x = center.getBlockX() + random.nextInt(30) - 15;
            int z = center.getBlockZ() + random.nextInt(30) - 15;
            int y;

            if (isNether) {
                // В аду — ищем уровень вблизи игрока (±5 блоков по Y)
                y = center.getBlockY() + random.nextInt(11) - 5;
            } else {
                // В обычных мирах — верхний безопасный блок
                y = world.getHighestBlockYAt(x, z);
            }

            Location testLoc = new Location(world, x + 0.5, y + 1, z + 0.5);

            if (canSpawnMobsAtLocation(testLoc)) {
                return testLoc;
            }

            // Для ада добавим попытку опустить на землю, если моб «в воздухе»
            if (isNether) {
                Location below = findNearestSolidBelow(testLoc);
                if (below != null && canSpawnMobsAtLocation(below)) {
                    return below;
                }
            }
        }

        return null;
    }

    private Location findNearestSolidBelow(Location start) {
        Location loc = start.clone();
        World world = loc.getWorld();

        for (int i = 0; i < 8; i++) {
            loc.subtract(0, 1, 0);
            Block blockBelow = world.getBlockAt(loc.clone().subtract(0, 1, 0));
            if (blockBelow.getType().isOccluding() && blockBelow.getType().isCollidable()) {
                // Возвращаем точку прямо над твёрдым блоком
                return blockBelow.getLocation().add(0.5, 1, 0.5);
            }
        }
        return null;
    }

    private boolean canSpawnMobsAtLocation(Location location) {
        if (isInsideProtectedRegion(location)) return false;

        Block blockBelow = location.clone().subtract(0, 1, 0).getBlock();
        Block blockAt = location.getBlock();

        // Проверяем воздух и отсутствие жидкости/лавы
        if (!blockAt.isPassable()) return false;
        if (blockBelow.isLiquid()) return false;

        Material typeBelow = blockBelow.getType();

        return typeBelow.isOccluding() &&
                typeBelow.isCollidable() &&
                typeBelow != Material.BEDROCK && // избегаем спавна на потолке ада
                typeBelow != Material.LAVA &&
                typeBelow != Material.FIRE;
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

    private void tryAutoSpawnForPlayer(final Player player) {
        final Location playerLoc = player.getLocation();
        final String[] bossTypes = {"ender", "nether", "forest", "desert"};

        for (final String bossType : bossTypes) {
            if (plugin.getConfigManager().isBossEnabled(bossType) && canSpawnBoss(bossType, playerLoc) &&
                    random.nextDouble() * 100 <= plugin.getConfigManager().getSpawnChance(bossType)) {
                trySpawnBossNearPlayer(player, bossType);
            }
        }
    }

    private boolean canSpawnBoss(final String bossType, final Location location) {
        return switch (bossType) {
            case "ender" -> canSpawnEnderBoss(location);
            case "nether" -> canSpawnNetherBoss(location);
            case "forest" -> canSpawnForestBoss(location);
            case "desert" -> canSpawnDesertBoss(location);
            default -> false;
        };
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
