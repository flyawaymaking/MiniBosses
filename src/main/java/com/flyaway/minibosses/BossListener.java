package com.flyaway.minibosses;

import com.flyaway.minibosses.boss.*;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class BossListener implements Listener {
    private final MiniBosses plugin;

    public BossListener(MiniBosses plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBossDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;

        LivingEntity entity = (LivingEntity) event.getEntity();

        if (!plugin.isBoss(entity)) return;

        String bossType = plugin.getBossType(entity);
        MiniBoss boss = createBossInstance(bossType, entity);

        if (boss == null) return;

        // Получаем текущее здоровье после урона
        double newHealth = entity.getHealth() - event.getFinalDamage();
        double maxHealth = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        double healthPercent = Math.max(0, newHealth) / maxHealth;

        // Проверяем призыв помощников
        if (boss.shouldSummonHelpers(healthPercent)) {
            boss.summonHelpers();
        }

        // Проверяем использование способности
        if (boss.shouldUseAbility()) {
            boss.useSpecialAbility();
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (!plugin.isBoss(entity)) return;

        String bossType = plugin.getBossType(entity);
        MiniBoss boss = createBossInstance(bossType, entity);

        if (boss != null) {
            Player killer = entity.getKiller();
            if (killer != null) {
                boss.onDeath(killer);
            }
        }

        // Очищаем PDC
        plugin.unmarkAsBoss(entity);
    }

    private MiniBoss createBossInstance(String bossType, LivingEntity entity) {
        switch (bossType) {
            case "ender":
                EnderLordBoss enderBoss = new EnderLordBoss(plugin, entity);
                return enderBoss;
            case "nether":
                NetherInfernoBoss netherBoss = new NetherInfernoBoss(plugin, entity);
                return netherBoss;
            case "forest":
                ForestGuardianBoss forestBoss = new ForestGuardianBoss(plugin, entity);
                return forestBoss;
            case "desert":
                DesertSandlordBoss desertBoss = new DesertSandlordBoss(plugin, entity);
                return desertBoss;
            default:
                return null;
        }
    }
}
