package dev.lushbit.hytems.asset;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.lushbit.hytems.HytemsPlugin;
import dev.lushbit.hytems.ui.ItemUiSupport;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ItemSearchService {
    private static final Set<String> VALID_CATEGORIES = new HashSet<>(Arrays.asList(
            "weapon", "weapons", "tool", "tools", "armor", "armour",
            "block", "blocks", "food", "consumable", "consumables",
            "material", "materials", "resource", "resources",
            "furniture", "craftable", "ingredient", "ingredients"
    ));
    private static final Map<String, List<Map.Entry<String, Item>>> BASE_RESULTS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Integer> BASE_RESULTS_ITEM_COUNT = new ConcurrentHashMap<>();

    private final PlayerRef playerRef;
    private final Map<String, String> translatedNameCache = new HashMap<>();

    public ItemSearchService(@Nonnull PlayerRef playerRef) {
        this.playerRef = playerRef;
    }

    public static void prewarmBaseResults(@Nonnull PlayerRef playerRef, @Nonnull Map<String, Item> items) {
        String cacheKey = baseCacheKey(playerRef);
        Integer cachedCount = BASE_RESULTS_ITEM_COUNT.get(cacheKey);
        if (cachedCount != null && cachedCount == items.size() && BASE_RESULTS_CACHE.containsKey(cacheKey)) {
            return;
        }

        ItemSearchService service = new ItemSearchService(playerRef);
        service.buildBaseResults(items, cacheKey);
    }

    public List<Map.Entry<String, Item>> filter(@Nonnull Map<String, Item> items, @Nonnull String searchQuery) {
        if (searchQuery.isEmpty()) {
            return getBaseResults(items);
        }

        return items.entrySet().stream()
                .filter(entry -> !isFromTodoBench(entry.getKey()))
                .filter(entry -> matchesQuery(entry, searchQuery))
                .sorted((a, b) -> translatedName(a).compareToIgnoreCase(translatedName(b)))
                .collect(Collectors.toList());
    }

    public boolean isValidCategory(String category) {
        return VALID_CATEGORIES.contains(category);
    }

    private boolean matchesQuery(Map.Entry<String, Item> entry, String searchQuery) {
        if (searchQuery.isEmpty()) return true;

        if (searchQuery.startsWith("@")) {
            CategoryQuery categoryQuery = CategoryQuery.parse(searchQuery);
            if (!matchesCategory(entry.getValue(), entry.getKey(), categoryQuery.category)) {
                return false;
            }
            return categoryQuery.additionalSearch.isEmpty() || matchesText(entry, categoryQuery.additionalSearch);
        }

        return matchesText(entry, searchQuery.toLowerCase(Locale.ENGLISH));
    }

    private boolean matchesText(Map.Entry<String, Item> entry, String lowerQuery) {
        if (entry.getValue() == null) return false;

        return entry.getKey().toLowerCase(Locale.ENGLISH).contains(lowerQuery)
                || translatedName(entry).toLowerCase(Locale.ENGLISH).contains(lowerQuery);
    }

    private boolean isFromTodoBench(String itemId) {
        return RecipeUtils.hasTodoBench(HytemsPlugin.recipeManager.getCraftingRecipes(itemId));
    }

    private boolean matchesCategory(Item item, String itemId, String category) {
        if (item == null) return false;

        try {
            switch (category) {
                case "weapon":
                case "weapons":
                    return hasComponent(item, "Weapon") || containsAny(itemId, "Sword", "Bow", "Staff", "Axe", "Dagger");
                case "tool":
                case "tools":
                    return hasComponent(item, "Tool") || containsAny(itemId, "Pickaxe", "Hoe", "Shovel");
                case "armor":
                case "armour":
                    return hasComponent(item, "Armor") || containsAny(itemId, "Helmet", "Chestplate", "Leggings", "Boots");
                case "block":
                case "blocks":
                    return hasComponent(item, "Block") || isBlock(item);
                case "food":
                case "consumable":
                case "consumables":
                    return hasComponent(item, "Consumable") || containsAny(itemId, "Food", "Potion", "Ingredient");
                case "material":
                case "materials":
                case "resource":
                case "resources":
                    return containsAny(itemId, "Ingot", "Ore", "Wood", "Stone", "Plank", "Bar");
                case "furniture":
                    return containsAny(itemId, "Chair", "Table", "Bed", "Torch");
                case "craftable":
                    return !HytemsPlugin.recipeManager.getCraftingRecipes(itemId).isEmpty() && !isFromTodoBench(itemId);
                case "ingredient":
                case "ingredients":
                    return itemId.contains("Ingredient");
                default:
                    return itemId.toLowerCase(Locale.ENGLISH).contains(category) || hasComponent(item, category);
            }
        } catch (Exception e) {
            System.err.println("[Hytems] Error checking category for " + itemId + ": " + e.getMessage());
            return false;
        }
    }

    private boolean hasComponent(Item item, String componentName) {
        if (item == null || componentName == null || componentName.isEmpty()) return false;

        if (tryInvokeNoArg(item, "get" + componentName) != null) return true;

        try {
            Method hasComponent = Item.class.getMethod("hasComponent", String.class);
            Object hasComponentResult = hasComponent.invoke(item, componentName);
            if (hasComponentResult instanceof Boolean && (Boolean) hasComponentResult) return true;

            Method getComponent = Item.class.getMethod("getComponent", String.class);
            if (getComponent.invoke(item, componentName) != null) return true;

            Object itemType = tryInvokeNoArg(item, "getItemType");
            return itemType != null && itemType.toString().toLowerCase(Locale.ENGLISH)
                    .contains(componentName.toLowerCase(Locale.ENGLISH));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isBlock(Item item) {
        Object itemType = tryInvokeNoArg(item, "getItemType");
        return itemType != null && itemType.toString().toLowerCase(Locale.ENGLISH).contains("block");
    }

    private Object tryInvokeNoArg(Item item, String methodName) {
        try {
            return Item.class.getMethod(methodName).invoke(item);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String translatedName(Map.Entry<String, Item> entry) {
        return translatedName(entry.getKey(), entry.getValue());
    }

    private String translatedName(String itemId, Item item) {
        return translatedNameCache.computeIfAbsent(
                itemId,
                ignored -> ItemUiSupport.translatedName(playerRef, item, itemId)
        );
    }

    private List<Map.Entry<String, Item>> getBaseResults(Map<String, Item> items) {
        String cacheKey = baseCacheKey(this.playerRef);
        Integer cachedCount = BASE_RESULTS_ITEM_COUNT.get(cacheKey);
        List<Map.Entry<String, Item>> cachedResults = BASE_RESULTS_CACHE.get(cacheKey);
        if (cachedCount != null && cachedCount == items.size() && cachedResults != null && !cachedResults.isEmpty()) {
            return cachedResults;
        }

        return buildBaseResults(items, cacheKey);
    }

    private List<Map.Entry<String, Item>> buildBaseResults(Map<String, Item> items, String cacheKey) {
        List<Map.Entry<String, Item>> baseResults = new ArrayList<>(items.entrySet());
        baseResults.removeIf(entry -> isFromTodoBench(entry.getKey()));
        baseResults.sort((a, b) -> translatedName(a).compareToIgnoreCase(translatedName(b)));

        List<Map.Entry<String, Item>> immutableResults = Collections.unmodifiableList(baseResults);
        BASE_RESULTS_CACHE.put(cacheKey, immutableResults);
        BASE_RESULTS_ITEM_COUNT.put(cacheKey, items.size());
        return immutableResults;
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private static final class CategoryQuery {
        private final String category;
        private final String additionalSearch;

        private CategoryQuery(String category, String additionalSearch) {
            this.category = category;
            this.additionalSearch = additionalSearch;
        }

        private static CategoryQuery parse(String searchQuery) {
            String queryAfterAt = searchQuery.substring(1).trim();
            int spaceIndex = queryAfterAt.indexOf(' ');
            if (spaceIndex > 0) {
                return new CategoryQuery(
                        queryAfterAt.substring(0, spaceIndex).toLowerCase(Locale.ENGLISH),
                        queryAfterAt.substring(spaceIndex + 1).trim().toLowerCase(Locale.ENGLISH)
                );
            }
            return new CategoryQuery(queryAfterAt.toLowerCase(Locale.ENGLISH), "");
        }
    }

    private static String baseCacheKey(@Nonnull PlayerRef playerRef) {
        return String.valueOf(playerRef.getLanguage());
    }
}
