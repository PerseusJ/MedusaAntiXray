package me.perseusj.medusaantixray.commands;

import me.perseusj.medusaantixray.data.PlayerData;
import me.perseusj.medusaantixray.managers.ConfigManager;
import me.perseusj.medusaantixray.managers.DataManager;
import me.perseusj.medusaantixray.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
            sender.sendMessage(Utils.colorize("&cUsage: /medusa <reload|check|trust>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            config.reload();
            sender.sendMessage(Utils.colorize(config.getReloadSuccessMessage()));
            return true;
        }

        if (args[0].equalsIgnoreCase("check")) {
            return handleCheck(sender, args);
        }

        if (args[0].equalsIgnoreCase("trust")) {
            return handleTrust(sender, args);
        }

        sender.sendMessage(Utils.colorize("&cUsage: /medusa <reload|check|trust>"));
        return true;
    }

    private boolean handleCheck(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Utils.colorize(config.getUsageCheckMessage()));
            return true;
        }

        String targetName = args[1];
        PlayerData targetData = findPlayerData(targetName);

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

    // =========================================================================
    // C4 — Trust subcommand
    // =========================================================================

    private boolean handleTrust(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Utils.colorize("&cUsage: /medusa trust <get|set> <player> [multiplier]"));
            return true;
        }

        if (args[1].equalsIgnoreCase("get")) {
            if (args.length < 3) {
                sender.sendMessage(Utils.colorize("&cUsage: /medusa trust get <player>"));
                return true;
            }
            PlayerData target = findPlayerData(args[2]);
            if (target == null) {
                sender.sendMessage(Utils.colorize(config.getPlayerNotFoundMessage()
                        .replace("{player}", args[2])));
                return true;
            }
            double mult = target.getTrustMultiplier();
            sender.sendMessage(Utils.colorize("&7Trust multiplier for &f" + target.getPlayerName()
                    + "&7: &c" + String.format("%.2f", mult)));
            return true;
        }

        if (args[1].equalsIgnoreCase("set")) {
            if (args.length < 4) {
                sender.sendMessage(Utils.colorize("&cUsage: /medusa trust set <player> <multiplier>"));
                return true;
            }
            PlayerData target = findPlayerData(args[2]);
            if (target == null) {
                sender.sendMessage(Utils.colorize(config.getPlayerNotFoundMessage()
                        .replace("{player}", args[2])));
                return true;
            }
            double multiplier;
            try {
                multiplier = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Utils.colorize("&cInvalid multiplier: " + args[3]));
                return true;
            }
            if (multiplier < 0) {
                sender.sendMessage(Utils.colorize("&cMultiplier must be >= 0."));
                return true;
            }
            target.setTrustMultiplier(multiplier);
            sender.sendMessage(Utils.colorize("&aTrust multiplier for &f" + target.getPlayerName()
                    + " &aset to &c" + String.format("%.2f", multiplier)
                    + "&a. (In-memory only — use config.yml for persistence.)"));
            return true;
        }

        sender.sendMessage(Utils.colorize("&cUsage: /medusa trust <get|set> <player> [multiplier]"));
        return true;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private PlayerData findPlayerData(String name) {
        for (PlayerData data : dataManager.getAllEntries()) {
            if (data.getPlayerName().equalsIgnoreCase(name)) {
                return data;
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("medusa.admin")) return completions;

        if (args.length == 1) {
            if ("reload".startsWith(args[0].toLowerCase())) completions.add("reload");
            if ("check".startsWith(args[0].toLowerCase())) completions.add("check");
            if ("trust".startsWith(args[0].toLowerCase())) completions.add("trust");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("trust")) {
                addPlayerCompletions(completions, args[1]);
            }
            if (args[0].equalsIgnoreCase("trust")) {
                if ("get".startsWith(args[1].toLowerCase())) completions.add("get");
                if ("set".startsWith(args[1].toLowerCase())) completions.add("set");
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("trust")) {
            if (args[1].equalsIgnoreCase("get") || args[1].equalsIgnoreCase("set")) {
                addPlayerCompletions(completions, args[2]);
            }
        }
        return completions;
    }

    private void addPlayerCompletions(List<String> completions, String prefix) {
        for (PlayerData data : dataManager.getAllEntries()) {
            if (data.getPlayerName().toLowerCase().startsWith(prefix.toLowerCase())) {
                completions.add(data.getPlayerName());
            }
        }
    }
}
