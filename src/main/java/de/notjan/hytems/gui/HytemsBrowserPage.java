package de.notjan.hytems.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import de.notjan.hytems.HytemsPlugin;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class HytemsBrowserPage extends InteractiveCustomUIPage<HytemsBrowserPage.BrowserData> {

    private static final int ITEMS_PER_ROW = 7;
    private static final int ROWS_PER_PAGE = 8;
    private static final int ITEMS_PER_PAGE = ITEMS_PER_ROW * ROWS_PER_PAGE;

    private String searchQuery = "";
    private int currentPage = 0;
    private String selectedItemId = null;
    private List<Map.Entry<String, Item>> filteredItems = new ArrayList<>();

    public HytemsBrowserPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, BrowserData.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append("hytems/ItemBrowser.ui");
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
                "#CloseDetailButton",
                EventData.of("CloseDetail", "true"),
                false
        );

        filterItems();
        renderItems(cmd, events);
        updateDetailPanel(cmd);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull BrowserData data) {
        super.handleDataEvent(ref, store, data);
        boolean needsUpdate = false;

        if (data.selectedItem != null && !data.selectedItem.isEmpty()) {
            this.selectedItemId = data.selectedItem;
            needsUpdate = true;
        }

        if (data.closeDetail != null && "true".equals(data.closeDetail)) {
            this.selectedItemId = null;
            needsUpdate = true;
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

        if (needsUpdate) {
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();

            if (data.clearSearch != null) {
                cmd.set("#SearchInput.Value", "");
            }

            filterItems();
            renderItems(cmd, events);
            updateDetailPanel(cmd);
            this.sendUpdate(cmd, events, false);
        }
    }

    private void filterItems() {
        Map<String, Item> allItems = HytemsPlugin.ITEMS;

        if (searchQuery.isEmpty()) {
            filteredItems = allItems.entrySet().stream()
                    .sorted((e1, e2) -> {
                        String name1 = getTranslatedName(e1.getValue(), e1.getKey());
                        String name2 = getTranslatedName(e2.getValue(), e2.getKey());
                        return name1.compareToIgnoreCase(name2);
                    })
                    .collect(Collectors.toList());
        } else {
            String lowerQuery = searchQuery.toLowerCase(Locale.ENGLISH);
            filteredItems = allItems.entrySet().stream()
                    .filter(entry -> {
                        Item item = entry.getValue();
                        if (item == null) return false;

                        String translatedName = getTranslatedName(item, entry.getKey());
                        return entry.getKey().toLowerCase(Locale.ENGLISH).contains(lowerQuery) ||
                                translatedName.toLowerCase(Locale.ENGLISH).contains(lowerQuery);
                    })
                    .sorted((e1, e2) -> {
                        String name1 = getTranslatedName(e1.getValue(), e1.getKey());
                        String name2 = getTranslatedName(e2.getValue(), e2.getKey());
                        return name1.compareToIgnoreCase(name2);
                    })
                    .collect(Collectors.toList());
        }
    }

    private String getTranslatedName(Item item, String itemId) {
        if (item == null) return itemId;

        String translatedName = I18nModule.get()
                .getMessage(this.playerRef.getLanguage(), item.getTranslationKey());
        return translatedName != null ? translatedName : itemId;
    }

    private void renderItems(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
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
                String translatedName = getTranslatedName(item, itemId);

                if (col == 0) {
                    cmd.appendInline("#ItemGrid",
                            "Group {\n" +
                                    "  Anchor: (Height: 109);\n" +
                                    "  LayoutMode: Left;\n" +
                                    "}\n"
                    );
                }

                cmd.appendInline("#ItemGrid[" + row + "]",
                        "Button {\n" +
                                "  Anchor: (Width: 92, Height: 102, Right: 7, Bottom: 7);\n" +
                                "  Background: #2a2a2a(0.7);\n" +
                                "  Padding: (Full: 6);\n" +
                                "  LayoutMode: Top;\n" +
                                "\n" +
                                "  ItemIcon #ItemIcon {\n" +
                                "    Anchor: (Width: 76, Height: 76);\n" +
                                "    Visible: true;\n" +
                                "  }\n" +
                                "\n" +
                                "  Group {\n" +
                                "    Anchor: (Height: 4);\n" +
                                "  }\n" +
                                "\n" +
                                "  Label #ItemName {\n" +
                                "    Text: \"\";\n" +
                                "    Anchor: (Height: 16);\n" +
                                "    Style: (\n" +
                                "      FontSize: 11,\n" +
                                "      TextColor: #ffffff,\n" +
                                "      HorizontalAlignment: Center\n" +
                                "    );\n" +
                                "  }\n" +
                                "}\n"
                );

                String selector = "#ItemGrid[" + row + "][" + col + "]";
                cmd.set(selector + " #ItemIcon.ItemId", itemId);
                cmd.set(selector + " #ItemName.Text", translatedName);

                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        selector,
                        EventData.of("SelectedItem", itemId),
                        false
                );

                col++;
                if (col >= ITEMS_PER_ROW) {
                    col = 0;
                    row++;
                }
            }
        }

        updateUI(cmd);
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

    private void updateDetailPanel(@Nonnull UICommandBuilder cmd) {
        try {
            if (selectedItemId != null && !selectedItemId.isEmpty()) {
                Item item = HytemsPlugin.ITEMS.get(selectedItemId);
                String translatedName = getTranslatedName(item, selectedItemId);

                cmd.set("#DetailPanelContainer.Visible", true);
                cmd.set("#DetailItemIcon.ItemId", selectedItemId);
                cmd.set("#DetailItemName.Text", translatedName);
                cmd.set("#DetailItemId.Text", selectedItemId);

                if (item != null) {
                    int maxStack = item.getMaxStack();
                    cmd.set("#DetailMaxStack.Text", String.valueOf(maxStack));

                    double durability = item.getMaxDurability();
                    if (durability > 0) {
                        cmd.set("#DetailDurability.Text", String.valueOf((int) durability));
                    } else {
                        cmd.set("#DetailDurability.Text", "N/A");
                    }
                }

                loadRecipes(cmd, selectedItemId);
            } else {
                cmd.set("#DetailPanelContainer.Visible", false);
            }
        } catch (Exception e) {
            System.err.println("[Hytems] ERROR: Failed to display item detail for: " + selectedItemId);
            System.err.println("[Hytems] Error: " + e.getMessage());
            e.printStackTrace();

            try {
                cmd.set("#DetailPanelContainer.Visible", true);
                cmd.set("#NoRecipeContainer.Visible", true);
                cmd.set("#RecipeContent.Visible", false);
            } catch (Exception ex) {
                System.err.println("[Hytems] Critical error in updateDetailPanel: " + ex.getMessage());
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
                    if (ingredientId == null) continue;

                    int quantity = ingredient.getQuantity();

                    StringBuilder uiBuilder = new StringBuilder();
                    uiBuilder.append("Group {\n");
                    uiBuilder.append("  LayoutMode: Left;\n");
                    uiBuilder.append("  Anchor: (Height: 50);\n");
                    uiBuilder.append("  Padding: (Bottom: 6);\n");
                    uiBuilder.append("  ItemIcon {\n");
                    uiBuilder.append("    Anchor: (Width: 44, Height: 44);\n");
                    uiBuilder.append("    Visible: true;\n");
                    uiBuilder.append("  }\n");
                    uiBuilder.append("  Group {\n");
                    uiBuilder.append("    Anchor: (Width: 8);\n");
                    uiBuilder.append("  }\n");
                    uiBuilder.append("  Label {\n");
                    uiBuilder.append("    Anchor: (Width: 40);\n");
                    uiBuilder.append("    Style: (\n");
                    uiBuilder.append("      FontSize: 12,\n");
                    uiBuilder.append("      TextColor: #ffaa00,\n");
                    uiBuilder.append("      VerticalAlignment: Center,\n");
                    uiBuilder.append("      RenderBold: true\n");
                    uiBuilder.append("    );\n");
                    uiBuilder.append("  }\n");
                    uiBuilder.append("  Label {\n");
                    uiBuilder.append("    FlexWeight: 1;\n");
                    uiBuilder.append("    Style: (\n");
                    uiBuilder.append("      FontSize: 12,\n");
                    uiBuilder.append("      TextColor: #cccccc,\n");
                    uiBuilder.append("      VerticalAlignment: Center\n");
                    uiBuilder.append("    );\n");
                    uiBuilder.append("  }\n");
                    uiBuilder.append("}\n");

                    cmd.appendInline("#IngredientsList", uiBuilder.toString());

                    String rowSelector = "#IngredientsList[" + i + "]";
                    cmd.set(rowSelector + "[0].ItemId", ingredientId);
                    cmd.set(rowSelector + "[0].Visible", true);
                    cmd.set(rowSelector + "[2].Text", "x" + quantity);

                    Item ingredientItem = HytemsPlugin.ITEMS.get(ingredientId);
                    String ingredientName = getTranslatedName(ingredientItem, ingredientId);
                    cmd.set(rowSelector + "[3].Text", ingredientName);
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
                }
            }
        }

        if (inputsObj != null) {
            if (inputsObj instanceof MaterialQuantity) {
                MaterialQuantity input = (MaterialQuantity) inputsObj;
                if (input != null && input.getItemId() != null) {
                    result.add(input);
                }
            } else if (inputsObj instanceof List) {
                List<?> inputs = (List<?>) inputsObj;
                for (Object obj : inputs) {
                    if (obj instanceof MaterialQuantity) {
                        MaterialQuantity input = (MaterialQuantity) obj;
                        if (input != null && input.getItemId() != null) {
                            result.add(input);
                        }
                    }
                }
            } else if (inputsObj instanceof MaterialQuantity[]) {
                MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                for (MaterialQuantity input : inputs) {
                    if (input != null && input.getItemId() != null) {
                        result.add(input);
                    }
                }
            } else if (inputsObj instanceof Collection) {
                Collection<?> inputs = (Collection<?>) inputsObj;
                for (Object obj : inputs) {
                    if (obj instanceof MaterialQuantity) {
                        MaterialQuantity input = (MaterialQuantity) obj;
                        if (input != null && input.getItemId() != null) {
                            result.add(input);
                        }
                    }
                }
            }
        }

        return result;
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
                        new KeyedCodec<>("CloseDetail", Codec.STRING),
                        (data, value) -> data.closeDetail = value,
                        data -> data.closeDetail
                )
                .build();

        private String searchQuery;
        private String pageAction;
        private String clearSearch;
        private String closeGUI;
        private String selectedItem;
        private String closeDetail;
    }
}
