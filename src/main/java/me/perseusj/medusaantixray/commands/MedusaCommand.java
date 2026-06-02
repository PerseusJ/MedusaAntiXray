package me.perseusj.medusaantixray.commands;

import me.perseusj.medusaantixray.data.PlayerData;
import me.perseusj.medusaantixray.managers.ConfigManager;
import me.perseusj.medusaantixray.managers.DataManager;
import me.perseusj.medusaantixray.utils.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class MedusaCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("medusa.admin")) {
            sender.sendMessage(Utils.colorize(ConfigManager.getInstance().getNoPermissionMessage()));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Utils.colorize("&cUsage: /medusa <reload|check>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            ConfigManager.getInstance().reload();
            sender.sendMessage(Utils.colorize(ConfigManager.getInstance().getReloadSuccessMessage()));
            return true;
        }

        if (args[0].equalsIgnoreCase("check")) {
            if (args.length < 2) {
                sender.sendMessage(Utils.colorize(ConfigManager.getInstance().getUsageCheckMessage()));
                return true;
            }

            String targetName = args[1];
            PlayerData targetData = null;

            for (PlayerData data : DataManager.getInstance().getAllEntries()) {
                if (data.getPlayerName().equalsIgnoreCase(targetName)) {
                    targetData = data;
                    break;
                }
            }

            if (targetData == null) {
                String notFound = ConfigManager.getInstance().getPlayerNotFoundMessage().replace("{player}", targetName);
                sender.sendMessage(Utils.colorize(notFound));
                return true;
            }

            // Ensure window is up to date for check
            long cutoff = System.currentTimeMillis() - (ConfigManager.getInstance().getWindowMinutes() * 60_000L);
            targetData.purgeExpired(cutoff);

            String checkMessage = ConfigManager.getInstance().getCheckMessage();
            checkMessage = checkMessage
                    .replace("{player}", targetData.getPlayerName())
                    .replace("{score}", String.format("%.2f", targetData.calculateScore()))
                    .replace("{total}", String.valueOf(targetData.getTotalBlocks()))
                    .replace("{ratio}", String.format("%.1f", targetData.calculateRatio() * 100))
                    .replace("{window}", String.valueOf(ConfigManager.getInstance().getWindowMinutes()));

            sender.sendMessage(Utils.colorize(checkMessage));
            return true;
        }

        sender.sendMessage(Utils.colorize("&cUsage: /medusa <reload|check>"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("medusa.admin")) return completions;

        if (args.length == 1) {
            if ("reload".startsWith(args[0].toLowerCase())) completions.add("reload");
            if ("check".startsWith(args[0].toLowerCase())) completions.add("check");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            for (PlayerData data : DataManager.getInstance().getAllEntries()) {
                if (data.getPlayerName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(data.getPlayerName());
                }
            }
        }
        return completions;
    }
}
