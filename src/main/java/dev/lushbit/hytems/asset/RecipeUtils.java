package dev.lushbit.hytems.asset;

import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class RecipeUtils {
    private static final String[] INPUT_METHODS = {"getInput", "getInputs", "getIngredients", "getMaterials"};

    private RecipeUtils() {
    }

    public static List<MaterialQuantity> getInputs(@Nonnull CraftingRecipe recipe) {
        List<MaterialQuantity> result = new ArrayList<>();
        Object inputs = invokeFirstInputMethod(recipe);

        if (inputs instanceof MaterialQuantity) {
            addIfUsable(result, (MaterialQuantity) inputs);
        } else if (inputs instanceof MaterialQuantity[]) {
            for (MaterialQuantity input : (MaterialQuantity[]) inputs) {
                addIfUsable(result, input);
            }
        } else if (inputs instanceof Collection) {
            for (Object input : (Collection<?>) inputs) {
                if (input instanceof MaterialQuantity) {
                    addIfUsable(result, (MaterialQuantity) input);
                }
            }
        }

        return result;
    }

    public static boolean hasTodoBench(List<CraftingRecipe> recipes) {
        if (recipes == null || recipes.isEmpty()) return false;

        try {
            for (CraftingRecipe recipe : recipes) {
                BenchRequirement[] benches = recipe.getBenchRequirement();
                if (benches == null) continue;

                for (BenchRequirement bench : benches) {
                    if (bench != null && bench.id != null
                            && bench.id.toLowerCase(Locale.ENGLISH).contains("todo")) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private static Object invokeFirstInputMethod(@Nonnull CraftingRecipe recipe) {
        for (String methodName : INPUT_METHODS) {
            try {
                Method method = CraftingRecipe.class.getMethod(methodName);
                Object value = method.invoke(recipe);
                if (value != null) {
                    return value;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void addIfUsable(List<MaterialQuantity> result, MaterialQuantity input) {
        if (input != null && (input.getItemId() != null || input.getResourceTypeId() != null)) {
            result.add(input);
        }
    }
}
