package me.perseusj.medusaantixray.commands;

import me.perseusj.medusaantixray.data.PlayerData;
import me.perseusj.medusaantixray.managers.ConfigManager;
import me.perseusj.medusaantixray.managers.DataManager;
import me.perseusj.medusaantixray.managers.DatabaseManager.AlertRecord;
import me.perseusj.medusaantixray.managers.WatchManager;
import me.perseusj.medusaantixray.ui.MedusaGui;
import me.perseusj.medusaantixray.utils.PaginationHelper;
import me.perseusj.medusaantixray.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class MedusaCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager config;
    private final DataManager dataManager;
    private final WatchManager watchManager;
    private final MedusaGui gui;

    private static final String[] SUBCOMMANDS = {
            "reload", "check", "trust", "top", "history",
            "reset", "stats", "watch", "list", "gui", "help"
    };

    public MedusaCommand(ConfigManager config, DataManager dataManager,
                         WatchManager watchManager, MedusaGui gui) {
        this.config = config;
        this.dataManager = dataManager;
        this.watchManager = watchManager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("medusa.admin")) {
            sender.sendMessage(Utils.colorize(config.getNoPermissionMessage()));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Utils.colorize(config.getUsageMessage()));
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "reload"  -> handleReload(sender);
            case "check"   -> handleCheck(sender, args);
            case "trust"   -> handleTrust(sender, args);
            case "top"     -> handleTop(sender, args);
            case "history" -> handleHistory(sender, args);
            case "reset"   -> handleReset(sender, args);
            case "stats"   -> handleStats(sender);
            case "watch"   -> handleWatch(sender, args);
            case "list"    -> handleList(sender, args);
            case "gui"     -> handleGui(sender);
            case "help"    -> handleHelp(sender);
            default -> {
                sender.sendMessage(Utils.colorize(config.getUsageMessage()));
                yield true;
            }
        };
    }

    // =========================================================================
    // reload
    // =========================================================================

    private boolean handleReload(CommandSender sender) {
        config.reload();
        sender.sendMessage(Utils.colorize(config.getReloadSuccessMessage()));
        return true;
    }

    // =========================================================================
    // check <player>
    // =========================================================================

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
    // trust <get|set> <player> [multiplier]
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

            Player onlinePlayer = Bukkit.getPlayerExact(target.getPlayerName());
            if (onlinePlayer != null) {
                config.setTrustPlayer(onlinePlayer.getUniqueId().toString(), multiplier);
                sender.sendMessage(Utils.colorize("&aTrust multiplier for &f" + target.getPlayerName()
                        + " &aset to &c" + String.format("%.2f", multiplier)
                        + "&a and saved to config.yml."));
            } else {
                sender.sendMessage(Utils.colorize("&aTrust multiplier for &f" + target.getPlayerName()
                        + " &aset to &c" + String.format("%.2f", multiplier)
                        + "&a (in-memory; player offline — add UUID manually to trust.players in config.yml)."));
            }
            return true;
        }

        sender.sendMessage(Utils.colorize("&cUsage: /medusa trust <get|set> <player> [multiplier]"));
        return true;
    }

    // =========================================================================
    // E1 — top [page]
    // =========================================================================

    private boolean handleTop(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
                if (page < 1) page = 1;
            } catch (NumberFormatException ignored) {}
        }

        int pageSize = config.getPageSize();
        double threshold = config.getDigestMinRatio();
        int minBlocks = config.getMinSampleSize();

        List<PlayerData> all = new ArrayList<>(dataManager.getAllEntries());
        all.removeIf(p -> p.getTotalBlocks() < minBlocks || p.calculateRatio() < threshold);
        all.sort((a, b) -> Double.compare(b.calculateRatio(), a.calculateRatio()));

        if (all.isEmpty()) {
            sender.sendMessage(Utils.colorize(config.getTopNoResultsMessage()));
            return true;
        }

        int totalPages = PaginationHelper.totalPages(all, pageSize);
        List<PlayerData> pageItems = PaginationHelper.getPage(all, page, pageSize);

        if (pageItems.isEmpty()) {
            sender.sendMessage(Utils.colorize("&7No results for page &c" + page + "&7."));
            return true;
        }

        String header = config.getTopHeaderMessage()
                .replace("{page}", String.valueOf(page))
                .replace("{totalPages}", String.valueOf(totalPages));
        sender.sendMessage(Utils.colorize(header));

        int rank = (page - 1) * pageSize + 1;
        for (PlayerData pd : pageItems) {
            String entry = config.getTopEntryMessage()
                    .replace("{rank}", String.valueOf(rank++))
                    .replace("{player}", pd.getPlayerName())
                    .replace("{ratio}", String.format("%.1f", pd.calculateRatio() * 100))
                    .replace("{score}", String.format("%.2f", pd.calculateScore()))
                    .replace("{total}", String.valueOf(pd.getTotalBlocks()));
            sender.sendMessage(Utils.colorize(entry));
        }

        if (page < totalPages) {
            sender.sendMessage(PaginationHelper.footer("top", page, totalPages));
        }
        return true;
    }

    // =========================================================================
    // E1 — history <player> [page]
    // =========================================================================

    private boolean handleHistory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Utils.colorize("&cUsage: /medusa history <player> [page]"));
            return true;
        }

        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
                if (page < 1) page = 1;
            } catch (NumberFormatException ignored) {}
        }

        String targetName = args[1];
        PlayerData targetData = findPlayerData(targetName);

        if (targetData == null) {
            sender.sendMessage(Utils.colorize(config.getPlayerNotFoundMessage()
                    .replace("{player}", targetName)));
            return true;
        }

        UUID uuid = targetData.getPlayerUuid();
        List<AlertRecord> alerts = dataManager.getDatabase().queryAllAlerts(uuid);

        if (alerts.isEmpty()) {
            String empty = config.getHistoryEmptyMessage().replace("{player}", targetData.getPlayerName());
            sender.sendMessage(Utils.colorize(empty));
            return true;
        }

        int pageSize = config.getPageSize();
        int totalPages = PaginationHelper.totalPages(alerts, pageSize);
        List<AlertRecord> pageItems = PaginationHelper.getPage(alerts, page, pageSize);

        if (pageItems.isEmpty()) {
            sender.sendMessage(Utils.colorize("&7No results for page &c" + page + "&7."));
            return true;
        }

        String header = config.getHistoryHeaderMessage()
                .replace("{player}", targetData.getPlayerName())
                .replace("{page}", String.valueOf(page))
                .replace("{totalPages}", String.valueOf(totalPages));
        sender.sendMessage(Utils.colorize(header));

        for (AlertRecord ar : pageItems) {
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(ar.timestamp()));
            String entry = config.getHistoryEntryMessage()
                    .replace("{timestamp}", ts)
                    .replace("{tier}", ar.tier())
                    .replace("{ratio}", String.format("%.1f", ar.ratio() * 100))
                    .replace("{score}", String.format("%.2f", ar.score()));
            sender.sendMessage(Utils.colorize(entry));
        }

        if (page < totalPages) {
            sender.sendMessage(PaginationHelper.footer("history " + targetName, page, totalPages));
        }
        return true;
    }

    // =========================================================================
    // E1 — reset <player> [confirm]
    // =========================================================================

    private boolean handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Utils.colorize("&cUsage: /medusa reset <player> [confirm]"));
            return true;
        }

        String targetName = args[1];
        PlayerData targetData = findPlayerData(targetName);

        if (targetData == null) {
            sender.sendMessage(Utils.colorize(config.getPlayerNotFoundMessage()
                    .replace("{player}", targetName)));
            return true;
        }

        boolean confirmed = args.length >= 3 && args[2].equalsIgnoreCase("confirm");

        if (!confirmed) {
            String warning = config.getResetNoConfirmMessage()
                    .replace("{player}", targetData.getPlayerName())
                    .replace("{ratio}", String.format("%.1f", targetData.calculateRatio() * 100))
                    .replace("{score}", String.format("%.2f", targetData.calculateScore()))
                    .replace("{total}", String.valueOf(targetData.getTotalBlocks()));
            sender.sendMessage(Utils.colorize(warning));
            String confirm = config.getResetConfirmMessage()
                    .replace("{player}", targetData.getPlayerName());
            sender.sendMessage(Utils.colorize(confirm));
            return true;
        }

        dataManager.resetPlayer(targetData);
        sender.sendMessage(Utils.colorize(config.getResetSuccessMessage()
                .replace("{player}", targetData.getPlayerName())));
        return true;
    }

    // =========================================================================
    // E1 — stats
    // =========================================================================

    private boolean handleStats(CommandSender sender) {
        int tracked = 0;
        int flagged = 0;
        double threshold = config.getAlertThreshold();
        int minBlocks = config.getMinSampleSize();

        long cutoff = System.currentTimeMillis() - (config.getWindowMinutes() * 60_000L);
        for (PlayerData pd : dataManager.getAllEntries()) {
            pd.purgeExpired(cutoff);
            if (pd.getTotalBlocks() >= minBlocks) {
                tracked++;
                if (pd.calculateRatio() >= threshold) {
                    flagged++;
                }
            }
        }

        int alertsToday = dataManager.getDatabase().countAlertsToday();
        String msg = config.getStatsMessage()
                .replace("{tracked}", String.valueOf(tracked))
                .replace("{flagged}", String.valueOf(flagged))
                .replace("{alertsToday}", String.valueOf(alertsToday));
        sender.sendMessage(Utils.colorize(msg));
        return true;
    }

    // =========================================================================
    // E1 — watch <player>
    // =========================================================================

    private boolean handleWatch(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Utils.colorize("&cUsage: /medusa watch <player>"));
            return true;
        }

        if (!(sender instanceof Player staff)) {
            sender.sendMessage(Utils.colorize("&cOnly players can use this command."));
            return true;
        }

        String targetName = args[1];
        PlayerData targetData = findPlayerData(targetName);

        if (targetData == null) {
            sender.sendMessage(Utils.colorize(config.getPlayerNotFoundMessage()
                    .replace("{player}", targetName)));
            return true;
        }

        boolean enabled = watchManager.toggleWatch(staff.getUniqueId(), targetData.getPlayerUuid());
        if (enabled) {
            sender.sendMessage(Utils.colorize(config.getWatchEnabledMessage()
                    .replace("{player}", targetData.getPlayerName())));
        } else {
            sender.sendMessage(Utils.colorize(config.getWatchDisabledMessage()
                    .replace("{player}", targetData.getPlayerName())));
        }
        return true;
    }

    // =========================================================================
    // E1 — list [page]
    // =========================================================================

    private boolean handleList(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
                if (page < 1) page = 1;
            } catch (NumberFormatException ignored) {}
        }

        double threshold = config.getAlertThreshold();
        int minBlocks = config.getMinSampleSize();
        int pageSize = config.getPageSize();

        List<PlayerData> flagged = dataManager.getFlagged(threshold, minBlocks);

        if (flagged.isEmpty()) {
            sender.sendMessage(Utils.colorize(config.getListEmptyMessage()));
            return true;
        }

        int totalPages = PaginationHelper.totalPages(flagged, pageSize);
        List<PlayerData> pageItems = PaginationHelper.getPage(flagged, page, pageSize);

        if (pageItems.isEmpty()) {
            sender.sendMessage(Utils.colorize("&7No results for page &c" + page + "&7."));
            return true;
        }

        String header = config.getListHeaderMessage()
                .replace("{page}", String.valueOf(page))
                .replace("{totalPages}", String.valueOf(totalPages));
        sender.sendMessage(Utils.colorize(header));

        for (PlayerData pd : pageItems) {
            String entry = config.getListEntryMessage()
                    .replace("{player}", pd.getPlayerName())
                    .replace("{ratio}", String.format("%.1f", pd.calculateRatio() * 100))
                    .replace("{score}", String.format("%.2f", pd.calculateScore()))
                    .replace("{total}", String.valueOf(pd.getTotalBlocks()));
            sender.sendMessage(Utils.colorize(entry));
        }

        if (page < totalPages) {
            sender.sendMessage(PaginationHelper.footer("list", page, totalPages));
        }
        return true;
    }

    // =========================================================================
    // E3 — gui
    // =========================================================================

    private boolean handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.colorize("&cOnly players can use the GUI."));
            return true;
        }
        if (!config.isGuiEnabled()) {
            sender.sendMessage(Utils.colorize("&cGUI is disabled in config.yml."));
            return true;
        }
        gui.open(player);
        return true;
    }

    // =========================================================================
    // E1 — help
    // =========================================================================

    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage(Utils.colorize(config.getHelpHeaderMessage()));
        sender.sendMessage(Utils.colorize("&7/medusa &freload &7— Reload the configuration"));
        sender.sendMessage(Utils.colorize("&7/medusa &fcheck <player> &7— Check a player's stats"));
        sender.sendMessage(Utils.colorize("&7/medusa &ftrust <get|set> <player> [mult] &7— View/set trust multiplier"));
        sender.sendMessage(Utils.colorize("&7/medusa &ftop [page] &7— List top suspects by ratio"));
        sender.sendMessage(Utils.colorize("&7/medusa &fhistory <player> [page] &7— View alert history"));
        sender.sendMessage(Utils.colorize("&7/medusa &freset <player> [confirm] &7— Reset detection data"));
        sender.sendMessage(Utils.colorize("&7/medusa &fstats &7— Show global detection statistics"));
        sender.sendMessage(Utils.colorize("&7/medusa &fwatch <player> &7— Toggle live verbose output"));
        sender.sendMessage(Utils.colorize("&7/medusa &flist [page] &7— List currently-flagged players"));
        sender.sendMessage(Utils.colorize("&7/medusa &fgui &7— Open the GUI dashboard"));
        return true;
    }

    // =========================================================================
    // Tab completion
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("medusa.admin")) return completions;

        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase())) completions.add(sub);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("check") || sub.equals("trust") || sub.equals("history")
                    || sub.equals("reset") || sub.equals("watch")) {
                addPlayerCompletions(completions, args[1]);
            }
            if (sub.equals("trust")) {
                if ("get".startsWith(args[1].toLowerCase())) completions.add("get");
                if ("set".startsWith(args[1].toLowerCase())) completions.add("set");
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("trust")) {
                if (args[1].equalsIgnoreCase("get") || args[1].equalsIgnoreCase("set")) {
                    addPlayerCompletions(completions, args[2]);
                }
            }
            if (sub.equals("reset")) {
                if ("confirm".startsWith(args[2].toLowerCase())) completions.add("confirm");
            }
        }
        return completions;
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

    private void addPlayerCompletions(List<String> completions, String prefix) {
        for (PlayerData data : dataManager.getAllEntries()) {
            if (data.getPlayerName().toLowerCase().startsWith(prefix.toLowerCase())) {
                completions.add(data.getPlayerName());
            }
        }
    }
}
