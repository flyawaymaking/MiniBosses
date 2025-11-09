package com.flyaway.minibosses.boss;

import com.flyaway.minibosses.MiniBosses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

import java.util.Objects;

public class ForestGuardianBoss extends AbstractMiniBoss {

    private static final String BOSS_NAME_COLORED = "§aЗащитник Леса";

    public ForestGuardianBoss(MiniBosses plugin, Location location) {
        super(plugin, "forest", BOSS_NAME_COLORED,
                (IronGolem) location.getWorld().spawnEntity(location, EntityType.IRON_GOLEM), BarColor.GREEN);
        setupBoss();
        setupBossBar();
        startBossBarUpdater();
        setHelperWave();
    }

    public ForestGuardianBoss(MiniBosses plugin, LivingEntity entity) {
        super(plugin, "forest", BOSS_NAME_COLORED, entity, BarColor.GREEN);
        setupBossBar();
        startBossBarUpdater();
    }

    private void setupBoss() {
        IronGolem golem = (IronGolem) entity;

        // Для имени существа используем Adventure Component
        Component bossName = Component.text("Защитник Леса", NamedTextColor.GREEN);
        golem.customName(bossName);
        golem.setCustomNameVisible(true);

        double bossHealth = plugin.getConfigManager().getForestBossHealth();
        double attackMultiplier = plugin.getConfigManager().getForestBossAttackMultiplier();
        double speedMultiplier = plugin.getConfigManager().getForestBossSpeedMultiplier();

        Objects.requireNonNull(golem.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(bossHealth);
        golem.setHealth(bossHealth);
        double attackDamage = Objects.requireNonNull(golem.getAttribute(Attribute.ATTACK_DAMAGE)).getBaseValue();
        Objects.requireNonNull(golem.getAttribute(Attribute.ATTACK_DAMAGE)).setBaseValue(attackDamage * attackMultiplier);
        double moveSpeed = Objects.requireNonNull(golem.getAttribute(Attribute.MOVEMENT_SPEED)).getBaseValue();
        Objects.requireNonNull(golem.getAttribute(Attribute.MOVEMENT_SPEED)).setBaseValue(moveSpeed * speedMultiplier);
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

            if (plugin.getConfigManager().isShowAbilityMessage()) {
                Component message = Component.text("Защитник Леса вызывает землетрясение!", NamedTextColor.DARK_GREEN);
                player.sendMessage(message);
            }
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

            if (plugin.getConfigManager().isShowAbilityMessage()) {
             Component message = Component.text("Защитник Леса притягивает вас магией природы!", NamedTextColor.DARK_GREEN);
             player.sendMessage(message);
             }
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

            // Для помощников используем Adventure Component
            Component helperName = Component.text("Лесной Охотник", NamedTextColor.GRAY);
            wolf.customName(helperName);
            wolf.setCustomNameVisible(true);

            Objects.requireNonNull(wolf.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(plugin.getConfigManager().getForestHelperHealth());
            double attackDamage = Objects.requireNonNull(wolf.getAttribute(Attribute.ATTACK_DAMAGE)).getBaseValue();
            double attackMultiplier = plugin.getConfigManager().getForestHelperAttackMultiplier();
            Objects.requireNonNull(wolf.getAttribute(Attribute.ATTACK_DAMAGE)).setBaseValue(attackDamage * attackMultiplier);
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
