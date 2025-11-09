package com.flyaway.minibosses;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandHandler implements CommandExecutor, TabCompleter {

    private final MiniBosses plugin;

    public CommandHandler(MiniBosses plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("minibosses.reload") && !sender.hasPermission("minibosses.admin")) {
                    sender.sendMessage(Component.text("Недостаточно прав! Нужен permission: minibosses.reload").color(NamedTextColor.RED));
                    return true;
                }
                handleReloadCommand(sender);
                break;

            case "spawn":
                if (!sender.hasPermission("minibosses.spawn") && !sender.hasPermission("minibosses.admin")) {
                    sender.sendMessage(Component.text("Недостаточно прав! Нужен permission: minibosses.spawn").color(NamedTextColor.RED));
                    return true;
                }
                handleSpawnCommand(sender, args);
                break;

            case "cleanup":
                if (!sender.hasPermission("minibosses.cleanup") && !sender.hasPermission("minibosses.admin")) {
                    sender.sendMessage(Component.text("Недостаточно прав! Нужен permission: minibosses.cleanup").color(NamedTextColor.RED));
                    return true;
                }
                handleCleanupCommand(sender);
                break;

            case "stats":
                if (!sender.hasPermission("minibosses.stats") && !sender.hasPermission("minibosses.admin")) {
                    sender.sendMessage(Component.text("Недостаточно прав! Нужен permission: minibosses.stats").color(NamedTextColor.RED));
                    return true;
                }
                handleStatsCommand(sender);
                break;

            case "buy":
                if (!sender.hasPermission("minibosses.buy") && !sender.hasPermission("minibosses.admin")) {
                    sender.sendMessage(Component.text("Недостаточно прав! Нужен permission: minibosses.buy").color(NamedTextColor.RED));
                    return true;
                }
                handleBuyCommand(sender, args);
                break;

            default:
                sender.sendMessage(Component.text("Неизвестная команда. Используйте /minibosses help для справки").color(NamedTextColor.RED));
                break;
        }

        return true;
    }

    private void handleBuyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("✗ Эта команда только для игроков!").color(NamedTextColor.RED));
            return;
        }

        if (plugin.getEconomyManager() == null || !plugin.getEconomyManager().isEconomyAvailable()) {
            player.sendMessage(Component.text("✗ Система экономики недоступна!").color(NamedTextColor.RED));
            player.sendMessage(Component.text("Для покупки боссов нужен плагин CoinsEngine.").color(NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            showBuyableBosses(player);
            return;
        }

        String bossType = args[1].toLowerCase();
        buyBoss(player, bossType);
    }

    private void showBuyableBosses(Player player) {
        player.sendMessage(createBoxHeader("Покупка Боссов"));

        String[] bossTypes = {"ender", "nether", "forest", "desert"};
        boolean hasBuyableBosses = false;

        for (String bossType : bossTypes) {
            if (plugin.getConfigManager().isBossBuyable(bossType)) {
                hasBuyableBosses = true;
                double price = plugin.getConfigManager().getBuyPrice(bossType);
                Component bossName = getBossDisplayName(bossType);
                Component line = Component.text()
                        .append(Component.text("║ ").color(NamedTextColor.GOLD))
                        .append(bossName)
                        .append(Component.text(" - ").color(NamedTextColor.WHITE))
                        .append(Component.text(plugin.getEconomyManager().formatMoney(price)).color(NamedTextColor.GREEN))
                        .append(Component.text(" (тип: " + bossType + ")").color(NamedTextColor.GRAY))
                        .append(Component.text(" ║").color(NamedTextColor.GOLD))
                        .build();
                player.sendMessage(line);
            }
        }

        if (!hasBuyableBosses) {
            player.sendMessage(Component.text("║   Нет доступных боссов     ║").color(NamedTextColor.GOLD));
        }

        player.sendMessage(Component.text("╠══════════════════════════════╣").color(NamedTextColor.GOLD));

        Component usageLine = Component.text()
                .append(Component.text("║ Используйте: ").color(NamedTextColor.GRAY))
                .append(Component.text("/minibosses buy <тип>").color(NamedTextColor.WHITE))
                .append(Component.text(" ║").color(NamedTextColor.GOLD))
                .build();
        player.sendMessage(usageLine);

        if (!plugin.getPlayerDataManager().canBuyBoss(player)) {
            String cooldown = plugin.getPlayerDataManager().getCooldownRemaining(player);
            Component cooldownLine = Component.text()
                    .append(Component.text("║ Кулдаун: ").color(NamedTextColor.RED))
                    .append(Component.text(cooldown).color(NamedTextColor.RED))
                    .append(Component.text("         ║").color(NamedTextColor.GOLD))
                    .build();
            player.sendMessage(cooldownLine);
        }

        player.sendMessage(Component.text("╚══════════════════════════════╝").color(NamedTextColor.GOLD));
    }

    private void buyBoss(Player player, String bossType) {
        // Проверяем существование типа босса
        if (!isValidBossType(bossType)) {
            player.sendMessage(Component.text("✗ Неизвестный тип босса! Доступные: ender, nether, forest, desert").color(NamedTextColor.RED));
            return;
        }

        if (!plugin.getConfigManager().isBossBuyable(bossType)) {
            player.sendMessage(Component.text("✗ Этот босс недоступен для покупки!").color(NamedTextColor.RED));
            return;
        }

        if (!plugin.getPlayerDataManager().canBuyBoss(player)) {
            String cooldown = plugin.getPlayerDataManager().getCooldownRemaining(player);
            player.sendMessage(Component.text("✗ Вы можете покупать боссов раз в 24 часа!").color(NamedTextColor.RED));
            player.sendMessage(Component.text("Осталось: " + cooldown).color(NamedTextColor.RED));
            return;
        }

        if (plugin.getBossManager().isInsideProtectedRegion(player.getLocation())) {
            player.sendMessage(Component.text("✗ Вы находитесь на защищенной территории!").color(NamedTextColor.RED));
            player.sendMessage(Component.text("Для призыва босса нужна зона без привата.").color(NamedTextColor.RED));
            return;
        }

        double price = plugin.getConfigManager().getBuyPrice(bossType);

        if (!plugin.getEconomyManager().hasEnoughMoney(player, price)) {
            player.sendMessage(Component.text("✗ Недостаточно средств!").color(NamedTextColor.RED));
            player.sendMessage(Component.text()
                    .append(Component.text("Нужно: ").color(NamedTextColor.RED))
                    .append(Component.text(plugin.getEconomyManager().formatMoney(price)).color(NamedTextColor.GOLD))
                    .build());
            player.sendMessage(Component.text()
                    .append(Component.text("У вас: ").color(NamedTextColor.RED))
                    .append(Component.text(plugin.getEconomyManager().formatMoney(plugin.getEconomyManager().getBalance(player))).color(NamedTextColor.GOLD))
                    .build());
            return;
        }

        if (!plugin.getEconomyManager().removeMoney(player, price)) {
            player.sendMessage(Component.text("✗ Ошибка при списании средств!").color(NamedTextColor.RED));
            return;
        }

        plugin.getPlayerDataManager().setLastBuyTime(player, System.currentTimeMillis());
        boolean success = trySpawnBoss(player, bossType);

        if (success) {
            Component bossName = getBossDisplayName(bossType);
            Component successMessage = Component.text()
                    .append(Component.text("✓ Вы купили ").color(NamedTextColor.GREEN))
                    .append(bossName)
                    .append(Component.text(" за ").color(NamedTextColor.GREEN))
                    .append(Component.text(plugin.getEconomyManager().formatMoney(price)).color(NamedTextColor.GOLD))
                    .append(Component.text("!").color(NamedTextColor.GREEN))
                    .build();
            player.sendMessage(successMessage);
            player.sendMessage(Component.text("Босс появится рядом с вами через несколько секунд...").color(NamedTextColor.GRAY));

            plugin.getLogger().info(player.getName() + " купил " + getBossNamePlain(bossType) + " за " + plugin.getEconomyManager().formatMoney(price));
        } else {
            player.sendMessage(Component.text("✗ Не удалось призвать босса!").color(NamedTextColor.RED));
            player.sendMessage(Component.text("Возможно, не нашлось подходящей локации.").color(NamedTextColor.RED));
            plugin.getEconomyManager().addMoney(player, price);
            plugin.getPlayerDataManager().setLastBuyTime(player, 0);
        }
    }

    private boolean trySpawnBoss(Player player, String bossType) {
        Location spawnLocation = plugin.getBossManager().findSpawnLocation(player.getLocation());
        if (spawnLocation == null) {
            return false;
        }
        return switch (bossType) {
            case "ender" -> {
                plugin.getBossManager().spawnEnderLord(spawnLocation, false);
                yield true;
            }
            case "nether" -> {
                plugin.getBossManager().spawnNetherInferno(spawnLocation, false);
                yield true;
            }
            case "forest" -> {
                plugin.getBossManager().spawnForestGuardian(spawnLocation, false);
                yield true;
            }
            case "desert" -> {
                plugin.getBossManager().spawnDesertSandlord(spawnLocation, false);
                yield true;
            }
            default -> false;
        };
    }

    private Component getBossDisplayName(String bossType) {
        return switch (bossType) {
            case "ender" -> Component.text("Повелителя Энда").color(TextColor.color(0xFF73FA));
            case "nether" -> Component.text("Инфернального Ифрита").color(NamedTextColor.RED);
            case "forest" -> Component.text("Защитника Леса").color(NamedTextColor.GREEN);
            case "desert" -> Component.text("Повелителя Песков").color(NamedTextColor.GOLD);
            default -> Component.text("Неизвестного Босса").color(NamedTextColor.GRAY);
        };
    }

    private String getBossNamePlain(String bossType) {
        return switch (bossType) {
            case "ender" -> "Повелителя Энда";
            case "nether" -> "Инфернального Ифрита";
            case "forest" -> "Защитника Леса";
            case "desert" -> "Повелителя Песков";
            default -> "Неизвестного Босса";
        };
    }

    private boolean isValidBossType(String bossType) {
        return bossType.equals("ender") || bossType.equals("nether") ||
                bossType.equals("forest") || bossType.equals("desert");
    }

    private void handleReloadCommand(CommandSender sender) {
        plugin.reloadPlugin();
        sender.sendMessage(Component.text("✓ MiniBosses перезагружен!").color(NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Конфигурация и задачи были обновлены.").color(NamedTextColor.GRAY));
    }

    private void handleSpawnCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("✗ Эта команда только для игроков!").color(NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("✗ Использование: /minibosses spawn <ender|nether|forest|desert>").color(NamedTextColor.RED));
            return;
        }

        String bossType = args[1].toLowerCase();

        switch (bossType) {
            case "ender":
                if (plugin.getConfigManager().isEnderBossEnabled()) {
                    plugin.getBossManager().spawnEnderLord(player.getLocation(), false);
                    player.sendMessage(Component.text("✓ Призван Повелитель Энда!").color(TextColor.color(0xFF73FA)));
                    plugin.getLogger().info(player.getName() + " призвал Повелителя Энда");
                } else {
                    player.sendMessage(Component.text("✗ Босс Энда отключен в конфигурации!").color(NamedTextColor.RED));
                }
                break;

            case "nether":
                if (plugin.getConfigManager().isNetherBossEnabled()) {
                    plugin.getBossManager().spawnNetherInferno(player.getLocation(), false);
                    player.sendMessage(Component.text("✓ Призван Инфернальный Ифрит!").color(NamedTextColor.RED));
                    plugin.getLogger().info(player.getName() + " призвал Инфернального Ифрита");
                } else {
                    player.sendMessage(Component.text("✗ Босс Ада отключен в конфигурации!").color(NamedTextColor.RED));
                }
                break;

            case "forest":
                if (plugin.getConfigManager().isForestBossEnabled()) {
                    plugin.getBossManager().spawnForestGuardian(player.getLocation(), false);
                    player.sendMessage(Component.text("✓ Призван Защитник Леса!").color(NamedTextColor.GREEN));
                    plugin.getLogger().info(player.getName() + " призвал Защитника Леса");
                } else {
                    player.sendMessage(Component.text("✗ Босс Леса отключен в конфигурации!").color(NamedTextColor.RED));
                }
                break;

            case "desert":
                if (plugin.getConfigManager().isDesertBossEnabled()) {
                    plugin.getBossManager().spawnDesertSandlord(player.getLocation(), false);
                    player.sendMessage(Component.text("✓ Призван Повелитель Песков!").color(NamedTextColor.GOLD));
                    plugin.getLogger().info(player.getName() + " призвал Повелителя Песков");
                } else {
                    player.sendMessage(Component.text("✗ Босс Пустыни отключен в конфигурации!").color(NamedTextColor.RED));
                }
                break;

            default:
                player.sendMessage(Component.text("✗ Неизвестный тип босса! Доступные: ender, nether, forest, desert").color(NamedTextColor.RED));
                break;
        }
    }

    private void handleCleanupCommand(CommandSender sender) {
        int count = plugin.getBossManager().getActiveBossCount();
        if (count == 0) {
            sender.sendMessage(Component.text("ℹ Активных боссов не найдено").color(NamedTextColor.YELLOW));
            return;
        }

        plugin.getBossManager().cleanupAllBosses();
        sender.sendMessage(Component.text("✓ Удалено " + count + " активных боссов").color(NamedTextColor.GREEN));
        plugin.getLogger().info(sender.getName() + " очистил " + count + " боссов");
    }

    private void handleStatsCommand(CommandSender sender) {
        int activeBosses = plugin.getBossManager().getActiveBossCount();
        int cooldownCount = plugin.getBossCooldowns().size();

        sender.sendMessage(createBoxHeader("MiniBosses Stats"));

        sender.sendMessage(createStatLine("Активных боссов: ", String.valueOf(activeBosses)));
        sender.sendMessage(createStatLine("Активных кулдаунов: ", String.valueOf(cooldownCount)));

        Component autoSpawnStatus = plugin.getConfigManager().isAutoSpawnEnabled() ?
                Component.text("ВКЛ").color(NamedTextColor.GREEN) :
                Component.text("ВЫКЛ").color(NamedTextColor.RED);
        Component autoSpawnLine = Component.text()
                .append(Component.text("║ Автоспавн: ").color(NamedTextColor.YELLOW))
                .append(autoSpawnStatus)
                .append(Component.text("              ║").color(NamedTextColor.GOLD))
                .build();
        sender.sendMessage(autoSpawnLine);

        sender.sendMessage(Component.text("╠══════════════════════════════╣").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("║ Статус боссов:              ║").color(NamedTextColor.YELLOW));
        sender.sendMessage(createBossStatusLine("Энда", plugin.getConfigManager().isEnderBossEnabled()));
        sender.sendMessage(createBossStatusLine("Ада", plugin.getConfigManager().isNetherBossEnabled()));
        sender.sendMessage(createBossStatusLine("Леса", plugin.getConfigManager().isForestBossEnabled()));
        sender.sendMessage(createBossStatusLine("Пустыни", plugin.getConfigManager().isDesertBossEnabled()));
        sender.sendMessage(Component.text("╚══════════════════════════════╝").color(NamedTextColor.GOLD));
    }

    private Component createBoxHeader(String title) {
        return Component.text()
                .append(Component.text("╔══════════════════════════════╗").color(NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("║       ").color(NamedTextColor.GOLD))
                .append(Component.text(title).color(NamedTextColor.WHITE))
                .append(Component.text("       ║").color(NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("╠══════════════════════════════╣").color(NamedTextColor.GOLD))
                .build();
    }

    private Component createStatLine(String label, String value) {
        return Component.text()
                .append(Component.text("║ ").color(NamedTextColor.GOLD))
                .append(Component.text(label).color(NamedTextColor.YELLOW))
                .append(Component.text(value).color(NamedTextColor.WHITE))
                .append(Component.text("           ║").color(NamedTextColor.GOLD))
                .build();
    }

    private Component createBossStatusLine(String bossName, boolean enabled) {
        Component status = enabled ?
                Component.text("✓").color(NamedTextColor.GREEN) :
                Component.text("✗").color(NamedTextColor.RED);

        return Component.text()
                .append(Component.text("║ ").color(NamedTextColor.GOLD))
                .append(Component.text("• " + bossName + ": ").color(NamedTextColor.YELLOW))
                .append(status)
                .append(Component.text("   ║").color(NamedTextColor.GOLD))
                .build();
    }

    private void sendHelp(CommandSender sender) {
        List<Component> availableCommands = new ArrayList<>();

        if (sender.hasPermission("minibosses.reload") || sender.hasPermission("minibosses.admin")) {
            availableCommands.add(createHelpLine("/minibosses reload", "Перезагрузить конфиг"));
        }
        if (sender.hasPermission("minibosses.spawn") || sender.hasPermission("minibosses.admin")) {
            availableCommands.add(createHelpLine("/minibosses spawn <тип>", "Призвать босса"));
        }
        if (sender.hasPermission("minibosses.cleanup") || sender.hasPermission("minibosses.admin")) {
            availableCommands.add(createHelpLine("/minibosses cleanup", "Удалить всех боссов"));
        }
        if (sender.hasPermission("minibosses.stats") || sender.hasPermission("minibosses.admin")) {
            availableCommands.add(createHelpLine("/minibosses stats", "Статистика"));
        }
        if (sender.hasPermission("minibosses.buy") || sender.hasPermission("minibosses.admin")) {
            availableCommands.add(createHelpLine("/minibosses buy <тип>", "Купить босса"));
        }

        availableCommands.add(createHelpLine("/minibosses help", "Справка"));

        sender.sendMessage(createBoxHeader("MiniBosses Commands"));

        for (Component cmd : availableCommands) {
            sender.sendMessage(cmd);
        }

        sender.sendMessage(Component.text("╠══════════════════════════════╣").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("║ Типы боссов: ender, nether,  ║").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("║ forest, desert               ║").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("╚══════════════════════════════╝").color(NamedTextColor.GOLD));
    }

    private Component createHelpLine(String command, String description) {
        return Component.text()
                .append(Component.text("║ ").color(NamedTextColor.GOLD))
                .append(Component.text(command).color(NamedTextColor.YELLOW))
                .append(Component.text(" - ").color(NamedTextColor.WHITE))
                .append(Component.text(description).color(NamedTextColor.WHITE))
                .append(Component.text(" ║").color(NamedTextColor.GOLD))
                .build();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> commands = new ArrayList<>();

            if (sender.hasPermission("minibosses.reload") || sender.hasPermission("minibosses.admin")) {
                commands.add("reload");
            }
            if (sender.hasPermission("minibosses.spawn") || sender.hasPermission("minibosses.admin")) {
                commands.add("spawn");
            }
            if (sender.hasPermission("minibosses.cleanup") || sender.hasPermission("minibosses.admin")) {
                commands.add("cleanup");
            }
            if (sender.hasPermission("minibosses.stats") || sender.hasPermission("minibosses.admin")) {
                commands.add("stats");
            }
            if (sender.hasPermission("minibosses.buy") || sender.hasPermission("minibosses.admin")) {
                commands.add("buy");
            }
            commands.add("help");

            completions.addAll(commands);

        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("spawn") &&
                    (sender.hasPermission("minibosses.spawn") || sender.hasPermission("minibosses.admin"))) {
                completions.addAll(Arrays.asList("ender", "nether", "forest", "desert"));
            } else if (args[0].equalsIgnoreCase("buy") &&
                    (sender.hasPermission("minibosses.buy") || sender.hasPermission("minibosses.admin"))) {
                String[] bossTypes = {"ender", "nether", "forest", "desert"};
                for (String bossType : bossTypes) {
                    if (plugin.getConfigManager().isBossBuyable(bossType)) {
                        completions.add(bossType);
                    }
                }
            }
        }

        if (args.length > 0) {
            String currentArg = args[args.length - 1].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(currentArg));
        }

        return completions;
    }
}
