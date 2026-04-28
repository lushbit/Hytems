package dev.lushbit.hytems.ui.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.lushbit.hytems.HytemsPlugin;
import dev.lushbit.hytems.asset.DropSourceParser;
import dev.lushbit.hytems.asset.RecipeUtils;
import dev.lushbit.hytems.data.PlayerDataManager;
import dev.lushbit.hytems.ui.DropSourceSummaries;
import dev.lushbit.hytems.ui.HytemsUiTemplates;
import dev.lushbit.hytems.ui.ItemUiSupport;
import dev.lushbit.hytems.ui.TextFormatters;

import javax.annotation.Nonnull;
import java.util.*;

public class PinnedItemsHud extends CustomUIHud {
    
    private final PlayerDataManager playerDataManager;
    private final PlayerRef playerRef;
    private final Store<EntityStore> store;
    private final Ref<EntityStore> ref;
    
    public PinnedItemsHud(@Nonnull PlayerRef playerRef, @Nonnull PlayerDataManager playerDataManager,
                          @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        super(playerRef);
        this.playerRef = playerRef;
        this.playerDataManager = playerDataManager;
        this.store = store;
        this.ref = ref;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder cmd) {
        cmd.append(HytemsUiTemplates.PINNED_ITEMS_HUD);
        updatePinnedItems(cmd);
    }
    
    public void updatePinnedItems(@Nonnull UICommandBuilder cmd) {
        List<String> pinnedItems = playerDataManager.getPinnedItems(playerRef);
        
        cmd.clear("#PinnedItemsList");

        for (int itemIndex = 0; itemIndex < pinnedItems.size(); itemIndex++) {
            String itemId = pinnedItems.get(itemIndex);
            buildPinnedItemBox(cmd, itemId, itemIndex);
        }
    }
    
    private void buildPinnedItemBox(@Nonnull UICommandBuilder cmd, @Nonnull String itemId, int index) {
        Item item = HytemsPlugin.ITEMS.get(itemId);
        String translatedName = ItemUiSupport.translatedName(playerRef, item, itemId);
        
        String rarityBg = ItemUiSupport.rarityBackground(item);
        String rarityColor = ItemUiSupport.rarityColor(item);

        List<CraftingRecipe> recipes = HytemsPlugin.recipeManager.getCraftingRecipes(itemId);
        List<String> dropSources = HytemsPlugin.dropListRegistry.getDropSourcesForItem(itemId);
        
        boolean hasRecipe = recipes != null && !recipes.isEmpty() && recipes.size() == 1;
        boolean hasDrops = dropSources != null && !dropSources.isEmpty();
        
        cmd.append("#PinnedItemsList", HytemsUiTemplates.PINNED_HUD_ITEM);
        
        String selector = "#PinnedItemsList[" + index + "]";
        cmd.set(selector + " #RarityBackground.Background", rarityBg);
        cmd.set(selector + " #Header #ItemIcon.ItemId", itemId);
        cmd.set(selector + " #Header #ItemName.Text", translatedName);
        cmd.set(selector + " #Header #ItemName.Style.TextColor", rarityColor);
        cmd.set(selector + " #RecipeTitle.Visible", hasRecipe);
        cmd.set(selector + " #IngredientsList.Visible", hasRecipe);
        cmd.set(selector + " #DropsTitle.Visible", hasDrops);
        cmd.set(selector + " #DropsList.Visible", hasDrops);
        cmd.set(selector + " #EmptyInfo.Visible", !hasRecipe && !hasDrops);
        
        if (hasRecipe) {
            displayRecipeIngredients(cmd, selector + " #IngredientsList", recipes.get(0));
        }
        
        if (hasDrops) {
            displayDrops(cmd, selector, dropSources);
        }
    }
    
