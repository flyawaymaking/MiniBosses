package com.flyaway.minibosses.boss;

import com.flyaway.minibosses.MiniBosses;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

public class DesertSandlordBoss extends AbstractMiniBoss {

    public DesertSandlordBoss(MiniBosses plugin, Location location) {
        super(plugin, "desert", ChatColor.GOLD + "Повелитель Песков",
              (Husk) location.getWorld().spawnEntity(location, EntityType.HUSK), BarColor.YELLOW);
        setupBoss();
        setupBossBar();
        startBossBarUpdater();
        setHelperWave();
    }

    public DesertSandlordBoss(MiniBosses plugin, LivingEntity entity) {
        super(plugin, "desert", ChatColor.GOLD + "Повелитель Песков", entity, BarColor.YELLOW);
        setupBossBar();
        startBossBarUpdater();
    }

    private void setupBoss() {
        Husk husk = (Husk) entity;

        husk.setCustomName(name);
        husk.setCustomNameVisible(true);
        husk.getAttribute(Attribute.MAX_HEALTH).setBaseValue(plugin.getConfigManager().getDesertBossHealth());
        husk.setHealth(plugin.getConfigManager().getDesertBossHealth());
        double attackDamage = husk.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue();
        husk.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(attackDamage * 2);
        double moveSpeed = husk.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue();
        husk.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(moveSpeed * 1.1);
        husk.setRemoveWhenFarAway(false);

        husk.getEquipment().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
        husk.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));

        husk.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 1));
        husk.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        husk.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1));

        plugin.markAsBoss(entity, "desert");
    }

    @Override
    public boolean shouldUseAbility() {
        return random.nextDouble() < plugin.getConfigManager().getDesertAbilityChance();
    }

    @Override
    public void useSpecialAbility() {
        if (random.nextBoolean()) {
            sandstorm();
        } else {
            slowingArrows();
        }
    }

    private void sandstorm() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        world.playSound(loc, Sound.ENTITY_HUSK_HURT, 1.0f, 0.5f);
        world.playSound(loc, Sound.BLOCK_SAND_BREAK, 1.0f, 0.8f);

        world.spawnParticle(Particle.CLOUD, loc, 40, 4, 2, 4, 0.2);
        world.spawnParticle(Particle.FALLING_DUST, loc, 30, 3, 2, 3, 0.3, Material.SAND.createBlockData());

        for (Player player : getNearbyPlayers(plugin.getConfigManager().getDesertAbilityRadius())) {
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.HUNGER,
                plugin.getConfigManager().getDesertHungerDuration(),
                plugin.getConfigManager().getDesertHungerLevel()
            ));
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS,
                plugin.getConfigManager().getDesertSandstormDuration(),
                0
            ));

            player.damage(plugin.getConfigManager().getDesertSandblastDamage());

            Vector direction = player.getLocation().toVector().subtract(loc.toVector()).normalize();
            player.setVelocity(direction.multiply(1.0));

//             player.sendMessage(ChatColor.YELLOW + "Повелитель Песков поднимает песчаную бурю!");
        }
    }

    private void slowingArrows() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        world.playSound(loc, Sound.ENTITY_SKELETON_SHOOT, 1.0f, 0.8f);

        for (Player player : getNearbyPlayers(plugin.getConfigManager().getDesertAbilityRadius())) {
            // Берем направление от босса к глазам игрока
            Vector direction = player.getEyeLocation().toVector().subtract(loc.toVector()).normalize();

            // Спавним стрелу с нужным направлением
            Arrow arrow = world.spawnArrow(
                loc.add(0, 1.5, 0), // немного выше центра босса, чтобы стрела не шла из ног
                direction,
                1.6f, // скорость
                0.0f  // разброс (0 = точное попадание)
            );

            arrow.setShooter(entity);
            arrow.setDamage(plugin.getConfigManager().getDesertArrowDamage());

            arrow.addCustomEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                plugin.getConfigManager().getDesertArrowSlownessDuration(),
                plugin.getConfigManager().getDesertArrowSlownessLevel()
            ), true);

            // Можно включить зажжение для эффектности:
            // arrow.setFireTicks(40);
        }
    }

    @Override
    public void summonHelpers() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        for (int i = 0; i < plugin.getConfigManager().getDesertHelperCount(); i++) {
            Location spawnLoc = loc.clone().add(
                random.nextDouble() * 5 - 2.5,
                0,
                random.nextDouble() * 5 - 2.5
            );
            Husk husk = (Husk) world.spawnEntity(spawnLoc, EntityType.HUSK);
            husk.setCustomName(ChatColor.YELLOW + "Песчаный Воин");
            husk.getAttribute(Attribute.MAX_HEALTH).setBaseValue(plugin.getConfigManager().getDesertHelperHealth());
            husk.setHealth(plugin.getConfigManager().getDesertHelperHealth());
            husk.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SHOVEL));

            Player target = findNearestPlayer();
            if (target != null) husk.setTarget(target);
        }

        world.playSound(loc, Sound.ENTITY_HUSK_AMBIENT, 1.0f, 0.7f);
        world.spawnParticle(Particle.CLOUD, loc, 25, 3, 1, 3, 0.1);
        world.playEffect(loc, Effect.MOBSPAWNER_FLAMES, 10);
    }

    @Override
    public void giveRewards() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        world.dropItemNaturally(loc, new ItemStack(Material.GOLD_INGOT, plugin.getConfigManager().getDesertRewardGold()));
        world.dropItemNaturally(loc, new ItemStack(Material.CACTUS, plugin.getConfigManager().getDesertRewardCactus()));
        world.dropItemNaturally(loc, new ItemStack(Material.SANDSTONE, plugin.getConfigManager().getDesertRewardSandstone()));
        world.dropItemNaturally(loc, new ItemStack(Material.RABBIT_HIDE, plugin.getConfigManager().getDesertRewardRabbitHide()));

        spawnExperienceOrbs(plugin.getConfigManager().getDesertRewardExp());
    }
}
