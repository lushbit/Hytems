package dev.lushbit.hytems.asset;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.lushbit.hytems.HytemsPlugin;
import dev.lushbit.hytems.data.BrowserFilterSettings;
import dev.lushbit.hytems.ui.ItemUiSupport;
import dev.lushbit.hytems.ui.TextFormatters;

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
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ItemSearchService {
    private static final Set<String> VALID_CATEGORIES = new HashSet<>(Arrays.asList(
            "weapon", "weapons", "tool", "tools", "armor", "armour",
            "block", "blocks", "food", "consumable", "consumables",
            "material", "materials", "resource", "resources",
            "furniture", "craftable", "ingredient", "ingredients"
    ));
    private static final Set<String> HIDDEN_QUALITIES = Set.of("Developer", "Tool", "Technical");
    private static final Map<String, List<Map.Entry<String, Item>>> BASE_RESULTS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Integer> BASE_RESULTS_ITEM_COUNT = new ConcurrentHashMap<>();

    private final PlayerRef playerRef;
    private final Map<String, String> translatedNameCache = new HashMap<>();
    private final Map<String, String> nativeCategoryAliases = new HashMap<>();
    private final Map<String, Set<String>> modItemsByPack = new HashMap<>();
    private final Map<String, String> modSearchNamesByPack = new HashMap<>();
    private final Set<String> modSearchAliases = new HashSet<>();
    private final Map<String, CategoryQuery> categoryQueryCache = new HashMap<>();
    private final Map<String, ModQuery> modQueryCache = new HashMap<>();
    private List<String> nativeCategoryPaths = List.of();
    private int indexedItemCount = -1;

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
        return items.entrySet().stream()
                .filter(entry -> !isFromTodoBench(entry.getKey()))
                .filter(entry -> matchesQuery(entry, searchQuery))
                .sorted((a, b) -> translatedName(a).compareToIgnoreCase(translatedName(b)))
                .collect(Collectors.toList());
    }

    public List<Map.Entry<String, Item>> filter(@Nonnull Map<String, Item> items, @Nonnull String searchQuery,
                                                @Nonnull BrowserFilterSettings filters, @Nonnull Set<String> pinnedItems) {
        ensureSearchIndexes(items);
        Comparator<Map.Entry<String, Item>> comparator = comparatorFor(filters.sorting);
        return items.entrySet().stream()
                .filter(entry -> !isFromTodoBench(entry.getKey()))
                .filter(entry -> filters.showHiddenItems || !isHiddenFromSearch(entry.getValue()))
                .filter(entry -> matchesQuery(entry, searchQuery))
                .filter(entry -> matchesNativeCategory(entry.getValue(), filters.category))
                .filter(entry -> matchesModFilter(entry.getKey(), filters.mod))
                .filter(entry -> matchesBoolean(filters.craftable, isCraftable(entry.getKey())))
                .filter(entry -> matchesBoolean(filters.droppable, HytemsPlugin.dropListRegistry.hasDropSources(entry.getKey())))
                .filter(entry -> matchesBoolean(filters.pinned, pinnedItems.contains(entry.getKey())))
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    public boolean isValidCategory(String category) {
        ensureSearchIndexes(HytemsPlugin.ITEMS);
        if (category != null && VALID_CATEGORIES.contains(category.toLowerCase(Locale.ENGLISH))) return true;
        return resolveNativeCategoryPath(category) != null;
    }

    public boolean isValidModSearch(String query) {
        ensureSearchIndexes(HytemsPlugin.ITEMS);
        if (query == null || query.isEmpty()) return false;
        String normalized = query.toLowerCase(Locale.ENGLISH);
        return modSearchNamesByPack.values().stream().anyMatch(name -> name.contains(normalized));
    }

    public boolean isValidCategoryTag(String query) {
        return resolveCategoryQuery(query).category != null;
    }

    public boolean isValidModTag(String query) {
        return resolveModQuery(query).mod != null;
    }

    private boolean matchesQuery(Map.Entry<String, Item> entry, String searchQuery) {
        if (searchQuery.isEmpty()) return true;

        if (searchQuery.startsWith("@")) {
            CategoryQuery categoryQuery = resolveCategoryQuery(searchQuery.substring(1));
            if (categoryQuery.category == null
                    || !matchesCategory(entry.getValue(), entry.getKey(), categoryQuery.category)) {
                return false;
            }
            return categoryQuery.additionalSearch.isEmpty() || matchesText(entry, categoryQuery.additionalSearch);
        }

        if (searchQuery.startsWith("#")) {
            ModQuery modQuery = resolveModQuery(searchQuery.substring(1));
            if (modQuery.mod == null || !matchesModSearch(entry.getKey(), modQuery.mod)) {
                return false;
            }
            return modQuery.additionalSearch.isEmpty() || matchesText(entry, modQuery.additionalSearch);
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
            if (matchesNativeCategory(item, category)) return true;
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

    public List<String> getNativeCategories(@Nonnull Map<String, Item> items) {
        Set<String> categories = new HashSet<>();
        for (Item item : items.values()) {
            if (item == null || item.getCategories() == null) continue;
            Collections.addAll(categories, item.getCategories());
        }
        List<String> sorted = new ArrayList<>(categories);
        sorted.removeIf(category -> category == null || category.isEmpty());
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    public List<String> getNativeCategoryPaths(@Nonnull Map<String, Item> items) {
        ensureSearchIndexes(items);
        return nativeCategoryPaths;
    }

    private void ensureSearchIndexes(@Nonnull Map<String, Item> items) {
        if (indexedItemCount == items.size()) return;

        nativeCategoryAliases.clear();
        modItemsByPack.clear();
        modSearchNamesByPack.clear();
        modSearchAliases.clear();
        categoryQueryCache.clear();
        modQueryCache.clear();

        Set<String> paths = new HashSet<>();
        for (String category : getNativeCategories(items)) {
            String[] parts = category.split("\\.");
            StringBuilder path = new StringBuilder();
            for (String part : parts) {
                if (path.length() > 0) path.append('.');
                path.append(part);
                paths.add(path.toString());
            }
        }
        List<String> sorted = new ArrayList<>(paths);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        nativeCategoryPaths = Collections.unmodifiableList(sorted);
        for (String path : nativeCategoryPaths) {
            nativeCategoryAliases.putIfAbsent(path.toLowerCase(Locale.ENGLISH), path);
            String[] parts = path.split("\\.");
            String leaf = parts[parts.length - 1];
            nativeCategoryAliases.putIfAbsent(leaf.toLowerCase(Locale.ENGLISH), path);
            nativeCategoryAliases.putIfAbsent(TextFormatters.itemName(leaf).toLowerCase(Locale.ENGLISH), path);
        }

        for (AssetPack pack : AssetModule.get().getAssetPacks()) {
            Set<String> itemIds = Item.getAssetMap().getKeysForPack(pack.getName());
            if (itemIds == null || itemIds.isEmpty()) continue;
            modItemsByPack.put(pack.getName(), itemIds);
            String displayName = pack.getManifest() == null ? "" : pack.getManifest().getName();
            String normalizedPackName = pack.getName().toLowerCase(Locale.ENGLISH);
            String normalizedDisplayName = displayName == null ? "" : displayName.toLowerCase(Locale.ENGLISH);
            modSearchNamesByPack.put(pack.getName(), normalizedPackName + " " + normalizedDisplayName);
            modSearchAliases.add(normalizedPackName);
            if (!normalizedDisplayName.isEmpty()) modSearchAliases.add(normalizedDisplayName);
        }
        indexedItemCount = items.size();
    }

    private String resolveNativeCategoryPath(String category) {
        if (category == null || category.isEmpty()) return null;
        ensureSearchIndexes(HytemsPlugin.ITEMS);
        return nativeCategoryAliases.get(category.toLowerCase(Locale.ENGLISH));
    }

    private CategoryQuery resolveCategoryQuery(String query) {
        ensureSearchIndexes(HytemsPlugin.ITEMS);
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ENGLISH);
        CategoryQuery cached = categoryQueryCache.get(normalized);
        if (cached != null) return cached;

        String bestAlias = null;
        String bestPath = null;
        for (Map.Entry<String, String> alias : nativeCategoryAliases.entrySet()) {
            if ((normalized.equals(alias.getKey()) || normalized.startsWith(alias.getKey() + " "))
                    && (bestAlias == null || alias.getKey().length() > bestAlias.length())) {
                bestAlias = alias.getKey();
                bestPath = alias.getValue();
            }
        }
        if (bestPath == null) {
            int spaceIndex = normalized.indexOf(' ');
            String category = spaceIndex > 0 ? normalized.substring(0, spaceIndex) : normalized;
            if (VALID_CATEGORIES.contains(category)) {
                CategoryQuery resolved = new CategoryQuery(category,
                        spaceIndex > 0 ? normalized.substring(spaceIndex + 1).trim() : "");
                categoryQueryCache.put(normalized, resolved);
                return resolved;
            }
        }
        CategoryQuery resolved = bestPath == null
                ? new CategoryQuery(null, "")
                : new CategoryQuery(bestPath, normalized.substring(bestAlias.length()).trim());
        categoryQueryCache.put(normalized, resolved);
        return resolved;
    }

    private ModQuery resolveModQuery(String query) {
        ensureSearchIndexes(HytemsPlugin.ITEMS);
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ENGLISH);
        ModQuery cached = modQueryCache.get(normalized);
        if (cached != null) return cached;

        String bestName = null;
        for (String alias : modSearchAliases) {
            if ((normalized.equals(alias) || normalized.startsWith(alias + " "))
                    && (bestName == null || alias.length() > bestName.length())) {
                bestName = alias;
            }
        }
        ModQuery resolved = bestName == null
                ? new ModQuery(null, "")
                : new ModQuery(bestName, normalized.substring(bestName.length()).trim());
        modQueryCache.put(normalized, resolved);
        return resolved;
    }

    private boolean matchesNativeCategory(Item item, String category) {
        if (category == null || category.isEmpty() || BrowserFilterSettings.ALL.equalsIgnoreCase(category)) return true;
        if (item == null) return false;
        String normalized = category.replaceFirst("(?i)^category:", "").toLowerCase(Locale.ENGLISH);
        if (item.getCategories() != null) {
            for (String itemCategory : item.getCategories()) {
                if (itemCategory != null && (itemCategory.equalsIgnoreCase(normalized)
                        || itemCategory.toLowerCase(Locale.ENGLISH).startsWith(normalized + "."))) return true;
            }
        }
        return item.getSubCategory() != null && item.getSubCategory().equalsIgnoreCase(normalized);
    }

    private boolean matchesModFilter(String itemId, String packName) {
        if (packName == null || packName.isEmpty() || BrowserFilterSettings.ALL.equalsIgnoreCase(packName)) return true;
        Set<String> packItems = modItemsByPack.get(packName);
        return packItems != null && packItems.contains(itemId);
    }

    private boolean matchesModSearch(String itemId, String query) {
        if (query == null || query.isEmpty()) return true;

        String packName = Item.getAssetMap().getAssetPack(itemId);
        if (packName == null) return false;
        String searchableName = modSearchNamesByPack.get(packName);
        return searchableName != null && searchableName.contains(query.toLowerCase(Locale.ENGLISH));
    }

    private boolean isCraftable(String itemId) {
        return !HytemsPlugin.recipeManager.getCraftingRecipes(itemId).isEmpty() && !isFromTodoBench(itemId);
    }

    private boolean isHiddenFromSearch(Item item) {
        try {
            ItemQuality quality = item == null ? null : ItemQuality.getAssetMap().getAsset(item.getQualityIndex());
            return quality != null && (quality.isHiddenFromSearch() || HIDDEN_QUALITIES.contains(quality.getId()));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean matchesBoolean(String filter, boolean value) {
        if ("yes".equalsIgnoreCase(filter)) return value;
        if ("no".equalsIgnoreCase(filter)) return !value;
        return true;
    }

    private Comparator<Map.Entry<String, Item>> comparatorFor(String sorting) {
        Comparator<Map.Entry<String, Item>> alphabetical =
                (a, b) -> translatedName(a).compareToIgnoreCase(translatedName(b));
        if ("z-a".equalsIgnoreCase(sorting)) return alphabetical.reversed();
        if ("quality-desc".equalsIgnoreCase(sorting) || "quality".equalsIgnoreCase(sorting)) {
            return Comparator.<Map.Entry<String, Item>>comparingInt(entry -> hasQuality(entry.getValue()) ? 0 : 1)
                    .thenComparing(Comparator.comparingInt((Map.Entry<String, Item> entry) -> qualityValue(entry.getValue())).reversed())
                    .thenComparing(alphabetical);
        }
        if ("quality-asc".equalsIgnoreCase(sorting)) {
            return Comparator.<Map.Entry<String, Item>>comparingInt(entry -> hasQuality(entry.getValue()) ? 0 : 1)
                    .thenComparingInt(entry -> qualityValue(entry.getValue()))
                    .thenComparing(alphabetical);
        }
        if ("weapon-damage".equalsIgnoreCase(sorting)) {
            return Comparator.<Map.Entry<String, Item>>comparingDouble(entry -> weaponDamage(entry.getValue()))
                    .reversed().thenComparing(alphabetical);
        }
        if ("category".equalsIgnoreCase(sorting)) {
            return Comparator.<Map.Entry<String, Item>, String>comparing(entry -> firstCategory(entry.getValue()),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(alphabetical);
        }
        return alphabetical;
    }

    private int qualityValue(Item item) {
        try {
            ItemQuality quality = item == null ? null : ItemQuality.getAssetMap().getAsset(item.getQualityIndex());
            return quality != null ? quality.getQualityValue() : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private boolean hasQuality(Item item) {
        try {
            return item != null && ItemQuality.getAssetMap().getAsset(item.getQualityIndex()) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private double weaponDamage(Item item) {
        if (item == null || item.getWeapon() == null || item.getWeapon().getBasicDamageBreakdown() == null) return 0;
        return item.getWeapon().getBasicDamageBreakdown().entries().stream().mapToDouble(entry -> entry.max()).sum();
    }

    private String firstCategory(Item item) {
        if (item == null || item.getCategories() == null || item.getCategories().length == 0) return "";
        return item.getCategories()[0];
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

    }

    private static final class ModQuery {
        private final String mod;
        private final String additionalSearch;

        private ModQuery(String mod, String additionalSearch) {
            this.mod = mod;
            this.additionalSearch = additionalSearch;
        }

    }

    private static String baseCacheKey(@Nonnull PlayerRef playerRef) {
        return String.valueOf(playerRef.getLanguage());
    }
}