    private void displayRecipeIngredients(@Nonnull UICommandBuilder cmd, @Nonnull String listSelector, @Nonnull CraftingRecipe recipe) {
        try {
            List<MaterialQuantity> ingredients = RecipeUtils.getInputs(recipe);
            if (ingredients == null || ingredients.isEmpty() || ingredients.size() > 4) {
                return;
            }
            
            Map<String, Integer> playerInventory = PinnedItemsInventoryTracker.scanPlayerInventory(store, ref);
            
            int renderedIndex = 0;
            for (MaterialQuantity ingredient : ingredients) {
                if (ingredient == null) continue;
                
                String ingredientId = ingredient.getItemId();
                String resourceTypeId = ingredient.getResourceTypeId();
                int quantity = ingredient.getQuantity();
                
                if (ingredientId == null && resourceTypeId == null) continue;
                
                int inventoryCount = 0;
                if (ingredientId != null) {
                    inventoryCount = playerInventory.getOrDefault(ingredientId, 0);
                }
                
                String countColor = inventoryCount >= quantity ? "#4CAF50" : "#F44336";
                String rowSelector = listSelector + "[" + renderedIndex + "]";
                
                if (ingredientId != null) {
                    cmd.append(listSelector, HytemsUiTemplates.PINNED_HUD_INGREDIENT_ENTRY);
                    renderedIndex++;
                    Item ingredientItem = HytemsPlugin.ITEMS.get(ingredientId);
                    cmd.set(rowSelector + " #IconBackground.Background", ItemUiSupport.rarityBackground(ingredientItem));
                    cmd.set(rowSelector + " #ItemIcon.ItemId", ingredientId);
                    cmd.set(rowSelector + " #ItemIcon.Visible", true);

                    String ingredientName = ItemUiSupport.translatedName(playerRef, ingredientItem, ingredientId);
                    cmd.set(rowSelector + " #IngredientName.Text", ingredientName);
                    
                    String countText = inventoryCount + "/" + quantity;
                    cmd.set(rowSelector + " #IngredientCount.Text", countText);
                    cmd.set(rowSelector + " #IngredientCount.Style.TextColor", countColor);
                } else if (resourceTypeId != null) {
                    try {
                        ResourceType resourceType = (ResourceType) ResourceType.getAssetMap().getAsset(resourceTypeId);
                        if (resourceType != null) {
                            cmd.append(listSelector, HytemsUiTemplates.PINNED_HUD_INGREDIENT_ENTRY);
                            renderedIndex++;
                            cmd.set(rowSelector + " #IconBackground.Background", ItemUiSupport.RARITY_DEFAULT_BACKGROUND);
                            cmd.set(rowSelector + " #ItemIcon.Visible", false);
                            cmd.set(rowSelector + " #ResourceIcon.AssetPath", resourceType.getIcon());
                            cmd.set(rowSelector + " #ResourceIcon.Visible", true);
                            
                            String resourceTypeName = TextFormatters.resourceTypeName(resourceTypeId);
                            cmd.set(rowSelector + " #IngredientName.Text", "Any " + resourceTypeName);
                            
                            String countText = inventoryCount + "/" + quantity;
                            cmd.set(rowSelector + " #IngredientCount.Text", countText);
                            cmd.set(rowSelector + " #IngredientCount.Style.TextColor", countColor);
                        }
                    } catch (Exception e) {
                        System.err.println("[Hytems] Error loading resource type: " + resourceTypeId);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Hytems] Error displaying ingredients: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void displayDrops(@Nonnull UICommandBuilder cmd, @Nonnull String itemSelector, @Nonnull List<String> dropSources) {
        try {
            String listSelector = itemSelector + " #DropsList";
            List<DropSourceSummaries.DisplayDropSource> mobSummaries = DropSourceSummaries.summarizeMobDrops(dropSources);
            int totalDropSources = DropSourceSummaries.summarize(dropSources).size();
            int dropIndex = 0;
            int maxDrops = 3;

            for (DropSourceSummaries.DisplayDropSource summary : mobSummaries) {
                if (dropIndex >= maxDrops) break;

                cmd.append(listSelector, HytemsUiTemplates.PINNED_HUD_DROP_ROW);
                String rowSelector = listSelector + "[" + dropIndex + "]";
                String badgesSelector = rowSelector + " #ZoneBadges";
                cmd.set(rowSelector + " #SourceName.Text", summary.fullLabel());

                List<Map.Entry<String, List<Integer>>> sortedZones = new ArrayList<>(summary.zoneData.entrySet());
                sortedZones.sort((a, b) -> DropSourceParser.compareZones(a.getKey(), b.getKey()));

                int badgeIndex = 0;
                for (Map.Entry<String, List<Integer>> entry : sortedZones) {
                    String zone = entry.getKey();
                    String zoneNumber = zone.replaceAll("[^0-9]", "");
                    cmd.append(badgesSelector, HytemsUiTemplates.PINNED_HUD_ZONE_BADGE);
                    String badgeSelector = badgesSelector + "[" + badgeIndex + "]";
                    configureZoneBadge(cmd, badgeSelector, zone, "Z" + zoneNumber);
                    badgeIndex++;

                }
                dropIndex++;
            }
            int remainingDrops = totalDropSources - dropIndex;

            if (remainingDrops > 0) {
                String suffix = remainingDrops == 1 ? "" : "s";
                cmd.set(itemSelector + " #MoreDropsLabel.Visible", true);
                cmd.set(itemSelector + " #MoreDropsLabel.Text", "... and " + remainingDrops + " other drop" + suffix + " (/h)");
            }
        } catch (Exception e) {
            System.err.println("[Hytems] Error displaying drops: " + e.getMessage());
            e.printStackTrace();
        }
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

}

