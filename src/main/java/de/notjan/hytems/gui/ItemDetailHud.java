package de.notjan.hytems.gui;

import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import de.notjan.hytems.HytemsPlugin;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.*;

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

    private ItemQuality getItemQuality(Item item) {
        if (item == null) return null;
        try {
            int qualityIndex = item.getQualityIndex();
            return ItemQuality.getAssetMap().getAsset(qualityIndex);
        } catch (Exception e) {
            return null;
        }
    }

    private String getRarityBackground(Item item) {
        ItemQuality quality = getItemQuality(item);
        if (quality != null) {
            String texture = quality.getSlotTexture();
            if (texture != null) {
                if (texture.contains("SlotCommon")) return "hytems/textures/rarity_common.png";
                if (texture.contains("SlotUncommon")) return "hytems/textures/rarity_uncommon.png";
                if (texture.contains("SlotRare")) return "hytems/textures/rarity_rare.png";
                if (texture.contains("SlotEpic")) return "hytems/textures/rarity_epic.png";
                if (texture.contains("SlotLegendary")) return "hytems/textures/rarity_legendary.png";
            }
        }
        return "hytems/textures/rarity_default.png";
    }


    private String getRarityColor(Item item) {
        ItemQuality quality = getItemQuality(item);
        if (quality != null && quality.getTextColor() != null) {
            Color color = quality.getTextColor();
            return String.format("#%02x%02x%02x", color.red & 0xFF, color.green & 0xFF, color.blue & 0xFF);
        }
        return "#ffffff";
    }

    @Override
    public void build(@Nonnull UICommandBuilder cmd) {
        if (!visible) {
            return;
        }

        cmd.append("hytems/ItemDetail.ui");
        updateDetailPanel(cmd);
    }

    private void updateDetailPanel(@Nonnull UICommandBuilder cmd) {
        try {
            if (itemId != null && !itemId.isEmpty()) {
                Item item = HytemsPlugin.ITEMS.get(itemId);
                String translatedName = getTranslatedName(item, itemId);
                
                String rarityBg = getRarityBackground(item);
                String rarityColor = getRarityColor(item);

                cmd.set("#DetailItemIcon.ItemId", itemId);
                cmd.set("#DetailItemName.Text", translatedName);
                cmd.set("#DetailItemName.Style.TextColor", rarityColor);
                cmd.set("#ItemHeader[0].Background", rarityBg);
                cmd.set("#DetailItemId.Text", itemId);

                if (item != null) {
                    int maxStack = item.getMaxStack();
                    cmd.set("#DetailMaxStack.Text", String.valueOf(maxStack));

                    double durability = item.getMaxDurability();
                    if (durability > 0) {
                        cmd.set("#DetailDurability.Text", String.valueOf((int) durability));
                    } else {
                        cmd.set("#DetailDurability.Text", "N/A");
                    }

                    loadRecipes(cmd, itemId);
                }
            }
        } catch (Exception e) {
            System.err.println("[Hytems] ERROR: Failed to display item detail for: " + itemId);
            System.err.println("[Hytems] Error: " + e.getMessage());
            e.printStackTrace();
            try {
                cmd.set("#NoRecipeContainer.Visible", true);
                cmd.set("#RecipeContent.Visible", false);
            } catch (Exception ex) {
                System.err.println("[Hytems] Critical error in updateDetailPanel: " + ex.getMessage());
            }
        }
    }

    private String getTranslatedName(Item item, String itemId) {
        if (item == null) return itemId;
        String translatedName = I18nModule.get()
                .getMessage(this.playerRef.getLanguage(), item.getTranslationKey());
        return translatedName != null ? translatedName : itemId;
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
                String stationName = getTranslatedName(stationItem, bench.id);

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
            List<MaterialQuantity> ingredients = getRecipeInputs(recipe);

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
                        cmd.set(rowSelector + " #IconBackground.Background", getRarityBackground(ingredientItem));
                        cmd.set(rowSelector + " #ItemIcon.ItemId", ingredientId);
                        cmd.set(rowSelector + " #ItemIcon.Visible", true);
                        cmd.set(rowSelector + " #Quantity.Text", "x" + quantity);

                        String ingredientName = getTranslatedName(ingredientItem, ingredientId);
                        cmd.set(rowSelector + " #IngredientName.Text", ingredientName);
                    }
                    else if (resourceTypeId != null) {
                        try {
                            ResourceType resourceType = (ResourceType) ResourceType.getAssetMap().getAsset(resourceTypeId);
                            if (resourceType != null) {
                                cmd.set(rowSelector + " #ResourceIcon.AssetPath", resourceType.getIcon());
                                cmd.set(rowSelector + " #ResourceIcon.Visible", true);

                                String resourceTypeName = formatResourceTypeName(resourceTypeId);

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

    private String formatResourceTypeName(String resourceTypeId) {
        if (resourceTypeId == null) return "Unknown";

        String name = resourceTypeId;

        if (name.contains(":")) {
            name = name.substring(name.indexOf(":") + 1);
        }

        name = name.replace("_", " ");

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);

            if (i == 0) {
                result.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c) && i > 0 && name.charAt(i - 1) != ' ') {
                result.append(" ").append(c);
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }


    private List<MaterialQuantity> getRecipeInputs(@Nonnull CraftingRecipe recipe) {
        List<MaterialQuantity> result = new ArrayList<>();
        Object inputsObj = null;

        try {
            Method getInputMethod = CraftingRecipe.class.getMethod("getInput");
            inputsObj = getInputMethod.invoke(recipe);
        } catch (Exception e) {
            String[] methodNames = {"getInputs", "getIngredients", "getMaterials"};
            for (String methodName : methodNames) {
                try {
                    Method method = CraftingRecipe.class.getMethod(methodName);
                    inputsObj = method.invoke(recipe);
                    if (inputsObj != null) break;
                } catch (Exception ex) {
                    // Continue trying other methods
                }
            }
        }

        if (inputsObj != null) {
            if (inputsObj instanceof MaterialQuantity) {
                MaterialQuantity input = (MaterialQuantity) inputsObj;
                if (input != null && (input.getItemId() != null || input.getResourceTypeId() != null)) {
                    result.add(input);
                }
            } else if (inputsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> inputs = (List<Object>) inputsObj;
                for (Object obj : inputs) {
                    if (obj instanceof MaterialQuantity) {
                        MaterialQuantity input = (MaterialQuantity) obj;
                        if (input != null && (input.getItemId() != null || input.getResourceTypeId() != null)) {
                            result.add(input);
                        }
                    }
                }
            } else if (inputsObj instanceof MaterialQuantity[]) {
                MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                for (MaterialQuantity input : inputs) {
                    if (input != null && (input.getItemId() != null || input.getResourceTypeId() != null)) {
                        result.add(input);
                    }
                }
            } else if (inputsObj instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<Object> inputs = (Collection<Object>) inputsObj;
                for (Object obj : inputs) {
                    if (obj instanceof MaterialQuantity) {
                        MaterialQuantity input = (MaterialQuantity) obj;
                        if (input != null && (input.getItemId() != null || input.getResourceTypeId() != null)) {
                            result.add(input);
                        }
                    }
                }
            }
        }

        return result;
    }
}
