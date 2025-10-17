package com.flyaway.minibosses;

import org.bukkit.*;
import org.bukkit.util.Vector;
import org.bukkit.block.Biome;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.lang.reflect.*;

public class MiniBosses extends JavaPlugin implements Listener {

    private Config config;
    private final Map<UUID, BossData> activeBosses = new HashMap<>();
    private final Map<UUID, Integer> summonedHelpers = new HashMap<>();
    private final Map<String, Long> bossCooldowns = new HashMap<>();
    private final Random random = new Random();
    private BukkitTask bossBarTask;
    private BukkitTask autoSpawnTask;

    private static boolean wgInitialized = false;
    private static boolean wgAvailable = false;

    // Кэшированные классы и методы
    private static Class<?> wgClass, bukkitAdapterClass, flagsClass, regionAssociableClass, flagClass, stateEnumClass;
    private static Method getInstanceMethod, getPlatformMethod, getRegionContainerMethod, createQueryMethod,
            adaptMethod, getApplicableRegionsMethod, queryValueMethod;
    private static Field mobSpawningFlagField;
    private static Object denyStateValue;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new Config(this);

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("minibosses").setExecutor(new CommandHandler(this));

        initWorldGuardReflection();

        startBossBarUpdater();
        startAutoSpawner();

        getLogger().info("MiniBosses включен!");
    }

    public void onDisable() {
        // Останавливаем все задачи
        stopAllTasks();

        // Удаляем все боссбары
        for (BossData data : activeBosses.values()) {
            data.bossBar.removeAll();
        }
        activeBosses.clear();
        summonedHelpers.clear();
        bossCooldowns.clear();

        getLogger().info("MiniBosses выключен!");
    }

    private void stopAllTasks() {
        // Останавливаем задачу обновления боссбаров
        if (bossBarTask != null && !bossBarTask.isCancelled()) {
            bossBarTask.cancel();
            bossBarTask = null;
        }

        // Останавливаем задачу автоспавна
        if (autoSpawnTask != null && !autoSpawnTask.isCancelled()) {
            autoSpawnTask.cancel();
            autoSpawnTask = null;
        }

        // Останавливаем все задачи, которые могли быть созданы через BukkitRunnable
        getServer().getScheduler().cancelTasks(this);
    }

    public void reloadPlugin() {
        // Останавливаем все задачи перед перезагрузкой
        stopAllTasks();

        reloadConfig();
        config = new Config(this);
        initWorldGuardReflection();
        // Перезапускаем задачи
        startBossBarUpdater();
        startAutoSpawner();
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
            getLogger().warning("[ThunderRider] WorldGuard не найден или структура изменилась, защита отключена: " + e.getMessage());
        }
    }

    // Автоспавн боссов
    private void startAutoSpawner() {
        // Останавливаем предыдущую задачу если есть
        if (autoSpawnTask != null) {
            autoSpawnTask.cancel();
        }

        autoSpawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!config.autoSpawnEnabled) return;

                for (World world : getServer().getWorlds()) {
                    for (Player player : world.getPlayers()) {
                        trySpawnBossNearPlayer(player);
                    }
                }
            }
        }.runTaskTimer(this, 0L, config.spawnCheckInterval * 20L); // Конвертируем секунды в тики
    }

    private void trySpawnBossNearPlayer(Player player) {
        Location playerLoc = player.getLocation();

        if (player.hasPermission("minibosses.ignore")) {
            return;
        }

        if (isInsideProtectedRegion(playerLoc)) {
            return;
        }

        // Проверяем условия для каждого типа босса
        if (config.enableEnderBoss && canSpawnEnderBoss(playerLoc)) {
            if (random.nextDouble() * 100 <= config.enderBossChance) {
                spawnEnderLord(findSpawnLocation(playerLoc, "ender"));
                getLogger().info("Автоспавн: Повелитель Энда для " + player.getName());
            }
        }

        if (config.enableNetherBoss && canSpawnNetherBoss(playerLoc)) {
            if (random.nextDouble() * 100 <= config.netherBossChance) {
                spawnNetherInferno(findSpawnLocation(playerLoc, "nether"));
                getLogger().info("Автоспавн: Инфернальный Ифрит для " + player.getName());
            }
        }

        if (config.enableForestBoss && canSpawnForestBoss(playerLoc)) {
            if (random.nextDouble() * 100 <= config.forestBossChance) {
                spawnForestGuardian(findSpawnLocation(playerLoc, "forest"));
                getLogger().info("Автоспавн: Защитник Леса для " + player.getName());
            }
        }

        if (config.enableDesertBoss && canSpawnDesertBoss(playerLoc)) {
            if (random.nextDouble() * 100 <= config.desertBossChance) {
                spawnDesertSandlord(findSpawnLocation(playerLoc, "desert"));
                getLogger().info("Автоспавн: Повелитель Песков для " + player.getName());
            }
        }
    }

    private boolean isInsideProtectedRegion(Location location) {
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
            getLogger().warning("Ошибка при проверке приватной территории (WorldGuard): " + e.getMessage());
            return false;
        }
    }

    private boolean canSpawnEnderBoss(Location location) {
        return location.getWorld().getEnvironment() == World.Environment.THE_END &&
               !hasActiveBossNearby(location) &&
               isCooldownExpired("ender") &&
               canSpawnMobsAtLocation(location);
    }

    private boolean canSpawnNetherBoss(Location location) {
        if (location.getWorld().getEnvironment() != World.Environment.NETHER) return false;

        // Проверяем, что в адской крепости
        if (!isInNetherFortress(location)) return false;

        return !hasActiveBossNearby(location) &&
               isCooldownExpired("nether") &&
               canSpawnMobsAtLocation(location);
    }

    private boolean canSpawnForestBoss(Location location) {
        if (location.getWorld().getEnvironment() != World.Environment.NORMAL) return false;

        // Проверяем, что в лесу
        if (!isInForest(location)) return false;

        return !hasActiveBossNearby(location) &&
               isCooldownExpired("forest") &&
               canSpawnMobsAtLocation(location);
    }

    // Новый метод проверки условий спавна:
    private boolean canSpawnDesertBoss(Location location) {
        if (location.getWorld().getEnvironment() != World.Environment.NORMAL) return false;

        // Проверяем, что в пустынном биоме
        if (!isInDesert(location)) return false;

        return !hasActiveBossNearby(location) &&
               isCooldownExpired("desert") &&
               canSpawnMobsAtLocation(location);
    }

    private boolean hasActiveBossNearby(Location location) {
        for (BossData data : activeBosses.values()) {
            Entity entity = getServer().getEntity(data.bossId);
            if (entity != null && entity.getLocation().distance(location) <= config.noBossSpawnRadius) {
                return true;
            }
        }
        return false;
    }

    private boolean isCooldownExpired(String bossType) {
        Long lastSpawn = bossCooldowns.get(bossType);
        if (lastSpawn == null) return true;

        long currentTime = System.currentTimeMillis();
        long cooldownMs = getCooldownForBoss(bossType) * 1000L;

        return (currentTime - lastSpawn) >= cooldownMs;
    }

    private long getCooldownForBoss(String bossType) {
        switch (bossType) {
            case "ender": return config.enderBossCooldown;
            case "nether": return config.netherBossCooldown;
            case "forest": return config.forestBossCooldown;
            case "desert": return config.desertBossCooldown;
            default: return 300;
        }
    }

    private boolean isInNetherFortress(Location location) {
        // Простая проверка на адскую крепость по наличию блоков крепости
        Material blockType = location.getBlock().getType();
        return blockType == Material.NETHER_BRICKS ||
               blockType == Material.NETHER_BRICK_FENCE ||
               blockType.toString().contains("NETHER_BRICK");
    }

    private boolean isInForest(Location location) {
        // Проверка биома леса
        Biome biome = location.getBlock().getBiome();
        return biome.toString().contains("FOREST") ||
               biome == Biome.TAIGA ||
               biome == Biome.BIRCH_FOREST ||
               biome == Biome.DARK_FOREST;
    }

    // Проверка биома пустыни:
    private boolean isInDesert(Location location) {
        Biome biome = location.getBlock().getBiome();
        return biome == Biome.DESERT ||
               biome == Biome.BADLANDS ||
               biome == Biome.ERODED_BADLANDS ||
               biome == Biome.WOODED_BADLANDS ||
               biome.toString().contains("DESERT") ||
               biome.toString().contains("BADLANDS");
    }

    private Location findSpawnLocation(Location center, String bossType) {
        for (int i = 0; i < 10; i++) {
            int x = center.getBlockX() + random.nextInt(30) - 15;
            int z = center.getBlockZ() + random.nextInt(30) - 15;
            int y = center.getWorld().getHighestBlockYAt(x, z);

            Location testLoc = new Location(center.getWorld(), x + 0.5, y + 1, z + 0.5);

            if (canSpawnMobsAtLocation(testLoc)) {
                return testLoc;
            }
        }
        return center; // Fallback
    }

    private boolean canSpawnMobsAtLocation(Location location) {
        if (!isWorldGuardEnabled()) {
            return true;
        }

        if (!wgInitialized) {
            initWorldGuardReflection();
        }

        if (!wgAvailable) {
            return true;
        }

        try {
            // Получаем RegionQuery через кэшированные методы
            Object wgInstance = getInstanceMethod.invoke(null);
            Object platform = getPlatformMethod.invoke(wgInstance);
            Object regionContainer = getRegionContainerMethod.invoke(platform);
            Object query = createQueryMethod.invoke(regionContainer);

            // Преобразуем Location → WorldEdit Location
            Object weLocation = adaptMethod.invoke(null, location);

            // Получаем регионы в этой точке
            Object regionSet = getApplicableRegionsMethod.invoke(query, weLocation);

            // Получаем MOB_SPAWNING флаг
            Object mobSpawningFlag = mobSpawningFlagField.get(null);

            // Проверяем флаг
            Object state = queryValueMethod.invoke(regionSet, null, mobSpawningFlag);

            // Разрешён ли спавн
            boolean canSpawn = (state == null) || !state.equals(denyStateValue);

            return canSpawn;

        } catch (Throwable e) {
            getLogger().warning("Ошибка при проверке WorldGuard (через рефлексию): " + e.getMessage());
            return true; // Если что-то пошло не так, не блокируем спавн
        }
    }

    private boolean isWorldGuardEnabled() {
        return getServer().getPluginManager().getPlugin("WorldGuard") != null;
    }

    // Методы спавна боссов (остаются такими же, но с небольшими изменениями)
    public void spawnEnderLord(Location location) {
        Enderman enderman = (Enderman) location.getWorld().spawnEntity(location, EntityType.ENDERMAN);

        enderman.setCustomName(ChatColor.LIGHT_PURPLE + "Повелитель Энда");
        enderman.setCustomNameVisible(true);
        enderman.getAttribute(Attribute.MAX_HEALTH).setBaseValue(config.enderBossHealth);
        enderman.setHealth(config.enderBossHealth);
        enderman.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(15);

        // НЕУЯЗВИМОСТЬ К ОГНЮ И ВОДЕ
        enderman.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 1));
        enderman.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 1));

        // Убрать урон от воды (если возможно)
        enderman.setRemoveWhenFarAway(false);

        enderman.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        enderman.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1));

        BossBar bossBar = getServer().createBossBar(
            ChatColor.LIGHT_PURPLE + "Повелитель Энда", BarColor.PURPLE, BarStyle.SEGMENTED_10
        );
        bossBar.setProgress(1.0);

        registerBoss(enderman, bossBar, "ender");
        bossCooldowns.put("ender", System.currentTimeMillis());
    }

    public void spawnNetherInferno(Location location) {
        Blaze blaze = (Blaze) location.getWorld().spawnEntity(location, EntityType.BLAZE);

        blaze.setCustomName(ChatColor.RED + "Инфернальный Ифрит");
        blaze.setCustomNameVisible(true);
        blaze.getAttribute(Attribute.MAX_HEALTH).setBaseValue(config.netherBossHealth);
        blaze.setHealth(config.netherBossHealth);
        blaze.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(12);

        blaze.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        blaze.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1));
        blaze.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 1));

        BossBar bossBar = getServer().createBossBar(
            ChatColor.RED + "Инфернальный Ифрит", BarColor.RED, BarStyle.SEGMENTED_10
        );
        bossBar.setProgress(1.0);

        registerBoss(blaze, bossBar, "nether");
        bossCooldowns.put("nether", System.currentTimeMillis());
    }

    public void spawnForestGuardian(Location location) {
        IronGolem golem = (IronGolem) location.getWorld().spawnEntity(location, EntityType.IRON_GOLEM);

        golem.setCustomName(ChatColor.GREEN + "Защитник Леса");
        golem.setCustomNameVisible(true);
        golem.getAttribute(Attribute.MAX_HEALTH).setBaseValue(config.forestBossHealth);
        golem.setHealth(config.forestBossHealth);
        golem.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(25);

        golem.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 2));

        BossBar bossBar = getServer().createBossBar(
            ChatColor.GREEN + "Защитник Леса", BarColor.GREEN, BarStyle.SEGMENTED_10
        );
        bossBar.setProgress(1.0);

        registerBoss(golem, bossBar, "forest");
        bossCooldowns.put("forest", System.currentTimeMillis());
    }

    private void registerBoss(LivingEntity boss, BossBar bossBar, String type) {
        activeBosses.put(boss.getUniqueId(), new BossData(bossBar, type, boss.getUniqueId()));
        summonedHelpers.put(boss.getUniqueId(), 0);
    }

    public void spawnDesertSandlord(Location location) {
        Husk husk = (Husk) location.getWorld().spawnEntity(location, EntityType.HUSK);

        husk.setCustomName(ChatColor.GOLD + "Повелитель Песков");
        husk.setCustomNameVisible(true);
        husk.getAttribute(Attribute.MAX_HEALTH).setBaseValue(config.desertBossHealth);
        husk.setHealth(config.desertBossHealth);
        husk.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(18);
        // ДОБАВИТЬ СКОРОСТЬ:
        husk.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.25); // Быстрее обычного

        // Экипировка
        husk.getEquipment().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
        husk.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));

        // Эффекты
        husk.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 1));
        husk.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        husk.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1));

        BossBar bossBar = getServer().createBossBar(
            ChatColor.GOLD + "Повелитель Песков", BarColor.YELLOW, BarStyle.SEGMENTED_10
        );
        bossBar.setProgress(1.0);

        registerBoss(husk, bossBar, "desert");
        bossCooldowns.put("desert", System.currentTimeMillis());
    }

    @EventHandler
    public void onBossDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;

        LivingEntity entity = (LivingEntity) event.getEntity();
        UUID bossId = entity.getUniqueId();

        if (!activeBosses.containsKey(bossId)) return;

        BossData data = activeBosses.get(bossId);
        double newHealth = entity.getHealth() - event.getFinalDamage();
        double maxHealth = entity.getAttribute(Attribute.MAX_HEALTH).getValue();
        double healthPercent = newHealth / maxHealth;

        data.bossBar.setProgress(Math.max(0, healthPercent));

        // Проверяем пороги для призыва помощников
        int currentWave = summonedHelpers.getOrDefault(bossId, 0);

        if (healthPercent <= 0.6 && currentWave < 1) {
            summonHelpers(entity, data.type);
            summonedHelpers.put(bossId, 1);
        } else if (healthPercent <= 0.4 && currentWave < 2) {
            summonHelpers(entity, data.type);
            summonedHelpers.put(bossId, 2);
        } else if (healthPercent <= 0.2 && currentWave < 3) {
            summonHelpers(entity, data.type);
            summonedHelpers.put(bossId, 3);
        }

        // Использование способностей с настраиваемым шансом
        double abilityChance = getAbilityChanceForBoss(data.type);
        if (Math.random() < abilityChance) {
            useSpecialAbility(entity, data.type);
        }
    }

    private double getAbilityChanceForBoss(String bossType) {
        switch (bossType) {
            case "ender": return config.enderAbilityChance;
            case "nether": return config.netherAbilityChance;
            case "forest": return config.forestAbilityChance;
            case "desert": return config.desertAbilityChance;
            default: return 0.3;
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        UUID bossId = event.getEntity().getUniqueId();

        if (activeBosses.containsKey(bossId)) {
            BossData data = activeBosses.get(bossId);
            data.bossBar.removeAll();
            activeBosses.remove(bossId);
            summonedHelpers.remove(bossId);

            // Находим игрока, который убил босса
            Player killer = event.getEntity().getKiller();
            if (killer != null) {
                giveRewards(event.getEntity(), data.type);
                executeDeathCommands(killer, data.type);
            }
        }
    }

    private void executeDeathCommands(Player killer, String bossType) {
        List<String> commands = getDeathCommandsForBoss(bossType);

        for (String command : commands) {
            String processedCommand = command.replace("%player%", killer.getName());

            if (processedCommand.startsWith("console:")) {
                // Команда от имени консоли
                String consoleCommand = processedCommand.substring(8);
                getServer().dispatchCommand(getServer().getConsoleSender(), consoleCommand);
            } else {
                // Команда от имени игрока
                killer.performCommand(processedCommand);
            }
        }
    }

    private List<String> getDeathCommandsForBoss(String bossType) {
        switch (bossType) {
            case "ender": return config.enderDeathCommands;
            case "nether": return config.netherDeathCommands;
            case "forest": return config.forestDeathCommands;
            case "desert": return config.desertDeathCommands;
            default: return new ArrayList<>();
        }
    }

    private Player findNearestPlayer(Location location) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player player : location.getWorld().getPlayers()) {
            double distance = player.getLocation().distance(location);
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void summonHelpers(LivingEntity boss, String bossType) {
        Location loc = boss.getLocation();
        World world = boss.getWorld();
        switch (bossType) {
            case "ender":
                int enderMiteCount = config.enderHelperCount;
                for (int i = 0; i < enderMiteCount; i++) {
                    Location spawnLoc = loc.clone().add(
                        random.nextDouble() * 4 - 2,
                        0,
                        random.nextDouble() * 4 - 2
                    );
                    Endermite endermite = (Endermite) world.spawnEntity(spawnLoc, EntityType.ENDERMITE);
                    endermite.setCustomName(ChatColor.LIGHT_PURPLE + "Слуга Энда");
                    endermite.getAttribute(Attribute.MAX_HEALTH).setBaseValue(config.enderHelperHealth);
                    endermite.setHealth(config.enderHelperHealth);
                    Player nearestPlayer = findNearestPlayer(boss.getLocation());
                    if (nearestPlayer != null) {
                        endermite.setTarget(nearestPlayer);
                    }
                }
                world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
                world.spawnParticle(Particle.PORTAL, loc, 20, 2, 1, 2, 0.1);
                break;

            case "nether":
                // Призываем магма-кубов
                int magmaCount = config.netherHelperCount;
                for (int i = 0; i < magmaCount; i++) {
                    Location spawnLoc = loc.clone().add(
                        random.nextDouble() * 5 - 2.5,
                        0,
                        random.nextDouble() * 5 - 2.5
                    );
                    MagmaCube magma = (MagmaCube) world.spawnEntity(spawnLoc, EntityType.MAGMA_CUBE);
                    magma.setSize(config.netherHelperSize);
                    magma.setCustomName(ChatColor.RED + "Расплавленный Слизень");
                    Player nearestPlayer = findNearestPlayer(boss.getLocation());
                    if (nearestPlayer != null) {
                        magma.setTarget(nearestPlayer);
                    }
                }
                world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
                world.spawnParticle(Particle.FLAME, loc, 30, 3, 1, 3, 0.1);
                break;

            case "forest":
                // Призываем агрессивных волков
                int wolfCount = config.forestHelperCount;
                for (int i = 0; i < wolfCount; i++) {
                    Location spawnLoc = loc.clone().add(
                        random.nextDouble() * 6 - 3,
                        0,
                        random.nextDouble() * 6 - 3
                    );
                    Wolf wolf = (Wolf) world.spawnEntity(spawnLoc, EntityType.WOLF);
                    wolf.setAngry(true);
                    wolf.setCustomName(ChatColor.GRAY + "Лесной Охотник");
                    wolf.getAttribute(Attribute.MAX_HEALTH).setBaseValue(config.forestHelperHealth);
                    wolf.setHealth(config.forestHelperHealth);
                    wolf.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 1));
                    Player nearestPlayer = findNearestPlayer(boss.getLocation());
                    if (nearestPlayer != null) {
                        wolf.setTarget(nearestPlayer);
                    }
                }
                world.playSound(loc, Sound.ENTITY_WOLF_AMBIENT, 1.0f, 1.0f);
                world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 15, 2, 1, 2, 0.1);
                break;

            case "desert":
                // Призываем хасков
                int huskCount = config.desertHelperCount;
                for (int i = 0; i < huskCount; i++) {
                    Location spawnLoc = loc.clone().add(
                        random.nextDouble() * 5 - 2.5,
                        0,
                        random.nextDouble() * 5 - 2.5
                    );
                    Husk husk = (Husk) world.spawnEntity(spawnLoc, EntityType.HUSK);
                    husk.setCustomName(ChatColor.YELLOW + "Песчаный Воин");
                    husk.getAttribute(Attribute.MAX_HEALTH).setBaseValue(config.desertHelperHealth);
                    husk.setHealth(config.desertHelperHealth);
                    Player nearestPlayer = findNearestPlayer(boss.getLocation());
                    if (nearestPlayer != null) {
                        husk.setTarget(nearestPlayer);
                    }

                    // Даём хаскам лопаты
                    husk.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SHOVEL));
                }
                world.playSound(loc, Sound.ENTITY_HUSK_AMBIENT, 1.0f, 0.7f);
                world.spawnParticle(Particle.CLOUD, loc, 25, 3, 1, 3, 0.1);
                break;
        }

        world.playEffect(loc, Effect.MOBSPAWNER_FLAMES, 10);
    }

    private void useSpecialAbility(LivingEntity boss, String bossType) {
        Location loc = boss.getLocation();
        World world = boss.getWorld();

        switch (bossType) {
            case "ender":
                if (random.nextBoolean()) {
                    // Телепортация и ослепление
                    List<Player> nearbyPlayers = getNearbyPlayers(loc, config.enderAbilityRadius);
                    if (!nearbyPlayers.isEmpty()) {
                        // Телепортируем босса к случайному игроку
                        Player targetPlayer = nearbyPlayers.get(random.nextInt(nearbyPlayers.size()));
                        Location teleportLoc = targetPlayer.getLocation().add(
                            random.nextDouble() * 4 - 2,
                            0,
                            random.nextDouble() * 4 - 2
                        );

                        if (boss instanceof Enderman) {
                            ((Enderman) boss).teleport(teleportLoc);
                        } else {
                            boss.teleport(teleportLoc);
                        }

                        // Ослепляем всех игроков в радиусе
                        for (Player player : nearbyPlayers) {
                            player.addPotionEffect(new PotionEffect(
                                PotionEffectType.BLINDNESS,
                                config.enderBlindnessDuration,
                                1
                            ));
                            world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        }

                        world.spawnParticle(Particle.REVERSE_PORTAL, loc, 30, 2, 2, 2, 0.1);
                    }
                } else {
                    // НОВАЯ СПОСОБНОСТЬ: Телепортация игроков к боссу
                    world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

                    for (Player player : getNearbyPlayers(loc, config.enderTeleportRadius)) {
                        // Телепортируем игрока к боссу с небольшим случайным смещением
                        Location teleportLoc = loc.clone().add(
                            random.nextDouble() * 4 - 2,
                            1,
                            random.nextDouble() * 4 - 2
                        );

                        // Проверяем безопасное место для телепортации
                        if (teleportLoc.getBlock().getType().isAir() &&
                            teleportLoc.clone().add(0, 1, 0).getBlock().getType().isAir()) {

                            player.teleport(teleportLoc);
                            player.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOWNESS, 40, 1 // Кратковременное замедление после телепортации
                            ));
                            player.sendMessage(ChatColor.LIGHT_PURPLE + "Повелитель Энда притягивает вас к себе!");
                        }
                    }
                    world.spawnParticle(Particle.REVERSE_PORTAL, loc, 50, 3, 2, 3, 0.2);
                }
                break;

            case "nether":
                double abilityRoll = random.nextDouble();

                if (abilityRoll < 0.4) {
                    // Огненный шторм
                    world.spawnParticle(Particle.FLAME, loc, 50, 5, 3, 5, 0.1);
                    world.spawnParticle(Particle.LAVA, loc, 20, 3, 1, 3, 0.1);
                    world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);

                    // Поджигаем игроков и наносим урон
                    for (Player player : getNearbyPlayers(loc, config.netherAbilityRadius)) {
                        player.setFireTicks(config.netherFireDuration);
                        player.damage(config.netherFireDamage);
                        player.sendMessage(ChatColor.RED + "Инфернальный Ифрит сжигает вас огненным штормом!");
                    }
                } else if (abilityRoll < 0.8) {
                    // НОВАЯ СПОСОБНОСТЬ: Фаерболы
                    world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);

                    for (Player player : getNearbyPlayers(loc, config.netherFireballRadius)) {
                        // Создаем фаербол
                        Fireball fireball = world.spawn(loc, Fireball.class);
                        fireball.setShooter(boss);
                        fireball.setDirection(player.getLocation().toVector().subtract(loc.toVector()).normalize());
                        fireball.setYield(config.netherFireballPower);
                        fireball.setIsIncendiary(true);

                        player.sendMessage(ChatColor.RED + "Инфернальный Ифрит запускает в вас огненный шар!");
                    }

                    world.spawnParticle(Particle.FLAME, loc, 30, 2, 1, 2, 0.1);
                } else {
                    world.playSound(loc, Sound.ENTITY_BLAZE_AMBIENT, 1.0f, 1.5f);

                    List<Player> nearbyPlayers = getNearbyPlayers(loc, config.netherTeleportRadius);
                    if (!nearbyPlayers.isEmpty()) {
                        Player targetPlayer = nearbyPlayers.get(random.nextInt(nearbyPlayers.size()));
                        Location targetLoc = targetPlayer.getLocation();

                        // Находим безопасное место для телепортации рядом с игроком
                        Location teleportLoc = findSafeTeleportLocation(targetLoc, 3);
                        if (teleportLoc != null) {
                            boss.teleport(teleportLoc);

                            // Эффекты при телепортации
                            world.playSound(teleportLoc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);
                            world.spawnParticle(Particle.FLAME, teleportLoc, 30, 2, 2, 2, 0.2);
                            world.spawnParticle(Particle.LARGE_SMOKE, teleportLoc, 15, 1, 1, 1, 0.1);

                            targetPlayer.sendMessage(ChatColor.RED + "Инфернальный Ифрит телепортируется к вам!");

                            // Кратковременное оглушение игрока
                            targetPlayer.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOWNESS, 20, 1
                            ));
                        }
                    }
                }
                break;

            case "forest":
                if (random.nextBoolean()) {
                    // Землетрясение и замедление
                    world.playSound(loc, Sound.ENTITY_IRON_GOLEM_HURT, 1.0f, 0.5f);
                    world.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.8f);

                    // Частицы землетрясения
                    world.spawnParticle(Particle.BLOCK, loc, 30, 3, 1, 3, 0.5, Material.DIRT.createBlockData());
                    world.spawnParticle(Particle.BLOCK, loc, 20, 3, 1, 3, 0.5, Material.STONE.createBlockData());

                    // Замедление и отбрасывание игроков
                    for (Player player : getNearbyPlayers(loc, config.forestAbilityRadius)) {
                        player.addPotionEffect(new PotionEffect(
                            PotionEffectType.SLOWNESS,
                            config.forestSlownessDuration,
                            config.forestSlownessLevel
                        ));
                        player.addPotionEffect(new PotionEffect(
                            PotionEffectType.WEAKNESS,
                            config.forestWeaknessDuration,
                            1
                        ));

                        // Отбрасывание
                        Vector direction = player.getLocation().toVector().subtract(loc.toVector()).normalize();
                        player.setVelocity(direction.multiply(config.forestKnockbackPower));

                        player.sendMessage(ChatColor.DARK_GREEN + "Защитник Леса вызывает землетрясение!");
                    }
                } else {
                    // НОВАЯ СПОСОБНОСТЬ: Притягивание игроков
                    world.playSound(loc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.6f);
                    world.playSound(loc, Sound.BLOCK_CHAIN_BREAK, 1.0f, 0.8f);

                    for (Player player : getNearbyPlayers(loc, config.forestPullRadius)) {
                        // Притягиваем игрока к боссу
                        org.bukkit.util.Vector direction = loc.toVector().subtract(player.getLocation().toVector()).normalize();
                        player.setVelocity(direction.multiply(config.forestPullPower));

                        player.addPotionEffect(new PotionEffect(
                            PotionEffectType.WEAKNESS, config.forestPullWeaknessDuration, 1
                        ));

                        player.sendMessage(ChatColor.DARK_GREEN + "Защитник Леса притягивает вас магией природы!");
                    }

                    world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 25, 3, 2, 3, 0.1);
                }
                break;

            case "desert":
                if (random.nextBoolean()) {
                    // Песчаная буря и голод
                    world.playSound(loc, Sound.ENTITY_HUSK_HURT, 1.0f, 0.5f);
                    world.playSound(loc, Sound.BLOCK_SAND_BREAK, 1.0f, 0.8f);

                    // Частицы песчаной бури
                    world.spawnParticle(Particle.CLOUD, loc, 40, 4, 2, 4, 0.2);
                    world.spawnParticle(Particle.FALLING_DUST, loc, 30, 3, 2, 3, 0.3, Material.SAND.createBlockData());

                    // Эффекты на игроков
                    for (Player player : getNearbyPlayers(loc, config.desertAbilityRadius)) {
                        // Голод
                        player.addPotionEffect(new PotionEffect(
                            PotionEffectType.HUNGER,
                            config.desertHungerDuration,
                            config.desertHungerLevel
                        ));

                        // Слепота от песка
                        player.addPotionEffect(new PotionEffect(
                            PotionEffectType.BLINDNESS,
                            config.desertSandstormDuration,
                            0
                        ));

                        // Урон песчаным взрывом
                        player.damage(config.desertSandblastDamage);

                        // Отбрасывание
                        Vector direction = player.getLocation().toVector().subtract(loc.toVector()).normalize();
                        player.setVelocity(direction.multiply(1.0));

                        player.sendMessage(ChatColor.YELLOW + "Повелитель Песков поднимает песчаную бурю!");
                    }
                } else {
                     // НОВАЯ СПОСОБНОСТЬ: Стрелы с замедлением
                     world.playSound(loc, Sound.ENTITY_SKELETON_SHOOT, 1.0f, 0.8f);

                     for (Player player : getNearbyPlayers(loc, config.desertAbilityRadius)) {
                         // Создаем стрелу
                         Arrow arrow = world.spawnArrow(loc, player.getLocation().toVector(), 1.2f, 12.0f);
                         arrow.setShooter(boss);
                         arrow.setDamage(config.desertArrowDamage);

                         // Эффект замедления при попадании
                         arrow.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                             config.desertArrowSlownessDuration, config.desertArrowSlownessLevel), true);

                         player.sendMessage(ChatColor.YELLOW + "Повелитель Песков выпускает замедляющие стрелы!");
                     }
                 }
                 break;
        }
    }

    private Location findSafeTeleportLocation(Location center, int radius) {
        World world = center.getWorld();

        for (int i = 0; i < 10; i++) {
            int x = center.getBlockX() + random.nextInt(radius * 2) - radius;
            int z = center.getBlockZ() + random.nextInt(radius * 2) - radius;
            int y = world.getHighestBlockYAt(x, z);

            Location testLoc = new Location(world, x + 0.5, y + 1, z + 0.5);

            // Проверяем, что место безопасное (не в воде/лаве и есть пространство)
            Material blockType = testLoc.getBlock().getType();
            Material blockAbove = testLoc.clone().add(0, 1, 0).getBlock().getType();

            if (blockType != Material.WATER && blockType != Material.LAVA &&
                blockType.isSolid() && blockAbove.isAir()) {
                return testLoc;
            }
        }
        return null;
    }

    private void giveRewards(LivingEntity boss, String bossType) {
        World world = boss.getWorld();
        Location loc = boss.getLocation();

        // Эффекты при смерти
        world.playSound(loc, Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        world.spawnParticle(Particle.EXPLOSION, loc, 10, 2, 2, 2, 0.1);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 20, 1, 1, 1, 0.2);

        // Выдача наград в зависимости от типа босса
        switch (bossType) {
            case "ender":
                world.dropItemNaturally(loc, new ItemStack(Material.ENDER_PEARL, config.enderRewardEnderPearls));
                world.dropItemNaturally(loc, new ItemStack(Material.ENDER_EYE, config.enderRewardEnderEyes));
                world.dropItemNaturally(loc, new ItemStack(Material.CHORUS_FRUIT, config.enderRewardChorusFruit));
                if (config.enderRewardShulkerShells > 0) {
                    world.dropItemNaturally(loc, new ItemStack(Material.SHULKER_SHELL, config.enderRewardShulkerShells));
                }
                break;

            case "nether":
                world.dropItemNaturally(loc, new ItemStack(Material.BLAZE_ROD, config.netherRewardBlazeRods));
                world.dropItemNaturally(loc, new ItemStack(Material.NETHER_BRICK, config.netherRewardNetherBricks));
                world.dropItemNaturally(loc, new ItemStack(Material.GOLD_NUGGET, config.netherRewardGoldNuggets));
                break;

            case "forest":
                world.dropItemNaturally(loc, new ItemStack(Material.IRON_INGOT, config.forestRewardIron));
                world.dropItemNaturally(loc, new ItemStack(Material.APPLE, config.forestRewardApples));
                world.dropItemNaturally(loc, new ItemStack(Material.OAK_LOG, config.forestRewardLogs));
                break;

            case "desert":
                world.dropItemNaturally(loc, new ItemStack(Material.GOLD_INGOT, config.desertRewardGold));
                world.dropItemNaturally(loc, new ItemStack(Material.CACTUS, config.desertRewardCactus));
                world.dropItemNaturally(loc, new ItemStack(Material.SANDSTONE, config.desertRewardSandstone));
                world.dropItemNaturally(loc, new ItemStack(Material.RABBIT_HIDE, config.desertRewardRabbitHide));
                break;
        }

        // Опыт
        int experience = getExperienceForBoss(bossType);
        if (experience > 0) {
            world.spawn(loc, ExperienceOrb.class).setExperience(experience);
        }
    }

    private int getExperienceForBoss(String bossType) {
        switch (bossType) {
            case "ender": return config.enderRewardExp;
            case "nether": return config.netherRewardExp;
            case "forest": return config.forestRewardExp;
            case "desert": return config.desertRewardExp;
            default: return 100;
        }
    }

    private List<Player> getNearbyPlayers(Location location, double radius) {
        List<Player> players = new ArrayList<>();
        for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (entity instanceof Player) {
                players.add((Player) entity);
            }
        }
        return players;
    }

    private void startBossBarUpdater() {
        if (bossBarTask != null) {
            bossBarTask.cancel();
        }

        bossBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<Map.Entry<UUID, BossData>> iterator = activeBosses.entrySet().iterator();

                while (iterator.hasNext()) {
                    Map.Entry<UUID, BossData> entry = iterator.next();
                    Entity entity = getServer().getEntity(entry.getKey());

                    if (entity instanceof LivingEntity) {
                        LivingEntity boss = (LivingEntity) entity;
                        BossBar bossBar = entry.getValue().bossBar;

                        // Обновляем прогресс боссбара
                        double healthPercent = boss.getHealth() / boss.getAttribute(Attribute.MAX_HEALTH).getValue();
                        bossBar.setProgress(Math.max(0, healthPercent));

                        // Показываем боссбар всем игрокам в радиусе
                        for (Player player : getNearbyPlayers(boss.getLocation(), 30)) {
                            if (!bossBar.getPlayers().contains(player)) {
                                bossBar.addPlayer(player);
                            }
                        }

                        // Убираем игроков которые ушли далеко
                        Iterator<Player> playerIterator = bossBar.getPlayers().iterator();
                        while (playerIterator.hasNext()) {
                            Player player = playerIterator.next();
                            if (!player.isOnline() || player.getLocation().distance(boss.getLocation()) > 40) {
                                bossBar.removePlayer(player);
                            }
                        }
                    } else {
                        // Босс больше не существует, убираем
                        entry.getValue().bossBar.removeAll();
                        iterator.remove();
                        summonedHelpers.remove(entry.getKey());
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L); // Обновляем каждую секунду
    }

    // Команда для спавна боссов (для тестирования)
    public void spawnBoss(Player player, String bossType) {
        Location loc = player.getLocation();

        switch (bossType.toLowerCase()) {
            case "ender":
                if (config.enableEnderBoss) {
                    spawnEnderLord(loc);
                    player.sendMessage(ChatColor.LIGHT_PURPLE + "Призван Повелитель Энда!");
                } else {
                    player.sendMessage(ChatColor.RED + "Босс Энда отключен в конфиге!");
                }
                break;
            case "nether":
                if (config.enableNetherBoss) {
                    spawnNetherInferno(loc);
                    player.sendMessage(ChatColor.RED + "Призван Инфернальный Ифрит!");
                } else {
                    player.sendMessage(ChatColor.RED + "Босс Ада отключен в конфиге!");
                }
                break;
            case "forest":
                if (config.enableForestBoss) {
                    spawnForestGuardian(loc);
                    player.sendMessage(ChatColor.GREEN + "Призван Защитник Леса!");
                } else {
                    player.sendMessage(ChatColor.RED + "Босс Леса отключен в конфиге!");
                }
                break;
            case "desert":
                if (config.enableDesertBoss) {
                    spawnDesertSandlord(loc);
                    player.sendMessage(ChatColor.GOLD + "Призван Повелитель Песков!");
                } else {
                    player.sendMessage(ChatColor.RED + "Босс Пустыни отключен в конфиге!");
                }
                break;
            default:
                player.sendMessage(ChatColor.RED + "Неизвестный тип босса: ender, nether, forest, desert");
        }
    }

    private static class BossData {
        public BossBar bossBar;
        public String type;
        public UUID bossId;

        public BossData(BossBar bossBar, String type, UUID bossId) {
            this.bossBar = bossBar;
            this.type = type;
            this.bossId = bossId;
        }
    }

    public static class Config {
        // Основные настройки автоспавна
        public final boolean autoSpawnEnabled;
        public final int spawnCheckInterval;
        public final int noBossSpawnRadius;

        // Босс Энда - основные настройки
        public final boolean enableEnderBoss;
        public final double enderBossChance;
        public final int enderBossCooldown;
        public final double enderBossHealth;
        public final double enderAbilityChance;
        public final List<String> enderDeathCommands;

        // Босс Энда - помощники
        public final int enderHelperCount;
        public final double enderHelperHealth;

        // Босс Энда - способности
        public final int enderAbilityRadius;
        public final int enderBlindnessDuration;
        public final int enderTeleportRadius;

        // Босс Энда - награды
        public final int enderRewardEnderPearls;
        public final int enderRewardEnderEyes;
        public final int enderRewardChorusFruit;
        public final int enderRewardShulkerShells;
        public final int enderRewardExp;

        // Босс Ада - основные настройки
        public final boolean enableNetherBoss;
        public final double netherBossChance;
        public final int netherBossCooldown;
        public final double netherBossHealth;
        public final double netherAbilityChance;
        public final List<String> netherDeathCommands;

        // Босс Ада - помощники
        public final int netherHelperCount;
        public final int netherHelperSize;

        // Босс Ада - способности
        public final int netherAbilityRadius;
        public final int netherFireDuration;
        public final double netherFireDamage;
        public final int netherFireballRadius;
        public final float netherFireballPower;
        public final int netherTeleportRadius;

        // Босс Ада - награды
        public final int netherRewardBlazeRods;
        public final int netherRewardNetherBricks;
        public final int netherRewardGoldNuggets;
        public final int netherRewardExp;

        // Босс Леса - основные настройки
        public final boolean enableForestBoss;
        public final double forestBossChance;
        public final int forestBossCooldown;
        public final double forestBossHealth;
        public final double forestAbilityChance;
        public final List<String> forestDeathCommands;

        // Босс Леса - помощники
        public final int forestHelperCount;
        public final double forestHelperHealth;

        // Босс Леса - способности
        public final int forestAbilityRadius;
        public final int forestSlownessDuration;
        public final int forestSlownessLevel;
        public final int forestWeaknessDuration;
        public final double forestKnockbackPower;
        public final int forestPullRadius;
        public final double forestPullPower;
        public final int forestPullWeaknessDuration;

        // Босс Леса - награды
        public final int forestRewardIron;
        public final int forestRewardApples;
        public final int forestRewardLogs;
        public final int forestRewardExp;

        // Босс Пустыни - основные настройки
        public final boolean enableDesertBoss;
        public final double desertBossChance;
        public final int desertBossCooldown;
        public final double desertBossHealth;
        public final double desertArrowDamage;
        public final double desertAbilityChance;
        public final List<String> desertDeathCommands;

        // Босс Пустыни - помощники
        public final int desertHelperCount;
        public final double desertHelperHealth;

        // Босс Пустыни - способности
        public final int desertAbilityRadius;
        public final int desertSandstormDuration;
        public final int desertHungerDuration;
        public final int desertHungerLevel;
        public final double desertSandblastDamage;
        public final int desertArrowSlownessDuration;
        public final int desertArrowSlownessLevel;

        // Босс Пустыни - награды
        public final int desertRewardGold;
        public final int desertRewardCactus;
        public final int desertRewardSandstone;
        public final int desertRewardRabbitHide;
        public final int desertRewardExp;

        public Config(MiniBosses plugin) {
            org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();

            // Основные настройки автоспавна
            autoSpawnEnabled = cfg.getBoolean("auto-spawn.enabled", true);
            spawnCheckInterval = cfg.getInt("auto-spawn.check-interval", 300);
            noBossSpawnRadius = cfg.getInt("auto-spawn.no-boss-spawn-radius", 100);

            // ========== БОСС ЭНДА ==========
            enableEnderBoss = cfg.getBoolean("ender-boss.enabled", true);
            enderBossChance = cfg.getDouble("ender-boss.spawn-chance", 15.0);
            enderBossCooldown = cfg.getInt("ender-boss.cooldown", 1200);
            enderBossHealth = cfg.getDouble("ender-boss.health", 300.0);
            enderAbilityChance = cfg.getDouble("ender-boss.ability-chance", 0.3);
            enderDeathCommands = cfg.getStringList("ender-boss.death-commands");

            // Помощники Энда
            enderHelperCount = cfg.getInt("ender-boss.helper-count", 4);
            enderHelperHealth = cfg.getDouble("ender-boss.helper-health", 40.0);

            // Способности Энда
            enderAbilityRadius = cfg.getInt("ender-boss.ability-radius", 15);
            enderBlindnessDuration = cfg.getInt("ender-boss.blindness-duration", 60);
            enderTeleportRadius = cfg.getInt("ender-boss.teleport-radius", 12);

            // Награды Энда
            enderRewardEnderPearls = cfg.getInt("ender-boss.reward-ender-pearls", 8);
            enderRewardEnderEyes = cfg.getInt("ender-boss.reward-ender-eyes", 2);
            enderRewardChorusFruit = cfg.getInt("ender-boss.reward-chorus-fruit", 5);
            enderRewardShulkerShells = cfg.getInt("ender-boss.reward-shulker-shells", 1);
            enderRewardExp = cfg.getInt("ender-boss.reward-exp", 150);

            // ========== БОСС АДА ==========
            enableNetherBoss = cfg.getBoolean("nether-boss.enabled", true);
            netherBossChance = cfg.getDouble("nether-boss.spawn-chance", 20.0);
            netherBossCooldown = cfg.getInt("nether-boss.cooldown", 900);
            netherBossHealth = cfg.getDouble("nether-boss.health", 250.0);
            netherAbilityChance = cfg.getDouble("nether-boss.ability-chance", 0.35);
            netherDeathCommands = cfg.getStringList("nether-boss.death-commands");

            // Помощники Ада
            netherHelperCount = cfg.getInt("nether-boss.helper-count", 3);
            netherHelperSize = cfg.getInt("nether-boss.helper-size", 3);

            // Способности Ада
            netherAbilityRadius = cfg.getInt("nether-boss.ability-radius", 10);
            netherFireDuration = cfg.getInt("nether-boss.fire-duration", 100);
            netherFireDamage = cfg.getDouble("nether-boss.fire-damage", 5.0);
            netherFireballRadius = cfg.getInt("nether-boss.fireball-radius", 15);
            netherFireballPower = (float) cfg.getDouble("nether-boss.fireball-power", 1.5);
            netherTeleportRadius = cfg.getInt("nether-boss.teleport-radius", 20);

            // Награды Ада
            netherRewardBlazeRods = cfg.getInt("nether-boss.reward-blaze-rods", 12);
            netherRewardNetherBricks = cfg.getInt("nether-boss.reward-nether-bricks", 16);
            netherRewardGoldNuggets = cfg.getInt("nether-boss.reward-gold-nuggets", 8);
            netherRewardExp = cfg.getInt("nether-boss.reward-exp", 120);

            // ========== БОСС ЛЕСА ==========
            enableForestBoss = cfg.getBoolean("forest-boss.enabled", true);
            forestBossChance = cfg.getDouble("forest-boss.spawn-chance", 25.0);
            forestBossCooldown = cfg.getInt("forest-boss.cooldown", 600);
            forestBossHealth = cfg.getDouble("forest-boss.health", 400.0);
            forestAbilityChance = cfg.getDouble("forest-boss.ability-chance", 0.25);
            forestDeathCommands = cfg.getStringList("forest-boss.death-commands");

            // Помощники Леса
            forestHelperCount = cfg.getInt("forest-boss.helper-count", 5);
            forestHelperHealth = cfg.getDouble("forest-boss.helper-health", 30.0);

            // Способности Леса
            forestAbilityRadius = cfg.getInt("forest-boss.ability-radius", 12);
            forestSlownessDuration = cfg.getInt("forest-boss.slowness-duration", 80);
            forestSlownessLevel = cfg.getInt("forest-boss.slowness-level", 2);
            forestWeaknessDuration = cfg.getInt("forest-boss.weakness-duration", 60);
            forestKnockbackPower = cfg.getDouble("forest-boss.knockback-power", 1.2);
            forestPullRadius = cfg.getInt("forest-boss.pull-radius", 10);
            forestPullPower = cfg.getDouble("forest-boss.pull-power", 1.5);
            forestPullWeaknessDuration = cfg.getInt("forest-boss.pull-weakness-duration", 60);

            // Награды Леса
            forestRewardIron = cfg.getInt("forest-boss.reward-iron", 16);
            forestRewardApples = cfg.getInt("forest-boss.reward-apples", 8);
            forestRewardLogs = cfg.getInt("forest-boss.reward-logs", 12);
            forestRewardExp = cfg.getInt("forest-boss.reward-exp", 200);

            // ========== БОСС ПУСТЫНИ ==========
            enableDesertBoss = cfg.getBoolean("desert-boss.enabled", true);
            desertBossChance = cfg.getDouble("desert-boss.spawn-chance", 18.0);
            desertBossCooldown = cfg.getInt("desert-boss.cooldown", 800);
            desertBossHealth = cfg.getDouble("desert-boss.health", 280.0);
            desertArrowDamage = cfg.getDouble("desert-boss.arrow-damage", 4.0);
            desertAbilityChance = cfg.getDouble("desert-boss.ability-chance", 0.32);
            desertDeathCommands = cfg.getStringList("desert-boss.death-commands");

            // Помощники Пустыни
            desertHelperCount = cfg.getInt("desert-boss.helper-count", 4);
            desertHelperHealth = cfg.getDouble("desert-boss.helper-health", 35.0);

            // Способности Пустыни
            desertAbilityRadius = cfg.getInt("desert-boss.ability-radius", 12);
            desertSandstormDuration = cfg.getInt("desert-boss.sandstorm-duration", 80);
            desertHungerDuration = cfg.getInt("desert-boss.hunger-duration", 100);
            desertHungerLevel = cfg.getInt("desert-boss.hunger-level", 1);
            desertSandblastDamage = cfg.getDouble("desert-boss.sandblast-damage", 6.0);
            desertArrowSlownessDuration = cfg.getInt("desert-boss.arrow-slowness-duration", 60);
            desertArrowSlownessLevel = cfg.getInt("desert-boss.arrow-slowness-level", 1);

            // Награды Пустыни
            desertRewardGold = cfg.getInt("desert-boss.reward-gold", 12);
            desertRewardCactus = cfg.getInt("desert-boss.reward-cactus", 8);
            desertRewardSandstone = cfg.getInt("desert-boss.reward-sandstone", 16);
            desertRewardRabbitHide = cfg.getInt("desert-boss.reward-rabbit-hide", 6);
            desertRewardExp = cfg.getInt("desert-boss.reward-exp", 140);
        }
    }
}
