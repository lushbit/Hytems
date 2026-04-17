package de.notjan.hytems.ui.hud;

import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import de.notjan.hytems.HytemsPlugin;
import de.notjan.hytems.asset.RecipeUtils;
import de.notjan.hytems.ui.HytemsUiTemplates;
import de.notjan.hytems.ui.ItemUiSupport;
import de.notjan.hytems.ui.TextFormatters;

import javax.annotation.Nonnull;
import java.util.List;

public class ItemDetailHud extends CustomUIHud {
    private final PlayerRef playerRef;
    private String itemId;
    private boolean visible = true;

    public ItemDetailHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
        this.playerRef = playerRef;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
        this.visible = true;
    }

    public void hide() {
        this.visible = false;
    }

    public void show() {
        this.visible = true;
    }

    @Override
    public void build(@Nonnull UICommandBuilder cmd) {
        if (!visible) return;

        cmd.append(HytemsUiTemplates.ITEM_DETAIL);
        updateDetailPanel(cmd);
    }

    private void updateDetailPanel(@Nonnull UICommandBuilder cmd) {
        if (itemId == null || itemId.isEmpty()) return;

        try {
            Item item = HytemsPlugin.ITEMS.get(itemId);
            cmd.set("#DetailItemIcon.ItemId", itemId);
            cmd.set("#DetailItemName.Text", ItemUiSupport.translatedName(playerRef, item, itemId));
            cmd.set("#DetailItemName.Style.TextColor", ItemUiSupport.rarityColor(item));
            cmd.set("#ItemHeader[0].Background", ItemUiSupport.rarityBackground(item));
            cmd.set("#DetailItemId.Text", itemId);

            if (item != null) {
                cmd.set("#DetailMaxStack.Text", String.valueOf(item.getMaxStack()));
                cmd.set("#DetailDurability.Text", item.getMaxDurability() > 0 ? String.valueOf((int) item.getMaxDurability()) : "N/A");
                loadRecipes(cmd, itemId);
            }
        } catch (Exception e) {
            System.err.println("[Hytems] ERROR: Failed to display item detail for: " + itemId);
            e.printStackTrace();
            showNoRecipe(cmd);
        }
    }

    private void loadRecipes(@Nonnull UICommandBuilder cmd, @Nonnull String itemId) {
        try {
            List<CraftingRecipe> recipes = HytemsPlugin.recipeManager.getCraftingRecipes(itemId);
            if (recipes == null || recipes.size() != 1) {
                showNoRecipe(cmd);
                return;
            }

            cmd.set("#NoRecipeContainer.Visible", false);
            cmd.set("#RecipeContent.Visible", true);
            displayRecipe(cmd, recipes.get(0));
        } catch (Exception e) {
            System.err.println("[Hytems] Error loading recipes for " + itemId + ": " + e.getMessage());
            e.printStackTrace();
            showNoRecipe(cmd);
        }
    }

    private void displayRecipe(@Nonnull UICommandBuilder cmd, @Nonnull CraftingRecipe recipe) {
        BenchRequirement[] benches = recipe.getBenchRequirement();
        if (benches != null && benches.length > 0) {
            BenchRequirement bench = benches[0];
            Item stationItem = HytemsPlugin.ITEMS.get(bench.id);
            cmd.set("#StationName.Text", ItemUiSupport.translatedName(playerRef, stationItem, bench.id));
            cmd.set("#StationTier.Text", bench.requiredTierLevel > 0 ? "Tier " + bench.requiredTierLevel : "Any tier");
        }

        cmd.clear("#IngredientsList");
        displayIngredients(cmd, recipe);
    }

    private void displayIngredients(@Nonnull UICommandBuilder cmd, @Nonnull CraftingRecipe recipe) {
        List<MaterialQuantity> ingredients = RecipeUtils.getInputs(recipe);
        if (ingredients.isEmpty() || ingredients.size() > 6) {
            showNoRecipe(cmd);
            return;
        }

        int renderedIndex = 0;
        for (MaterialQuantity ingredient : ingredients) {
            if (appendIngredient(cmd, "#IngredientsList[" + renderedIndex + "]", ingredient)) {
                renderedIndex++;
            }
        }
    }

    private boolean appendIngredient(@Nonnull UICommandBuilder cmd, @Nonnull String rowSelector, @Nonnull MaterialQuantity ingredient) {
        String ingredientId = ingredient.getItemId();
        String resourceTypeId = ingredient.getResourceTypeId();
        if (ingredientId == null && resourceTypeId == null) return false;

        if (ingredientId != null) {
            cmd.append("#IngredientsList", HytemsUiTemplates.INGREDIENT_ENTRY);
            Item ingredientItem = HytemsPlugin.ITEMS.get(ingredientId);
            cmd.set(rowSelector + " #IconBackground.Background", ItemUiSupport.rarityBackground(ingredientItem));
            cmd.set(rowSelector + " #ItemIcon.ItemId", ingredientId);
            cmd.set(rowSelector + " #ItemIcon.Visible", true);
            cmd.set(rowSelector + " #Quantity.Text", "x" + ingredient.getQuantity());
            cmd.set(rowSelector + " #IngredientName.Text", ItemUiSupport.translatedName(playerRef, ingredientItem, ingredientId));
            return true;
        }

        try {
            ResourceType resourceType = (ResourceType) ResourceType.getAssetMap().getAsset(resourceTypeId);
            if (resourceType != null) {
                cmd.append("#IngredientsList", HytemsUiTemplates.INGREDIENT_ENTRY);
                cmd.set(rowSelector + " #IconBackground.Background", ItemUiSupport.RARITY_DEFAULT_BACKGROUND);
                cmd.set(rowSelector + " #ItemIcon.Visible", false);
                cmd.set(rowSelector + " #ResourceIcon.AssetPath", resourceType.getIcon());
                cmd.set(rowSelector + " #ResourceIcon.Visible", true);
                cmd.set(rowSelector + " #Quantity.Text", "x" + ingredient.getQuantity());
                cmd.set(rowSelector + " #IngredientName.Text", "Any " + TextFormatters.resourceTypeName(resourceTypeId));
                return true;
            }
        } catch (Exception e) {
            System.err.println("[Hytems] Error loading resource type: " + resourceTypeId);
            e.printStackTrace();
        }
        return false;
    }

    private void showNoRecipe(@Nonnull UICommandBuilder cmd) {
        cmd.set("#NoRecipeContainer.Visible", true);
        cmd.set("#RecipeContent.Visible", false);
    }
}
