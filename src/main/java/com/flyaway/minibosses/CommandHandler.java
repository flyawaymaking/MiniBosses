package com.flyaway.minibosses;

import org.bukkit.ChatColor;
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
                    sender.sendMessage(ChatColor.RED + "Недостаточно прав! Нужен permission: minibosses.reload");
                    return true;
                }
                handleReloadCommand(sender);
                break;

            case "spawn":
                if (!sender.hasPermission("minibosses.spawn") && !sender.hasPermission("minibosses.admin")) {
                    sender.sendMessage(ChatColor.RED + "Недостаточно прав! Нужен permission: minibosses.spawn");
                    return true;
                }
                handleSpawnCommand(sender, args);
                break;

            case "cleanup":
                if (!sender.hasPermission("minibosses.cleanup") && !sender.hasPermission("minibosses.admin")) {
                    sender.sendMessage(ChatColor.RED + "Недостаточно прав! Нужен permission: minibosses.cleanup");
                    return true;
                }
                handleCleanupCommand(sender);
                break;

            case "stats":
                if (!sender.hasPermission("minibosses.stats") && !sender.hasPermission("minibosses.admin")) {
                    sender.sendMessage(ChatColor.RED + "Недостаточно прав! Нужен permission: minibosses.stats");
                    return true;
                }
                handleStatsCommand(sender);
                break;

            case "buy":
                if (!sender.hasPermission("minibosses.buy") && !sender.hasPermission("minibosses.admin")) {
                    sender.sendMessage(ChatColor.RED + "Недостаточно прав! Нужен permission: minibosses.buy");
                    return true;
                }
                handleBuyCommand(sender, args);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Неизвестная команда. Используйте /minibosses help для справки");
                break;
        }

        return true;
    }

    private void handleBuyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "✗ Эта команда только для игроков!");
            return;
        }

        Player player = (Player) sender;

        // Проверяем доступность экономики
        if (plugin.getEconomyManager() == null || !plugin.getEconomyManager().isEconomyAvailable()) {
            player.sendMessage(ChatColor.RED + "✗ Система экономики недоступна!");
            player.sendMessage(ChatColor.RED + "Для покупки боссов нужен плагин CoinsEngine.");
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
        player.sendMessage(ChatColor.GOLD + "╔══════════════════════════════╗");
        player.sendMessage(ChatColor.GOLD + "║       " + ChatColor.WHITE + "Покупка Боссов" + ChatColor.GOLD + "        ║");
        player.sendMessage(ChatColor.GOLD + "╠══════════════════════════════╣");

        String[] bossTypes = {"ender", "nether", "forest", "desert"};
        boolean hasBuyableBosses = false;

        for (String bossType : bossTypes) {
            if (plugin.getConfigManager().isBossBuyable(bossType)) {
                hasBuyableBosses = true;
                double price = plugin.getConfigManager().getBuyPrice(bossType);
                String bossName = getBossDisplayName(bossType);
                player.sendMessage(ChatColor.YELLOW + "║ " + bossName +
                                   ChatColor.WHITE + " - " + ChatColor.GREEN +
                                   plugin.getEconomyManager().formatMoney(price) +
                                   ChatColor.GRAY + " (тип: " + bossType + ")" +
                                   ChatColor.GOLD + " ║");
            }
        }

        if (!hasBuyableBosses) {
            player.sendMessage(ChatColor.RED + "║   Нет доступных боссов     " + ChatColor.GOLD + "║");
        }

        player.sendMessage(ChatColor.GOLD + "╠══════════════════════════════╣");
        player.sendMessage(ChatColor.GRAY + "║ Используйте: " + ChatColor.WHITE + "/minibosses buy <тип>" + ChatColor.GOLD + " ║");

        // Показываем кулдаун
        if (!plugin.getPlayerDataManager().canBuyBoss(player)) {
            String cooldown = plugin.getPlayerDataManager().getCooldownRemaining(player);
            player.sendMessage(ChatColor.RED + "║ Кулдаун: " + cooldown + ChatColor.GOLD + "         ║");
        }

        player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════╝");
    }

    private void buyBoss(Player player, String bossType) {
        // Проверяем существование типа босса
        if (!isValidBossType(bossType)) {
            player.sendMessage(ChatColor.RED + "✗ Неизвестный тип босса! Доступные: ender, nether, forest, desert");
            return;
        }

        // Проверяем доступность покупки
        if (!plugin.getConfigManager().isBossBuyable(bossType)) {
            player.sendMessage(ChatColor.RED + "✗ Этот босс недоступен для покупки!");
            return;
        }

        // Проверяем кулдаун
        if (!plugin.getPlayerDataManager().canBuyBoss(player)) {
            String cooldown = plugin.getPlayerDataManager().getCooldownRemaining(player);
            player.sendMessage(ChatColor.RED + "✗ Вы можете покупать боссов раз в 24 часа!");
            player.sendMessage(ChatColor.RED + "Осталось: " + cooldown);
            return;
        }

        // Проверяем приватную территорию
        if (plugin.getBossManager().isInsideProtectedRegion(player.getLocation())) {
            player.sendMessage(ChatColor.RED + "✗ Вы находитесь на защищенной территории!");
            player.sendMessage(ChatColor.RED + "Для призыва босса нужна зона без привата.");
            return;
        }

        double price = plugin.getConfigManager().getBuyPrice(bossType);

        // Проверяем баланс
        if (!plugin.getEconomyManager().hasEnoughMoney(player, price)) {
            player.sendMessage(ChatColor.RED + "✗ Недостаточно средств!");
            player.sendMessage(ChatColor.RED + "Нужно: " + ChatColor.GOLD + plugin.getEconomyManager().formatMoney(price));
            player.sendMessage(ChatColor.RED + "У вас: " + ChatColor.GOLD +
                               plugin.getEconomyManager().formatMoney(plugin.getEconomyManager().getBalance(player)));
            return;
        }

        // Списываем деньги
        if (!plugin.getEconomyManager().removeMoney(player, price)) {
            player.sendMessage(ChatColor.RED + "✗ Ошибка при списании средств!");
            return;
        }

        // Устанавливаем кулдаун
        plugin.getPlayerDataManager().setLastBuyTime(player, System.currentTimeMillis());

        // Призываем босса
        boolean success = trySpawnBoss(player, bossType);

        if (success) {
            String bossName = getBossDisplayName(bossType);
            player.sendMessage(ChatColor.GREEN + "✓ Вы купили " + bossName + " за " +
                               ChatColor.GOLD + plugin.getEconomyManager().formatMoney(price) + "!");
            player.sendMessage(ChatColor.GRAY + "Босс появится рядом с вами через несколько секунд...");

            // Логируем покупку
            plugin.getLogger().info(player.getName() + " купил " + bossName + " за " + plugin.getEconomyManager().formatMoney(price));
        } else {
            player.sendMessage(ChatColor.RED + "✗ Не удалось призвать босса!");
            player.sendMessage(ChatColor.RED + "Возможно, не нашлось подходящей локации.");
            // Возвращаем деньги при ошибке
            plugin.getEconomyManager().addMoney(player, price);
            // Сбрасываем кулдаун при ошибке
            plugin.getPlayerDataManager().setLastBuyTime(player, 0);
        }
    }

    private boolean trySpawnBoss(Player player, String bossType) {
        Location spawnLocation = plugin.getBossManager().findSpawnLocation(player.getLocation());
        if (spawnLocation == null) {
            return false;
        }
        switch (bossType) {
            case "ender":
                plugin.getBossManager().spawnEnderLord(spawnLocation);
                return true;
            case "nether":
                plugin.getBossManager().spawnNetherInferno(spawnLocation);
                return true;
            case "forest":
                plugin.getBossManager().spawnForestGuardian(spawnLocation);
                return true;
            case "desert":
                plugin.getBossManager().spawnDesertSandlord(spawnLocation);
                return true;
            default: return false;
        }
    }

    private String getBossDisplayName(String bossType) {
        switch (bossType) {
            case "ender": return ChatColor.LIGHT_PURPLE + "Повелителя Энда";
            case "nether": return ChatColor.RED + "Инфернального Ифрита";
            case "forest": return ChatColor.GREEN + "Защитника Леса";
            case "desert": return ChatColor.GOLD + "Повелителя Песков";
            default: return "Неизвестного Босса";
        }
    }

    private boolean isValidBossType(String bossType) {
        return bossType.equals("ender") || bossType.equals("nether") ||
               bossType.equals("forest") || bossType.equals("desert");
    }

    private void handleReloadCommand(CommandSender sender) {
        plugin.reloadPlugin();
        sender.sendMessage(ChatColor.GREEN + "✓ MiniBosses перезагружен!");
        sender.sendMessage(ChatColor.GRAY + "Конфигурация и задачи были обновлены.");
    }

    private void handleSpawnCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "✗ Эта команда только для игроков!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "✗ Использование: /minibosses spawn <ender|nether|forest|desert>");
            return;
        }

        Player player = (Player) sender;
        String bossType = args[1].toLowerCase();

        switch (bossType) {
            case "ender":
                if (plugin.getConfigManager().isEnderBossEnabled()) {
                    plugin.getBossManager().spawnEnderLord(player.getLocation());
                    player.sendMessage(ChatColor.LIGHT_PURPLE + "✓ Призван Повелитель Энда!");
                    plugin.getLogger().info(player.getName() + " призвал Повелителя Энда");
                } else {
                    player.sendMessage(ChatColor.RED + "✗ Босс Энда отключен в конфигурации!");
                }
                break;

            case "nether":
                if (plugin.getConfigManager().isNetherBossEnabled()) {
                    plugin.getBossManager().spawnNetherInferno(player.getLocation());
                    player.sendMessage(ChatColor.RED + "✓ Призван Инфернальный Ифрит!");
                    plugin.getLogger().info(player.getName() + " призвал Инфернального Ифрита");
                } else {
                    player.sendMessage(ChatColor.RED + "✗ Босс Ада отключен в конфигурации!");
                }
                break;

            case "forest":
                if (plugin.getConfigManager().isForestBossEnabled()) {
                    plugin.getBossManager().spawnForestGuardian(player.getLocation());
                    player.sendMessage(ChatColor.GREEN + "✓ Призван Защитник Леса!");
                    plugin.getLogger().info(player.getName() + " призвал Защитника Леса");
                } else {
                    player.sendMessage(ChatColor.RED + "✗ Босс Леса отключен в конфигурации!");
                }
                break;

            case "desert":
                if (plugin.getConfigManager().isDesertBossEnabled()) {
                    plugin.getBossManager().spawnDesertSandlord(player.getLocation());
                    player.sendMessage(ChatColor.GOLD + "✓ Призван Повелитель Песков!");
                    plugin.getLogger().info(player.getName() + " призвал Повелителя Песков");
                } else {
                    player.sendMessage(ChatColor.RED + "✗ Босс Пустыни отключен в конфигурации!");
                }
                break;

            default:
                player.sendMessage(ChatColor.RED + "✗ Неизвестный тип босса! Доступные: ender, nether, forest, desert");
                break;
        }
    }

    private void handleCleanupCommand(CommandSender sender) {
        int count = plugin.getBossManager().getActiveBossCount();
        if (count == 0) {
            sender.sendMessage(ChatColor.YELLOW + "ℹ Активных боссов не найдено");
            return;
        }

        plugin.getBossManager().cleanupAllBosses();
        sender.sendMessage(ChatColor.GREEN + "✓ Удалено " + count + " активных боссов");
        plugin.getLogger().info(sender.getName() + " очистил " + count + " боссов");
    }

    private void handleStatsCommand(CommandSender sender) {
        int activeBosses = plugin.getBossManager().getActiveBossCount();
        int cooldownCount = plugin.getBossCooldowns().size();

        sender.sendMessage(ChatColor.GOLD + "╔══════════════════════════════╗");
        sender.sendMessage(ChatColor.GOLD + "║        " + ChatColor.WHITE + "MiniBosses Stats" + ChatColor.GOLD + "         ║");
        sender.sendMessage(ChatColor.GOLD + "╠══════════════════════════════╣");
        sender.sendMessage(ChatColor.YELLOW + "║ Активных боссов: " + ChatColor.WHITE + activeBosses + ChatColor.GOLD + "           ║");
        sender.sendMessage(ChatColor.YELLOW + "║ Активных кулдаунов: " + ChatColor.WHITE + cooldownCount + ChatColor.GOLD + "        ║");
        sender.sendMessage(ChatColor.YELLOW + "║ Автоспавн: " +
            (plugin.getConfigManager().isAutoSpawnEnabled() ? ChatColor.GREEN + "ВКЛ" : ChatColor.RED + "ВЫКЛ") + ChatColor.GOLD + "              ║");
        sender.sendMessage(ChatColor.GOLD + "╠══════════════════════════════╣");
        sender.sendMessage(ChatColor.YELLOW + "║ Статус боссов:              " + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GOLD + "║ " + getBossStatus("Энда", plugin.getConfigManager().isEnderBossEnabled()) + ChatColor.GOLD + "   ║");
        sender.sendMessage(ChatColor.GOLD + "║ " + getBossStatus("Ада", plugin.getConfigManager().isNetherBossEnabled()) + ChatColor.GOLD + "     ║");
        sender.sendMessage(ChatColor.GOLD + "║ " + getBossStatus("Леса", plugin.getConfigManager().isForestBossEnabled()) + ChatColor.GOLD + "    ║");
        sender.sendMessage(ChatColor.GOLD + "║ " + getBossStatus("Пустыни", plugin.getConfigManager().isDesertBossEnabled()) + ChatColor.GOLD + "  ║");
        sender.sendMessage(ChatColor.GOLD + "╚══════════════════════════════╝");
    }

    private String getBossStatus(String name, boolean enabled) {
        return ChatColor.YELLOW + "• " + name + ": " +
               (enabled ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗");
    }

    private void sendHelp(CommandSender sender) {
        List<String> availableCommands = new ArrayList<>();

        // Проверяем доступные команды на основе прав
        if (sender.hasPermission("minibosses.reload") || sender.hasPermission("minibosses.admin")) {
            availableCommands.add("/minibosses reload" + ChatColor.WHITE + " - Перезагрузить конфиг");
        }
        if (sender.hasPermission("minibosses.spawn") || sender.hasPermission("minibosses.admin")) {
            availableCommands.add("/minibosses spawn <тип>" + ChatColor.WHITE + " - Призвать босса");
        }
        if (sender.hasPermission("minibosses.cleanup") || sender.hasPermission("minibosses.admin")) {
            availableCommands.add("/minibosses cleanup" + ChatColor.WHITE + " - Удалить всех боссов");
        }
        if (sender.hasPermission("minibosses.stats") || sender.hasPermission("minibosses.admin")) {
            availableCommands.add("/minibosses stats" + ChatColor.WHITE + " - Статистика");
        }
        if (sender.hasPermission("minibosses.buy") || sender.hasPermission("minibosses.admin")) {
            availableCommands.add("/minibosses buy <тип>" + ChatColor.WHITE + " - Купить босса");
        }

        // Всегда доступна команда help
        availableCommands.add("/minibosses help" + ChatColor.WHITE + " - Справка");

        sender.sendMessage(ChatColor.GOLD + "╔══════════════════════════════╗");
        sender.sendMessage(ChatColor.GOLD + "║       " + ChatColor.WHITE + "MiniBosses Commands" + ChatColor.GOLD + "       ║");
        sender.sendMessage(ChatColor.GOLD + "╠══════════════════════════════╣");

        for (String cmd : availableCommands) {
            sender.sendMessage(ChatColor.YELLOW + "║ " + cmd + ChatColor.GOLD + " ║");
        }

        sender.sendMessage(ChatColor.GOLD + "╠══════════════════════════════╣");
        sender.sendMessage(ChatColor.GRAY + "║ Типы боссов: ender, nether,  " + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GRAY + "║ forest, desert               " + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GOLD + "╚══════════════════════════════╝");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Основные команды - только те, на которые есть права
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
            // Типы боссов для spawn и buy
            if (args[0].equalsIgnoreCase("spawn") &&
                (sender.hasPermission("minibosses.spawn") || sender.hasPermission("minibosses.admin"))) {
                completions.addAll(Arrays.asList("ender", "nether", "forest", "desert"));
            } else if (args[0].equalsIgnoreCase("buy") &&
                       (sender.hasPermission("minibosses.buy") || sender.hasPermission("minibosses.admin"))) {
                // Показываем только покупаемых боссов
                String[] bossTypes = {"ender", "nether", "forest", "desert"};
                for (String bossType : bossTypes) {
                    if (plugin.getConfigManager().isBossBuyable(bossType)) {
                        completions.add(bossType);
                    }
                }
            }
        }

        // Фильтрация по введенному тексту
        if (args.length > 0) {
            String currentArg = args[args.length - 1].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(currentArg));
        }

        return completions;
    }
}
