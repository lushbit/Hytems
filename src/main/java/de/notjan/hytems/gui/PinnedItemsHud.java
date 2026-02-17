package de.notjan.hytems.gui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import de.notjan.hytems.HytemsPlugin;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PinnedItemsHud extends CustomUIHud {
    
    private final PinnedItemsManager pinnedItemsManager;
    private final PlayerRef playerRef;
    private final Store<EntityStore> store;
    private final Ref<EntityStore> ref;
    
    public PinnedItemsHud(@Nonnull PlayerRef playerRef, @Nonnull PinnedItemsManager pinnedItemsManager, 
                          @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        super(playerRef);
        this.playerRef = playerRef;
        this.pinnedItemsManager = pinnedItemsManager;
        this.store = store;
        this.ref = ref;
    }
    
    @Override
    protected void build(@Nonnull UICommandBuilder cmd) {
        cmd.append("hytems/PinnedItems.ui");
        updatePinnedItems(cmd);
    }
    
    public void updatePinnedItems(@Nonnull UICommandBuilder cmd) {
        List<String> pinnedItems = pinnedItemsManager.getPinnedItems(playerRef);
        
        cmd.clear("#PinnedItemsList");
        
        int index = 0;
        for (String itemId : pinnedItems) {
            buildPinnedItemBox(cmd, itemId, index);
            
            if (index < pinnedItems.size() - 1) {
                cmd.appendInline("#PinnedItemsList", "Group {\n  Anchor: (Height: 6);\n}\n");
            }
            
            index++;
        }
    }
    
    private void buildPinnedItemBox(@Nonnull UICommandBuilder cmd, @Nonnull String itemId, int index) {
        Item item = HytemsPlugin.ITEMS.get(itemId);
        String translatedName = getTranslatedName(item, itemId);
        
        List<CraftingRecipe> recipes = HytemsPlugin.recipeManager.getCraftingRecipes(itemId);
        List<String> dropSources = HytemsPlugin.dropListRegistry.getDropSourcesForItem(itemId);
        
        boolean hasRecipe = recipes != null && !recipes.isEmpty() && recipes.size() == 1;
        boolean hasDrops = dropSources != null && !dropSources.isEmpty();
        
        StringBuilder uiBuilder = new StringBuilder();
        uiBuilder.append("Group #PinnedItem").append(index).append(" {\n");
        uiBuilder.append("  LayoutMode: Top;\n");
        uiBuilder.append("  Anchor: (Width: 280);\n");
        uiBuilder.append("  Background: #1e1e1e(0.9);\n");
        uiBuilder.append("  Padding: (Full: 8);\n");
        uiBuilder.append("\n");
        
        uiBuilder.append("  Group #Header {\n");
        uiBuilder.append("    LayoutMode: Left;\n");
        uiBuilder.append("    Anchor: (Height: 44);\n");
        uiBuilder.append("    Padding: (Bottom: 8);\n");
        uiBuilder.append("    ItemIcon #ItemIcon {\n");
        uiBuilder.append("      Anchor: (Width: 40, Height: 40);\n");
        uiBuilder.append("      Visible: true;\n");
        uiBuilder.append("    }\n");
        uiBuilder.append("    Group {\n");
        uiBuilder.append("      Anchor: (Width: 8);\n");
        uiBuilder.append("    }\n");
        uiBuilder.append("    Label #ItemName {\n");
        uiBuilder.append("      FlexWeight: 1;\n");
        uiBuilder.append("      Style: (\n");
        uiBuilder.append("        FontSize: 14,\n");
        uiBuilder.append("        TextColor: #ffffff,\n");
        uiBuilder.append("        VerticalAlignment: Center,\n");
        uiBuilder.append("        RenderBold: true\n");
        uiBuilder.append("      );\n");
        uiBuilder.append("    }\n");
        uiBuilder.append("  }\n");
        
        if (hasRecipe) {
            uiBuilder.append("  Label {\n");
            uiBuilder.append("    Text: \"Recipe:\";\n");
            uiBuilder.append("    Anchor: (Height: 18);\n");
            uiBuilder.append("    Padding: (Top: 4, Bottom: 6);\n");
            uiBuilder.append("    Style: (\n");
            uiBuilder.append("      FontSize: 11,\n");
            uiBuilder.append("      TextColor: #66ccff,\n");
            uiBuilder.append("      RenderBold: true\n");
            uiBuilder.append("    );\n");
            uiBuilder.append("  }\n");
            uiBuilder.append("  Group #IngredientsList {\n");
            uiBuilder.append("    LayoutMode: Top;\n");
            uiBuilder.append("  }\n");
        }
        
        if (hasDrops) {
            uiBuilder.append("  Label {\n");
            uiBuilder.append("    Text: \"Drops from:\";\n");
            uiBuilder.append("    Anchor: (Height: 18);\n");
            uiBuilder.append("    Padding: (Top: 6, Bottom: 6);\n");
            uiBuilder.append("    Style: (\n");
            uiBuilder.append("      FontSize: 11,\n");
            uiBuilder.append("      TextColor: #66ccff,\n");
            uiBuilder.append("      RenderBold: true\n");
            uiBuilder.append("    );\n");
            uiBuilder.append("  }\n");
            uiBuilder.append("  Group #DropsList {\n");
            uiBuilder.append("    LayoutMode: Top;\n");
            uiBuilder.append("  }\n");
            uiBuilder.append("  Label {\n");
            uiBuilder.append("    Text: \"For other drop variants, check the browser using /h!\";\n");
            uiBuilder.append("    Anchor: (Height: 16);\n");
            uiBuilder.append("    Padding: (Top: 6);\n");
            uiBuilder.append("    Style: (\n");
            uiBuilder.append("      FontSize: 9,\n");
            uiBuilder.append("      TextColor: #ffffff,\n");
            uiBuilder.append("      HorizontalAlignment: Center\n");
            uiBuilder.append("    );\n");
            uiBuilder.append("  }\n");
        }
        
        if (!hasRecipe && !hasDrops) {
            uiBuilder.append("  Label {\n");
            uiBuilder.append("    Text: \"No recipe or drops available\";\n");
            uiBuilder.append("    Anchor: (Height: 16);\n");
            uiBuilder.append("    Padding: (Top: 2);\n");
            uiBuilder.append("    Style: (\n");
            uiBuilder.append("      FontSize: 9,\n");
            uiBuilder.append("      TextColor: #888888,\n");
            uiBuilder.append("      HorizontalAlignment: Center\n");
            uiBuilder.append("    );\n");
            uiBuilder.append("  }\n");
        }
        
        uiBuilder.append("}\n");
        
        cmd.appendInline("#PinnedItemsList", uiBuilder.toString());
        
        String selector = "#PinnedItem" + index;
        cmd.set(selector + " #Header #ItemIcon.ItemId", itemId);
        cmd.set(selector + " #Header #ItemName.Text", translatedName);
        
        if (hasRecipe) {
            displayRecipeIngredients(cmd, selector + " #IngredientsList", recipes.get(0));
        }
        
        if (hasDrops) {
            displayDrops(cmd, selector + " #DropsList", dropSources);
        }
    }
    
    private void displayRecipeIngredients(@Nonnull UICommandBuilder cmd, @Nonnull String listSelector, @Nonnull CraftingRecipe recipe) {
        try {
            List<MaterialQuantity> ingredients = getRecipeInputs(recipe);
            if (ingredients == null || ingredients.isEmpty() || ingredients.size() > 4) {
                return;
            }
            
            for (int i = 0; i < ingredients.size(); i++) {
                MaterialQuantity ingredient = ingredients.get(i);
                if (ingredient == null) continue;
                
                String ingredientId = ingredient.getItemId();
                String resourceTypeId = ingredient.getResourceTypeId();
                int quantity = ingredient.getQuantity();
                
                if (ingredientId == null && resourceTypeId == null) continue;
                
                StringBuilder uiBuilder = new StringBuilder();
                uiBuilder.append("Group {\n");
                uiBuilder.append("  LayoutMode: Top;\n");
                uiBuilder.append("  Anchor: (Height: 42);\n");
                uiBuilder.append("  Padding: (Bottom: 5);\n");
                
                int inventoryCount = 0;
                String countColor = "#F44336";
                
                uiBuilder.append("  Group {\n");
                uiBuilder.append("    LayoutMode: Left;\n");
                uiBuilder.append("    Anchor: (Height: 24);\n");
                
                if (ingredientId != null) {
                    uiBuilder.append("    ItemIcon {\n");
                    uiBuilder.append("      Anchor: (Width: 22, Height: 22);\n");
                    uiBuilder.append("      Visible: true;\n");
                    uiBuilder.append("    }\n");
                } else if (resourceTypeId != null) {
                    uiBuilder.append("    AssetImage {\n");
                    uiBuilder.append("      Anchor: (Width: 22, Height: 22);\n");
                    uiBuilder.append("      Visible: true;\n");
                    uiBuilder.append("    }\n");
                }
                
                uiBuilder.append("    Group {\n");
                uiBuilder.append("      Anchor: (Width: 6);\n");
                uiBuilder.append("    }\n");
                uiBuilder.append("    Label {\n");
                uiBuilder.append("      FlexWeight: 1;\n");
                uiBuilder.append("      Style: (\n");
                uiBuilder.append("        FontSize: 11,\n");
                uiBuilder.append("        TextColor: #cccccc,\n");
                uiBuilder.append("        VerticalAlignment: Center\n");
                uiBuilder.append("      );\n");
                uiBuilder.append("    }\n");
                uiBuilder.append("  }\n");
                
                uiBuilder.append("  Label {\n");
                uiBuilder.append("    Anchor: (Height: 14);\n");
                uiBuilder.append("    Padding: (Left: 28);\n");
                uiBuilder.append("    Style: (\n");
                uiBuilder.append("      FontSize: 10,\n");
                uiBuilder.append("      TextColor: ").append(countColor).append(",\n");
                uiBuilder.append("      RenderBold: true\n");
                uiBuilder.append("    );\n");
                uiBuilder.append("  }\n");
                
                uiBuilder.append("}\n");
                
                cmd.appendInline(listSelector, uiBuilder.toString());
                
                String rowSelector = listSelector + "[" + i + "]";
                
                if (ingredientId != null) {
                    cmd.set(rowSelector + "[0][0].ItemId", ingredientId);
                    cmd.set(rowSelector + "[0][0].Visible", true);
                    
                    Item ingredientItem = HytemsPlugin.ITEMS.get(ingredientId);
                    String ingredientName = getTranslatedName(ingredientItem, ingredientId);
                    cmd.set(rowSelector + "[0][2].Text", ingredientName);
                    
                    String countText = inventoryCount + "/" + quantity;
                    cmd.set(rowSelector + "[1].Text", countText);
                } else if (resourceTypeId != null) {
                    try {
                        ResourceType resourceType = (ResourceType) ResourceType.getAssetMap().getAsset(resourceTypeId);
                        if (resourceType != null) {
                            cmd.set(rowSelector + "[0][0].AssetPath", resourceType.getIcon());
                            cmd.set(rowSelector + "[0][0].Visible", true);
                            
                            String resourceTypeName = formatResourceTypeName(resourceTypeId);
                            cmd.set(rowSelector + "[0][2].Text", "Any " + resourceTypeName);
                            
                            String countText = inventoryCount + "/" + quantity;
                            cmd.set(rowSelector + "[1].Text", countText);
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
    
    private void displayDrops(@Nonnull UICommandBuilder cmd, @Nonnull String listSelector, @Nonnull List<String> dropSources) {
        try {
            Map<String, Map<String, List<Integer>>> mobGrouping = new LinkedHashMap<>();
            int totalDropSources = dropSources.size();
            
            for (String dropSourceId : dropSources) {
                ParsedDropSource parsed = parseDropSource(dropSourceId);
                
                if (parsed.mobType != null) {
                    mobGrouping.computeIfAbsent(parsed.mobType, k -> new LinkedHashMap<>())
                            .computeIfAbsent(parsed.zone != null ? parsed.zone : "Unknown", k -> new ArrayList<>())
                            .add(parsed.tier);
                }
            }
            
            int dropIndex = 0;
            int maxDrops = 3;
            int otherDrops = totalDropSources - mobGrouping.size();
            
            for (Map.Entry<String, Map<String, List<Integer>>> mobEntry : mobGrouping.entrySet()) {
                if (dropIndex >= maxDrops) break;
                
                String mobType = mobEntry.getKey();
                Map<String, List<Integer>> zoneData = mobEntry.getValue();
                String displayName = formatMobName(mobType);
                
                StringBuilder uiBuilder = new StringBuilder();
                uiBuilder.append("Group {\n");
                uiBuilder.append("  LayoutMode: Left;\n");
                uiBuilder.append("  Anchor: (Height: 30);\n");
                uiBuilder.append("  Padding: (Bottom: 4);\n");
                
                uiBuilder.append("  Label {\n");
                uiBuilder.append("    Text: \"").append(displayName).append("\";\n");
                uiBuilder.append("    Anchor: (Width: 150);\n");
                uiBuilder.append("    Style: (\n");
                uiBuilder.append("      FontSize: 11,\n");
                uiBuilder.append("      TextColor: #ffffff,\n");
                uiBuilder.append("      VerticalAlignment: Center,\n");
                uiBuilder.append("      RenderBold: true\n");
                uiBuilder.append("    );\n");
                uiBuilder.append("  }\n");
                
                uiBuilder.append("  Group {\n");
                uiBuilder.append("    Anchor: (Width: 4);\n");
                uiBuilder.append("  }\n");
                
                uiBuilder.append("  Group {\n");
                uiBuilder.append("    LayoutMode: Left;\n");
                uiBuilder.append("    FlexWeight: 1;\n");
                
                List<Map.Entry<String, List<Integer>>> sortedZones = new ArrayList<>(zoneData.entrySet());
                sortedZones.sort((a, b) -> {
                    String numA = a.getKey().replaceAll("[^0-9]", "");
                    String numB = b.getKey().replaceAll("[^0-9]", "");
                    if (numA.isEmpty()) return 1;
                    if (numB.isEmpty()) return -1;
                    return Integer.compare(Integer.parseInt(numA), Integer.parseInt(numB));
                });
                
                for (Map.Entry<String, List<Integer>> entry : sortedZones) {
                    String zone = entry.getKey();
                    String zoneNumber = zone.replaceAll("[^0-9]", "");
                    String color = getZoneColor(zone);
                    
                    uiBuilder.append("    Group {\n");
                    uiBuilder.append("      Anchor: (Width: 30, Height: 24);\n");
                    uiBuilder.append("      Background: ").append(color).append("(0.9);\n");
                    uiBuilder.append("      LayoutMode: Center;\n");
                    uiBuilder.append("      Label {\n");
                    uiBuilder.append("        Text: \"Z").append(zoneNumber).append("\";\n");
                    uiBuilder.append("        Style: (\n");
                    uiBuilder.append("          FontSize: 10,\n");
                    uiBuilder.append("          TextColor: #ffffff,\n");
                    uiBuilder.append("          HorizontalAlignment: Center,\n");
                    uiBuilder.append("          VerticalAlignment: Center,\n");
                    uiBuilder.append("          RenderBold: true\n");
                    uiBuilder.append("        );\n");
                    uiBuilder.append("      }\n");
                    uiBuilder.append("    }\n");
                    
                    uiBuilder.append("    Group { Anchor: (Width: 3); }\n");
                }
                
                uiBuilder.append("  }\n");
                uiBuilder.append("}\n");
                
                cmd.appendInline(listSelector, uiBuilder.toString());
                dropIndex++;
            }
            
            int remainingMobs = mobGrouping.size() - maxDrops;
            
            if (remainingMobs > 0 || otherDrops > 0) {
                int totalRemaining = (remainingMobs > 0 ? remainingMobs : 0) + otherDrops;
                
                StringBuilder moreBuilder = new StringBuilder();
                moreBuilder.append("Label {\n");
                moreBuilder.append("  Text: \"...and ").append(totalRemaining).append(" other drop");
                if (totalRemaining != 1) {
                    moreBuilder.append("s");
                }
                moreBuilder.append("\";\n");
                moreBuilder.append("  Anchor: (Height: 16);\n");
                moreBuilder.append("  Padding: (Top: 4);\n");
                moreBuilder.append("  Style: (\n");
                moreBuilder.append("    FontSize: 9,\n");
                moreBuilder.append("    TextColor: #888888\n");
                moreBuilder.append("  );\n");
                moreBuilder.append("}\n");
                
                cmd.appendInline(listSelector, moreBuilder.toString());
            }
        } catch (Exception e) {
            System.err.println("[Hytems] Error displaying drops: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private String getZoneColor(String zone) {
        if (zone == null) return "#888888";
        
        String zoneNumber = zone.replaceAll("[^0-9]", "");
        if (zoneNumber.isEmpty()) return "#888888";
        
        int zoneNum = Integer.parseInt(zoneNumber);
        switch (zoneNum) {
            case 1: return "#4CAF50";
            case 2: return "#FFC107";
            case 3: return "#FF9800";
            case 4: return "#F44336";
            default: return "#888888";
        }
    }
    
    private static class ParsedDropSource {
        String mobType;
        String zone;
        Integer tier;
    }
    
    private ParsedDropSource parseDropSource(String dropSourceId) {
        ParsedDropSource result = new ParsedDropSource();
        if (dropSourceId == null) return result;
        
        String name = dropSourceId;
        if (name.contains(":")) {
            name = name.substring(name.indexOf(":") + 1);
        }
        
        Pattern zonePattern = Pattern.compile("(?i)(zone\\d+)\\s*(.+?)\\s*tier(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = zonePattern.matcher(name);
        
        if (matcher.find()) {
            result.zone = matcher.group(1);
            result.mobType = matcher.group(2).trim();
            result.tier = Integer.parseInt(matcher.group(3));
        } else {
            Pattern zoneOnlyPattern = Pattern.compile("(?i)(zone\\d+)\\s*(.+)", Pattern.CASE_INSENSITIVE);
            Matcher zoneOnlyMatcher = zoneOnlyPattern.matcher(name);
            
            if (zoneOnlyMatcher.find()) {
                result.zone = zoneOnlyMatcher.group(1);
                result.mobType = zoneOnlyMatcher.group(2).trim();
            }
        }
        
        return result;
    }
    
    private String formatMobName(String mobType) {
        if (mobType == null) return "Unknown";
        
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (int i = 0; i < mobType.length(); i++) {
            char c = mobType.charAt(i);
            
            if (c == '_' || c == '-') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else if (Character.isUpperCase(c) && i > 0 && !Character.isUpperCase(mobType.charAt(i - 1))) {
                result.append(' ').append(c);
            } else {
                result.append(c);
            }
        }
        
        return result.toString().trim();
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
                    // Continue trying
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
    
    private String getTranslatedName(Item item, String itemId) {
        try {
            if (item == null) return itemId;
            String translationKey = item.getTranslationKey();
            if (translationKey == null || translationKey.isEmpty()) {
                return itemId;
            }
            
            String translated = I18nModule.get().getMessage(this.playerRef.getLanguage(), translationKey);
            if (translated != null && !translated.equals(translationKey)) {
                return translated;
            }
            return itemId;
        } catch (Exception e) {
            return itemId;
        }
    }
}
