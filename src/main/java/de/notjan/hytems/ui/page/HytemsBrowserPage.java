package de.notjan.hytems.ui.page;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import de.notjan.hytems.HytemsPlugin;
import de.notjan.hytems.asset.DropSourceParser;
import de.notjan.hytems.asset.ItemSearchService;
import de.notjan.hytems.asset.RecipeUtils;
import de.notjan.hytems.ui.HytemsUiTemplates;
import de.notjan.hytems.ui.ItemUiSupport;
import de.notjan.hytems.ui.TextFormatters;

import javax.annotation.Nonnull;
import java.util.*;

public class HytemsBrowserPage extends InteractiveCustomUIPage<HytemsBrowserPage.BrowserData> {

    private static final int ITEMS_PER_ROW = 7;
    private static final int ROWS_PER_PAGE = 8;
    private static final int ITEMS_PER_PAGE = ITEMS_PER_ROW * ROWS_PER_PAGE;

    private String searchQuery = "";
    private int currentPage = 0;
    private String selectedItemId = null;
    private String dropsItemId = null;
    private List<Map.Entry<String, Item>> filteredItems = new ArrayList<>();
    private Set<String> favoriteItems = new LinkedHashSet<>();
    private boolean favoritesExpanded = false;
    private Ref<EntityStore> pageRef;
    private Store<EntityStore> pageStore;
    private PlayerRef playerRef;
    private final ItemSearchService itemSearchService;

