package de.notjan.hytems.gui;

import com.buuz135.mhud.MultipleHUD;
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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HytemsBrowserPage extends InteractiveCustomUIPage<HytemsBrowserPage.BrowserData> {

    private static final int ITEMS_PER_ROW = 7;
    private static final int ROWS_PER_PAGE = 8;
    private static final int ITEMS_PER_PAGE = ITEMS_PER_ROW * ROWS_PER_PAGE;
    private static final String DETAIL_HUD_ID = "hytems_detail_panel";

    private String searchQuery = "";
    private int currentPage = 0;
    private String selectedItemId = null;
    private String dropsItemId = null;
    private List<Map.Entry<String, Item>> filteredItems = new ArrayList<>();
    private ItemDetailHud detailHud;
    private Ref<EntityStore> pageRef;
    private Store<EntityStore> pageStore;

    public HytemsBrowserPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, BrowserData.CODEC);
        this.detailHud = new ItemDetailHud(playerRef);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        this.pageRef = ref;
        this.pageStore = store;

        cmd.append("hytems/ItemBrowser.ui");
        cmd.set("#SearchInput.Value", this.searchQuery);
        updateSearchInputColor(cmd);
        cmd.set("#DropPanelContainer.Visible", false);

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

        filterItems();
        renderItems(cmd, events);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull BrowserData data) {
        super.handleDataEvent(ref, store, data);
        boolean needsUpdate = false;

        if (data.showDrops != null && !data.showDrops.isEmpty()) {
            hideDetailPanel(ref, store);
            this.selectedItemId = null;
            this.dropsItemId = data.showDrops;
            needsUpdate = true;
        }

        if (data.selectedItem != null && !data.selectedItem.isEmpty()) {
            this.selectedItemId = data.selectedItem;
            showDetailPanel(ref, store);
            return;
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
            System.out.println("[Hytems] Close button pressed - hiding HUD");
            hideDetailPanel(ref, store);
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
            updateSearchInputColor(cmd);

            if (this.dropsItemId != null) {
                renderDropsPanel(cmd);
            } else {
                cmd.set("#DropPanelContainer.Visible", false);
            }

            this.sendUpdate(cmd, events, false);
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        System.out.println("[Hytems] onDismiss called - hiding HUD");
        hideDetailPanel(ref, store);
        super.onDismiss(ref, store);
    }

    private void showDetailPanel(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        try {
            Player player = (Player) store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                System.err.println("[Hytems] Error: Could not get player component");
                return;
            }

            this.dropsItemId = null;
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#DropPanelContainer.Visible", false);
            this.sendUpdate(cmd, new UIEventBuilder(), false);

            detailHud.setItemId(this.selectedItemId);
            detailHud.show();
            MultipleHUD.getInstance().setCustomHud(player, this.playerRef, DETAIL_HUD_ID, detailHud);
            System.out.println("[Hytems] Showing detail HUD for: " + this.selectedItemId);
        } catch (Exception e) {
            System.err.println("[Hytems] Error showing detail panel: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void hideDetailPanel(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                System.err.println("[Hytems] hideDetailPanel: Could not get player component");
                return;
            }

            MultipleHUD.getInstance().hideCustomHud(player, DETAIL_HUD_ID);
            System.out.println("[Hytems] HUD hidden successfully");
        } catch (Exception e) {
            System.err.println("[Hytems] Error hiding detail panel: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void renderDropsPanel(@Nonnull UICommandBuilder cmd) {
        if (dropsItemId == null || dropsItemId.isEmpty()) {
            cmd.set("#DropPanelContainer.Visible", false);
            return;
        }

        cmd.set("#DropPanelContainer.Visible", true);

        try {
            Item item = HytemsPlugin.ITEMS.get(dropsItemId);
            String translatedName = getTranslatedName(item, dropsItemId);

            cmd.set("#DropDetailItemIcon.ItemId", dropsItemId);
            cmd.set("#DropDetailItemName.Text", translatedName);
            cmd.set("#DropDetailItemId.Text", dropsItemId);

            List<String> dropSources = HytemsPlugin.dropListRegistry.getDropSourcesForItem(dropsItemId);

            if (dropSources == null || dropSources.isEmpty()) {
                cmd.set("#NoDropsContainer.Visible", true);
                cmd.set("#DropsContent.Visible", false);
            } else {
                cmd.set("#NoDropsContainer.Visible", false);
                cmd.set("#DropsContent.Visible", true);
                displayDropSources(cmd, dropSources);
            }

        } catch (Exception e) {
            System.err.println("[Hytems] Error rendering drops panel: " + e.getMessage());
            e.printStackTrace();
            cmd.set("#NoDropsContainer.Visible", true);
            cmd.set("#DropsContent.Visible", false);
        }
    }

    private void displayDropSources(@Nonnull UICommandBuilder cmd, @Nonnull List<String> dropSources) {
        try {
            cmd.clear("#DropSourcesList");

            Map<String, Map<String, List<Integer>>> mobGrouping = new LinkedHashMap<>();
            Map<String, Map<String, List<String>>> cropGrouping = new LinkedHashMap<>();
            List<String> otherSources = new ArrayList<>();

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
                    otherSources.add(dropSourceId);
                }
            }

            int index = 0;

            for (Map.Entry<String, Map<String, List<Integer>>> mobEntry : mobGrouping.entrySet()) {
                String mobType = mobEntry.getKey();
                Map<String, List<Integer>> zoneData = mobEntry.getValue();
                String displayName = formatMobName(mobType);

                if (zoneData.size() >= 2) {
                    index = addDropSourceRowMultiZone(cmd, index, displayName, zoneData);
                } else {
                    String zoneInfo = formatZoneInfo(zoneData);
                    index = addDropSourceRow(cmd, index, displayName, zoneInfo);
                }
            }

            for (Map.Entry<String, Map<String, List<String>>> cropEntry : cropGrouping.entrySet()) {
                String cropType = cropEntry.getKey();
                Map<String, List<String>> zoneData = cropEntry.getValue();
                String displayName = formatCropName(cropType);

                if (zoneData.size() >= 2) {
                    index = addCropSourceRowMultiZone(cmd, index, displayName, zoneData);
                } else {
                    String zoneInfo = formatCropZoneInfo(zoneData);
                    index = addDropSourceRow(cmd, index, displayName, zoneInfo);
                }
            }

            for (String source : otherSources) {
                String displayName = formatDropSourceName(source);
                index = addDropSourceRow(cmd, index, displayName, "");
            }

        } catch (Exception e) {
            System.err.println("[Hytems] Error displaying drop sources: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int addDropSourceRow(UICommandBuilder cmd, int index, String displayName, String zoneInfo) {
        StringBuilder uiBuilder = new StringBuilder();
        uiBuilder.append("Group {\n");
        uiBuilder.append("  LayoutMode: Top;\n");
        uiBuilder.append("  Anchor: (Height: ").append(zoneInfo.isEmpty() ? "40" : "55").append(");\n");
        uiBuilder.append("  Padding: (Bottom: 8, Full: 10);\n");
        uiBuilder.append("  Background: #1e1e1e(0.8);\n");

        uiBuilder.append("  Label {\n");
        uiBuilder.append("    Text: \"\";\n");
        uiBuilder.append("    Anchor: (Height: 24);\n");
        uiBuilder.append("    Style: (\n");
        uiBuilder.append("      FontSize: 14,\n");
        uiBuilder.append("      TextColor: #ffffff,\n");
        uiBuilder.append("      VerticalAlignment: Center,\n");
        uiBuilder.append("      RenderBold: true\n");
        uiBuilder.append("    );\n");
        uiBuilder.append("  }\n");

        if (!zoneInfo.isEmpty()) {
            uiBuilder.append("  Label {\n");
            uiBuilder.append("    Text: \"\";\n");
            uiBuilder.append("    Anchor: (Height: 18);\n");
            uiBuilder.append("    Style: (\n");
            uiBuilder.append("      FontSize: 11,\n");
            uiBuilder.append("      TextColor: #888888,\n");
            uiBuilder.append("      VerticalAlignment: Center\n");
            uiBuilder.append("    );\n");
            uiBuilder.append("  }\n");
        }

        uiBuilder.append("}\n");

        cmd.appendInline("#DropSourcesList", uiBuilder.toString());

        String rowSelector = "#DropSourcesList[" + index + "]";
        cmd.set(rowSelector + "[0].Text", displayName);

        if (!zoneInfo.isEmpty()) {
            cmd.set(rowSelector + "[1].Text", zoneInfo);
        }

        return index + 1;
    }

    private int addDropSourceRowMultiZone(UICommandBuilder cmd, int index, String displayName, Map<String, List<Integer>> zoneData) {
        int numZones = zoneData.size();
        int height = 30 + (numZones * 18);

        StringBuilder uiBuilder = new StringBuilder();
        uiBuilder.append("Group {\n");
        uiBuilder.append("  LayoutMode: Top;\n");
        uiBuilder.append("  Anchor: (Height: ").append(height).append(");\n");
        uiBuilder.append("  Padding: (Bottom: 8, Full: 10);\n");
        uiBuilder.append("  Background: #1e1e1e(0.8);\n");

        uiBuilder.append("  Label {\n");
        uiBuilder.append("    Text: \"\";\n");
        uiBuilder.append("    Anchor: (Height: 24);\n");
        uiBuilder.append("    Style: (\n");
        uiBuilder.append("      FontSize: 14,\n");
        uiBuilder.append("      TextColor: #ffffff,\n");
        uiBuilder.append("      VerticalAlignment: Center,\n");
        uiBuilder.append("      RenderBold: true\n");
        uiBuilder.append("    );\n");
        uiBuilder.append("  }\n");

        for (int i = 0; i < numZones; i++) {
            uiBuilder.append("  Label {\n");
            uiBuilder.append("    Text: \"\";\n");
            uiBuilder.append("    Anchor: (Height: 16);\n");
            uiBuilder.append("    Padding: (Left: 10);\n");
            uiBuilder.append("    Style: (\n");
            uiBuilder.append("      FontSize: 11,\n");
            uiBuilder.append("      TextColor: #888888,\n");
            uiBuilder.append("      VerticalAlignment: Center\n");
            uiBuilder.append("    );\n");
            uiBuilder.append("  }\n");
        }

        uiBuilder.append("}\n");

        cmd.appendInline("#DropSourcesList", uiBuilder.toString());

        String rowSelector = "#DropSourcesList[" + index + "]";
        cmd.set(rowSelector + "[0].Text", displayName);

        int labelIndex = 1;
        for (Map.Entry<String, List<Integer>> entry : zoneData.entrySet()) {
            String zone = entry.getKey();
            List<Integer> tiers = entry.getValue();
            Collections.sort(tiers);

            String zoneName = formatZoneName(zone);
            String tierRange = formatTierRange(tiers);

            String zoneText = !tierRange.isEmpty() ? zoneName + ": " + tierRange : zoneName;
            cmd.set(rowSelector + "[" + labelIndex + "].Text", zoneText);
            labelIndex++;
        }

        return index + 1;
    }

    private int addCropSourceRowMultiZone(UICommandBuilder cmd, int index, String displayName, Map<String, List<String>> zoneData) {
        int numZones = zoneData.size();
        int height = 30 + (numZones * 18);

        StringBuilder uiBuilder = new StringBuilder();
        uiBuilder.append("Group {\n");
        uiBuilder.append("  LayoutMode: Top;\n");
        uiBuilder.append("  Anchor: (Height: ").append(height).append(");\n");
        uiBuilder.append("  Padding: (Bottom: 8, Full: 10);\n");
        uiBuilder.append("  Background: #1e1e1e(0.8);\n");

        uiBuilder.append("  Label {\n");
        uiBuilder.append("    Text: \"\";\n");
        uiBuilder.append("    Anchor: (Height: 24);\n");
        uiBuilder.append("    Style: (\n");
        uiBuilder.append("      FontSize: 14,\n");
        uiBuilder.append("      TextColor: #ffffff,\n");
        uiBuilder.append("      VerticalAlignment: Center,\n");
        uiBuilder.append("      RenderBold: true\n");
        uiBuilder.append("    );\n");
        uiBuilder.append("  }\n");

        for (int i = 0; i < numZones; i++) {
            uiBuilder.append("  Label {\n");
            uiBuilder.append("    Text: \"\";\n");
            uiBuilder.append("    Anchor: (Height: 16);\n");
            uiBuilder.append("    Padding: (Left: 10);\n");
            uiBuilder.append("    Style: (\n");
            uiBuilder.append("      FontSize: 11,\n");
            uiBuilder.append("      TextColor: #888888,\n");
            uiBuilder.append("      VerticalAlignment: Center\n");
            uiBuilder.append("    );\n");
            uiBuilder.append("  }\n");
        }

        uiBuilder.append("}\n");

        cmd.appendInline("#DropSourcesList", uiBuilder.toString());

        String rowSelector = "#DropSourcesList[" + index + "]";
        cmd.set(rowSelector + "[0].Text", displayName);

        int labelIndex = 1;
        for (Map.Entry<String, List<String>> entry : zoneData.entrySet()) {
            String zone = entry.getKey();
            List<String> stages = entry.getValue();

            String zoneName = zone.equals("Unknown") ? "Unknown" : zone;
            String stageInfo = formatStageInfo(stages);

            String zoneText = !stageInfo.isEmpty() ? zoneName + ": " + stageInfo : zoneName;
            cmd.set(rowSelector + "[" + labelIndex + "].Text", zoneText);
            labelIndex++;
        }

        return index + 1;
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

    private String formatZoneInfo(Map<String, List<Integer>> zoneData) {
        List<String> zoneParts = new ArrayList<>();

        for (Map.Entry<String, List<Integer>> entry : zoneData.entrySet()) {
            String zone = entry.getKey();
            List<Integer> tiers = entry.getValue();
            Collections.sort(tiers);

            String zoneName = formatZoneName(zone);
            String tierRange = formatTierRange(tiers);

            if (!tierRange.isEmpty()) {
                zoneParts.add(zoneName + ": " + tierRange);
            } else {
                zoneParts.add(zoneName);
            }
        }

        return String.join(", ", zoneParts);
    }

    private String formatCropZoneInfo(Map<String, List<String>> zoneData) {
        List<String> zoneParts = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : zoneData.entrySet()) {
            String zone = entry.getKey();
            List<String> stages = entry.getValue();

            String stageInfo = formatStageInfo(stages);
            zoneParts.add(zone + ": " + stageInfo);
        }

        return String.join(", ", zoneParts);
    }

    private String formatStageInfo(List<String> stages) {
        if (stages == null || stages.isEmpty()) return "";

        Set<String> uniqueStages = new LinkedHashSet<>(stages);
        if (uniqueStages.size() == 1) {
            return "Stage " + stages.get(0);
        }

        return "Stages: " + String.join(", ", uniqueStages);
    }

    private String formatZoneName(String zone) {
        if (zone == null || zone.equalsIgnoreCase("Unknown")) return "Unknown Zone";

        Pattern pattern = Pattern.compile("(?i)zone(\\d+)");
        Matcher matcher = pattern.matcher(zone);

        if (matcher.find()) {
            return "Zone " + matcher.group(1);
        }

        return zone;
    }

    private String formatTierRange(List<Integer> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return "";
        }

        Set<Integer> uniqueTiers = new TreeSet<>(tiers);
        List<Integer> sortedTiers = new ArrayList<>(uniqueTiers);

        if (sortedTiers.size() == 1) {
            return "Tier " + sortedTiers.get(0);
        } else {
            int min = sortedTiers.get(0);
            int max = sortedTiers.get(sortedTiers.size() - 1);
            return "Tier " + min + "-" + max;
        }
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

        return result.toString();
    }

    private String formatCropName(String cropType) {
        if (cropType == null) return "Unknown Crop";

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (int i = 0; i < cropType.length(); i++) {
            char c = cropType.charAt(i);

            if (c == '_' || c == '-') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString() + " Crop";
    }

    private String formatDropSourceName(String dropSourceId) {
        if (dropSourceId == null) return "Unknown";

        String name = dropSourceId;
        if (name.contains(":")) {
            name = name.substring(name.indexOf(":") + 1);
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);

            if (c == '_' || c == '-') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else if (Character.isUpperCase(c) && i > 0 && !Character.isUpperCase(name.charAt(i - 1))) {
                result.append(' ').append(c);
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    private void filterItems() {
        Map<String, Item> allItems = HytemsPlugin.ITEMS;
        Map<String, Item> nonTodoItems = allItems.entrySet().stream()
                .filter(entry -> !isFromTodoBench(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (searchQuery.isEmpty()) {
            filteredItems = nonTodoItems.entrySet().stream()
                    .sorted((e1, e2) -> {
                        String name1 = getTranslatedName(e1.getValue(), e1.getKey());
                        String name2 = getTranslatedName(e2.getValue(), e2.getKey());
                        return name1.compareToIgnoreCase(name2);
                    })
                    .collect(Collectors.toList());
        } else {
            if (searchQuery.startsWith("@")) {
                String queryAfterAt = searchQuery.substring(1).trim();
                String category;
                String additionalSearch = "";

                int spaceIndex = queryAfterAt.indexOf(' ');
                if (spaceIndex > 0) {
                    category = queryAfterAt.substring(0, spaceIndex).toLowerCase(Locale.ENGLISH);
                    additionalSearch = queryAfterAt.substring(spaceIndex + 1).trim().toLowerCase(Locale.ENGLISH);
                } else {
                    category = queryAfterAt.toLowerCase(Locale.ENGLISH);
                }

                final String finalAdditionalSearch = additionalSearch;
                filteredItems = nonTodoItems.entrySet().stream()
                        .filter(entry -> {
                            if (!matchesCategory(entry.getValue(), entry.getKey(), category)) {
                                return false;
                            }

                            if (!finalAdditionalSearch.isEmpty()) {
                                Item item = entry.getValue();
                                if (item == null) return false;
                                String translatedName = getTranslatedName(item, entry.getKey());
                                return entry.getKey().toLowerCase(Locale.ENGLISH).contains(finalAdditionalSearch) ||
                                        translatedName.toLowerCase(Locale.ENGLISH).contains(finalAdditionalSearch);
                            }

                            return true;
                        })
                        .sorted((e1, e2) -> {
                            String name1 = getTranslatedName(e1.getValue(), e1.getKey());
                            String name2 = getTranslatedName(e2.getValue(), e2.getKey());
                            return name1.compareToIgnoreCase(name2);
                        })
                        .collect(Collectors.toList());
            } else {
                String lowerQuery = searchQuery.toLowerCase(Locale.ENGLISH);
                filteredItems = nonTodoItems.entrySet().stream()
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
    }

    private boolean isFromTodoBench(String itemId) {
        try {
            List<CraftingRecipe> recipes = HytemsPlugin.recipeManager.getCraftingRecipes(itemId);
            if (recipes == null || recipes.isEmpty()) {
                return false;
            }

            for (CraftingRecipe recipe : recipes) {
                BenchRequirement[] benchReqs = recipe.getBenchRequirement();
                if (benchReqs != null) {
                    for (BenchRequirement bench : benchReqs) {
                        if (bench != null && bench.id != null &&
                                bench.id.toLowerCase(Locale.ENGLISH).contains("todo")) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
        return false;
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

            boolean isValid = isValidCategory(category);
            if (isValid) {
                cmd.set("#SearchInput.Style.TextColor", "#00cc00");
            } else {
                cmd.set("#SearchInput.Style.TextColor", "#cc0000");
            }
        } else {
            cmd.set("#SearchInput.Style.TextColor", "#ffffff");
        }
    }

    private boolean isValidCategory(String category) {
        Set<String> validCategories = new HashSet<>(Arrays.asList(
                "weapon", "weapons", "tool", "tools", "armor", "armour",
                "block", "blocks", "food", "consumable", "consumables",
                "material", "materials", "resource", "resources",
                "furniture", "craftable", "ingredient", "ingredients"
        ));
        return validCategories.contains(category);
    }

    private boolean matchesCategory(Item item, String itemId, String category) {
        if (item == null) return false;

        try {
            switch (category) {
                case "weapon":
                case "weapons":
                    return hasComponent(item, "Weapon") || itemId.contains("Sword") ||
                            itemId.contains("Bow") || itemId.contains("Staff") ||
                            itemId.contains("Axe") || itemId.contains("Dagger");

                case "tool":
                case "tools":
                    return hasComponent(item, "Tool") || itemId.contains("Pickaxe") ||
                            itemId.contains("Hoe") || itemId.contains("Shovel");

                case "armor":
                case "armour":
                    return hasComponent(item, "Armor") || itemId.contains("Helmet") ||
                            itemId.contains("Chestplate") || itemId.contains("Leggings") ||
                            itemId.contains("Boots");

                case "block":
                case "blocks":
                    return hasComponent(item, "Block") || isBlock(item);

                case "food":
                case "consumable":
                case "consumables":
                    return hasComponent(item, "Consumable") || itemId.contains("Food") ||
                            itemId.contains("Potion") || itemId.contains("Ingredient");

                case "material":
                case "materials":
                case "resource":
                case "resources":
                    return itemId.contains("Ingot") || itemId.contains("Ore") ||
                            itemId.contains("Wood") || itemId.contains("Stone") ||
                            itemId.contains("Plank") || itemId.contains("Bar");

                case "furniture":
                    return itemId.contains("Chair") || itemId.contains("Table") ||
                            itemId.contains("Bed") || itemId.contains("Torch");

                case "craftable":
                    return HytemsPlugin.recipeManager.getCraftingRecipes(itemId) != null &&
                            !HytemsPlugin.recipeManager.getCraftingRecipes(itemId).isEmpty() &&
                            !isFromTodoBench(itemId);

                case "ingredient":
                case "ingredients":
                    return itemId.contains("Ingredient");

                default:
                    return itemId.toLowerCase(Locale.ENGLISH).contains(category) ||
                            hasComponent(item, category);
            }
        } catch (Exception e) {
            System.err.println("[Hytems] Error checking category for " + itemId + ": " + e.getMessage());
            return false;
        }
    }

    private boolean isBlock(Item item) {
        try {
            Method getItemTypeMethod = Item.class.getMethod("getItemType");
            Object itemType = getItemTypeMethod.invoke(item);
            if (itemType != null) {
                String typeName = itemType.toString().toLowerCase(Locale.ENGLISH);
                return typeName.contains("block");
            }
        } catch (Exception e) {
        }
        return false;
    }

    private boolean hasComponent(Item item, String componentName) {
        if (item == null || componentName == null || componentName.isEmpty()) {
            return false;
        }

        try {
            if (componentName.equalsIgnoreCase("Weapon")) {
                Method method = Item.class.getMethod("getWeapon");
                Object result = method.invoke(item);
                if (result != null) return true;
            }

            if (componentName.equalsIgnoreCase("Tool")) {
                Method method = Item.class.getMethod("getTool");
                Object result = method.invoke(item);
                if (result != null) return true;
            }

            if (componentName.equalsIgnoreCase("Armor")) {
                Method method = Item.class.getMethod("getArmor");
                Object result = method.invoke(item);
                if (result != null) return true;
            }

            if (componentName.equalsIgnoreCase("Consumable")) {
                Method method = Item.class.getMethod("getConsumable");
                Object result = method.invoke(item);
                if (result != null) return true;
            }

            if (componentName.equalsIgnoreCase("Block")) {
                Method method = Item.class.getMethod("getBlock");
                Object result = method.invoke(item);
                if (result != null) return true;
            }

            Method hasComponentMethod = Item.class.getMethod("hasComponent", String.class);
            Object result = hasComponentMethod.invoke(item, componentName);
            if (result instanceof Boolean && (Boolean) result) {
                return true;
            }

            Method getComponentMethod = Item.class.getMethod("getComponent", String.class);
            Object component = getComponentMethod.invoke(item, componentName);
            if (component != null) {
                return true;
            }

            Method getItemTypeMethod = Item.class.getMethod("getItemType");
            Object itemType = getItemTypeMethod.invoke(item);
            if (itemType != null) {
                String typeStr = itemType.toString().toLowerCase(Locale.ENGLISH);
                if (typeStr.contains(componentName.toLowerCase(Locale.ENGLISH))) {
                    return true;
                }
            }

        } catch (Exception e) {
        }

        return false;
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
                                "  Style: ButtonStyle(\n" +
                                "    Default: (Background: #2a2a2a(0.7)),\n" +
                                "    Hovered: (Background: #3a3a3a(0.85)),\n" +
                                "    Pressed: (Background: #4a4a4a(0.9))\n" +
                                "  );\n" +
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

                events.addEventBinding(
                        CustomUIEventBindingType.RightClicking,
                        selector,
                        EventData.of("ShowDrops", itemId),
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
                        new KeyedCodec<>("ShowDrops", Codec.STRING),
                        (data, value) -> data.showDrops = value,
                        data -> data.showDrops
                ).build();

        private String searchQuery;
        private String pageAction;
        private String clearSearch;
        private String closeGUI;
        private String selectedItem;
        private String showDrops;
    }
}
