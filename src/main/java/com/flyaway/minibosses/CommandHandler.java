package com.flyaway.minibosses;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandHandler implements CommandExecutor {

    private final MiniBosses plugin;

    public CommandHandler(MiniBosses plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minibosses.admin")) {
            sender.sendMessage(ChatColor.RED + "Недостаточно прав!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadPlugin();
                sender.sendMessage(ChatColor.GREEN + "MiniBosses перезагружен!");
                break;

            case "spawn":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Команда только для игроков!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Использование: /minibosses spawn <ender|nether|forest|desert>");
                    return true;
                }
                plugin.spawnBoss((Player) sender, args[1]);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== MiniBosses Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/minibosses reload" + ChatColor.WHITE + " - Перезагрузить конфиг");
        sender.sendMessage(ChatColor.YELLOW + "/minibosses spawn <тип>" + ChatColor.WHITE + " - Призвать босса");
        sender.sendMessage(ChatColor.GRAY + "Типы: ender, nether, forest, desert");
    }
}
