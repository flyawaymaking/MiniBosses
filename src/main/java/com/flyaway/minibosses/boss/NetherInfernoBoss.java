package com.flyaway.minibosses.boss;

import com.flyaway.minibosses.MiniBosses;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class NetherInfernoBoss extends AbstractMiniBoss {

    public NetherInfernoBoss(MiniBosses plugin, Location location) {
        super(plugin, "nether", ChatColor.RED + "Инфернальный Ифрит",
              (Blaze) location.getWorld().spawnEntity(location, EntityType.BLAZE), BarColor.RED);
        setupBoss();
        setupBossBar();
        startBossBarUpdater();
        setHelperWave();
    }

    public NetherInfernoBoss(MiniBosses plugin, LivingEntity entity) {
        super(plugin, "nether", ChatColor.RED + "Инфернальный Ифрит", entity, BarColor.RED);
        setupBossBar();
        startBossBarUpdater();
    }

    private void setupBoss() {
        Blaze blaze = (Blaze) entity;

        blaze.setCustomName(name);
        blaze.setCustomNameVisible(true);
        blaze.getAttribute(Attribute.MAX_HEALTH).setBaseValue(plugin.getConfigManager().getNetherBossHealth());
        blaze.setHealth(plugin.getConfigManager().getNetherBossHealth());
        blaze.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(12);
        blaze.setRemoveWhenFarAway(false);

        blaze.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        blaze.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1));
        blaze.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 1));

        plugin.markAsBoss(entity, "nether");
    }

    @Override
    public boolean shouldUseAbility() {
        return random.nextDouble() < plugin.getConfigManager().getNetherAbilityChance();
    }

    @Override
    public void useSpecialAbility() {
        double abilityRoll = random.nextDouble();
        if (abilityRoll < 0.4) {
            fireStorm();
        } else if (abilityRoll < 0.8) {
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
//             player.sendMessage(ChatColor.RED + "Инфернальный Ифрит сжигает вас огненным штормом!");
        }
    }

    private void fireballs() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);

        for (Player player : getNearbyPlayers(plugin.getConfigManager().getNetherFireballRadius())) {
            Fireball fireball = world.spawn(loc, Fireball.class);
            fireball.setShooter(entity);
            fireball.setDirection(player.getLocation().toVector().subtract(loc.toVector()).normalize());
            fireball.setYield(plugin.getConfigManager().getNetherFireballPower());
            fireball.setIsIncendiary(true);
//             player.sendMessage(ChatColor.RED + "Инфернальный Ифрит запускает в вас огненный шар!");
        }
        world.spawnParticle(Particle.FLAME, loc, 30, 2, 1, 2, 0.1);
    }

    private void teleportToPlayer() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        world.playSound(loc, Sound.ENTITY_BLAZE_AMBIENT, 1.0f, 1.5f);

        List<Player> nearbyPlayers = getNearbyPlayers(plugin.getConfigManager().getNetherTeleportRadius());
        if (!nearbyPlayers.isEmpty()) {
            Player targetPlayer = nearbyPlayers.get(random.nextInt(nearbyPlayers.size()));
            Location targetLoc = targetPlayer.getLocation();

            Location teleportLoc = findSafeTeleportLocation(targetLoc, 3);
            if (teleportLoc != null) {
                entity.teleport(teleportLoc);

                world.playSound(teleportLoc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);
                world.spawnParticle(Particle.FLAME, teleportLoc, 30, 2, 2, 2, 0.2);
                world.spawnParticle(Particle.LARGE_SMOKE, teleportLoc, 15, 1, 1, 1, 0.1);

//                 targetPlayer.sendMessage(ChatColor.RED + "Инфернальный Ифрит телепортируется к вам!");
                targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1));
            }
        }
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
            magma.setSize(plugin.getConfigManager().getNetherHelperSize());
            magma.setCustomName(ChatColor.RED + "Расплавленный Слизень");

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
