package com.flyaway.minibosses.boss;

import com.flyaway.minibosses.MiniBosses;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Random;
import java.util.stream.Collectors;

public abstract class AbstractMiniBoss implements MiniBoss {
    protected final MiniBosses plugin;
    protected LivingEntity entity;
    protected final Random random = new Random();
    protected final String type;
    protected final String name;
    protected BossBar bossBar;
    protected BarColor bossBarColor;
    private static final Map<UUID, BossBar> bossBars = new HashMap<>();
    protected boolean bossBarInitialized = false;

    // Единственный конструктор
    public AbstractMiniBoss(MiniBosses plugin, String type, String name, LivingEntity entity, BarColor bossBarColor) {
        this.plugin = plugin;
        this.type = type;
        this.name = name;
        this.entity = entity;
        this.bossBarColor = bossBarColor;
    }

    public void setHelperWave() {
        saveHelperWave(0);
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public LivingEntity getEntity() {
        return entity;
    }

    @Override
    public double getHealthPercent() {
        if (entity == null || entity.isDead() || !entity.isValid()) return 0;
        return entity.getHealth() / entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
    }

    @Override
    public boolean shouldSummonHelpers(double healthPercent) {
        if (entity == null || entity.isDead()) return false;

        int currentWave = getHelperWave(); // Всегда читаем из PDC

        boolean shouldSummon = false;

        if (healthPercent <= 0.6 && currentWave < 1) {
            saveHelperWave(1);
            shouldSummon = true;
        } else if (healthPercent <= 0.4 && currentWave < 2) {
            saveHelperWave(2);
            shouldSummon = true;
        } else if (healthPercent <= 0.2 && currentWave < 3) {
            saveHelperWave(3);
            shouldSummon = true;
        }

        return shouldSummon;
    }

    protected int getHelperWave() {
        if (entity == null) return 0;
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return pdc.getOrDefault(getHelperWaveKey(), PersistentDataType.INTEGER, 0);
    }

    protected void saveHelperWave(int wave) {
        if (entity == null) return;
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(getHelperWaveKey(), PersistentDataType.INTEGER, wave);
    }

    private NamespacedKey getHelperWaveKey() {
        return new NamespacedKey(plugin, "miniboss_helper_wave");
    }

    // Вспомогательный метод для проверки валидности entity
    protected boolean isEntityValid() {
        return entity != null && !entity.isDead() && entity.isValid();
    }

    // === ВЫНЕСЕННЫЕ ПОВТОРЯЮЩИЕСЯ МЕТОДЫ ===

    protected void setupBossBar() {
        if (bossBarInitialized) {
            return; // Боссбар уже инициализирован
        }

        UUID entityId = entity.getUniqueId();

        // Проверяем, есть ли уже боссбар для этой entity
        if (bossBars.containsKey(entityId)) {
            bossBar = bossBars.get(entityId);
            bossBar.setTitle(name); // Обновляем название на случай изменения
        } else {
            bossBar = Bukkit.createBossBar(name, bossBarColor, BarStyle.SEGMENTED_10);
            bossBars.put(entityId, bossBar);

            // Устанавливаем начальное значение здоровья
            double healthPercent = getHealthPercent();
            bossBar.setProgress(Math.max(0, healthPercent));
        }

        updateBossBarPlayers();
        bossBarInitialized = true;
    }

    public static void cleanupBossBar(UUID entityId) {
        if (bossBars.containsKey(entityId)) {
            BossBar bar = bossBars.remove(entityId);
            bar.removeAll();
        }
    }

    protected void startBossBarUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || entity.isDead() || !entity.isValid()) {
                    bossBar.removeAll();
                    cancel();
                    return;
                }
                updateBossBar();
                updateBossBarPlayers();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    protected void updateBossBar() {
        double healthPercent = getHealthPercent();
        bossBar.setProgress(Math.max(0, healthPercent));
    }

    protected void updateBossBarPlayers() {
        // Добавляем игроков в радиусе
        for (Player player : getNearbyPlayers(30)) {
            if (!bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }

        // Убираем игроков которые ушли далеко - правильный способ
        List<Player> toRemove = new ArrayList<>();
        for (Player player : bossBar.getPlayers()) {
            if (!player.isOnline() || player.getLocation().distance(entity.getLocation()) > 40) {
                toRemove.add(player);
            }
        }
        for (Player player : toRemove) {
            bossBar.removePlayer(player);
        }
    }

    protected List<Player> getNearbyPlayers(double radius) {
        return entity.getNearbyEntities(radius, radius, radius).stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .collect(Collectors.toList());
    }

    protected Player findNearestPlayer() {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player player : entity.getWorld().getPlayers()) {
            double distance = player.getLocation().distance(entity.getLocation());
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    protected void executeDeathCommands(Player killer) {
        List<String> commands = plugin.getConfigManager().getDeathCommands(type);
        for (String command : commands) {
            String processedCommand = command.replace("%player%", killer.getName());
            if (processedCommand.startsWith("console:")) {
                String consoleCommand = processedCommand.substring(8);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
            } else {
                killer.performCommand(processedCommand);
            }
        }
    }

    protected void playDeathEffects() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        world.playSound(loc, Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        world.spawnParticle(Particle.EXPLOSION, loc, 10, 2, 2, 2, 0.1);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 20, 1, 1, 1, 0.2);
    }

    protected void spawnExperienceOrbs(int experience) {
        if (experience > 0) {
            Location loc = entity.getLocation();
            loc.getWorld().spawn(loc, org.bukkit.entity.ExperienceOrb.class)
               .setExperience(experience);
        }
    }

    protected Location findSafeTeleportLocation(Location center, int radius) {
        World world = center.getWorld();
        for (int i = 0; i < 10; i++) {
            int x = center.getBlockX() + random.nextInt(radius * 2) - radius;
            int z = center.getBlockZ() + random.nextInt(radius * 2) - radius;
            int y = world.getHighestBlockYAt(x, z);

            Location testLoc = new Location(world, x + 0.5, y + 1, z + 0.5);
            Material blockType = testLoc.getBlock().getType();
            Material blockAbove = testLoc.clone().add(0, 1, 0).getBlock().getType();

            if (blockType != Material.WATER && blockType != Material.LAVA &&
                blockType.isSolid() && blockAbove.isAir()) {
                return testLoc;
            }
        }
        return null;
    }

    public void onDeath(Player killer) {
        playDeathEffects();
        executeDeathCommands(killer);

        if (bossBar != null) {
            bossBar.removeAll();
            cleanupBossBar(entity.getUniqueId());
        }

        giveRewards();
    }

    public void giveRewards() {}
}
