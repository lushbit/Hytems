package dev.lushbit.hytems.asset;

import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DropListRegistry {
    private final Map<String, ItemDropList> dropLists = new LinkedHashMap<>();
    private final Map<String, Set<String>> itemDropSources = new HashMap<>();

    public void reload(Map<String, ItemDropList> newDropLists) {
        this.dropLists.clear();
        this.itemDropSources.clear();

        for (Map.Entry<String, ItemDropList> entry : newDropLists.entrySet()) {
            String dropListId = entry.getKey();
            ItemDropList dropList = entry.getValue();
            this.dropLists.put(dropListId, dropList);
            indexDropListContents(dropList, dropListId);
        }

        System.out.println("[Hytems] Indexed " + this.dropLists.size() + " drop lists");
        System.out.println("[Hytems] Found " + this.itemDropSources.size() + " items with drop sources");
    }

    private void indexDropListContents(ItemDropList dropList, String dropListId) {
        if (shouldIgnoreDropSource(dropListId)) {
            return;
        }

        try {
            Object container = dropList.getContainer();
            if (container != null) {
                extractItemIds(container, dropListId);
            }
        } catch (Exception e) {
            System.err.println("[Hytems] Error getting container for drop list " + dropListId + ": " + e.getMessage());
        }
    }

    private void extractItemIds(Object container, String dropListId) {
        if (container == null) return;

        try {
            if (container instanceof Collection) {
                Collection<?> items = (Collection<?>) container;
                for (Object item : items) {
                    String itemId = extractItemIdFromDrop(item);
                    if (itemId != null && !shouldIgnoreDroppedItem(itemId)) {
                        itemDropSources.computeIfAbsent(itemId, k -> new LinkedHashSet<>()).add(dropListId);
                    }
                }
            } else if (container.getClass().isArray()) {
                Object[] items = (Object[]) container;
                for (Object item : items) {
                    String itemId = extractItemIdFromDrop(item);
                    if (itemId != null && !shouldIgnoreDroppedItem(itemId)) {
                        itemDropSources.computeIfAbsent(itemId, k -> new LinkedHashSet<>()).add(dropListId);
                    }
                }
            } else {
                tryExtractFromContainer(container, dropListId);
            }
        } catch (Exception e) {
            System.err.println("[Hytems] Error extracting items from drop list " + dropListId + ": " + e.getMessage());
        }
    }

    private String extractItemIdFromDrop(Object drop) {
        if (drop == null) return null;

        try {
            String[] methodNames = {"getItemId", "getId", "getItem", "getMaterial"};
            for (String methodName : methodNames) {
                try {
                    Method method = drop.getClass().getMethod(methodName);
                    Object result = method.invoke(drop);
                    if (result instanceof String) {
                        return (String) result;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("[Hytems] Error extracting item ID: " + e.getMessage());
        }
        return null;
    }

    private void tryExtractFromContainer(Object container, String dropListId) {
        try {
            Method getAllDropsMethod = container.getClass().getMethod("getAllDrops", List.class);
            List<Object> dropsList = new ArrayList<>();
            Object result = getAllDropsMethod.invoke(container, dropsList);

            if (result instanceof List) {
                List<?> drops = (List<?>) result;
                for (Object drop : drops) {
                    String itemId = extractItemIdFromDrop(drop);
                    if (itemId != null && !shouldIgnoreDroppedItem(itemId)) {
                        itemDropSources.computeIfAbsent(itemId, k -> new LinkedHashSet<>()).add(dropListId);
                    }
                }
            }
        } catch (Exception e) {

        }
    }

    public List<String> getDropSourcesForItem(String itemId) {
        Set<String> sources = itemDropSources.get(itemId);
        return sources != null ? new ArrayList<>(sources) : Collections.emptyList();
    }

    public boolean hasDropSources(String itemId) {
        return itemDropSources.containsKey(itemId) && !itemDropSources.get(itemId).isEmpty();
    }

    public int size() {
        return dropLists.size();
    }

    private boolean shouldIgnoreDropSource(String dropListId) {
        if (dropListId == null || dropListId.isEmpty()) {
            return true;
        }

        String normalized = dropListId.replace('\\', '/');
        String lower = normalized.toLowerCase(Locale.ENGLISH);
        return lower.startsWith("*")
                || lower.contains("state_definitions")
                || lower.contains("gathering_breaking_droplist")
                || lower.contains("breaking_droplist")
                || lower.contains("droplist_container");
    }

    private boolean shouldIgnoreDroppedItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return true;
        }

        String lower = itemId.toLowerCase(Locale.ENGLISH);
        return lower.endsWith("_half");
    }
}
