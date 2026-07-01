package me.perseusj.medusaantixray.ui;

import me.perseusj.medusaantixray.data.PlayerData;
import me.perseusj.medusaantixray.managers.ConfigManager;
import me.perseusj.medusaantixray.managers.DataManager;
import me.perseusj.medusaantixray.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * E3 — Optional inventory-based GUI dashboard listing flagged players with
 * skull icons, hover-info lore, and click actions (teleport, history, reset).
 *
 * <p>Implements {@link InventoryHolder} so the inventory can be opened directly.
 * Navigation arrows occupy the bottom row (slots 45–53).</p>
 */
public class MedusaGui implements InventoryHolder {

    private static final int GUI_SIZE = 54;
    private static final int ARROW_SLOT_PREV = 48;
    private static final int ARROW_SLOT_NEXT = 50;
    private static final int ARROW_SLOT_INFO  = 49;

    private static final int[] CONTENT_SLOTS = new int[45];
    static {
        for (int i = 0; i < 45; i++) CONTENT_SLOTS[i] = i;
    }

    private final ConfigManager config;
    private final DataManager dataManager;

    public MedusaGui(ConfigManager config, DataManager dataManager) {
        this.config = config;
        this.dataManager = dataManager;
    }

    /**
     * Opens the GUI for {@code player} at the given page.
     */
    public void open(Player player) {
        open(player, 1);
    }

    /**
     * Opens the GUI for {@code player} at {@code page} (1-indexed).
     */
    public void open(Player player, int page) {
        if (page < 1) page = 1;

        double threshold = config.getAlertThreshold();
        int minBlocks = config.getMinSampleSize();
        List<PlayerData> flagged = dataManager.getFlagged(threshold, minBlocks);
        int pageSize = config.getGuiPageSize();
        int totalPages = Math.max(1, (flagged.size() + pageSize - 1) / pageSize);
        if (page > totalPages) page = totalPages;

        Inventory inv = Bukkit.createInventory(this, GUI_SIZE, Utils.colorize(config.getGuiTitle()));

        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, flagged.size());
        List<PlayerData> pageItems = flagged.subList(fromIndex, toIndex);

        for (int i = 0; i < pageItems.size() && i < CONTENT_SLOTS.length; i++) {
            PlayerData pd = pageItems.get(i);
            inv.setItem(CONTENT_SLOTS[i], buildPlayerSkull(pd));
        }

        if (page > 1) {
            inv.setItem(ARROW_SLOT_PREV, buildArrow("&a\u25c0 Page " + (page - 1), page - 1));
        }
        if (page < totalPages) {
            inv.setItem(ARROW_SLOT_NEXT, buildArrow("&aPage " + (page + 1) + " \u25b6", page + 1));
        }
        inv.setItem(ARROW_SLOT_INFO, buildInfoItem(page, totalPages));

        player.openInventory(inv);
    }

    /**
     * Builds a player skull {@link ItemStack} with lore showing ratio, score,
     * total blocks, and click instructions.
     */
    private ItemStack buildPlayerSkull(PlayerData pd) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            Player online = Bukkit.getPlayerExact(pd.getPlayerName());
            if (online != null) {
                meta.setOwningPlayer(online);
            }
            meta.setDisplayName(Utils.colorize("&c" + pd.getPlayerName()));

            List<String> lore = new ArrayList<>(config.getGuiSkullLore());
            lore.replaceAll(line -> Utils.colorize(line
                    .replace("{ratio}", String.format("%.1f", pd.calculateRatio() * 100))
                    .replace("{score}", String.format("%.2f", pd.calculateScore()))
                    .replace("{total}", String.valueOf(pd.getTotalBlocks()))));
            meta.setLore(lore);

            skull.setItemMeta(meta);
        }
        return skull;
    }

    /** Builds a navigation arrow with the target page stored in lore so GuiListener can read it. */
    private ItemStack buildArrow(String displayName, int targetPage) {
        ItemStack arrow = new ItemStack(Material.ARROW, 1);
        ItemMeta meta = arrow.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Utils.colorize(displayName));
            meta.setLore(List.of(String.valueOf(targetPage)));
            arrow.setItemMeta(meta);
        }
        return arrow;
    }

    /** Builds an info item showing current page. */
    private ItemStack buildInfoItem(int page, int totalPages) {
        ItemStack info = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Utils.colorize("&7Page &f" + page + "&7/&f" + totalPages));
            meta.setLore(List.of(Utils.colorize("&7" + dataManager.getFlagged(
                    config.getAlertThreshold(), config.getMinSampleSize()).size() + " &7flagged players")));
            info.setItemMeta(meta);
        }
        return info;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
