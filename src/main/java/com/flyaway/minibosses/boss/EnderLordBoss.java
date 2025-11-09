package com.flyaway.minibosses.boss;

import com.flyaway.minibosses.MiniBosses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Objects;

public class EnderLordBoss extends AbstractMiniBoss {

    private static final String BOSS_NAME_COLORED = "§dПовелитель Энда";

    public EnderLordBoss(MiniBosses plugin, Location location) {
        super(plugin, "ender", BOSS_NAME_COLORED,
                (Enderman) location.getWorld().spawnEntity(location, EntityType.ENDERMAN), BarColor.PURPLE);
        setupBoss();
        setupBossBar();
        startBossBarUpdater();
        setHelperWave();
    }

    public EnderLordBoss(MiniBosses plugin, LivingEntity entity) {
        super(plugin, "ender", BOSS_NAME_COLORED, entity, BarColor.PURPLE);
        setupBossBar();
        startBossBarUpdater();
    }

    private void setupBoss() {
        Enderman enderman = (Enderman) entity;

        Component bossName = Component.text("Повелитель Энда", NamedTextColor.LIGHT_PURPLE);
        enderman.customName(bossName);
        enderman.setCustomNameVisible(true);

        double bossHealth = plugin.getConfigManager().getEnderBossHealth();
        double attackMultiplier = plugin.getConfigManager().getEnderBossAttackMultiplier();
        double speedMultiplier = plugin.getConfigManager().getEnderBossSpeedMultiplier();

        Objects.requireNonNull(enderman.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(bossHealth);
        enderman.setHealth(bossHealth);
        double attackDamage = Objects.requireNonNull(enderman.getAttribute(Attribute.ATTACK_DAMAGE)).getBaseValue();
        Objects.requireNonNull(enderman.getAttribute(Attribute.ATTACK_DAMAGE)).setBaseValue(attackDamage * attackMultiplier);
        double moveSpeed = Objects.requireNonNull(enderman.getAttribute(Attribute.MOVEMENT_SPEED)).getBaseValue();
        Objects.requireNonNull(enderman.getAttribute(Attribute.MOVEMENT_SPEED)).setBaseValue(moveSpeed * speedMultiplier);
        enderman.setRemoveWhenFarAway(false);

        enderman.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 1));
        enderman.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 1));
        enderman.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        enderman.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1));

        plugin.markAsBoss(entity, "ender");
    }

    @Override
    public boolean shouldUseAbility() {
        return random.nextDouble() < plugin.getConfigManager().getEnderAbilityChance();
    }

    @Override
    public void useSpecialAbility() {
        if (random.nextBoolean()) {
            teleportAndBlind();
        } else {
            pullPlayers();
        }
    }

    private void teleportAndBlind() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        List<Player> nearbyPlayers = getNearbyPlayers(plugin.getConfigManager().getEnderAbilityRadius());
        if (!nearbyPlayers.isEmpty()) {
            // Телепортируем босса к случайному игроку
            Player targetPlayer = nearbyPlayers.get(random.nextInt(nearbyPlayers.size()));
            Location teleportLoc = targetPlayer.getLocation().add(
                    random.nextDouble() * 4 - 2,
                    0,
                    random.nextDouble() * 4 - 2
            );

            if (entity instanceof Enderman) {
                ((Enderman) entity).teleport(teleportLoc);
            } else {
                entity.teleport(teleportLoc);
            }

            // Ослепляем всех игроков в радиусе
            for (Player player : nearbyPlayers) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.BLINDNESS,
                        plugin.getConfigManager().getEnderBlindnessDuration(),
                        1
                ));
                world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

                if (plugin.getConfigManager().isShowAbilityMessage()) {
                    Component message = Component.text("Повелитель Энда ослепил вас!", NamedTextColor.LIGHT_PURPLE);
                    player.sendMessage(message);
                }
            }

            world.spawnParticle(Particle.REVERSE_PORTAL, loc, 30, 2, 2, 2, 0.1);
        }
    }

    private void pullPlayers() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

        for (Player player : getNearbyPlayers(plugin.getConfigManager().getEnderTeleportRadius())) {
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
                        PotionEffectType.SLOWNESS, 40, 1
                ));

                if (plugin.getConfigManager().isShowAbilityMessage()) {
                 Component message = Component.text("Повелитель Энда притягивает вас к себе!", NamedTextColor.LIGHT_PURPLE);
                 player.sendMessage(message);
                 }
            }
        }
        world.spawnParticle(Particle.REVERSE_PORTAL, loc, 50, 3, 2, 3, 0.2);
    }

    @Override
    public void summonHelpers() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        for (int i = 0; i < plugin.getConfigManager().getEnderHelperCount(); i++) {
            Location spawnLoc = loc.clone().add(
                    random.nextDouble() * 4 - 2,
                    0,
                    random.nextDouble() * 4 - 2
            );

            Endermite endermite = (Endermite) world.spawnEntity(spawnLoc, EntityType.ENDERMITE);

            Component helperName = Component.text("Слуга Энда", NamedTextColor.LIGHT_PURPLE);
            endermite.customName(helperName);
            endermite.setCustomNameVisible(true);

            Objects.requireNonNull(endermite.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(plugin.getConfigManager().getEnderHelperHealth());
            double attackDamage = Objects.requireNonNull(endermite.getAttribute(Attribute.ATTACK_DAMAGE)).getBaseValue();
            double attackMultiplier = plugin.getConfigManager().getEnderHelperAttackMultiplier();
            Objects.requireNonNull(endermite.getAttribute(Attribute.ATTACK_DAMAGE)).setBaseValue(attackDamage * attackMultiplier);
            endermite.setHealth(plugin.getConfigManager().getEnderHelperHealth());

            Player target = findNearestPlayer();
            if (target != null) endermite.setTarget(target);
        }

        world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        world.spawnParticle(Particle.PORTAL, loc, 20, 2, 1, 2, 0.1);
        world.playEffect(loc, Effect.MOBSPAWNER_FLAMES, 10);
    }

    @Override
    public void giveRewards() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        world.dropItemNaturally(loc, new ItemStack(Material.ENDER_PEARL, plugin.getConfigManager().getEnderRewardEnderPearls()));
        world.dropItemNaturally(loc, new ItemStack(Material.ENDER_EYE, plugin.getConfigManager().getEnderRewardEnderEyes()));
        world.dropItemNaturally(loc, new ItemStack(Material.CHORUS_FRUIT, plugin.getConfigManager().getEnderRewardChorusFruit()));

        if (plugin.getConfigManager().getEnderRewardShulkerShells() > 0) {
            world.dropItemNaturally(loc, new ItemStack(Material.SHULKER_SHELL, plugin.getConfigManager().getEnderRewardShulkerShells()));
        }

        spawnExperienceOrbs(plugin.getConfigManager().getEnderRewardExp());
    }
}
