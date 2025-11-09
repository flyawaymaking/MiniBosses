package com.flyaway.minibosses.boss;

import com.flyaway.minibosses.MiniBosses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Objects;

public class NetherInfernoBoss extends AbstractMiniBoss {

    private static final String BOSS_NAME_COLORED = "§cИнфернальный Ифрит";

    public NetherInfernoBoss(MiniBosses plugin, Location location) {
        super(plugin, "nether", BOSS_NAME_COLORED,
                (Blaze) location.getWorld().spawnEntity(location, EntityType.BLAZE), BarColor.RED);
        setupBoss();
        setupBossBar();
        startBossBarUpdater();
        setHelperWave();
    }

    public NetherInfernoBoss(MiniBosses plugin, LivingEntity entity) {
        super(plugin, "nether", BOSS_NAME_COLORED, entity, BarColor.RED);
        setupBossBar();
        startBossBarUpdater();
    }

    private void setupBoss() {
        Blaze blaze = (Blaze) entity;

        // Для имени существа используем Adventure Component
        Component bossName = Component.text("Инфернальный Ифрит", NamedTextColor.RED);
        blaze.customName(bossName);
        blaze.setCustomNameVisible(true);

        double bossHealth = plugin.getConfigManager().getNetherBossHealth();
        double attackMultiplier = plugin.getConfigManager().getNetherBossAttackMultiplier();
        double speedMultiplier = plugin.getConfigManager().getNetherBossSpeedMultiplier();

        Objects.requireNonNull(blaze.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(bossHealth);
        blaze.setHealth(bossHealth);
        double attackDamage = Objects.requireNonNull(blaze.getAttribute(Attribute.ATTACK_DAMAGE)).getBaseValue();
        Objects.requireNonNull(blaze.getAttribute(Attribute.ATTACK_DAMAGE)).setBaseValue(attackDamage * attackMultiplier);
        double moveSpeed = Objects.requireNonNull(blaze.getAttribute(Attribute.MOVEMENT_SPEED)).getBaseValue();
        Objects.requireNonNull(blaze.getAttribute(Attribute.MOVEMENT_SPEED)).setBaseValue(moveSpeed * speedMultiplier);

        blaze.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        blaze.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1));

