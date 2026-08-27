package dev.lushbit.hytems.ui.page;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.lushbit.hytems.asset.MobMetadataRegistry;
import dev.lushbit.hytems.ui.HytemsUiTemplates;
import dev.lushbit.hytems.ui.MobPortraitResolver;
import dev.lushbit.hytems.ui.TextFormatters;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MobBrowserPage extends InteractiveCustomUIPage<MobBrowserPage.MobBrowserData> {
    private static final int MOBS_PER_ROW = 8;
    private static final int ROWS_PER_PAGE = 7;
    private static final int MOBS_PER_PAGE = MOBS_PER_ROW * ROWS_PER_PAGE;
    private static final int GRID_LABEL_MAX_CHARS = 22;

    private final PlayerRef playerRef;
    private final boolean openedFromBrowser;
    private String searchQuery = "";
    private int currentPage = 0;
    private List<String> filteredMobs = new ArrayList<>();

    public MobBrowserPage(@Nonnull PlayerRef playerRef) {
        this(playerRef, false);
    }

    public MobBrowserPage(@Nonnull PlayerRef playerRef, boolean openedFromBrowser) {
        super(playerRef, CustomPageLifetime.CanDismiss, MobBrowserData.CODEC);
        this.playerRef = playerRef;
        this.openedFromBrowser = openedFromBrowser;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append(HytemsUiTemplates.MOB_BROWSER);
        cmd.set("#SearchInput.Value", this.searchQuery);
        cmd.set("#BackButton.Visible", this.openedFromBrowser);
        cmd.set("#BackButtonSpacer.Visible", this.openedFromBrowser);

        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchInput",
                EventData.of("@SearchQuery", "#SearchInput.Value"),
                false
        );
        if (this.openedFromBrowser) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#BackButton",
                    EventData.of("NavAction", "menu"),
                    false
            );
        }
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("NavAction", "close"),
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

        filterMobs();
        clampCurrentPage();
        renderMobs(cmd, events);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull MobBrowserData data) {
        boolean needsUpdate = false;

        if (data.searchQuery != null && !data.searchQuery.equals(this.searchQuery)) {
            this.searchQuery = data.searchQuery.trim();
            this.currentPage = 0;
            filterMobs();
            needsUpdate = true;
        }

        if (data.pageAction != null) {
            int totalPages = getTotalPages();
            if ("prev".equals(data.pageAction)) {
                this.currentPage = Math.floorMod(this.currentPage - 1, totalPages);
                needsUpdate = true;
            } else if ("next".equals(data.pageAction)) {
                this.currentPage = Math.floorMod(this.currentPage + 1, totalPages);
                needsUpdate = true;
            }
        }

        if ("menu".equals(data.navAction)) {
            if (!this.openedFromBrowser) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().openCustomPage(ref, store, new HytemsNavigationPage(this.playerRef));
            }
            return;
        }

        if ("close".equals(data.navAction)) {
            this.close();
            return;
        }

        if (data.selectedMob != null && !data.selectedMob.isEmpty()) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().openCustomPage(ref, store, new MobOverviewPage(this.playerRef, data.selectedMob));
            }
            return;
        }

        if (needsUpdate) {
            clampCurrentPage();
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            renderMobs(cmd, events);
            this.sendUpdate(cmd, events, false);
        }
    }

    private void filterMobs() {
        String query = normalizeSearch(this.searchQuery);
        List<String> allMobs = MobMetadataRegistry.knownMobIds();
        if (query.isEmpty()) {
            this.filteredMobs = allMobs;
            return;
        }

        List<String> matches = new ArrayList<>();
        for (String mobId : allMobs) {
            String name = TextFormatters.mobName(mobId);
            if (normalizeSearch(mobId).contains(query) || normalizeSearch(name).contains(query)) {
                matches.add(mobId);
            }
        }
        this.filteredMobs = matches;
    }

    private void renderMobs(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        cmd.clear("#MobGrid");
        cmd.append("#MobGrid", HytemsUiTemplates.MOB_GRID);

        int startIndex = this.currentPage * MOBS_PER_PAGE;
        int endIndex = Math.min(startIndex + MOBS_PER_PAGE, this.filteredMobs.size());
        boolean hasMobs = !this.filteredMobs.isEmpty();
        cmd.set("#MobGrid[0] #NoMobsRow.Visible", !hasMobs);

        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            cmd.set("#MobGrid[0] #MobRow" + row + ".Visible", hasMobs);
            String rowSelector = "#MobGrid[0] #MobRow" + row + "Columns";
            for (int col = 0; col < MOBS_PER_ROW; col++) {
                int mobIndex = startIndex + (row * MOBS_PER_ROW) + col;
                if (mobIndex >= endIndex) {
                    break;
                }
                renderMobCard(cmd, events, rowSelector, col, this.filteredMobs.get(mobIndex));
            }
        }

        updatePagination(cmd);
    }

    private void renderMobCard(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events,
                               @Nonnull String rowSelector, int index, @Nonnull String mobId) {
        cmd.append(rowSelector, HytemsUiTemplates.MOB_CARD);
        String selector = rowSelector + "[" + index + "]";
        String portraitPath = MobPortraitResolver.resolvePortraitPath(mobId);
        boolean hasPortrait = portraitPath != null && !portraitPath.isEmpty();
        cmd.set(selector + " #MobName.Text", shortenGridLabel(TextFormatters.mobName(mobId)));
        cmd.set(selector + " #CardBackground.Background", "hytems/textures/rarity_default.png");
        cmd.set(selector + " #MobPortrait.Visible", true);
        cmd.set(selector + " #MobPortrait.Background", hasPortrait ? portraitPath : MobPortraitResolver.FALLBACK_PORTRAIT_PATH);
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #InteractButton",
                EventData.of("SelectedMob", mobId),
                false
        );
        MobMetadataRegistry.preload(mobId);
    }

    private void updatePagination(@Nonnull UICommandBuilder cmd) {
        int totalPages = getTotalPages();
        cmd.set("#PageLabel.Text", "Page " + (this.currentPage + 1) + " / " + totalPages);
        cmd.set("#MobCountLabel.Text", this.filteredMobs.size() + " mobs found");
        cmd.set("#PrevPageButton.Visible", true);
        cmd.set("#NextPageButton.Visible", true);
    }

    private int getTotalPages() {
        if (this.filteredMobs.isEmpty()) {
            return 1;
        }
        return (int) Math.ceil((double) this.filteredMobs.size() / MOBS_PER_PAGE);
    }

    private void clampCurrentPage() {
        int totalPages = getTotalPages();
        if (this.currentPage < 0) {
            this.currentPage = 0;
        } else if (this.currentPage > totalPages - 1) {
            this.currentPage = Math.max(0, totalPages - 1);
        }
    }

    private String shortenGridLabel(@Nonnull String name) {
        if (name.length() <= GRID_LABEL_MAX_CHARS) {
            return name;
        }
        return name.substring(0, GRID_LABEL_MAX_CHARS - 3) + "...";
    }

    private String normalizeSearch(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]", "");
    }

    public static class MobBrowserData {
        public static final BuilderCodec<MobBrowserData> CODEC = BuilderCodec.builder(
                        MobBrowserData.class,
                        MobBrowserData::new
                )
                .append(
                        new KeyedCodec<>("@SearchQuery", Codec.STRING),
                        (data, value) -> data.searchQuery = value,
                        data -> data.searchQuery
                ).add()
                .append(
                        new KeyedCodec<>("PageAction", Codec.STRING),
                        (data, value) -> data.pageAction = value,
                        data -> data.pageAction
                ).add()
                .append(
                        new KeyedCodec<>("NavAction", Codec.STRING),
                        (data, value) -> data.navAction = value,
                        data -> data.navAction
                ).add()
                .append(
                        new KeyedCodec<>("SelectedMob", Codec.STRING),
                        (data, value) -> data.selectedMob = value,
                        data -> data.selectedMob
                ).add()
                .build();

        private String searchQuery;
        private String pageAction;
        private String navAction;
        private String selectedMob;
    }
}
