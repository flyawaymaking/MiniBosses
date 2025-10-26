package com.flyaway.minibosses;

import com.flyaway.minibosses.boss.*;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.Random;

public class BossListener implements Listener {
    private final MiniBosses plugin;
    private final Random random = new Random();

    public BossListener(MiniBosses plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlazeShoot(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof SmallFireball proj)) return;
        ProjectileSource shooter = proj.getShooter();
        if (!(shooter instanceof Blaze blaze)) return;
        // Пропускаем фаерболы, созданные вручную
        if (proj.getPersistentDataContainer().has(plugin.getExtraFireballsKey(), PersistentDataType.BYTE)) return;
        if (!plugin.isBoss(blaze)) return;

        String bossType = plugin.getBossType(blaze);
        MiniBoss boss = createBossInstance(bossType, blaze);

        if (!(boss instanceof NetherInfernoBoss netherBoss)) return;

        netherBoss.attackPlayers(proj);
    }

    @EventHandler
    public void onBossDamage(EntityDamageEvent event) {
        // Использование способностей
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
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();

        // Проверяем, что это стрела
        if (projectile instanceof Arrow arrow) {
            // Отражение стрел в игроков
            // Проверяем, что стрелу выпустил игрок
            if (!(arrow.getShooter() instanceof Player shooter)) return;

            // Проверяем, что цель — минибосс
            if (!(event.getHitEntity() instanceof LivingEntity entity)) return;
            if (!plugin.isBoss(entity)) return;

            // 50% вероятность отражения
            if (random.nextBoolean()) {
                reflectArrow(arrow, shooter, entity);
            }
        } else if (projectile instanceof SmallFireball fireball) {
            // Урон от доп. фаерболов блейза
            if (!(fireball.getShooter() instanceof LivingEntity shooter)) return;

            PersistentDataContainer pdc = fireball.getPersistentDataContainer();
            if (!pdc.has(plugin.getExtraFireballsKey(), PersistentDataType.BYTE)) return;

            if (event.getHitEntity() instanceof Player player) {
                double damage = 9.0; // Урон от выстрела
                player.damage(damage, shooter); // Наносим урон с указанием источника
                player.setFireTicks(40); // Поджигаем на 2 секунды

                fireball.remove(); // Убираем фаербол, чтобы не продолжал лететь
            }
        }
    }

    private void reflectArrow(Arrow oldArrow, Player shooter, LivingEntity boss) {
        World world = oldArrow.getWorld();
        Location hitLoc = oldArrow.getLocation();

        // Удаляем старую стрелу
        oldArrow.remove();

        // Вектор направления обратно к стрелявшему
        Vector direction = shooter.getEyeLocation().toVector().subtract(hitLoc.toVector()).normalize();

        // Спавним новую стрелу
        Arrow newArrow = world.spawnArrow(
            hitLoc.add(0, 0.2, 0), // чуть выше, чтобы не в землю
            direction,
            1.6f, // скорость
            0.0f  // разброс
        );

        newArrow.setShooter(boss);
        newArrow.setDamage(oldArrow.getDamage());
        newArrow.setGravity(true);
        newArrow.setCritical(true); // визуальный эффект
        newArrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);

        world.playSound(hitLoc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.2f);
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
