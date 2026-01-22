package de.notjan.hytems.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Main browser page for Hytems plugin.
 * Displays a searchable, paginated list of items.
 *
 * @author NotJan
 */
public class HytemsBrowserPage extends InteractiveCustomUIPage<HytemsBrowserPage.BrowserData> {

    private static final int ITEMS_PER_ROW = 7;
    private static final int ROWS_PER_PAGE = 8;
    private static final int ITEMS_PER_PAGE = ITEMS_PER_ROW * ROWS_PER_PAGE; // 56

    private String searchQuery = "";
    private int currentPage = 0;
    private int totalItems = 0;

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

        // Update UI with current state
        updateUI(cmd);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull BrowserData data) {
        super.handleDataEvent(ref, store, data);

        boolean needsUpdate = false;

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

            updateUI(cmd);
            this.sendUpdate(cmd, events, false);
        }
    }

    /**
     * Updates the UI with current state.
     */
    private void updateUI(@Nonnull UICommandBuilder cmd) {
        // Update item count
        cmd.set("#ItemCount.Text", totalItems + " items found");

        // Update pagination
        int totalPages = getTotalPages();
        if (totalPages == 0) {
            totalPages = 1;
        }

        cmd.set("#PageLabel.Text", "Page " + (currentPage + 1) + " / " + totalPages);

        // Show/hide pagination buttons based on available pages
        // Previous button: only visible if not on first page
        cmd.set("#PrevPageButton.Visible", currentPage > 0);

        // Next button: only visible if not on last page
        cmd.set("#NextPageButton.Visible", currentPage < totalPages - 1);
    }




    /**
     * Calculates total pages based on item count.
     */
    private int getTotalPages() {
        if (totalItems == 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);
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
                .build();

        private String searchQuery;
        private String pageAction;
        private String clearSearch;
        private String closeGUI;
    }
}
