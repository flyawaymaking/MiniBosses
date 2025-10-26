package com.flyaway.minibosses.boss;

import com.flyaway.minibosses.MiniBosses;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

public class ForestGuardianBoss extends AbstractMiniBoss {

    public ForestGuardianBoss(MiniBosses plugin, Location location) {
        super(plugin, "forest", ChatColor.GREEN + "Защитник Леса",
              (IronGolem) location.getWorld().spawnEntity(location, EntityType.IRON_GOLEM), BarColor.GREEN);
        setupBoss();
        setupBossBar();
        startBossBarUpdater();
        setHelperWave();
    }

    public ForestGuardianBoss(MiniBosses plugin, LivingEntity entity) {
        super(plugin, "forest", ChatColor.GREEN + "Защитник Леса", entity, BarColor.GREEN);
        setupBossBar();
        startBossBarUpdater();
    }

    private void setupBoss() {
        IronGolem golem = (IronGolem) entity;

        golem.setCustomName(name);
        golem.setCustomNameVisible(true);
        golem.getAttribute(Attribute.MAX_HEALTH).setBaseValue(plugin.getConfigManager().getForestBossHealth());
        golem.setHealth(plugin.getConfigManager().getForestBossHealth());
        double attackDamage = golem.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue();
        golem.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(attackDamage * 1.2);
        double moveSpeed = golem.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue();
        golem.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(moveSpeed * 1.1);
        golem.setRemoveWhenFarAway(false);

        golem.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 2));

        plugin.markAsBoss(entity, "forest");
    }

    @Override
    public boolean shouldUseAbility() {
        return random.nextDouble() < plugin.getConfigManager().getForestAbilityChance();
    }

    @Override
    public void useSpecialAbility() {
        if (random.nextBoolean()) {
            earthquake();
        } else {
            pullPlayers();
        }
    }

    private void earthquake() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        world.playSound(loc, Sound.ENTITY_IRON_GOLEM_HURT, 1.0f, 0.5f);
        world.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.8f);

        world.spawnParticle(Particle.BLOCK, loc, 30, 3, 1, 3, 0.5, Material.DIRT.createBlockData());
        world.spawnParticle(Particle.BLOCK, loc, 20, 3, 1, 3, 0.5, Material.STONE.createBlockData());

        for (Player player : getNearbyPlayers(plugin.getConfigManager().getForestAbilityRadius())) {
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                plugin.getConfigManager().getForestSlownessDuration(),
                plugin.getConfigManager().getForestSlownessLevel()
            ));
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.WEAKNESS,
                plugin.getConfigManager().getForestWeaknessDuration(),
                1
            ));

            Vector direction = player.getLocation().toVector().subtract(loc.toVector()).normalize();
            player.setVelocity(direction.multiply(plugin.getConfigManager().getForestKnockbackPower()));

//             player.sendMessage(ChatColor.DARK_GREEN + "Защитник Леса вызывает землетрясение!");
        }
    }

    private void pullPlayers() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        world.playSound(loc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.6f);
        world.playSound(loc, Sound.BLOCK_CHAIN_BREAK, 1.0f, 0.8f);

        for (Player player : getNearbyPlayers(plugin.getConfigManager().getForestPullRadius())) {
            Vector direction = loc.toVector().subtract(player.getLocation().toVector()).normalize();
            player.setVelocity(direction.multiply(plugin.getConfigManager().getForestPullPower()));

            player.addPotionEffect(new PotionEffect(
                PotionEffectType.WEAKNESS,
                plugin.getConfigManager().getForestPullWeaknessDuration(),
                1
            ));

//             player.sendMessage(ChatColor.DARK_GREEN + "Защитник Леса притягивает вас магией природы!");
        }

        world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 25, 3, 2, 3, 0.1);
    }

    @Override
    public void summonHelpers() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        for (int i = 0; i < plugin.getConfigManager().getForestHelperCount(); i++) {
            Location spawnLoc = loc.clone().add(
                random.nextDouble() * 6 - 3,
                0,
                random.nextDouble() * 6 - 3
            );
            Wolf wolf = (Wolf) world.spawnEntity(spawnLoc, EntityType.WOLF);
            wolf.setAngry(true);
            wolf.setCustomName(ChatColor.GRAY + "Лесной Охотник");
            wolf.getAttribute(Attribute.MAX_HEALTH).setBaseValue(plugin.getConfigManager().getForestHelperHealth());
            wolf.setHealth(plugin.getConfigManager().getForestHelperHealth());
            wolf.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 1));

            Player target = findNearestPlayer();
            if (target != null) wolf.setTarget(target);
        }

        world.playSound(loc, Sound.ENTITY_WOLF_AMBIENT, 1.0f, 1.0f);
        world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 15, 2, 1, 2, 0.1);
        world.playEffect(loc, Effect.MOBSPAWNER_FLAMES, 10);
    }

    @Override
    public void giveRewards() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        world.dropItemNaturally(loc, new ItemStack(Material.IRON_INGOT, plugin.getConfigManager().getForestRewardIron()));
        world.dropItemNaturally(loc, new ItemStack(Material.APPLE, plugin.getConfigManager().getForestRewardApples()));
        world.dropItemNaturally(loc, new ItemStack(Material.OAK_LOG, plugin.getConfigManager().getForestRewardLogs()));

        spawnExperienceOrbs(plugin.getConfigManager().getForestRewardExp());
    }
}
