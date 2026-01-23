package de.notjan.hytems;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import java.util.*;

public class RecipeManager {
    private final Map<String, CraftingRecipe> allRecipes = new HashMap<>();
    private final Map<String, List<CraftingRecipe>> outputRecipes = new HashMap<>();

    public void initialize(Map<String, CraftingRecipe> recipes) {
        allRecipes.clear();
        outputRecipes.clear();

        for (Map.Entry<String, CraftingRecipe> entry : recipes.entrySet()) {
            allRecipes.put(entry.getKey(), entry.getValue());
            MaterialQuantity[] outputs = entry.getValue().getOutputs();

            if (outputs != null) {
                for (MaterialQuantity output : outputs) {
                    if (output != null && output.getItemId() != null) {
                        outputRecipes.computeIfAbsent(output.getItemId(), k -> new ArrayList<>()).add(entry.getValue());
                    }
                }
            }
        }
    }

    public List<CraftingRecipe> getCraftingRecipes(String itemId) {
        return outputRecipes.getOrDefault(itemId, Collections.emptyList());
    }

    public int getTotalRecipeCount() {
        return allRecipes.size();
    }
}
