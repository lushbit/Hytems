package de.notjan.hytems.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import de.notjan.hytems.HytemsPlugin;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main browser page for Hytems plugin.
 * Displays a searchable, paginated list of items with clickable buttons.
 *
 * @author NotJan
 */
public class HytemsBrowserPage extends InteractiveCustomUIPage<HytemsBrowserPage.BrowserData> {

    private static final int ITEMS_PER_ROW = 7;
    private static final int ROWS_PER_PAGE = 8;
    private static final int ITEMS_PER_PAGE = ITEMS_PER_ROW * ROWS_PER_PAGE; // 56

    private String searchQuery = "";
    private int currentPage = 0;
    private List<Map.Entry<String, Item>> filteredItems = new ArrayList<>();

    public HytemsBrowserPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, BrowserData.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        // Load the main UI file
        cmd.append("hytems/ItemBrowser.ui");

        // Set initial search value
        cmd.set("#SearchInput.Value", this.searchQuery);

        // Bind events
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

        // Filter and render items
        filterItems();
        renderItems(cmd, events);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull BrowserData data) {
        super.handleDataEvent(ref, store, data);

        boolean needsUpdate = false;

        // Handle item clicks - output to console
        if (data.selectedItem != null && !data.selectedItem.isEmpty()) {
            handleItemClick(data.selectedItem);
            return; // Don't update UI for clicks
        }

        // Handle search query changes
        if (data.searchQuery != null && !data.searchQuery.equals(this.searchQuery)) {
            this.searchQuery = data.searchQuery.trim();
            this.currentPage = 0; // Reset to first page on new search
            needsUpdate = true;
        }

        // Handle clear search
        if (data.clearSearch != null && "true".equals(data.clearSearch)) {
            this.searchQuery = "";
            this.currentPage = 0;
            needsUpdate = true;
        }

        // Handle page navigation
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

        // Handle close
        if (data.closeGUI != null && "true".equals(data.closeGUI)) {
            this.close();
            return;
        }

        // Send UI update if needed
        if (needsUpdate) {
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();

            // Update search field if cleared
            if (data.clearSearch != null) {
                cmd.set("#SearchInput.Value", "");
            }

            filterItems();
            renderItems(cmd, events);
            this.sendUpdate(cmd, events, false);
        }
    }

    /**
     * Handles item click events - outputs name to console.
     * TODO: Later open item detail page here
     */
    private void handleItemClick(String itemId) {
        Item item = HytemsPlugin.ITEMS.get(itemId);
        String translatedName = getTranslatedName(item, itemId);

        System.out.println("===== ITEM CLICKED =====");
        System.out.println("Item ID: " + itemId);
        System.out.println("Item Name: " + translatedName);
        System.out.println("========================");

        // TODO: Open detail page
        // new HytemsDetailPage(playerRef, itemId, CustomPageLifetime.CanDismiss).open();
    }

    /**
     * Filters items based on search query.
     */
    private void filterItems() {
        Map<String, Item> allItems = HytemsPlugin.ITEMS;

        if (searchQuery.isEmpty()) {
            // Show all items sorted alphabetically by translated name
            filteredItems = allItems.entrySet().stream()
                    .sorted((e1, e2) -> {
                        String name1 = getTranslatedName(e1.getValue(), e1.getKey());
                        String name2 = getTranslatedName(e2.getValue(), e2.getKey());
                        return name1.compareToIgnoreCase(name2);
                    })
                    .collect(Collectors.toList());
        } else {
            // Filter items by search query and sort alphabetically
            String lowerQuery = searchQuery.toLowerCase(Locale.ENGLISH);
            filteredItems = allItems.entrySet().stream()
                    .filter(entry -> {
                        Item item = entry.getValue();
                        if (item == null) return false;

                        // Get translated name
                        String translatedName = getTranslatedName(item, entry.getKey());

                        // Search in both ID and translated name
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

    /**
     * Gets the translated name for an item, falling back to the item ID.
     */
    private String getTranslatedName(Item item, String itemId) {
        if (item == null) return itemId;
        String translatedName = I18nModule.get()
                .getMessage(this.playerRef.getLanguage(), item.getTranslationKey());
        return translatedName != null ? translatedName : itemId;
    }

    /**
     */
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

                // Create new row if needed
                if (col == 0) {
                    cmd.appendInline("#ItemGrid",
                            "Group {\n" +
                                    "  Anchor: (Height: 109);\n" +
                                    "  LayoutMode: Left;\n" +
                                    "}\n"
                    );
                }

                // Append button to current row
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

                // Set item data - col is the index within THIS row
                String selector = "#ItemGrid[" + row + "][" + col + "]";
                cmd.set(selector + " #ItemIcon.ItemId", itemId);
                cmd.set(selector + " #ItemName.Text", translatedName);

                // Bind click event (THIS WORKS with inline buttons!)
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        selector,
                        EventData.of("SelectedItem", itemId),
                        false
                );

                col++;
                if (col >= ITEMS_PER_ROW) {
                    col = 0;  // Reset column for new row
                    row++;
                }
            }
        }

        updateUI(cmd);
    }

    /**
     * Updates the UI with current state.
     */
    private void updateUI(@Nonnull UICommandBuilder cmd) {
        // Update item count
        cmd.set("#ItemCount.Text", filteredItems.size() + " items found");

        // Update pagination
        int totalPages = getTotalPages();
        if (totalPages == 0) {
            totalPages = 1;
        }

        cmd.set("#PageLabel.Text", "Page " + (currentPage + 1) + " / " + totalPages);

        // Show/hide pagination buttons
        cmd.set("#PrevPageButton.Visible", currentPage > 0);
        cmd.set("#NextPageButton.Visible", currentPage < totalPages - 1);
    }

    /**
     * Calculates total pages based on item count.
     */
    private int getTotalPages() {
        if (filteredItems.isEmpty()) {
            return 1;
        }
        return (int) Math.ceil((double) filteredItems.size() / ITEMS_PER_PAGE);
    }

    /**
     * Data class for handling UI events.
     */
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
                .build();

        private String searchQuery;
        private String pageAction;
        private String clearSearch;
        private String closeGUI;
        private String selectedItem;
    }
}
