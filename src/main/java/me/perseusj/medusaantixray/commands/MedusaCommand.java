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

    private final ConfigManager config;
    private final DataManager dataManager;

    public MedusaCommand(ConfigManager config, DataManager dataManager) {
        this.config = config;
        this.dataManager = dataManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("medusa.admin")) {
            sender.sendMessage(Utils.colorize(config.getNoPermissionMessage()));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Utils.colorize("&cUsage: /medusa <reload|check>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            config.reload();
            sender.sendMessage(Utils.colorize(config.getReloadSuccessMessage()));
            return true;
        }

        if (args[0].equalsIgnoreCase("check")) {
            if (args.length < 2) {
                sender.sendMessage(Utils.colorize(config.getUsageCheckMessage()));
                return true;
            }

            String targetName = args[1];
            PlayerData targetData = null;

            for (PlayerData data : dataManager.getAllEntries()) {
                if (data.getPlayerName().equalsIgnoreCase(targetName)) {
                    targetData = data;
                    break;
                }
            }

            if (targetData == null) {
                String notFound = config.getPlayerNotFoundMessage().replace("{player}", targetName);
                sender.sendMessage(Utils.colorize(notFound));
                return true;
            }

            long cutoff = System.currentTimeMillis() - (config.getWindowMinutes() * 60_000L);
            targetData.purgeExpired(cutoff);

            String checkMessage = config.getCheckMessage();
            checkMessage = checkMessage
                    .replace("{player}", targetData.getPlayerName())
                    .replace("{score}", String.format("%.2f", targetData.calculateScore()))
                    .replace("{total}", String.valueOf(targetData.getTotalBlocks()))
                    .replace("{ratio}", String.format("%.1f", targetData.calculateRatio() * 100))
                    .replace("{window}", String.valueOf(config.getWindowMinutes()));

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
            for (PlayerData data : dataManager.getAllEntries()) {
                if (data.getPlayerName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(data.getPlayerName());
                }
            }
        }
        return completions;
    }
}