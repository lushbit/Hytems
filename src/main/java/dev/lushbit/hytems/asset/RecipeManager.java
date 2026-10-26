package dev.lushbit.hytems.asset;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import java.util.*;

public class RecipeManager {
    private volatile Map<String, CraftingRecipe> pendingRecipes;
    private volatile Map<String, List<CraftingRecipe>> outputRecipes = Map.of();
    private volatile int totalRecipeCount;

    public void initialize(Map<String, CraftingRecipe> recipes) {
        pendingRecipes = recipes;
        totalRecipeCount = recipes.size();
    }

    private Map<String, List<CraftingRecipe>> index() {
        if (pendingRecipes != null) {
            synchronized (this) {
                Map<String, CraftingRecipe> recipes = pendingRecipes;
                if (recipes != null) {
                    outputRecipes = build(recipes);
                    pendingRecipes = null;
                }
            }
        }
        return outputRecipes;
    }

    private static Map<String, List<CraftingRecipe>> build(Map<String, CraftingRecipe> recipes) {
        Map<String, List<CraftingRecipe>> byOutput = new HashMap<>();
        for (Map.Entry<String, CraftingRecipe> entry : recipes.entrySet()) {
            MaterialQuantity[] outputs = entry.getValue().getOutputs();

            if (outputs != null) {
                for (MaterialQuantity output : outputs) {
                    if (output != null && output.getItemId() != null) {
                        byOutput.computeIfAbsent(output.getItemId(), k -> new ArrayList<>()).add(entry.getValue());
                    }
                }
            }
        }
        return byOutput;
    }

    public List<CraftingRecipe> getCraftingRecipes(String itemId) {
        return index().getOrDefault(itemId, Collections.emptyList());
    }

    public int getTotalRecipeCount() {
        return totalRecipeCount;
    }
}
