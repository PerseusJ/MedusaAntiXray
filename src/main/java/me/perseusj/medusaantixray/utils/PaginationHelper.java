package me.perseusj.medusaantixray.utils;

import java.util.List;

/**
 * E2 — Shared pagination utility for consistent multi-entry command output.
 */
public class PaginationHelper {

    private PaginationHelper() {}

    public static <T> List<T> getPage(List<T> items, int page, int pageSize) {
        if (page < 1) page = 1;
        int total = items.size();
        int fromIndex = (page - 1) * pageSize;
        if (fromIndex >= total) return List.of();
        int toIndex = Math.min(fromIndex + pageSize, total);
        return items.subList(fromIndex, toIndex);
    }

    public static int totalPages(List<?> items, int pageSize) {
        if (pageSize <= 0) return 1;
        int total = items.size();
        return Math.max(1, (total + pageSize - 1) / pageSize);
    }

    public static String footer(String subcommand, int page, int totalPages) {
        if (totalPages <= 1) return "";
        if (page >= totalPages) {
            return Utils.colorize("&7No results for page &c" + page + "&7.");
        }
        return Utils.colorize("&7Page &f" + page + "&7/&f" + totalPages
                + "&7. Use &f/medusa " + subcommand + " " + (page + 1) + " &7for next page.");
    }
}