        plugin.markAsBoss(entity, "nether");
    }

    @Override
    public boolean shouldUseAbility() {
        return random.nextDouble() < plugin.getConfigManager().getNetherAbilityChance();
    }

    @Override
    public void useSpecialAbility() {
        double abilityRoll = random.nextDouble();
        if (abilityRoll < 0.3) {
            fireStorm();
        } else if (abilityRoll < 0.7) {
            fireballs();
        } else {
            teleportToPlayer();
        }
    }

    private void fireStorm() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        world.spawnParticle(Particle.FLAME, loc, 50, 5, 3, 5, 0.1);
        world.spawnParticle(Particle.LAVA, loc, 20, 3, 1, 3, 0.1);
        world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);

        for (Player player : getNearbyPlayers(plugin.getConfigManager().getNetherAbilityRadius())) {
            player.setFireTicks(plugin.getConfigManager().getNetherFireDuration());
            player.damage(plugin.getConfigManager().getNetherFireDamage());

            if (plugin.getConfigManager().isShowAbilityMessage()) {
                Component message = Component.text("Инфернальный Ифрит сжигает вас огненным штормом!", NamedTextColor.RED);
                player.sendMessage(message);
            }
        }
    }

    public void attackPlayers(SmallFireball proj) {
        World world = entity.getWorld();

        // Ускоряем основной фаербол
        proj.setVelocity(proj.getVelocity().multiply(2.0));

        for (Player player : getNearbyPlayers(plugin.getConfigManager().getNetherFireballRadius())) {
            Vector toTarget = player.getEyeLocation().toVector()
                    .subtract(entity.getEyeLocation().toVector())
                    .normalize();

            // Направление в игрока
            Location spawnLoc = entity.getEyeLocation().add(toTarget.multiply(0.5));
            double baseSpeed = 0.6; // скорость фаербола

            int fireballCount = 4;
            for (int i = 0; i < fireballCount; i++) {
                int delay = i * 5;

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!entity.isValid() || entity.isDead()) return;

                        Vector spread = toTarget.clone().add(randomSpreadVector()).normalize();

                        SmallFireball extra = (SmallFireball) world.spawnEntity(spawnLoc, EntityType.SMALL_FIREBALL);
                        extra.getPersistentDataContainer().set(plugin.getExtraFireballsKey(), PersistentDataType.BYTE, (byte) 1);
                        extra.setShooter(entity);
                        extra.setVelocity(spread.multiply(baseSpeed * 2.0));
                    }
                }.runTaskLater(plugin, delay);
            }
        }
    }

    private Vector randomSpreadVector() {
        double maxOffset = 0.08;
        double ox = (random.nextDouble() * 2 - 1) * maxOffset;
        double oy = (random.nextDouble() * 2 - 1) * maxOffset;
        double oz = (random.nextDouble() * 2 - 1) * maxOffset;
        return new Vector(ox, oy, oz);
    }

    private void fireballs() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        // Звук и эффект выстрела
        world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);
        world.spawnParticle(Particle.FLAME, loc, 30, 2, 1, 2, 0.1);

        double baseSpeed = 0.8; // базовая скорость полёта
        double spreadAmount = 0.3; // разброс между снарядами

        for (Player player : getNearbyPlayers(plugin.getConfigManager().getNetherFireballRadius())) {
            Vector baseDir = player.getLocation().toVector().subtract(loc.toVector()).normalize();
            Vector perpendicular = new Vector(-baseDir.getZ(), 0, baseDir.getX()).normalize();

            Location spawnLoc = loc.clone().add(baseDir.multiply(1.0)); // немного впереди от Ифрита

            // Центральный фаербол
            Fireball center = world.spawn(spawnLoc, Fireball.class);
            center.setShooter(entity);
            center.setDirection(baseDir);
            center.setVelocity(baseDir.clone().multiply(baseSpeed));
            center.setYield(plugin.getConfigManager().getNetherFireballPower());
            center.setIsIncendiary(true);

            // Левый фаербол
            Fireball left = world.spawn(spawnLoc.clone().add(perpendicular.clone().multiply(spreadAmount)), Fireball.class);
            left.setShooter(entity);
            Vector leftVel = baseDir.clone().add(perpendicular.clone().multiply(spreadAmount)).normalize().multiply(baseSpeed);
            left.setDirection(leftVel);
            left.setVelocity(leftVel);
            left.setYield(plugin.getConfigManager().getNetherFireballPower());
            left.setIsIncendiary(true);

            // Правый фаербол
            Fireball right = world.spawn(spawnLoc.clone().subtract(perpendicular.clone().multiply(spreadAmount)), Fireball.class);
            right.setShooter(entity);
            Vector rightVel = baseDir.clone().subtract(perpendicular.clone().multiply(spreadAmount)).normalize().multiply(baseSpeed);
            right.setDirection(rightVel);
            right.setVelocity(rightVel);
            right.setYield(plugin.getConfigManager().getNetherFireballPower());
            right.setIsIncendiary(true);

            if (plugin.getConfigManager().isShowAbilityMessage()) {
                Component message = Component.text("Инфернальный Ифрит выпускает фаерболы!", NamedTextColor.RED);
                player.sendMessage(message);
            }
        }
    }

    private void teleportToPlayer() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        world.playSound(loc, Sound.ENTITY_BLAZE_AMBIENT, 1.0f, 1.5f);

        List<Player> nearbyPlayers = getNearbyPlayers(plugin.getConfigManager().getNetherTeleportRadius());
        if (!nearbyPlayers.isEmpty()) {
            Player targetPlayer = nearbyPlayers.get(random.nextInt(nearbyPlayers.size()));
            Location targetLoc = targetPlayer.getLocation();

            Location teleportLoc = findSafeTeleportLocation(targetLoc);
            if (teleportLoc != null) {
                entity.teleport(teleportLoc);

                world.playSound(teleportLoc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);
                world.spawnParticle(Particle.FLAME, teleportLoc, 30, 2, 2, 2, 0.2);
                world.spawnParticle(Particle.LARGE_SMOKE, teleportLoc, 15, 1, 1, 1, 0.1);

                if (plugin.getConfigManager().isShowAbilityMessage()) {
                    Component message = Component.text("Инфернальный Ифрит телепортируется к вам!", NamedTextColor.RED);
                    targetPlayer.sendMessage(message);
                }
                targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1));
            }
        }
    }

    protected Location findSafeTeleportLocation(Location center) {
        World world = center.getWorld();
        int radius = 10;

        for (int i = 0; i < 15; i++) {
            int x = center.getBlockX() + random.nextInt(radius * 2) - radius;
            int z = center.getBlockZ() + random.nextInt(radius * 2) - radius;
            int y = center.getBlockY() + random.nextInt(6) - 3; // немного выше/ниже текущей высоты

            Location testLoc = new Location(world, x + 0.5, y, z + 0.5);

            // Проверяем, чтобы место не было внутри блока
            if (!testLoc.getBlock().isPassable()) continue;

            // Проверяем, чтобы под мобом не было пустоты (но для ифрита можно позволить 1–2 блока)
            Material below = testLoc.clone().subtract(0, 2, 0).getBlock().getType();
            if (below == Material.LAVA || below == Material.WATER) continue;

            // Проверяем пространство вокруг
            if (testLoc.clone().add(0, 1, 0).getBlock().isPassable() &&
                    testLoc.clone().add(0, 2, 0).getBlock().isPassable()) {
                return testLoc;
            }
        }

        return null;
    }

    @Override
    public void summonHelpers() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        for (int i = 0; i < plugin.getConfigManager().getNetherHelperCount(); i++) {
            Location spawnLoc = loc.clone().add(
                    random.nextDouble() * 5 - 2.5,
                    0,
                    random.nextDouble() * 5 - 2.5
            );
            MagmaCube magma = (MagmaCube) world.spawnEntity(spawnLoc, EntityType.MAGMA_CUBE);
            double maxHp = Objects.requireNonNull(magma.getAttribute(Attribute.MAX_HEALTH)).getBaseValue();
            double hpMultiplier = plugin.getConfigManager().getNetherHelperHealthMultiplier();
            Objects.requireNonNull(magma.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(maxHp * hpMultiplier);
            magma.setHealth(maxHp * 1.5);
            double attackDamage = Objects.requireNonNull(magma.getAttribute(Attribute.ATTACK_DAMAGE)).getBaseValue();
            double attackMultiplier = plugin.getConfigManager().getNetherHelperAttackMultiplier();
            Objects.requireNonNull(magma.getAttribute(Attribute.ATTACK_DAMAGE)).setBaseValue(attackDamage * attackMultiplier);
            magma.setSize(plugin.getConfigManager().getNetherHelperSize());

            // Для помощников используем Adventure Component
            Component helperName = Component.text("Расплавленный Слизень", NamedTextColor.RED);
            magma.customName(helperName);
            magma.setCustomNameVisible(true);

            Player target = findNearestPlayer();
            if (target != null) magma.setTarget(target);
        }

        world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
        world.spawnParticle(Particle.FLAME, loc, 30, 3, 1, 3, 0.1);
        world.playEffect(loc, Effect.MOBSPAWNER_FLAMES, 10);
    }

    @Override
    public void giveRewards() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        world.dropItemNaturally(loc, new ItemStack(Material.BLAZE_ROD, plugin.getConfigManager().getNetherRewardBlazeRods()));
        world.dropItemNaturally(loc, new ItemStack(Material.NETHER_BRICK, plugin.getConfigManager().getNetherRewardNetherBricks()));
        world.dropItemNaturally(loc, new ItemStack(Material.GOLD_NUGGET, plugin.getConfigManager().getNetherRewardGoldNuggets()));

        spawnExperienceOrbs(plugin.getConfigManager().getNetherRewardExp());
    }
}
