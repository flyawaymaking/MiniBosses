package com.flyaway.minibosses.boss;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public interface MiniBoss {
    String getType();

    String getName();

    LivingEntity getEntity();

    double getHealthPercent();

    boolean shouldUseAbility();

    boolean shouldSummonHelpers(double healthPercent);

    void useSpecialAbility();

    void summonHelpers();

    void onDeath(Player killer);
}