    public HytemsBrowserPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, BrowserData.CODEC);
        this.playerRef = playerRef;
        this.itemSearchService = new ItemSearchService(playerRef);
        this.favoriteItems = new LinkedHashSet<>(HytemsPlugin.pinnedItemsManager.getFavoriteItems(playerRef));
    }

    private void updatePinnedItemsHud() {
        try {
            if (this.pageStore != null && this.pageRef != null) {
                HytemsPlugin.pinnedItemsHudManager.registerPlayer(this.playerRef, this.pageStore, this.pageRef);
            }
        } catch (Exception e) {
            System.err.println("[Hytems] Failed to update pinned items HUD: " + e.getMessage());
            e.printStackTrace();
        }
    }


    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        this.pageRef = ref;
        this.pageStore = store;

        cmd.append("hytems/ui/ItemBrowser.ui");
        cmd.set("#SearchInput.Value", this.searchQuery);
        updateSearchInputColor(cmd);
        cmd.set("#DropPanelContainer.Visible", false);
        cmd.set("#DetailPanelContainer.Visible", false);

        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchInput",
                EventData.of("@SearchQuery", "#SearchInput.Value"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#PrevPageButton",
                EventData.of("PageAction", "prev"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NextPageButton",
                EventData.of("PageAction", "next"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ClearSearchButton",
                EventData.of("ClearSearch", "true"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("CloseGUI", "true"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ToggleFavoritesButton",
                EventData.of("ToggleFavoritesCollapse", "true"),
                false
        );

        filterItems();
        renderItems(cmd, events);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull BrowserData data) {
        super.handleDataEvent(ref, store, data);
        boolean needsUpdate = false;

        if (data.toggleFavoritesCollapse != null) {
            this.favoritesExpanded = !this.favoritesExpanded;
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#FavoritesGrid.Visible", this.favoritesExpanded);
            cmd.set("#ToggleIndicator.Text", this.favoritesExpanded ? "^" : "v");
            this.sendUpdate(cmd, new UIEventBuilder(), false);
            return;
        }

        if (data.toggleFavorite != null && !data.toggleFavorite.isEmpty()) {
            HytemsPlugin.pinnedItemsManager.toggleFavorite(this.playerRef, data.toggleFavorite);
            this.favoriteItems = new LinkedHashSet<>(HytemsPlugin.pinnedItemsManager.getFavoriteItems(this.playerRef));

            if (favoriteItems.isEmpty()) {
                this.favoritesExpanded = false;
            }
            
            needsUpdate = true;
        }

        if (data.showDrops != null && !data.showDrops.isEmpty()) {
            this.selectedItemId = null;
            this.dropsItemId = data.showDrops;
            needsUpdate = true;
        }

        if (data.selectedItem != null && !data.selectedItem.isEmpty()) {
            this.dropsItemId = null;
            this.selectedItemId = data.selectedItem;
            needsUpdate = true;
        }

        if (data.closeDetail != null && "true".equals(data.closeDetail)) {
            this.selectedItemId = null;
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#DetailPanelContainer.Visible", false);
            this.sendUpdate(cmd, new UIEventBuilder(), false);
            return;
        }

        if (data.closeDropPanel != null && "true".equals(data.closeDropPanel)) {
            this.dropsItemId = null;
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#DropPanelContainer.Visible", false);
            this.sendUpdate(cmd, new UIEventBuilder(), false);
            return;
        }

        if (data.searchQuery != null && !data.searchQuery.equals(this.searchQuery)) {
            this.searchQuery = data.searchQuery.trim();
            this.currentPage = 0;
            needsUpdate = true;
        }

        if (data.clearSearch != null && "true".equals(data.clearSearch)) {
            this.searchQuery = "";
            this.currentPage = 0;
            needsUpdate = true;
        }

        if (data.pageAction != null) {
            int totalPages = getTotalPages();
            if ("prev".equals(data.pageAction) && this.currentPage > 0) {
                this.currentPage--;
                needsUpdate = true;
            } else if ("next".equals(data.pageAction) && this.currentPage < totalPages - 1) {
                this.currentPage++;
                needsUpdate = true;
            }
        }

        if (data.closeGUI != null && "true".equals(data.closeGUI)) {
            this.close();
            return;
        }

        if (data.pinItem != null && !data.pinItem.isEmpty()) {
            HytemsPlugin.pinnedItemsManager.togglePin(this.playerRef, data.pinItem);
            updatePinnedItemsHud();
            needsUpdate = true;
        }

        if (needsUpdate) {
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();

            if (data.clearSearch != null) {
                cmd.set("#SearchInput.Value", "");
            }

            filterItems();
            renderItems(cmd, events);
            updateSearchInputColor(cmd);

            if (this.dropsItemId != null) {
                renderDropsPanel(cmd, events);
                cmd.set("#DetailPanelContainer.Visible", false);
            } else if (this.selectedItemId != null) {
                renderDetailPanel(cmd, events);
                cmd.set("#DropPanelContainer.Visible", false);
            } else {
                cmd.set("#DropPanelContainer.Visible", false);
                cmd.set("#DetailPanelContainer.Visible", false);
            }

            this.sendUpdate(cmd, events, false);
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        this.selectedItemId = null;
        this.dropsItemId = null;
        super.onDismiss(ref, store);
    }

    private void renderDetailPanel(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        if (selectedItemId == null || selectedItemId.isEmpty()) {
            cmd.set("#DetailPanelContainer.Visible", false);
            return;
        }

        cmd.set("#DetailPanelContainer.Visible", true);
        cmd.clear("#DetailPanelContainer");
        cmd.append("#DetailPanelContainer", "hytems/ui/ItemDetail.ui");

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseDetailButton",
                EventData.of("CloseDetail", "true"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#FavoriteItemButtonEmpty",
                EventData.of("ToggleFavorite", selectedItemId),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#FavoriteItemButtonFilled",
                EventData.of("ToggleFavorite", selectedItemId),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#PinItemButtonEmpty",
                EventData.of("PinItem", selectedItemId),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#PinItemButtonPinned",
                EventData.of("PinItem", selectedItemId),
                false
        );

        try {
            Item item = HytemsPlugin.ITEMS.get(selectedItemId);
            String translatedName = translatedName(item, selectedItemId);
            
            String rarityBg = ItemUiSupport.rarityBackground(item);
            String rarityColor = ItemUiSupport.rarityColor(item);

            cmd.set("#DetailItemIcon.ItemId", selectedItemId);
            cmd.set("#DetailItemName.Text", translatedName);
            cmd.set("#DetailItemName.Style.TextColor", rarityColor);
            cmd.set("#DetailPanelContainer #ItemHeader[0].Background", rarityBg);
            cmd.set("#DetailItemId.Text", selectedItemId);

            boolean isPinned = HytemsPlugin.pinnedItemsManager.isPinned(this.playerRef, selectedItemId);
            ItemUiSupport.setBinaryIconState(cmd, "#PinItemButtonEmpty", "#PinItemButtonPinned", isPinned);
            ItemUiSupport.setButtonIcon(cmd, "#PinItemButtonEmpty", "hytems/textures/unpinned.png");
            ItemUiSupport.setButtonIcon(cmd, "#PinItemButtonPinned", "hytems/textures/pinned.png");

            boolean isFav = favoriteItems.contains(selectedItemId);
            ItemUiSupport.setBinaryIconState(cmd, "#FavoriteItemButtonEmpty", "#FavoriteItemButtonFilled", isFav);
            ItemUiSupport.setButtonIcon(cmd, "#FavoriteItemButtonEmpty", "hytems/textures/star.png");
            ItemUiSupport.setButtonIcon(cmd, "#FavoriteItemButtonFilled", "hytems/textures/star_filled.png");

            if (item != null) {
                int maxStack = item.getMaxStack();
                cmd.set("#DetailMaxStack.Text", String.valueOf(maxStack));

                double durability = item.getMaxDurability();
                if (durability > 0) {
                    cmd.set("#DetailDurability.Text", String.valueOf((int) durability));
                } else {
                    cmd.set("#DetailDurability.Text", "N/A");
                }

                loadRecipes(cmd, selectedItemId);
            }
        } catch (Exception e) {
            System.err.println("[Hytems] ERROR: Failed to display item detail for: " + selectedItemId);
            System.err.println("[Hytems] Error: " + e.getMessage());
            e.printStackTrace();
            try {
                cmd.set("#NoRecipeContainer.Visible", true);
                cmd.set("#RecipeContent.Visible", false);
            } catch (Exception ex) {
                System.err.println("[Hytems] Critical error in renderDetailPanel: " + ex.getMessage());
            }
        }
    }

    private void loadRecipes(@Nonnull UICommandBuilder cmd, @Nonnull String itemId) {
        try {
            List<CraftingRecipe> allRecipes = HytemsPlugin.recipeManager.getCraftingRecipes(itemId);

            if (allRecipes == null || allRecipes.isEmpty() || allRecipes.size() > 1) {
                cmd.set("#NoRecipeContainer.Visible", true);
                cmd.set("#RecipeContent.Visible", false);
            } else {
                cmd.set("#NoRecipeContainer.Visible", false);
                cmd.set("#RecipeContent.Visible", true);
                displayRecipes(cmd, allRecipes);
            }
        } catch (Exception e) {
            System.err.println("[Hytems] Error loading recipes for " + itemId + ": " + e.getMessage());
            e.printStackTrace();
            cmd.set("#NoRecipeContainer.Visible", true);
            cmd.set("#RecipeContent.Visible", false);
        }
    }

    private void displayRecipes(@Nonnull UICommandBuilder cmd, @Nonnull List<CraftingRecipe> recipes) {
        try {
            if (recipes == null || recipes.isEmpty()) return;

            CraftingRecipe firstRecipe = recipes.get(0);
            BenchRequirement[] benchReqs = firstRecipe.getBenchRequirement();

            if (benchReqs != null && benchReqs.length > 0) {
                BenchRequirement bench = benchReqs[0];
                int tier = bench.requiredTierLevel;
                Item stationItem = HytemsPlugin.ITEMS.get(bench.id);
                String stationName = translatedName(stationItem, bench.id);

                cmd.set("#StationName.Text", stationName);
                if (tier > 0) {
                    cmd.set("#StationTier.Text", "Tier " + tier);
                } else {
                    cmd.set("#StationTier.Text", "Any tier");
                }
            }

            cmd.clear("#IngredientsList");
            displaySingleRecipe(cmd, firstRecipe);
        } catch (Exception e) {
            System.err.println("[Hytems] Error displaying recipes: " + e.getMessage());
            e.printStackTrace();
            cmd.set("#NoRecipeContainer.Visible", true);
            cmd.set("#RecipeContent.Visible", false);
        }
    }

    private void displaySingleRecipe(@Nonnull UICommandBuilder cmd, @Nonnull CraftingRecipe recipe) {
        try {
            List<MaterialQuantity> ingredients = RecipeUtils.getInputs(recipe);

            if (ingredients == null || ingredients.isEmpty()) {
                cmd.set("#NoRecipeContainer.Visible", true);
                cmd.set("#RecipeContent.Visible", false);
                return;
            }

            if (ingredients.size() > 6) {
                cmd.set("#NoRecipeContainer.Visible", true);
                cmd.set("#RecipeContent.Visible", false);
                return;
            }

            for (int i = 0; i < ingredients.size(); i++) {
                try {
                    MaterialQuantity ingredient = ingredients.get(i);
                    if (ingredient == null) continue;

                    String ingredientId = ingredient.getItemId();
                    String resourceTypeId = ingredient.getResourceTypeId();
                    int quantity = ingredient.getQuantity();

                    if (ingredientId == null && resourceTypeId == null) continue;

                    cmd.append("#IngredientsList", HytemsUiTemplates.INGREDIENT_ENTRY);

                    String rowSelector = "#IngredientsList[" + i + "]";

                    if (ingredientId != null) {
                        Item ingredientItem = HytemsPlugin.ITEMS.get(ingredientId);
                        cmd.set(rowSelector + " #IconBackground.Background", ItemUiSupport.rarityBackground(ingredientItem));
                        cmd.set(rowSelector + " #ItemIcon.ItemId", ingredientId);
                        cmd.set(rowSelector + " #ItemIcon.Visible", true);
                        cmd.set(rowSelector + " #Quantity.Text", "x" + quantity);

                        String ingredientName = translatedName(ingredientItem, ingredientId);
                        cmd.set(rowSelector + " #IngredientName.Text", ingredientName);
                    }
                    else if (resourceTypeId != null) {
                        try {
                            ResourceType resourceType = (ResourceType) ResourceType.getAssetMap().getAsset(resourceTypeId);
                            if (resourceType != null) {
                                cmd.set(rowSelector + " #ResourceIcon.AssetPath", resourceType.getIcon());
                                cmd.set(rowSelector + " #ResourceIcon.Visible", true);

                                String resourceTypeName = TextFormatters.resourceTypeName(resourceTypeId);

                                cmd.set(rowSelector + " #Quantity.Text", "x" + quantity);
                                cmd.set(rowSelector + " #IngredientName.Text", "Any " + resourceTypeName);
                            }
                        } catch (Exception e) {
                            System.err.println("[Hytems] Error loading resource type: " + resourceTypeId);
                            e.printStackTrace();
                        }
                    }

                } catch (Exception e) {
                    System.err.println("[Hytems] Error displaying ingredient " + i + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("[Hytems] Error displaying single recipe: " + e.getMessage());
            e.printStackTrace();
            cmd.set("#NoRecipeContainer.Visible", true);
            cmd.set("#RecipeContent.Visible", false);
        }
    }

    private void renderDropsPanel(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        if (dropsItemId == null || dropsItemId.isEmpty()) {
            cmd.set("#DropPanelContainer.Visible", false);
            return;
        }

        cmd.set("#DropPanelContainer.Visible", true);

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseDropButton",
                EventData.of("CloseDropPanel", "true"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#FavoriteDropItemButtonEmpty",
                EventData.of("ToggleFavorite", dropsItemId),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#FavoriteDropItemButtonFilled",
                EventData.of("ToggleFavorite", dropsItemId),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#PinDropItemButtonEmpty",
                EventData.of("PinItem", dropsItemId),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#PinDropItemButtonPinned",
                EventData.of("PinItem", dropsItemId),
                false
        );

        try {
            Item item = HytemsPlugin.ITEMS.get(dropsItemId);
            String translatedName = translatedName(item, dropsItemId);
            
            String rarityBg = ItemUiSupport.rarityBackground(item);
            String rarityColor = ItemUiSupport.rarityColor(item);

            cmd.set("#DropDetailItemIcon.ItemId", dropsItemId);
            cmd.set("#DropDetailItemName.Text", translatedName);
            cmd.set("#DropDetailItemName.Style.TextColor", rarityColor);
            cmd.set("#DropPanelContainer #ItemHeader[0].Background", rarityBg);
            cmd.set("#DropDetailItemId.Text", dropsItemId);

            boolean isPinned = HytemsPlugin.pinnedItemsManager.isPinned(this.playerRef, dropsItemId);
            ItemUiSupport.setBinaryIconState(cmd, "#PinDropItemButtonEmpty", "#PinDropItemButtonPinned", isPinned);
            ItemUiSupport.setButtonIcon(cmd, "#PinDropItemButtonEmpty", "hytems/textures/unpinned.png");
            ItemUiSupport.setButtonIcon(cmd, "#PinDropItemButtonPinned", "hytems/textures/pinned.png");

            boolean isFav = favoriteItems.contains(dropsItemId);
            ItemUiSupport.setBinaryIconState(cmd, "#FavoriteDropItemButtonEmpty", "#FavoriteDropItemButtonFilled", isFav);
            ItemUiSupport.setButtonIcon(cmd, "#FavoriteDropItemButtonEmpty", "hytems/textures/star.png");
            ItemUiSupport.setButtonIcon(cmd, "#FavoriteDropItemButtonFilled", "hytems/textures/star_filled.png");

            if (item != null) {
                int maxStack = item.getMaxStack();
                cmd.set("#DropDetailMaxStack.Text", String.valueOf(maxStack));

                double durability = item.getMaxDurability();
                if (durability > 0) {
                    cmd.set("#DropDetailDurability.Text", String.valueOf((int) durability));
                } else {
                    cmd.set("#DropDetailDurability.Text", "N/A");
                }
            } else {
                cmd.set("#DropDetailMaxStack.Text", "N/A");
                cmd.set("#DropDetailDurability.Text", "N/A");
            }

            List<String> dropSources = HytemsPlugin.dropListRegistry.getDropSourcesForItem(dropsItemId);

            if (dropSources == null || dropSources.isEmpty()) {
                cmd.set("#NoDropsContainer.Visible", true);
                cmd.set("#DropsContent.Visible", false);
            } else {
                cmd.set("#NoDropsContainer.Visible", false);
                cmd.set("#DropsContent.Visible", true);
                displayDropSources(cmd, dropSources);
            }

        } catch (Exception e) {
            System.err.println("[Hytems] Error rendering drops panel: " + e.getMessage());
            e.printStackTrace();
            cmd.set("#NoDropsContainer.Visible", true);
            cmd.set("#DropsContent.Visible", false);
        }
    }


    private void displayDropSources(@Nonnull UICommandBuilder cmd, @Nonnull List<String> dropSources) {
        try {
            cmd.clear("#DropSourcesList");

            Map<String, Map<String, List<Integer>>> mobGrouping = new LinkedHashMap<>();
            Map<String, Map<String, List<String>>> cropGrouping = new LinkedHashMap<>();
            List<String> otherSources = new ArrayList<>();

            for (String dropSourceId : dropSources) {
                DropSourceParser.ParsedDropSource parsed = DropSourceParser.parse(dropSourceId);

                if (parsed.isMobSource()) {
                    mobGrouping.computeIfAbsent(parsed.mobType, k -> new LinkedHashMap<>())
                            .computeIfAbsent(parsed.zone != null ? parsed.zone : "Unknown", k -> new ArrayList<>())
                            .add(parsed.tier);
                } else if (parsed.isCropSource()) {
                    cropGrouping.computeIfAbsent(parsed.cropType, k -> new LinkedHashMap<>())
                            .computeIfAbsent(parsed.cropZone != null ? parsed.cropZone : "Unknown", k -> new ArrayList<>())
                            .add(parsed.cropStage);
                } else {
                    otherSources.add(dropSourceId);
                }
            }

            int index = 0;

            for (Map.Entry<String, Map<String, List<Integer>>> mobEntry : mobGrouping.entrySet()) {
                String mobType = mobEntry.getKey();
                Map<String, List<Integer>> zoneData = mobEntry.getValue();
                String displayName = TextFormatters.mobName(mobType);

                if (zoneData.size() >= 2) {
                    index = addDropSourceRowMultiZone(cmd, index, displayName, zoneData);
                } else {
                    String zoneInfo = formatZoneInfo(zoneData);
                    index = addDropSourceRow(cmd, index, displayName, zoneInfo);
                }
            }

            for (Map.Entry<String, Map<String, List<String>>> cropEntry : cropGrouping.entrySet()) {
                String cropType = cropEntry.getKey();
                String displayName = TextFormatters.cropName(cropType);

                index = addCropSourceRow(cmd, index, displayName);
            }

            for (String source : otherSources) {
                String displayName = TextFormatters.dropSourceName(source);
                index = addSimpleDropSourceRow(cmd, index, displayName);
            }

        } catch (Exception e) {
            System.err.println("[Hytems] Error displaying drop sources: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int addDropSourceRow(UICommandBuilder cmd, int index, String displayName, String zoneInfo) {
        Map<String, List<Integer>> zoneData = new LinkedHashMap<>();
        if (!zoneInfo.isEmpty()) {
            String[] parts = zoneInfo.split(":");
            if (parts.length > 0) {
                String zonePart = parts[0].trim();
                zoneData.put(zonePart, new ArrayList<>());
            }
        }

        return addDropSourceRowWithZoneBoxes(cmd, index, displayName, zoneData);
    }

    private int addDropSourceRowMultiZone(UICommandBuilder cmd, int index, String displayName, Map<String, List<Integer>> zoneData) {
        return addDropSourceRowWithZoneBoxes(cmd, index, displayName, zoneData);
    }

    private int addDropSourceRowWithZoneBoxes(UICommandBuilder cmd, int index, String displayName, Map<String, List<Integer>> zoneData) {
        List<Map.Entry<String, List<Integer>>> sortedZones = new ArrayList<>(zoneData.entrySet());
        sortedZones.sort((a, b) -> DropSourceParser.compareZones(a.getKey(), b.getKey()));

        cmd.append("#DropSourcesList", HytemsUiTemplates.DROP_SOURCE_ROW);
        String rowSelector = "#DropSourcesList[" + index + "]";
        String badgesSelector = rowSelector + " #ZoneBadges";
        cmd.set(rowSelector + " #SourceName.Text", displayName);

        int badgeIndex = 0;
        for (Map.Entry<String, List<Integer>> entry : sortedZones) {
            String zone = entry.getKey();
            String zoneNumber = zone.replaceAll("[^0-9]", "");
            cmd.append(badgesSelector, HytemsUiTemplates.DROP_ZONE_BADGE);
            String badgeSelector = badgesSelector + "[" + badgeIndex + "]";
            configureZoneBadge(cmd, badgeSelector, zone, "Z" + zoneNumber);
            badgeIndex++;

            cmd.appendInline(badgesSelector, "Group { Anchor: (Width: 4); }");
            badgeIndex++;
        }

        return index + 1;
    }

    private void configureZoneBadge(@Nonnull UICommandBuilder cmd, @Nonnull String badgeSelector,
                                    @Nonnull String zone, @Nonnull String label) {
        int zoneNumber = DropSourceParser.zoneNumber(zone);
        cmd.set(badgeSelector + " #BgDefault.Visible", zoneNumber < 1 || zoneNumber > 4);
        cmd.set(badgeSelector + " #BgZone1.Visible", zoneNumber == 1);
        cmd.set(badgeSelector + " #BgZone2.Visible", zoneNumber == 2);
        cmd.set(badgeSelector + " #BgZone3.Visible", zoneNumber == 3);
        cmd.set(badgeSelector + " #BgZone4.Visible", zoneNumber == 4);
        cmd.set(badgeSelector + " #ZoneLabel.Text", label);
    }

    private int addSimpleDropSourceRow(UICommandBuilder cmd, int index, String displayName) {
        cmd.append("#DropSourcesList", HytemsUiTemplates.SIMPLE_DROP_SOURCE_ROW);
        cmd.set("#DropSourcesList[" + index + "] #SourceName.Text", displayName);

        return index + 1;
    }


    private int addCropSourceRow(UICommandBuilder cmd, int index, String displayName) {
        return addSimpleDropSourceRow(cmd, index, displayName);
    }

    private String formatZoneInfo(Map<String, List<Integer>> zoneData) {
        List<String> zoneParts = new ArrayList<>();

        for (Map.Entry<String, List<Integer>> entry : zoneData.entrySet()) {
            String zone = entry.getKey();
            List<Integer> tiers = entry.getValue();

            String zoneName = TextFormatters.zoneName(zone);
            String tierRange = TextFormatters.tierRange(tiers);

            if (!tierRange.isEmpty()) {
                zoneParts.add(zoneName + ": " + tierRange);
            } else {
                zoneParts.add(zoneName);
            }
        }

        return String.join(", ", zoneParts);
    }

    private void filterItems() {
        filteredItems = itemSearchService.filter(HytemsPlugin.ITEMS, searchQuery);
    }

    private void updateSearchInputColor(@Nonnull UICommandBuilder cmd) {
        if (searchQuery.startsWith("@") && searchQuery.length() > 1) {
            String queryAfterAt = searchQuery.substring(1).trim();
            String category;

            int spaceIndex = queryAfterAt.indexOf(' ');
            if (spaceIndex > 0) {
                category = queryAfterAt.substring(0, spaceIndex).toLowerCase(Locale.ENGLISH);
            } else {
                category = queryAfterAt.toLowerCase(Locale.ENGLISH);
            }

            boolean isValid = itemSearchService.isValidCategory(category);
            if (isValid) {
                cmd.set("#SearchInput.Style.TextColor", "#00cc00");
            } else {
                cmd.set("#SearchInput.Style.TextColor", "#cc0000");
            }
        } else {
            cmd.set("#SearchInput.Style.TextColor", "#ffffff");
        }
    }

    private String translatedName(Item item, String itemId) {
        return ItemUiSupport.translatedName(playerRef, item, itemId);
    }

    private void renderItems(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        boolean hasFavorites = !favoriteItems.isEmpty();
        cmd.set("#FavoritesSection.Visible", hasFavorites);
        
        cmd.clear("#FavoritesGrid");
        if (hasFavorites) {
            int col = 0;
            for (String favId : favoriteItems) {
                Item item = HytemsPlugin.ITEMS.get(favId);
                String translatedName = translatedName(item, favId);
                String selector = "#FavoritesGrid[" + col + "]";
                
                renderItemButton(cmd, events, selector, favId, translatedName, true);
                col++;
                if (col >= 7) break;
            }
        }
        
        cmd.set("#FavoritesGrid.Visible", this.favoritesExpanded && hasFavorites);
        cmd.set("#ToggleIndicator.Text", this.favoritesExpanded ? "^" : "v");

        cmd.clear("#ItemGrid");
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredItems.size());

        if (filteredItems.isEmpty()) {
            cmd.set("#PlaceholderText.Visible", true);
        } else {
            cmd.set("#PlaceholderText.Visible", false);

            int row = 0;
            int col = 0;

            for (int i = startIndex; i < endIndex; i++) {
                Map.Entry<String, Item> entry = filteredItems.get(i);
                String itemId = entry.getKey();
                Item item = entry.getValue();
                String translatedName = translatedName(item, itemId);

                if (col == 0) {
                    cmd.appendInline("#ItemGrid", "Group { Anchor: (Height: 116); LayoutMode: Left; }");
                }

                String selector = "#ItemGrid[" + row + "][" + col + "]";
                renderItemButton(cmd, events, selector, itemId, translatedName, false);

                col++;
                if (col >= ITEMS_PER_ROW) {
                    col = 0;
                    row++;
                }
            }
        }

        updateUI(cmd);
    }

    private void renderItemButton(UICommandBuilder cmd, UIEventBuilder events, String selector, String itemId, String translatedName, boolean isFavoriteSection) {
        String parentSelector = selector.substring(0, selector.lastIndexOf("["));
        Item item = HytemsPlugin.ITEMS.get(itemId);
        
        String rarityBg = ItemUiSupport.rarityBackground(item);
        String rarityColor = ItemUiSupport.rarityColor(item);
        
        boolean isFav = favoriteItems.contains(itemId);
        boolean isPinned = HytemsPlugin.pinnedItemsManager.isPinned(this.playerRef, itemId);

        cmd.append(parentSelector, HytemsUiTemplates.ITEM_CARD);

        cmd.set(selector + " #ItemIcon.ItemId", itemId);
        cmd.set(selector + " #ItemName.Text", translatedName);
        cmd.set(selector + " #ItemName.Style.TextColor", rarityColor);
        cmd.set(selector + " #RarityBackground.Background", rarityBg);
        cmd.set(selector + " #FavoriteButtonEmpty.Visible", !isFav);
        cmd.set(selector + " #FavoriteButtonFilled.Visible", isFav);
        cmd.set(selector + " #PinButtonEmpty.Visible", !isPinned);
        cmd.set(selector + " #PinButtonFilled.Visible", isPinned);
        ItemUiSupport.setButtonIconHoverOnly(cmd, selector + " #FavoriteButtonEmpty", "hytems/textures/star.png");
        ItemUiSupport.setButtonIcon(cmd, selector + " #FavoriteButtonFilled", "hytems/textures/star_filled.png");
        ItemUiSupport.setButtonIconHoverOnly(cmd, selector + " #PinButtonEmpty", "hytems/textures/unpinned.png");
        ItemUiSupport.setButtonIcon(cmd, selector + " #PinButtonFilled", "hytems/textures/pinned.png");

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                EventData.of("SelectedItem", itemId),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.RightClicking,
                selector,
                EventData.of("ShowDrops", itemId),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #FavoriteButtonEmpty",
                EventData.of("ToggleFavorite", itemId).append("FavSelector", selector),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #FavoriteButtonFilled",
                EventData.of("ToggleFavorite", itemId).append("FavSelector", selector),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #PinButtonEmpty",
                EventData.of("PinItem", itemId),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #PinButtonFilled",
                EventData.of("PinItem", itemId),
                false
        );
    }
    private void updateUI(@Nonnull UICommandBuilder cmd) {
        cmd.set("#ItemCount.Text", filteredItems.size() + " items found");

        int totalPages = getTotalPages();
        if (totalPages == 0) {
            totalPages = 1;
        }

        cmd.set("#PageLabel.Text", "Page " + (currentPage + 1) + " / " + totalPages);
        cmd.set("#PrevPageButton.Visible", currentPage > 0);
        cmd.set("#NextPageButton.Visible", currentPage < totalPages - 1);
    }

    private int getTotalPages() {
        if (filteredItems.isEmpty()) {
            return 1;
        }
        return (int) Math.ceil((double) filteredItems.size() / ITEMS_PER_PAGE);
    }

    public static class BrowserData {
        public static final BuilderCodec<BrowserData> CODEC = BuilderCodec.builder(
                        BrowserData.class,
                        BrowserData::new
                )
                .addField(
                        new KeyedCodec<>("@SearchQuery", Codec.STRING),
                        (data, value) -> data.searchQuery = value,
                        data -> data.searchQuery
                )
                .addField(
                        new KeyedCodec<>("PageAction", Codec.STRING),
                        (data, value) -> data.pageAction = value,
                        data -> data.pageAction
                )
                .addField(
                        new KeyedCodec<>("ClearSearch", Codec.STRING),
                        (data, value) -> data.clearSearch = value,
                        data -> data.clearSearch
                )
                .addField(
                        new KeyedCodec<>("CloseGUI", Codec.STRING),
                        (data, value) -> data.closeGUI = value,
                        data -> data.closeGUI
                )
                .addField(
                        new KeyedCodec<>("SelectedItem", Codec.STRING),
                        (data, value) -> data.selectedItem = value,
                        data -> data.selectedItem
                )

                .addField(
                        new KeyedCodec<>("ShowDrops", Codec.STRING),
                        (data, value) -> data.showDrops = value,
                        data -> data.showDrops
                )
                .addField(
                        new KeyedCodec<>("CloseDetail", Codec.STRING),
                        (data, value) -> data.closeDetail = value,
                        data -> data.closeDetail
                )
                .addField(
                        new KeyedCodec<>("CloseDropPanel", Codec.STRING),
                        (data, value) -> data.closeDropPanel = value,
                        data -> data.closeDropPanel
                )
                .addField(
                        new KeyedCodec<>("PinItem", Codec.STRING),
                        (data, value) -> data.pinItem = value,
                        data -> data.pinItem
                )
                .addField(
                        new KeyedCodec<>("HoverItem", Codec.STRING),
                        (data, value) -> data.hoverItem = value,
                        data -> data.hoverItem
                )
                .addField(
                        new KeyedCodec<>("HoverSelector", Codec.STRING),
                        (data, value) -> data.hoverItemSelector = value,
                        data -> data.hoverItemSelector
                )
                .addField(
                        new KeyedCodec<>("UnhoverItem", Codec.STRING),
                        (data, value) -> data.unhoverItem = value,
                        data -> data.unhoverItem
                )
                .addField(
                        new KeyedCodec<>("UnhoverSelector", Codec.STRING),
                        (data, value) -> data.unhoverItemSelector = value,
                        data -> data.unhoverItemSelector
                )
                .addField(
                        new KeyedCodec<>("ToggleFavorite", Codec.STRING),
                        (data, value) -> data.toggleFavorite = value,
                        data -> data.toggleFavorite
                )
                .addField(
                        new KeyedCodec<>("FavSelector", Codec.STRING),
                        (data, value) -> data.toggleFavoriteSelector = value,
                        data -> data.toggleFavoriteSelector
                )
                .addField(
                        new KeyedCodec<>("ToggleFavoritesCollapse", Codec.STRING),
                        (data, value) -> data.toggleFavoritesCollapse = value,
                        data -> data.toggleFavoritesCollapse
                )
                .addField(
                        new KeyedCodec<>("IsFavSection", Codec.STRING),
                        (data, value) -> data.isFavSectionStr = value,
                        data -> data.isFavSectionStr
                ).build();

        
        private String searchQuery;
        private String pageAction;
        private String clearSearch;
        private String closeGUI;
        private String selectedItem;
        private String showDrops;
        private String closeDetail;
        private String closeDropPanel;
        private String pinItem;
        private String hoverItem;
        private String hoverItemSelector;
        private String unhoverItem;
        private String unhoverItemSelector;
        private String toggleFavorite;
        private String toggleFavoriteSelector;
        private String toggleFavoritesCollapse;
        private String isFavSectionStr;
    }
}



