package me.perseusj.medusaantixray.ui;

import me.perseusj.medusaantixray.data.PlayerData;
import me.perseusj.medusaantixray.managers.DataManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * E3 — Handles interaction with the {@link MedusaGui} inventory.
 *
 * <p>Click actions:
 * <ul>
 *   <li><b>Left-click</b> → teleport to the player (staff permission required)</li>
 *   <li><b>Right-click</b> → show alert history in chat</li>
 *   <li><b>Shift-click</b> → reset the player's detection data (with confirmation in chat)</li>
 *   <li><b>Arrow (prev/next)</b> → navigate pages</li>
 * </ul>
 */
public class GuiListener implements Listener {

    private final MedusaGui gui;
    private final DataManager dataManager;

    public GuiListener(MedusaGui gui, DataManager dataManager) {
        this.gui = gui;
        this.dataManager = dataManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MedusaGui)) return;
        if (!(event.getWhoClicked() instanceof Player clicker)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        // Navigation arrows — page target stored in lore
        if (item.getType() == Material.ARROW) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasLore()) {
                List<String> lore = meta.getLore();
                if (!lore.isEmpty()) {
                    String pageStr = lore.get(0).replaceAll("[^0-9]", "");
                    if (!pageStr.isEmpty()) {
                        gui.open(clicker, Integer.parseInt(pageStr));
                    }
                }
            }
            return;
        }

        // Player head actions
        if (item.getType() == Material.PLAYER_HEAD) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) return;

            String displayName = meta.getDisplayName();
            // Strip color codes to get the raw player name (colorized as "§cPlayerName")
            String playerName = displayName.substring(displayName.lastIndexOf(' ') + 1);
            // Also handle plain "§cName" — strip leading section-sign sequences
            playerName = playerName.replaceAll("^[§&][0-9a-fk-or]*", "").trim();
            if (playerName.isEmpty()) {
                // Fallback: try to extract the last non-empty segment
                String raw = org.bukkit.ChatColor.stripColor(displayName);
                playerName = raw != null ? raw.trim() : "";
            }

            if (playerName.isEmpty()) return;

            PlayerData pd = findPlayerData(playerName);
            if (pd == null) return;

            if (event.isShiftClick()) {
                clicker.chat("/medusa reset " + pd.getPlayerName());
            } else if (event.isRightClick()) {
                clicker.chat("/medusa history " + pd.getPlayerName());
            } else {
                if (clicker.hasPermission("medusa.admin")) {
                    clicker.chat("/tp " + pd.getPlayerName());
                } else {
                    clicker.sendMessage("§cYou don't have permission to teleport.");
                }
            }
        }
    }

    private PlayerData findPlayerData(String name) {
        for (PlayerData pd : dataManager.getAllEntries()) {
            if (pd.getPlayerName().equalsIgnoreCase(name)) {
                return pd;
            }
        }
        return null;
    }
}
