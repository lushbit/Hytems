package dev.lushbit.hytems.ui.page;

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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.lushbit.hytems.HytemsPlugin;
import dev.lushbit.hytems.asset.DropSourceParser;
import dev.lushbit.hytems.asset.ItemSearchService;
import dev.lushbit.hytems.asset.RecipeUtils;
import dev.lushbit.hytems.ui.DropSourceSummaries;
import dev.lushbit.hytems.ui.HytemsUiTemplates;
import dev.lushbit.hytems.ui.ItemUiSupport;
import dev.lushbit.hytems.ui.MobPortraitResolver;
import dev.lushbit.hytems.ui.TextFormatters;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HytemsBrowserPage extends InteractiveCustomUIPage<HytemsBrowserPage.BrowserData> {

    private static final int ITEMS_PER_ROW = 8;
    private static final int ROWS_PER_PAGE = 8;
    private static final int FAVORITE_ROWS = 1;
    private static final int BODY_ROWS_PER_PAGE = ROWS_PER_PAGE - FAVORITE_ROWS;
    private static final int ITEMS_PER_PAGE = ITEMS_PER_ROW * BODY_ROWS_PER_PAGE;
    private static final int GRID_LABEL_MAX_CHARS = 16;
    private static final int GRID_HEIGHT = 744;
    private static final int INFO_CONTAINER_MAX_HEIGHT = 876;
    private static final int INFO_CONTAINER_MIN_HEIGHT = 220;
    private static final int INFO_CONTAINER_PADDING_VERTICAL = 28;
    private static final int INFO_CONTAINER_SCROLL_BUFFER = 24;
    private static final int ITEM_HEADER_HEIGHT = 124;
    private static final int SECTION_GAP = 12;
    private static final int DETAILS_SECTION_HEIGHT = 116;
    private static final int EMPTY_RECIPE_SECTION_HEIGHT = 91;
    private static final int EMPTY_DROPS_SECTION_HEIGHT = 67;
    private static final int RECIPE_SECTION_BASE_HEIGHT = 214;
    private static final int RECIPE_SECTION_NO_STATION_BASE_HEIGHT = 96;
    private static final int CRAFTING_STATION_SECTION_HEIGHT = 108;
    private static final int DROP_SECTION_BASE_HEIGHT = 32;
    private static final int LIST_ROW_HEIGHT = 50;
    private static final int DROP_ROW_COMPACT_HEIGHT = 40;
    private static final int DROP_ROW_HEIGHT = 68;
    private static final int MOB_DROPS_SECTION_HEIGHT = 220;
    private static final int OTHER_DROPS_SECTION_BASE_HEIGHT = 30;
    private static final int SEARCH_HISTORY_MAX_ITEMS = 12;

    private enum InfoTab {
        RECIPES,
        DROPS
    }

    private String searchQuery = "";
    private int currentPage = 0;
    private String selectedItemId = null;
    private String dropsItemId = null;
    private int currentMobDropIndex = 0;
    private InfoTab activeInfoTab = InfoTab.RECIPES;

    private List<Map.Entry<String, Item>> filteredItems = new ArrayList<>();
    private Set<String> favoriteItems = new LinkedHashSet<>();
    private List<String> searchHistoryItems = new ArrayList<>();

    private Ref<EntityStore> pageRef;
    private Store<EntityStore> pageStore;
    private final PlayerRef playerRef;
    private final ItemSearchService itemSearchService;
    private String cachedRecipeItemId;
    private List<CraftingRecipe> cachedRecipes = Collections.emptyList();
    private String cachedDropItemId;
    private List<DropSourceSummaries.DisplayDropSource> cachedDropSummaries = Collections.emptyList();

    public HytemsBrowserPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, BrowserData.CODEC);
        this.playerRef = playerRef;
        this.itemSearchService = new ItemSearchService(playerRef);
        this.favoriteItems = new LinkedHashSet<>(HytemsPlugin.playerDataManager.getFavoriteItems(playerRef));
        this.searchHistoryItems = sanitizeHistory(HytemsPlugin.playerDataManager.getSearchHistoryItems(playerRef));

        String lastViewedItem = HytemsPlugin.playerDataManager.getLastViewedItem(playerRef);
        if (isKnownItem(lastViewedItem)) {
            this.selectedItemId = lastViewedItem;
            this.dropsItemId = lastViewedItem;
        }

        InfoTab lastViewedTab = parseInfoTab(HytemsPlugin.playerDataManager.getLastViewedTab(playerRef));
        if (lastViewedTab != null) {
            this.activeInfoTab = lastViewedTab;
        }
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

        cmd.append(HytemsUiTemplates.ITEM_BROWSER);
        cmd.append("#InfoPanelSlot", HytemsUiTemplates.BROWSER_INFO_PANEL);
        cmd.append("#SearchHistorySlot", HytemsUiTemplates.SEARCH_HISTORY);
        cmd.set("#SearchInput.Value", this.searchQuery);

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
                "#RecipesTabButton",
                EventData.of("InfoTab", "recipes"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#DropsTabButton",
                EventData.of("InfoTab", "drops"),
                false
        );
        bindSearchHistoryEvents(events);

        filterItems();
        clampCurrentPage();
        ensureDetailItemAvailable();
        renderSearchHistory(cmd);
        renderItems(cmd, events);
        updateSearchInputColor(cmd);
        renderActiveInfoPanel(cmd, events);
        updateTabVisualState(cmd);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull BrowserData data) {
        super.handleDataEvent(ref, store, data);

        boolean needsUpdate = false;
        boolean needsGridUpdate = false;
        boolean needsInfoUpdate = false;
        boolean needsSearchUpdate = false;
        boolean needsTabUpdate = false;
        boolean needsHistoryUpdate = false;

        if (data.toggleFavorite != null && !data.toggleFavorite.isEmpty()) {
            HytemsPlugin.playerDataManager.toggleFavorite(this.playerRef, data.toggleFavorite);
            this.favoriteItems = new LinkedHashSet<>(HytemsPlugin.playerDataManager.getFavoriteItems(this.playerRef));
            needsUpdate = true;
            needsGridUpdate = true;
            needsInfoUpdate = true;
        }

        if (data.infoTab != null) {
            if ("drops".equalsIgnoreCase(data.infoTab)) {
                selectInfoTab(InfoTab.DROPS, true);
                needsUpdate = true;
                needsInfoUpdate = true;
                needsTabUpdate = true;
            } else if ("recipes".equalsIgnoreCase(data.infoTab)) {
                selectInfoTab(InfoTab.RECIPES, true);
                needsUpdate = true;
                needsInfoUpdate = true;
                needsTabUpdate = true;
            }
        }

        // Backwards-compatibility for any old events still being sent by stale clients.
        if (data.showDrops != null && !data.showDrops.isEmpty()) {
            if (selectDetailItem(data.showDrops, true)) {
                selectInfoTab(InfoTab.DROPS, true);
                needsUpdate = true;
                needsInfoUpdate = true;
                needsTabUpdate = true;
            }
        }

        if (data.itemPath != null && !data.itemPath.isEmpty()) {
            if (selectDetailItem(data.itemPath, true)) {
                recordSearchHistoryItem(data.itemPath);
                needsHistoryUpdate = true;
                needsUpdate = true;
                needsInfoUpdate = true;
            }
        }

        if (data.selectedItem != null && !data.selectedItem.isEmpty()) {
            if (selectDetailItem(data.selectedItem, true)) {
                recordSearchHistoryItem(data.selectedItem);
                needsHistoryUpdate = true;
                needsUpdate = true;
                needsInfoUpdate = true;
            }
        }

        if (data.historyIndex != null && !data.historyIndex.isEmpty()) {
            String historyItemId = getSearchHistoryItemByIndex(data.historyIndex);
            if (selectDetailItem(historyItemId, true)) {
                if (historyItemId != null && !historyItemId.isEmpty()) {
                    recordSearchHistoryItem(historyItemId);
                    needsHistoryUpdate = true;
                }
                needsUpdate = true;
                needsInfoUpdate = true;
            }
        }

        if (data.searchQuery != null && !data.searchQuery.equals(this.searchQuery)) {
            this.searchQuery = data.searchQuery.trim();
            this.currentPage = 0;
            needsUpdate = true;
            needsGridUpdate = true;
            needsSearchUpdate = true;
        }

        if (data.clearSearch != null && "true".equals(data.clearSearch)) {
            this.searchQuery = "";
            this.currentPage = 0;
            needsUpdate = true;
            needsGridUpdate = true;
            needsSearchUpdate = true;
        }

        if (data.pageAction != null) {
            int totalPages = getTotalPages();
            if ("prev".equals(data.pageAction) && this.currentPage > 0) {
                this.currentPage--;
                needsUpdate = true;
                needsGridUpdate = true;
            } else if ("next".equals(data.pageAction) && this.currentPage < totalPages - 1) {
                this.currentPage++;
                needsUpdate = true;
                needsGridUpdate = true;
            }
        }

        if (data.mobDropAction != null && !data.mobDropAction.isEmpty()) {
            if (handleMobDropAction(data.mobDropAction)) {
                needsUpdate = true;
                needsInfoUpdate = true;
            }
        }

        if (data.closeGUI != null && "true".equals(data.closeGUI)) {
            this.close();
            return;
        }

        if (data.pinItem != null && !data.pinItem.isEmpty()) {
            HytemsPlugin.playerDataManager.togglePin(this.playerRef, data.pinItem);
            updatePinnedItemsHud();
            needsUpdate = true;
            needsGridUpdate = true;
            needsInfoUpdate = true;
        }

        if (needsUpdate) {
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();

            if (data.clearSearch != null) {
                cmd.set("#SearchInput.Value", "");
            }

            if (needsGridUpdate) {
                filterItems();
                clampCurrentPage();
                renderItems(cmd, events);
            }
            if (needsSearchUpdate) {
                updateSearchInputColor(cmd);
            }
            if (needsHistoryUpdate) {
                renderSearchHistory(cmd);
            }
            if (needsInfoUpdate) {
                ensureDetailItemAvailable();
                renderActiveInfoPanel(cmd, events);
            }
            if (needsTabUpdate) {
                updateTabVisualState(cmd);
            }

            this.sendUpdate(cmd, events, false);
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        super.onDismiss(ref, store);
    }

    private void renderActiveInfoPanel(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        if (selectedItemId == null || selectedItemId.isEmpty()) {
            cmd.set("#NoItemSelectedContainer.Visible", true);
            cmd.set("#DetailPanelContainer.Visible", false);
            cmd.set("#DropPanelContainer.Visible", false);
            updateInfoContainerHeight(cmd, INFO_CONTAINER_MIN_HEIGHT);
            return;
        }

        cmd.set("#NoItemSelectedContainer.Visible", false);

        if (this.activeInfoTab == InfoTab.DROPS) {
            renderDropsPanel(cmd, events);
            cmd.set("#DetailPanelContainer.Visible", false);
            cmd.set("#DropPanelContainer.Visible", true);
        } else {
            renderDetailPanel(cmd, events);
            cmd.set("#DropPanelContainer.Visible", false);
            cmd.set("#DetailPanelContainer.Visible", true);
        }

        updateInfoContainerHeight(cmd, estimateActiveInfoContentHeight());
    }

    private void updateInfoContainerHeight(@Nonnull UICommandBuilder cmd, int contentHeight) {
        Anchor anchor = new Anchor();
        int height = Math.max(INFO_CONTAINER_MIN_HEIGHT, Math.min(INFO_CONTAINER_MAX_HEIGHT, contentHeight));
        anchor.setTop(Value.of(0));
        anchor.setHeight(Value.of(height));
        cmd.setObject("#InfoContentContainer.Anchor", anchor);
    }

    private int estimateActiveInfoContentHeight() {
        if (this.activeInfoTab == InfoTab.DROPS) {
            return estimateDropsPanelHeight();
        }
        return estimateRecipePanelHeight();
    }

    private int estimateRecipePanelHeight() {
        int recipeSectionHeight = EMPTY_RECIPE_SECTION_HEIGHT;

        try {
            if (selectedItemId != null && !selectedItemId.isEmpty()) {
                List<CraftingRecipe> recipes = getCachedRecipes(selectedItemId);
                if (recipes != null && recipes.size() == 1) {
                    int baseHeight = hasVisibleCraftingStation(recipes.get(0))
                            ? RECIPE_SECTION_BASE_HEIGHT
                            : RECIPE_SECTION_NO_STATION_BASE_HEIGHT;
                    List<MaterialQuantity> ingredients = RecipeUtils.getInputs(recipes.get(0));
                    if (ingredients != null && !ingredients.isEmpty()) {
                        recipeSectionHeight = baseHeight + (ingredients.size() * LIST_ROW_HEIGHT);
                    }
                }
            }
        } catch (Exception ignored) {
            recipeSectionHeight = EMPTY_RECIPE_SECTION_HEIGHT;
        }

        return INFO_CONTAINER_PADDING_VERTICAL
                + ITEM_HEADER_HEIGHT
                + SECTION_GAP
                + recipeSectionHeight
                + SECTION_GAP
                + DETAILS_SECTION_HEIGHT
                + INFO_CONTAINER_SCROLL_BUFFER;
    }

    private int estimateDropsPanelHeight() {
        int dropsSectionHeight = EMPTY_DROPS_SECTION_HEIGHT;

        try {
            if (dropsItemId != null && !dropsItemId.isEmpty()) {
                List<DropSourceSummaries.DisplayDropSource> dropSummaries = getCachedDropSummaries(dropsItemId);
                List<DropSourceSummaries.DisplayDropSource> mobDrops = filterDropSummariesByKind(
                        dropSummaries,
                        DropSourceParser.DropSourceKind.MOB
                );
                List<DropSourceSummaries.DisplayDropSource> otherDrops = filterNonMobDropSummaries(dropSummaries);

                if (!mobDrops.isEmpty() || !otherDrops.isEmpty()) {
                    dropsSectionHeight = DROP_SECTION_BASE_HEIGHT;
                    if (!mobDrops.isEmpty()) {
                        dropsSectionHeight += MOB_DROPS_SECTION_HEIGHT;
                    }
                    if (!otherDrops.isEmpty()) {
                        if (dropsSectionHeight > 0) {
                            dropsSectionHeight += mobDrops.isEmpty() ? 0 : SECTION_GAP;
                        }
                        dropsSectionHeight += estimateOtherDropsSectionHeight(otherDrops);
                    }
                }
            }
        } catch (Exception ignored) {
            dropsSectionHeight = EMPTY_DROPS_SECTION_HEIGHT;
        }

        return INFO_CONTAINER_PADDING_VERTICAL
                + ITEM_HEADER_HEIGHT
                + SECTION_GAP
                + dropsSectionHeight
                + SECTION_GAP
                + DETAILS_SECTION_HEIGHT
                + INFO_CONTAINER_SCROLL_BUFFER;
    }

    private int estimateOtherDropsSectionHeight(@Nonnull List<DropSourceSummaries.DisplayDropSource> otherDrops) {
        int height = OTHER_DROPS_SECTION_BASE_HEIGHT;
        for (DropSourceSummaries.DisplayDropSource summary : otherDrops) {
            boolean hasSecondaryLabel = summary.secondaryLabel() != null && !summary.secondaryLabel().isEmpty();
            height += hasSecondaryLabel ? DROP_ROW_HEIGHT : DROP_ROW_COMPACT_HEIGHT;
        }
        return height;
    }

    private void updateTabVisualState(@Nonnull UICommandBuilder cmd) {
        boolean recipesActive = this.activeInfoTab == InfoTab.RECIPES;
        cmd.set("#RecipesTabOverlay.Visible", false);
        cmd.set("#RecipesTabSelectedOverlay.Visible", recipesActive);
        cmd.set("#DropsTabOverlay.Visible", false);
        cmd.set("#DropsTabSelectedOverlay.Visible", !recipesActive);
        setTabButtonTextState(cmd, "#RecipesTabButton", recipesActive);
        setTabButtonTextState(cmd, "#DropsTabButton", !recipesActive);
    }

    private void setTabButtonTextState(@Nonnull UICommandBuilder cmd, @Nonnull String selector, boolean active) {
        String color = active ? "#ffcc66" : "#b4c8c9";
        cmd.set(selector + ".Style.Default.LabelStyle.TextColor", color);
        cmd.set(selector + ".Style.Hovered.LabelStyle.TextColor", color);
        cmd.set(selector + ".Style.Pressed.LabelStyle.TextColor", color);
        cmd.set(selector + ".Style.Default.LabelStyle.RenderBold", active);
        cmd.set(selector + ".Style.Hovered.LabelStyle.RenderBold", active);
        cmd.set(selector + ".Style.Pressed.LabelStyle.RenderBold", active);
    }

    private void bindItemActionButtons(@Nonnull UIEventBuilder events, @Nonnull String pinEmptySelector,
                                       @Nonnull String pinFilledSelector, @Nonnull String favoriteEmptySelector,
                                       @Nonnull String favoriteFilledSelector, @Nonnull String itemId) {
        bindItemActionButton(events, pinEmptySelector, "PinItem", itemId);
        bindItemActionButton(events, pinFilledSelector, "PinItem", itemId);
        bindItemActionButton(events, favoriteEmptySelector, "ToggleFavorite", itemId);
        bindItemActionButton(events, favoriteFilledSelector, "ToggleFavorite", itemId);
    }

    private void bindItemActionButton(@Nonnull UIEventBuilder events, @Nonnull String selector,
                                      @Nonnull String action, @Nonnull String itemId) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                EventData.of(action, itemId),
                false
        );
    }

    private void bindItemPathButton(@Nonnull UIEventBuilder events, @Nonnull String selector,
                                    @Nonnull String itemId) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                EventData.of("ItemPath", itemId),
                false
        );
    }

    private void updateItemActionIcons(@Nonnull UICommandBuilder cmd, @Nonnull String pinEmptySelector,
                                       @Nonnull String pinFilledSelector, @Nonnull String favoriteEmptySelector,
                                       @Nonnull String favoriteFilledSelector, @Nonnull String itemId) {
        boolean pinned = HytemsPlugin.playerDataManager.isPinned(this.playerRef, itemId);
        boolean favorite = favoriteItems.contains(itemId);

        ItemUiSupport.setButtonIcon(cmd, pinEmptySelector, ItemUiSupport.ICON_PIN_EMPTY);
        ItemUiSupport.setButtonIcon(cmd, pinFilledSelector, ItemUiSupport.ICON_PIN_FILLED);
        ItemUiSupport.setButtonIcon(cmd, favoriteEmptySelector, ItemUiSupport.ICON_STAR_EMPTY);
        ItemUiSupport.setButtonIcon(cmd, favoriteFilledSelector, ItemUiSupport.ICON_STAR_FILLED);
        ItemUiSupport.setBinaryIconState(cmd, pinEmptySelector, pinFilledSelector, pinned);
        ItemUiSupport.setBinaryIconState(cmd, favoriteEmptySelector, favoriteFilledSelector, favorite);
    }

    private void renderDetailPanel(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        if (selectedItemId == null || selectedItemId.isEmpty()) {
            return;
        }

        cmd.clear("#DetailPanelContainer");
        cmd.append("#DetailPanelContainer", HytemsUiTemplates.ITEM_DETAIL);
        bindItemActionButtons(
                events,
                "#DetailPinButtonEmpty",
                "#DetailPinButtonFilled",
                "#DetailFavoriteButtonEmpty",
                "#DetailFavoriteButtonFilled",
                selectedItemId
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
            updateItemActionIcons(
                    cmd,
                    "#DetailPinButtonEmpty",
                    "#DetailPinButtonFilled",
                    "#DetailFavoriteButtonEmpty",
                    "#DetailFavoriteButtonFilled",
                    selectedItemId
            );

            if (item != null) {
                int maxStack = item.getMaxStack();
                cmd.set("#DetailMaxStack.Text", String.valueOf(maxStack));

                double durability = item.getMaxDurability();
                if (durability > 0) {
                    cmd.set("#DetailDurability.Text", String.valueOf((int) durability));
                } else {
                    cmd.set("#DetailDurability.Text", "N/A");
                }

                loadRecipes(cmd, events, selectedItemId);
            } else {
                cmd.set("#DetailMaxStack.Text", "N/A");
                cmd.set("#DetailDurability.Text", "N/A");
                cmd.set("#NoRecipeContainer.Visible", true);
                cmd.set("#RecipeContent.Visible", false);
            }
        } catch (Exception e) {
            System.err.println("[Hytems] ERROR: Failed to display item detail for: " + selectedItemId);
            System.err.println("[Hytems] Error: " + e.getMessage());
            e.printStackTrace();
            cmd.set("#NoRecipeContainer.Visible", true);
            cmd.set("#RecipeContent.Visible", false);
        }
    }

    private void loadRecipes(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nonnull String itemId) {
        try {
            List<CraftingRecipe> allRecipes = getCachedRecipes(itemId);

            if (allRecipes == null || allRecipes.isEmpty() || allRecipes.size() > 1) {
                cmd.set("#NoRecipeContainer.Visible", true);
                cmd.set("#RecipeContent.Visible", false);
            } else {
                cmd.set("#NoRecipeContainer.Visible", false);
                cmd.set("#RecipeContent.Visible", true);
                displayRecipes(cmd, events, allRecipes);
            }
        } catch (Exception e) {
            System.err.println("[Hytems] Error loading recipes for " + itemId + ": " + e.getMessage());
            e.printStackTrace();
            cmd.set("#NoRecipeContainer.Visible", true);
            cmd.set("#RecipeContent.Visible", false);
        }
    }

    private void displayRecipes(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events,
                                @Nonnull List<CraftingRecipe> recipes) {
        try {
            if (recipes.isEmpty()) return;

            CraftingRecipe firstRecipe = recipes.get(0);
            BenchRequirement[] benchReqs = firstRecipe.getBenchRequirement();

            if (benchReqs != null && benchReqs.length > 0) {
                BenchRequirement bench = benchReqs[0];
                int tier = bench.requiredTierLevel;
                String stationItemId = resolveBenchItemId(bench.id);
                setCraftingStationSectionVisible(cmd, stationItemId != null);

                if (stationItemId == null) {
                    cmd.set("#StationPathButton.Visible", false);
                    cmd.set("#StationIcon.Visible", false);
                    cmd.set("#StationAssetIcon.Visible", false);
                } else {
                    Item stationItem = HytemsPlugin.ITEMS.get(stationItemId);
                    String stationName = translatedName(stationItem, stationItemId);
                    if (stationName.equals(stationItemId)) {
                        stationName = benchDisplayName(stationItemId);
                    }

                    cmd.set("#StationName.Text", stationName);
                    cmd.set("#StationIcon.ItemId", stationItemId);
                    setStationIcon(cmd, stationItem);
                    cmd.set("#StationIconBackground.Background", ItemUiSupport.rarityBackground(stationItem));
                    cmd.set("#StationPathButton.Visible", true);
                    bindItemPathButton(events, "#StationPathButton", stationItemId);
                    if (tier > 0) {
                        cmd.set("#StationTier.Text", "Tier " + tier);
                    } else {
                        cmd.set("#StationTier.Text", "Any tier");
                    }
                }
            } else {
                setCraftingStationSectionVisible(cmd, false);
                cmd.set("#StationPathButton.Visible", false);
                cmd.set("#StationIcon.Visible", false);
                cmd.set("#StationAssetIcon.Visible", false);
            }

            cmd.clear("#IngredientsList");
            displaySingleRecipe(cmd, events, firstRecipe);
        } catch (Exception e) {
            System.err.println("[Hytems] Error displaying recipes: " + e.getMessage());
            e.printStackTrace();
            cmd.set("#NoRecipeContainer.Visible", true);
            cmd.set("#RecipeContent.Visible", false);
        }
    }

    private void displaySingleRecipe(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events,
                                     @Nonnull CraftingRecipe recipe) {
        try {
            List<MaterialQuantity> ingredients = RecipeUtils.getInputs(recipe);

            if (ingredients == null || ingredients.isEmpty()) {
                cmd.set("#NoRecipeContainer.Visible", true);
                cmd.set("#RecipeContent.Visible", false);
                return;
            }

            int renderedIndex = 0;
            for (int i = 0; i < ingredients.size(); i++) {
                try {
                    MaterialQuantity ingredient = ingredients.get(i);
                    if (ingredient == null) continue;

                    String ingredientId = ingredient.getItemId();
                    String resourceTypeId = ingredient.getResourceTypeId();
                    int quantity = ingredient.getQuantity();

                    if (ingredientId == null && resourceTypeId == null) continue;

                    String rowSelector = "#IngredientsList[" + renderedIndex + "]";

                    if (ingredientId != null) {
                        cmd.append("#IngredientsList", HytemsUiTemplates.INGREDIENT_ENTRY);
                        renderedIndex++;
                        Item ingredientItem = HytemsPlugin.ITEMS.get(ingredientId);
                        cmd.set(rowSelector + " #IconBackground.Background", ItemUiSupport.rarityBackground(ingredientItem));
                        cmd.set(rowSelector + " #ItemIcon.ItemId", ingredientId);
                        cmd.set(rowSelector + " #ItemIcon.Visible", true);
                        cmd.set(rowSelector + " #Quantity.Text", "x" + quantity);

                        String ingredientName = translatedName(ingredientItem, ingredientId);
                        cmd.set(rowSelector + " #IngredientName.Text", ingredientName);
                        cmd.set(rowSelector + " #PathButton.Visible", true);
                        bindItemPathButton(events, rowSelector + " #PathButton", ingredientId);
                    } else {
                        ResourceType resourceType = (ResourceType) ResourceType.getAssetMap().getAsset(resourceTypeId);
                        if (resourceType != null) {
                            cmd.append("#IngredientsList", HytemsUiTemplates.INGREDIENT_ENTRY);
                            renderedIndex++;
                            cmd.set(rowSelector + " #IconBackground.Background", ItemUiSupport.RARITY_DEFAULT_BACKGROUND);
                            cmd.set(rowSelector + " #ItemIcon.Visible", false);
                            cmd.set(rowSelector + " #ResourceIcon.AssetPath", resourceType.getIcon());
                            cmd.set(rowSelector + " #ResourceIcon.Visible", true);

                            String resourceTypeName = TextFormatters.resourceTypeName(resourceTypeId);
                            cmd.set(rowSelector + " #Quantity.Text", "x" + quantity);
                            cmd.set(rowSelector + " #IngredientName.Text", "Any " + resourceTypeName);
                            cmd.set(rowSelector + " #PathButton.Visible", false);
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
            return;
        }

        cmd.clear("#DropPanelContainer");
        cmd.append("#DropPanelContainer", HytemsUiTemplates.ITEM_DROPS_DETAIL);
        bindItemActionButtons(
                events,
                "#DropPinButtonEmpty",
                "#DropPinButtonFilled",
                "#DropFavoriteButtonEmpty",
                "#DropFavoriteButtonFilled",
                dropsItemId
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
            updateItemActionIcons(
                    cmd,
                    "#DropPinButtonEmpty",
                    "#DropPinButtonFilled",
                    "#DropFavoriteButtonEmpty",
                    "#DropFavoriteButtonFilled",
                    dropsItemId
            );

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

            List<DropSourceSummaries.DisplayDropSource> dropSummaries = getCachedDropSummaries(dropsItemId);
            List<DropSourceSummaries.DisplayDropSource> mobDrops = filterDropSummariesByKind(
                    dropSummaries,
                    DropSourceParser.DropSourceKind.MOB
            );
            List<DropSourceSummaries.DisplayDropSource> otherDrops = filterNonMobDropSummaries(dropSummaries);

            if (mobDrops.isEmpty() && otherDrops.isEmpty()) {
                cmd.set("#NoDropsContainer.Visible", true);
                cmd.set("#DropsContent.Visible", false);
            } else {
                cmd.set("#NoDropsContainer.Visible", false);
                cmd.set("#DropsContent.Visible", true);
                renderMobDropsSection(cmd, events, mobDrops);
                renderOtherDropsSection(cmd, !mobDrops.isEmpty(), otherDrops);
            }
        } catch (Exception e) {
            System.err.println("[Hytems] Error rendering drops panel: " + e.getMessage());
            e.printStackTrace();
            cmd.set("#NoDropsContainer.Visible", true);
            cmd.set("#DropsContent.Visible", false);
        }
    }

    private void renderMobDropsSection(@Nonnull UICommandBuilder cmd,
                                       @Nonnull UIEventBuilder events,
                                       @Nonnull List<DropSourceSummaries.DisplayDropSource> mobDrops) {
        boolean hasMobDrops = !mobDrops.isEmpty();
        cmd.set("#MobDropsSection.Visible", hasMobDrops);
        if (!hasMobDrops) {
            return;
        }

        this.currentMobDropIndex = Math.floorMod(this.currentMobDropIndex, mobDrops.size());
        DropSourceSummaries.DisplayDropSource currentMobDrop = mobDrops.get(this.currentMobDropIndex);
        cmd.set("#MobDropName.Text", currentMobDrop.primaryLabel());
        cmd.set("#MobDropCounter.Text", (this.currentMobDropIndex + 1) + " / " + mobDrops.size());
        setMobDropPortrait(cmd, currentMobDrop.previewSource.mobType);
        boolean showCarouselControls = mobDrops.size() > 1;
        cmd.set("#MobDropPrevButton.Visible", showCarouselControls);
        cmd.set("#MobDropNextButton.Visible", showCarouselControls);

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#MobDropPrevButton",
                EventData.of("MobDropAction", "prev"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#MobDropNextButton",
                EventData.of("MobDropAction", "next"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#MobDropInteractButton",
                EventData.of("MobDropAction", "open"),
                false
        );
    }

    private void renderOtherDropsSection(@Nonnull UICommandBuilder cmd,
                                         boolean hasMobDrops,
                                         @Nonnull List<DropSourceSummaries.DisplayDropSource> otherDrops) {
        boolean hasOtherDrops = !otherDrops.isEmpty();
        cmd.set("#OtherDropsSpacer.Visible", hasMobDrops && hasOtherDrops);
        cmd.set("#OtherDropsSection.Visible", hasOtherDrops);
        if (!hasOtherDrops) {
            cmd.clear("#OtherDropSourcesList");
            return;
        }

        displayDropSources(cmd, "#OtherDropSourcesList", otherDrops);
    }

    private void displayDropSources(@Nonnull UICommandBuilder cmd,
                                    @Nonnull String listSelector,
                                    @Nonnull List<DropSourceSummaries.DisplayDropSource> dropSummaries) {
        try {
            cmd.clear(listSelector);
            int index = 0;
            for (DropSourceSummaries.DisplayDropSource summary : dropSummaries) {
                if (summary.hasZoneData()) {
                    index = addDropSourceRowWithZoneBoxes(
                            cmd,
                            listSelector,
                            index,
                            summary.primaryLabel(),
                            summary.secondaryLabel(),
                            summary.zoneData
                    );
                } else {
                    index = addSimpleDropSourceRow(cmd, listSelector, index, summary.primaryLabel(), summary.secondaryLabel());
                }
            }
        } catch (Exception e) {
            System.err.println("[Hytems] Error displaying drop sources: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int addDropSourceRowWithZoneBoxes(UICommandBuilder cmd, String listSelector, int index, String displayName,
                                              String secondaryLabel, Map<String, List<Integer>> zoneData) {
        List<Map.Entry<String, List<Integer>>> sortedZones = new ArrayList<>(zoneData.entrySet());
        sortedZones.sort((a, b) -> DropSourceParser.compareZones(a.getKey(), b.getKey()));

        cmd.append(listSelector, HytemsUiTemplates.DROP_SOURCE_ROW);
        String rowSelector = listSelector + "[" + index + "]";
        String badgesSelector = rowSelector + " #ZoneBadges";
        cmd.set(rowSelector + " #SourceName.Text", displayName);
        String formattedSecondary = formatSecondaryLabelForTwoLines(secondaryLabel);
        cmd.set(rowSelector + " #SourceContext.Text", formattedSecondary);
        cmd.set(rowSelector + " #SourceContext.Visible", formattedSecondary != null && !formattedSecondary.isEmpty());
        setDropRowHeight(cmd, rowSelector, formattedSecondary != null && !formattedSecondary.isEmpty() ? DROP_ROW_HEIGHT : DROP_ROW_COMPACT_HEIGHT);

        for (int i = 0; i < sortedZones.size(); i++) {
            Map.Entry<String, List<Integer>> entry = sortedZones.get(i);
            String zone = entry.getKey();
            String zoneNumber = zone.replaceAll("[^0-9]", "");

            cmd.append(badgesSelector, HytemsUiTemplates.DROP_ZONE_BADGE);
            String badgeSelector = badgesSelector + "[" + i + "]";
            configureZoneBadge(cmd, badgeSelector, zone, "Z" + zoneNumber);
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

    private int addSimpleDropSourceRow(UICommandBuilder cmd, String listSelector, int index, String displayName,
                                       String secondaryLabel) {
        cmd.append(listSelector, HytemsUiTemplates.SIMPLE_DROP_SOURCE_ROW);
        String rowSelector = listSelector + "[" + index + "]";
        cmd.set(rowSelector + " #SourceName.Text", displayName);
        String formattedSecondary = formatSecondaryLabelForTwoLines(secondaryLabel);
        cmd.set(rowSelector + " #SourceContext.Text", formattedSecondary);
        cmd.set(rowSelector + " #SourceContext.Visible", formattedSecondary != null && !formattedSecondary.isEmpty());
        setDropRowHeight(cmd, rowSelector, formattedSecondary != null && !formattedSecondary.isEmpty() ? DROP_ROW_HEIGHT : DROP_ROW_COMPACT_HEIGHT);
        return index + 1;
    }

    private void setDropRowHeight(@Nonnull UICommandBuilder cmd, @Nonnull String rowSelector, int height) {
        Anchor anchor = new Anchor();
        anchor.setHeight(Value.of(height));
        cmd.setObject(rowSelector + ".Anchor", anchor);
    }

    private String formatSecondaryLabelForTwoLines(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        final int firstLineLimit = 58;
        if (trimmed.length() <= firstLineLimit) {
            return trimmed;
        }

        String[] parts = trimmed.split(",\\s+");
        if (parts.length <= 1) {
            int split = trimmed.lastIndexOf(' ', firstLineLimit);
            if (split > 0 && split < trimmed.length() - 1) {
                return trimmed.substring(0, split) + "\n" + trimmed.substring(split + 1);
            }
            return trimmed;
        }

        StringBuilder firstLine = new StringBuilder();
        StringBuilder secondLine = new StringBuilder();
        for (String part : parts) {
            if (firstLine.length() == 0) {
                firstLine.append(part);
                continue;
            }

            String candidate = firstLine + ", " + part;
            if (candidate.length() <= firstLineLimit || secondLine.length() == 0) {
                if (candidate.length() <= firstLineLimit) {
                    firstLine.append(", ").append(part);
                } else {
                    secondLine.append(part);
                }
            } else {
                secondLine.append(", ").append(part);
            }
        }

        if (secondLine.length() == 0) {
            return firstLine.toString();
        }
        String firstLineText = firstLine.toString();
        if (!firstLineText.endsWith(",")) {
            firstLineText = firstLineText + ",";
        }
        return firstLineText + "\n" + secondLine;
    }

    private List<CraftingRecipe> getCachedRecipes(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return Collections.emptyList();
        }
        if (itemId.equals(this.cachedRecipeItemId)) {
            return this.cachedRecipes;
        }

        this.cachedRecipeItemId = itemId;
        this.cachedRecipes = HytemsPlugin.recipeManager.getCraftingRecipes(itemId);
        return this.cachedRecipes;
    }

    private List<DropSourceSummaries.DisplayDropSource> getCachedDropSummaries(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return Collections.emptyList();
        }
        if (itemId.equals(this.cachedDropItemId)) {
            return this.cachedDropSummaries;
        }

        this.cachedDropItemId = itemId;
        List<String> dropSources = HytemsPlugin.dropListRegistry.getDropSourcesForItem(itemId);
        this.cachedDropSummaries = DropSourceSummaries.summarize(dropSources);
        return this.cachedDropSummaries;
    }

    private void filterItems() {
        filteredItems = itemSearchService.filter(HytemsPlugin.ITEMS, searchQuery);
    }

    private void ensureDetailItemAvailable() {
        if (isKnownItem(this.selectedItemId)) {
            if (!isKnownItem(this.dropsItemId)) {
                this.dropsItemId = this.selectedItemId;
            }
            return;
        }

        String storedItem = HytemsPlugin.playerDataManager.getLastViewedItem(this.playerRef);
        if (isKnownItem(storedItem)) {
            this.selectedItemId = storedItem;
            this.dropsItemId = storedItem;
            return;
        }

        if (HytemsPlugin.ITEMS.isEmpty()) {
            this.selectedItemId = null;
            this.dropsItemId = null;
            return;
        }

        String fallbackItem = HytemsPlugin.ITEMS.keySet().iterator().next();
        selectDetailItem(fallbackItem, true);
    }

    private boolean selectDetailItem(String itemId, boolean persist) {
        if (!isKnownItem(itemId)) {
            return false;
        }

        boolean itemChanged = !itemId.equals(this.selectedItemId) || !itemId.equals(this.dropsItemId);
        this.selectedItemId = itemId;
        this.dropsItemId = itemId;
        if (itemChanged) {
            this.currentMobDropIndex = 0;
        }
        if (persist) {
            HytemsPlugin.playerDataManager.setLastViewedItem(this.playerRef, itemId);
        }
        return true;
    }

    private boolean handleMobDropAction(@Nonnull String action) {
        List<DropSourceSummaries.DisplayDropSource> mobDrops = filterDropSummariesByKind(
                getCachedDropSummaries(this.dropsItemId),
                DropSourceParser.DropSourceKind.MOB
        );
        if (mobDrops.isEmpty()) {
            this.currentMobDropIndex = 0;
            return false;
        }

        switch (action.toLowerCase(Locale.ENGLISH)) {
            case "prev":
                this.currentMobDropIndex = Math.floorMod(this.currentMobDropIndex - 1, mobDrops.size());
                return true;
            case "next":
                this.currentMobDropIndex = Math.floorMod(this.currentMobDropIndex + 1, mobDrops.size());
                return true;
            case "open":
                openCurrentMobOverview();
                return false;
            default:
                return false;
        }
    }

    private void openCurrentMobOverview() {
        if (this.pageRef == null || this.pageStore == null) {
            return;
        }

        List<DropSourceSummaries.DisplayDropSource> mobDrops = filterDropSummariesByKind(
                getCachedDropSummaries(this.dropsItemId),
                DropSourceParser.DropSourceKind.MOB
        );
        if (mobDrops.isEmpty()) {
            return;
        }

        this.currentMobDropIndex = Math.floorMod(this.currentMobDropIndex, mobDrops.size());
        DropSourceSummaries.DisplayDropSource currentMobDrop = mobDrops.get(this.currentMobDropIndex);
        String mobId = currentMobDrop.previewSource != null ? currentMobDrop.previewSource.mobType : null;
        if (mobId == null || mobId.isEmpty()) {
            return;
        }

        Player player = this.pageStore.getComponent(this.pageRef, Player.getComponentType());
        if (player != null) {
            player.getPageManager().openCustomPage(this.pageRef, this.pageStore, new MobOverviewPage(this.playerRef, mobId));
        }
    }

    private List<DropSourceSummaries.DisplayDropSource> filterDropSummariesByKind(
            @Nonnull List<DropSourceSummaries.DisplayDropSource> summaries,
            @Nonnull DropSourceParser.DropSourceKind kind
    ) {
        List<DropSourceSummaries.DisplayDropSource> filtered = new ArrayList<>();
        for (DropSourceSummaries.DisplayDropSource summary : summaries) {
            if (summary.kind == kind && (kind != DropSourceParser.DropSourceKind.MOB
                    || DropSourceSummaries.shouldDisplayInMobCarousel(summary))) {
                filtered.add(summary);
            }
        }
        return filtered;
    }

    private List<DropSourceSummaries.DisplayDropSource> filterNonMobDropSummaries(
            @Nonnull List<DropSourceSummaries.DisplayDropSource> summaries
    ) {
        List<DropSourceSummaries.DisplayDropSource> filtered = new ArrayList<>();
        for (DropSourceSummaries.DisplayDropSource summary : summaries) {
            if (DropSourceSummaries.shouldDisplayInOtherDrops(summary)) {
                filtered.add(summary);
            }
        }
        return filtered;
    }

    private void selectInfoTab(@Nonnull InfoTab tab, boolean persist) {
        this.activeInfoTab = tab;
        if (persist) {
            HytemsPlugin.playerDataManager.setLastViewedTab(this.playerRef, tab.name().toLowerCase(Locale.ENGLISH));
        }
    }

    private InfoTab parseInfoTab(String tab) {
        if ("drops".equalsIgnoreCase(tab)) {
            return InfoTab.DROPS;
        }
        if ("recipes".equalsIgnoreCase(tab)) {
            return InfoTab.RECIPES;
        }
        return null;
    }

    private boolean isKnownItem(String itemId) {
        return itemId != null && !itemId.isEmpty() && HytemsPlugin.ITEMS.containsKey(itemId);
    }

    private boolean hasVisibleCraftingStation(CraftingRecipe recipe) {
        BenchRequirement[] benchReqs = recipe.getBenchRequirement();
        if (benchReqs == null || benchReqs.length == 0) {
            return false;
        }
        return resolveBenchItemId(benchReqs[0].id) != null;
    }

    private void setCraftingStationSectionVisible(@Nonnull UICommandBuilder cmd, boolean visible) {
        Anchor anchor = new Anchor();
        anchor.setHeight(Value.of(visible ? CRAFTING_STATION_SECTION_HEIGHT : 0));
        cmd.set("#CraftingStationSection.Visible", visible);
        cmd.setObject("#CraftingStationSection.Anchor", anchor);
    }

    private void setMobDropPortrait(@Nonnull UICommandBuilder cmd, String mobType) {
        String portraitPath = MobPortraitResolver.resolvePortraitPath(mobType);
        boolean hasPortrait = portraitPath != null && !portraitPath.isEmpty();

        cmd.set("#MobDropPortrait.Visible", hasPortrait);
        cmd.set("#MobDropPortraitFallback.Visible", !hasPortrait);
        if (hasPortrait) {
            cmd.set("#MobDropPortrait.Background", portraitPath);
        } else {
            cmd.set("#MobDropPortrait.Background", "#000000(0)");
        }
    }

    private void setStationIcon(@Nonnull UICommandBuilder cmd, Item stationItem) {
        String iconPath = stationItem == null ? null : stationItem.getIcon();
        boolean hasIconPath = iconPath != null && !iconPath.isEmpty();

        cmd.set("#StationIcon.Visible", !hasIconPath);
        cmd.set("#StationAssetIcon.Visible", hasIconPath);
        if (hasIconPath) {
            cmd.set("#StationAssetIcon.AssetPath", iconPath);
        }
    }

    private String resolveBenchItemId(String benchId) {
        if (benchId == null || benchId.isEmpty()) {
            return null;
        }

        String strippedBenchId = stripNamespace(benchId);
        String stationCore = stationCoreName(strippedBenchId);
        List<String> candidates = List.of(
                canonicalBenchItemId(stationCore),
                benchId,
                strippedBenchId,
                "Bench_" + stationCore,
                "Bench_" + stationCore.replace("Workbench", "WorkBench"),
                "Bench_" + stationCore.replace("Armor", "Armour"),
                "Bench_" + stationCore.replace("Armour", "Armor")
        );

        for (String candidate : candidates) {
            String itemId = findKnownItemId(candidate);
            if (itemId != null && isCraftingStationItemId(itemId)) {
                return itemId;
            }
        }

        String normalizedStationCore = normalizeLookupKey(stationCore);
        String fallback = null;
        int fallbackScore = Integer.MIN_VALUE;

        for (String itemId : HytemsPlugin.ITEMS.keySet()) {
            if (!isCraftingStationItemId(itemId)) {
                continue;
            }

            String normalizedItemCore = normalizeLookupKey(stationCoreName(stripNamespace(itemId)));
            int score = stationMatchScore(normalizedStationCore, normalizedItemCore, itemId);
            if (score > fallbackScore) {
                fallback = itemId;
                fallbackScore = score;
            }
        }

        return fallbackScore > 0 ? fallback : null;
    }

    private String canonicalBenchItemId(String stationCore) {
        if (stationCore.equalsIgnoreCase("Workbench") || stationCore.equalsIgnoreCase("WorkBench")) {
            return "Bench_WorkBench";
        }
        return "Bench_" + stationCore;
    }

    private String findKnownItemId(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return null;
        }

        if (isKnownItem(candidate)) {
            return candidate;
        }

        String normalizedCandidate = normalizeLookupKey(candidate);
        for (String itemId : HytemsPlugin.ITEMS.keySet()) {
            if (normalizeLookupKey(itemId).equals(normalizedCandidate)) {
                return itemId;
            }
        }

        return null;
    }

    private String stripNamespace(String value) {
        return value.contains(":") ? value.substring(value.indexOf(":") + 1) : value;
    }

    private String stationCoreName(String value) {
        String core = stripNamespace(value);
        if (core.startsWith("Bench_")) {
            core = core.substring("Bench_".length());
        }
        if (core.endsWith("_Bench")) {
            core = core.substring(0, core.length() - "_Bench".length());
        }
        return core;
    }

    private String benchDisplayName(String stationItemId) {
        return TextFormatters.benchName(stationCoreName(stationItemId));
    }

    private String normalizeLookupKey(String value) {
        String stripped = stripNamespace(value).toLowerCase(Locale.ENGLISH);
        StringBuilder normalized = new StringBuilder(stripped.length());
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                normalized.append(c);
            }
        }
        return normalized.toString();
    }

    private boolean isCraftingStationItemId(String itemId) {
        String stripped = stripNamespace(itemId);
        return stripped.startsWith("Bench_") || stripped.equals("Bench_WorkBench");
    }

    private int stationMatchScore(String normalizedStationCore, String normalizedItemCore, String itemId) {
        if (normalizedStationCore.isEmpty() || normalizedItemCore.isEmpty()) {
            return 0;
        }

        int score = 0;
        if (normalizedItemCore.equals(normalizedStationCore)) {
            score += 40;
        }
        if (normalizedItemCore.contains(normalizedStationCore)) {
            score += 20;
        }
        if (normalizedStationCore.contains(normalizedItemCore)) {
            score += 16;
        }

        return score - Math.max(0, stripNamespace(itemId).length() - normalizedStationCore.length()) / 24;
    }

    private void clampCurrentPage() {
        int totalPages = getTotalPages();
        if (this.currentPage < 0) {
            this.currentPage = 0;
        } else if (this.currentPage > totalPages - 1) {
            this.currentPage = Math.max(0, totalPages - 1);
        }
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

    private void recordSearchHistoryItem(@Nonnull String itemId) {
        if (!isKnownItem(itemId)) {
            return;
        }

        this.searchHistoryItems.remove(itemId);
        this.searchHistoryItems.add(0, itemId);
        if (this.searchHistoryItems.size() > SEARCH_HISTORY_MAX_ITEMS) {
            this.searchHistoryItems = new ArrayList<>(this.searchHistoryItems.subList(0, SEARCH_HISTORY_MAX_ITEMS));
        }

        HytemsPlugin.playerDataManager.recordSearchHistoryItem(this.playerRef, itemId);
    }

    private String getSearchHistoryItemByIndex(@Nonnull String indexValue) {
        int index;
        try {
            index = Integer.parseInt(indexValue);
        } catch (NumberFormatException e) {
            return null;
        }

        if (index < 0 || index >= this.searchHistoryItems.size()) {
            return null;
        }
        return this.searchHistoryItems.get(index);
    }

    private void bindSearchHistoryEvents(@Nonnull UIEventBuilder events) {
        String base = "#SearchHistorySlot[0]";
        for (int i = 0; i < SEARCH_HISTORY_MAX_ITEMS; i++) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    base + " #HistorySlot" + i + " #InteractButton" + i,
                    EventData.of("HistoryIndex", Integer.toString(i)),
                    false
            );
        }
    }

    private void renderSearchHistory(@Nonnull UICommandBuilder cmd) {
        String base = "#SearchHistorySlot[0]";
        List<String> validHistory = sanitizeHistory(this.searchHistoryItems);
        if (validHistory.size() != this.searchHistoryItems.size()) {
            this.searchHistoryItems = validHistory;
        }

        boolean hasHistory = !validHistory.isEmpty();
        cmd.set(base + " #HistoryEmptyLabel.Visible", !hasHistory);
        cmd.set(base + " #HistoryRow.Visible", hasHistory);

        for (int i = 0; i < SEARCH_HISTORY_MAX_ITEMS; i++) {
            String slot = base + " #HistorySlot" + i;
            String icon = slot + " #ItemIcon" + i;
            String rarityBg = slot + " #RarityBackground" + i;

            if (i < validHistory.size()) {
                String itemId = validHistory.get(i);
                Item item = HytemsPlugin.ITEMS.get(itemId);
                cmd.set(slot + ".Visible", true);
                cmd.set(icon + ".ItemId", itemId);
                cmd.set(rarityBg + ".Background", ItemUiSupport.rarityBackground(item));
            } else {
                cmd.set(slot + ".Visible", false);
                cmd.set(icon + ".ItemId", "");
                cmd.set(rarityBg + ".Background", ItemUiSupport.RARITY_DEFAULT_BACKGROUND);
            }
        }
    }

    private List<String> sanitizeHistory(List<String> rawHistory) {
        List<String> validHistory = new ArrayList<>();
        if (rawHistory == null || rawHistory.isEmpty()) {
            return validHistory;
        }

        for (String itemId : rawHistory) {
            if (!isKnownItem(itemId) || validHistory.contains(itemId)) {
                continue;
            }
            validHistory.add(itemId);
            if (validHistory.size() >= SEARCH_HISTORY_MAX_ITEMS) {
                break;
            }
        }
        return validHistory;
    }

    private String translatedName(Item item, String itemId) {
        String name = ItemUiSupport.translatedName(playerRef, item, itemId);
        return name.equals(itemId) ? TextFormatters.itemName(itemId) : name;
    }

    private void renderItems(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        cmd.clear("#ItemGrid");

        cmd.append("#ItemGrid", HytemsUiTemplates.ITEM_GRID);

        List<Map.Entry<String, Item>> favorites = getFavoriteEntries();
        List<Map.Entry<String, Item>> bodyItems = getBodyGridEntries();
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, bodyItems.size());
        boolean hasItems = !bodyItems.isEmpty();
        boolean hasFavorites = !favorites.isEmpty();
        cmd.set("#ItemGrid[0] #FavoritesHeader.Visible", true);
        cmd.set("#ItemGrid[0] #FavoritesRow.Visible", true);
        cmd.set("#ItemGrid[0] #FavoritesRowColumns.Visible", hasFavorites);
        cmd.set("#ItemGrid[0] #NoFavoritesLabel.Visible", !hasFavorites);
        cmd.set("#ItemGrid[0] #NoItemsRow.Visible", !hasItems);
        updateGridHeight(cmd, hasFavorites);

        String favoritesRowSelector = "#ItemGrid[0] #FavoritesRowColumns";
        int favoriteCount = Math.min(favorites.size(), ITEMS_PER_ROW);
        for (int index = 0; index < favoriteCount; index++) {
            renderGridEntry(cmd, events, favoritesRowSelector, index, favorites.get(index));
        }

        for (int row = 0; row < BODY_ROWS_PER_PAGE; row++) {
            cmd.set("#ItemGrid[0] #ItemRow" + row + ".Visible", hasItems);
            String rowColumnsSelector = "#ItemGrid[0] #ItemRow" + row + "Columns";
            for (int col = 0; col < ITEMS_PER_ROW; col++) {
                int itemIndex = startIndex + (row * ITEMS_PER_ROW) + col;
                if (itemIndex >= endIndex) {
                    break;
                }
                renderGridEntry(cmd, events, rowColumnsSelector, col, bodyItems.get(itemIndex));
            }
        }

        updateUI(cmd);
    }

    private void updateGridHeight(@Nonnull UICommandBuilder cmd, boolean hasFavorites) {
        Anchor anchor = new Anchor();
        anchor.setHeight(Value.of(GRID_HEIGHT));
        cmd.setObject("#GridSection.Anchor", anchor);
        cmd.setObject("#ItemGrid[0].Anchor", anchor);
    }

    private void renderGridEntry(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events,
                                 @Nonnull String rowColumnsSelector, int index,
                                 @Nonnull Map.Entry<String, Item> entry) {
        String itemId = entry.getKey();
        Item item = entry.getValue();
        String translatedName = translatedName(item, itemId);

        cmd.append(rowColumnsSelector, HytemsUiTemplates.ITEM_CARD);
        String selector = rowColumnsSelector + "[" + index + "]";
        renderItemButton(cmd, events, selector, itemId, translatedName);
    }

    private List<Map.Entry<String, Item>> getFavoriteEntries() {
        List<Map.Entry<String, Item>> favorites = new ArrayList<>();
        for (String itemId : favoriteItems) {
            Item item = HytemsPlugin.ITEMS.get(itemId);
            if (item != null) {
                favorites.add(Map.entry(itemId, item));
            }
            if (favorites.size() >= ITEMS_PER_ROW) {
                break;
            }
        }
        return favorites;
    }

    private List<Map.Entry<String, Item>> getBodyGridEntries() {
        return filteredItems;
    }

    private void renderItemButton(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events,
                                  @Nonnull String selector, @Nonnull String itemId, @Nonnull String translatedName) {
        Item item = HytemsPlugin.ITEMS.get(itemId);
        String rarityBg = ItemUiSupport.rarityBackground(item);

        cmd.set(selector + " #ItemIcon.ItemId", itemId);
        cmd.set(selector + " #ItemName.Text", shortenGridLabel(translatedName));
        cmd.set(selector + " #ItemName.Style.TextColor", "#ffffff");
        cmd.set(selector + " #RarityBackground.Background", rarityBg);
        cmd.set(selector + " #PinnedMarker.Background", ItemUiSupport.ICON_PIN_FILLED);
        cmd.set(selector + " #PinnedMarker.Visible", HytemsPlugin.playerDataManager.isPinned(this.playerRef, itemId));
        cmd.set(selector + " #FavoriteMarker.Background", ItemUiSupport.ICON_STAR_FILLED);
        cmd.set(selector + " #FavoriteMarker.Visible", favoriteItems.contains(itemId));

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #InteractButton",
                EventData.of("SelectedItem", itemId),
                false
        );
    }

    private void updateUI(@Nonnull UICommandBuilder cmd) {
        int totalPages = getTotalPages();
        cmd.set("#PageLabel.Text", "Page " + (currentPage + 1) + " / " + totalPages);
        cmd.set("#ItemCountLabel.Text", filteredItems.size() + " items found");
        cmd.set("#PrevPageButton.Visible", true);
        cmd.set("#NextPageButton.Visible", true);
    }

    private String shortenGridLabel(@Nonnull String name) {
        if (name.length() <= GRID_LABEL_MAX_CHARS) {
            return name;
        }
        return name.substring(0, GRID_LABEL_MAX_CHARS - 3) + "...";
    }

    private int getTotalPages() {
        int bodyItemCount = getBodyGridEntries().size();
        if (bodyItemCount == 0) {
            return 1;
        }
        return (int) Math.ceil((double) bodyItemCount / ITEMS_PER_PAGE);
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
                        new KeyedCodec<>("ItemPath", Codec.STRING),
                        (data, value) -> data.itemPath = value,
                        data -> data.itemPath
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
                )
                .addField(
                        new KeyedCodec<>("InfoTab", Codec.STRING),
                        (data, value) -> data.infoTab = value,
                        data -> data.infoTab
                )
                .addField(
                        new KeyedCodec<>("HistoryIndex", Codec.STRING),
                        (data, value) -> data.historyIndex = value,
                        data -> data.historyIndex
                )
                .addField(
                        new KeyedCodec<>("MobDropAction", Codec.STRING),
                        (data, value) -> data.mobDropAction = value,
                        data -> data.mobDropAction
                )
                .build();

        private String searchQuery;
        private String pageAction;
        private String clearSearch;
        private String closeGUI;
        private String selectedItem;
        private String itemPath;
        private String showDrops;
        private String closeDetail;
        private String closeDropPanel;
        private String pinItem;
        private String toggleFavorite;
        private String toggleFavoriteSelector;
        private String toggleFavoritesCollapse;
        private String isFavSectionStr;
        private String infoTab;
        private String historyIndex;
        private String mobDropAction;
    }
}



