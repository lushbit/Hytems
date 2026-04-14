package de.notjan.hytems.gui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import de.notjan.hytems.HytemsPlugin;
import de.notjan.hytems.util.PinnedItemsManager;

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
    protected void build(@Nonnull UICommandBuilder cmd) {
        cmd.append("hytems/PinnedItems.ui");
        updatePinnedItems(cmd);
    }
    
    public void updatePinnedItems(@Nonnull UICommandBuilder cmd) {
        List<String> pinnedItems = pinnedItemsManager.getPinnedItems(playerRef);
        
        cmd.clear("#PinnedItemsList");

        for (int itemIndex = 0; itemIndex < pinnedItems.size(); itemIndex++) {
            String itemId = pinnedItems.get(itemIndex);
            int childIndex = itemIndex * 2;
            buildPinnedItemBox(cmd, itemId, childIndex);

            if (itemIndex < pinnedItems.size() - 1) {
                cmd.appendInline("#PinnedItemsList", "Group { Anchor: (Height: 6); }");
            }
        }
    }
    
    private void buildPinnedItemBox(@Nonnull UICommandBuilder cmd, @Nonnull String itemId, int index) {
        Item item = HytemsPlugin.ITEMS.get(itemId);
        String translatedName = getTranslatedName(item, itemId);
        
        String rarityBg = getRarityBackground(item);
        String rarityColor = getRarityColor(item);

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
            displayDrops(cmd, selector + " #DropsList", dropSources);
        }
    }
    
    private void displayRecipeIngredients(@Nonnull UICommandBuilder cmd, @Nonnull String listSelector, @Nonnull CraftingRecipe recipe) {
        try {
            List<MaterialQuantity> ingredients = getRecipeInputs(recipe);
            if (ingredients == null || ingredients.isEmpty() || ingredients.size() > 4) {
                return;
            }
            
            Map<String, Integer> playerInventory = PinnedItemsInventoryTracker.scanPlayerInventory(store, ref);
            
            for (int i = 0; i < ingredients.size(); i++) {
                MaterialQuantity ingredient = ingredients.get(i);
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
                cmd.append(listSelector, HytemsUiTemplates.PINNED_HUD_INGREDIENT_ENTRY);
                
                String rowSelector = listSelector + "[" + i + "]";
                
                if (ingredientId != null) {
                    Item ingredientItem = HytemsPlugin.ITEMS.get(ingredientId);
                    cmd.set(rowSelector + " #IconBackground.Background", getRarityBackground(ingredientItem));
                    cmd.set(rowSelector + " #ItemIcon.ItemId", ingredientId);
                    cmd.set(rowSelector + " #ItemIcon.Visible", true);

                    String ingredientName = getTranslatedName(ingredientItem, ingredientId);
                    cmd.set(rowSelector + " #IngredientName.Text", ingredientName);
                    
                    String countText = inventoryCount + "/" + quantity;
                    cmd.set(rowSelector + " #IngredientCount.Text", countText);
                    cmd.set(rowSelector + " #IngredientCount.Style.TextColor", countColor);
                } else if (resourceTypeId != null) {
                    try {
                        ResourceType resourceType = (ResourceType) ResourceType.getAssetMap().getAsset(resourceTypeId);
                        if (resourceType != null) {
                            cmd.set(rowSelector + " #ResourceIcon.AssetPath", resourceType.getIcon());
                            cmd.set(rowSelector + " #ResourceIcon.Visible", true);
                            
                            String resourceTypeName = formatResourceTypeName(resourceTypeId);
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
    
    private void displayDrops(@Nonnull UICommandBuilder cmd, @Nonnull String listSelector, @Nonnull List<String> dropSources) {
        try {
            Map<String, Map<String, List<Integer>>> mobGrouping = new LinkedHashMap<>();
            Map<String, Map<String, List<String>>> cropGrouping = new LinkedHashMap<>();
            int otherSourcesCount = 0;
            
            for (String dropSourceId : dropSources) {
                ParsedDropSource parsed = parseDropSource(dropSourceId);
                
                if (parsed.mobType != null) {
                    mobGrouping.computeIfAbsent(parsed.mobType, k -> new LinkedHashMap<>())
                            .computeIfAbsent(parsed.zone != null ? parsed.zone : "Unknown", k -> new ArrayList<>())
                            .add(parsed.tier);
                } else if (parsed.cropType != null) {
                    cropGrouping.computeIfAbsent(parsed.cropType, k -> new LinkedHashMap<>())
                            .computeIfAbsent(parsed.cropZone != null ? parsed.cropZone : "Unknown", k -> new ArrayList<>())
                            .add(parsed.cropStage);
                } else {
                    otherSourcesCount++;
                }
            }
            
            int dropIndex = 0;
            int maxDrops = 3;
            
            for (Map.Entry<String, Map<String, List<Integer>>> mobEntry : mobGrouping.entrySet()) {
                if (dropIndex >= maxDrops) break;
                
                String mobType = mobEntry.getKey();
                Map<String, List<Integer>> zoneData = mobEntry.getValue();
                String displayName = formatMobName(mobType);
                
                cmd.append(listSelector, HytemsUiTemplates.PINNED_HUD_DROP_ROW);
                String rowSelector = listSelector + "[" + dropIndex + "]";
                String badgesSelector = rowSelector + " #ZoneBadges";
                cmd.set(rowSelector + " #SourceName.Text", displayName);

                List<Map.Entry<String, List<Integer>>> sortedZones = new ArrayList<>(zoneData.entrySet());
                sortedZones.sort((a, b) -> {
                    String numA = a.getKey().replaceAll("[^0-9]", "");
                    String numB = b.getKey().replaceAll("[^0-9]", "");
                    if (numA.isEmpty()) return 1;
                    if (numB.isEmpty()) return -1;
                    return Integer.compare(Integer.parseInt(numA), Integer.parseInt(numB));
                });
                
                int badgeIndex = 0;
                for (Map.Entry<String, List<Integer>> entry : sortedZones) {
                    String zone = entry.getKey();
                    String zoneNumber = zone.replaceAll("[^0-9]", "");
                    cmd.append(badgesSelector, HytemsUiTemplates.PINNED_HUD_ZONE_BADGE);
                    String badgeSelector = badgesSelector + "[" + badgeIndex + "]";
                    configureZoneBadge(cmd, badgeSelector, zone, "Z" + zoneNumber);
                    badgeIndex++;

                    cmd.appendInline(badgesSelector, "Group { Anchor: (Width: 2); }");
                    badgeIndex++;
                }
                dropIndex++;
            }
            
            int totalDropSources = mobGrouping.size() + cropGrouping.size() + otherSourcesCount;
            int remainingDrops = totalDropSources - dropIndex;
            
            if (remainingDrops > 0) {
                cmd.appendInline(listSelector,
                        "Label {\n" +
                                "  Text: \"\";\n" +
                                "  Anchor: (Height: 16);\n" +
                                "  Padding: (Top: 4);\n" +
                                "  Style: (\n" +
                                "    FontSize: 12,\n" +
                                "    TextColor: #888888\n" +
                                "  );\n" +
                                "}"
                );
                String suffix = remainingDrops == 1 ? "" : "s";
                cmd.set(listSelector + "[" + dropIndex + "].Text", "... and " + remainingDrops + " other drop" + suffix + " (/h)");
            }
        } catch (Exception e) {
            System.err.println("[Hytems] Error displaying drops: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void configureZoneBadge(@Nonnull UICommandBuilder cmd, @Nonnull String badgeSelector,
                                    @Nonnull String zone, @Nonnull String label) {
        int zoneNumber = parseZoneNumber(zone);
        cmd.set(badgeSelector + " #BgDefault.Visible", zoneNumber < 1 || zoneNumber > 4);
        cmd.set(badgeSelector + " #BgZone1.Visible", zoneNumber == 1);
        cmd.set(badgeSelector + " #BgZone2.Visible", zoneNumber == 2);
        cmd.set(badgeSelector + " #BgZone3.Visible", zoneNumber == 3);
        cmd.set(badgeSelector + " #BgZone4.Visible", zoneNumber == 4);
        cmd.set(badgeSelector + " #ZoneLabel.Text", label);
    }

    private int parseZoneNumber(@Nonnull String zone) {
        String zoneDigits = zone.replaceAll("[^0-9]", "");
        if (zoneDigits.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(zoneDigits);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
    
    private static class ParsedDropSource {
        String mobType;
        String zone;
        Integer tier;
        String cropType;
        String cropZone;
        String cropStage;
    }
    
    private ParsedDropSource parseDropSource(String dropSourceId) {
        ParsedDropSource result = new ParsedDropSource();
        if (dropSourceId == null) return result;
        
        String name = dropSourceId;
        if (name.contains(":")) {
            name = name.substring(name.indexOf(":") + 1);
        }
        
        Pattern cropPattern = Pattern.compile("(?i)drops?_?plant_?crop_(.+?)_(eternal_)?stage(.+)", Pattern.CASE_INSENSITIVE);
        Matcher cropMatcher = cropPattern.matcher(name);
        
        if (cropMatcher.find()) {
            result.cropType = cropMatcher.group(1);
            result.cropStage = cropMatcher.group(3);
            result.cropZone = "Eternal";
            return result;
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
