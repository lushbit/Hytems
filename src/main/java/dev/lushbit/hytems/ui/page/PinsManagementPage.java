package dev.lushbit.hytems.ui.page;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.lushbit.hytems.HytemsPlugin;
import dev.lushbit.hytems.ui.HytemsUiTemplates;
import dev.lushbit.hytems.ui.ItemUiSupport;

import javax.annotation.Nonnull;
import java.util.List;

public class PinsManagementPage extends InteractiveCustomUIPage<PinsManagementPage.PinsEventData> {

    private final PlayerRef playerRef;
    private final boolean openedFromBrowser;

    public PinsManagementPage(@Nonnull PlayerRef playerRef) {
        this(playerRef, false);
    }

    public PinsManagementPage(@Nonnull PlayerRef playerRef, boolean openedFromBrowser) {
        super(playerRef, CustomPageLifetime.CanDismiss, PinsEventData.CODEC);
        this.playerRef = playerRef;
        this.openedFromBrowser = openedFromBrowser;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append(HytemsUiTemplates.PINS_MANAGEMENT);
        cmd.set("#BackButton.Visible", this.openedFromBrowser);
        cmd.set("#BackButtonSpacer.Visible", this.openedFromBrowser);

        List<String> pinnedItems = HytemsPlugin.playerDataManager.getPinnedItems(playerRef);

        if (pinnedItems.isEmpty()) {
            cmd.set("#EmptyState.Visible", true);
            cmd.set("#PinnedItemsContainer.Visible", false);
        } else {
            cmd.set("#EmptyState.Visible", false);
            cmd.set("#PinnedItemsContainer.Visible", true);

            for (int i = 0; i < pinnedItems.size(); i++) {
                String itemId = pinnedItems.get(i);
                Item item = HytemsPlugin.ITEMS.get(itemId);
                String translatedName = ItemUiSupport.translatedName(playerRef, item, itemId);

                buildPinnedItemCard(cmd, events, itemId, translatedName, i, pinnedItems.size());
            }
        }

        if (this.openedFromBrowser) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#BackButton",
                    EventData.of("Action", "back"),
                    false
            );
        }
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("Action", "close"),
                false
        );
    }

    private void buildPinnedItemCard(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events,
                                     @Nonnull String itemId, @Nonnull String translatedName,
                                     int index, int totalItems) {
        Item item = HytemsPlugin.ITEMS.get(itemId);
        String rarityBg = ItemUiSupport.rarityBackground(item);
        String rarityColor = ItemUiSupport.rarityColor(item);

        boolean isFirst = (index == 0);
        boolean isLast = (index == totalItems - 1);

        cmd.append("#PinnedItemsList", HytemsUiTemplates.PINS_MANAGEMENT_CARD);

        String cardSelector = "#PinnedItemsList[" + index + "]";
        cmd.set(cardSelector + " #IconBackground.Background", rarityBg);
        cmd.set(cardSelector + " #ItemIcon.ItemId", itemId);
        cmd.set(cardSelector + " #ItemName.Text", translatedName);
        cmd.set(cardSelector + " #ItemName.Style.TextColor", rarityColor);

        if (!isFirst) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    cardSelector + " #MoveLeftBtn",
                    new EventData()
                            .append("Action", "moveLeft")
                            .append("ItemId", itemId),
                    false
            );
        } else {
            cmd.set(cardSelector + " #MoveLeftBtn.Visible", false);
        }

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                cardSelector + " #DeleteBtn",
                new EventData()
                        .append("Action", "delete")
                        .append("ItemId", itemId),
                false
        );

        if (!isLast) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    cardSelector + " #MoveRightBtn",
                    new EventData()
                            .append("Action", "moveRight")
                            .append("ItemId", itemId),
                    false
            );
        } else {
            cmd.set(cardSelector + " #MoveRightBtn.Visible", false);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PinsEventData data) {
        String action = data.action;
        String itemId = data.itemId;

        if ("close".equals(action)) {
            this.close();
            return;
        }

        if ("back".equals(action)) {
            if (!this.openedFromBrowser) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().openCustomPage(ref, store, new HytemsBrowserPage(this.playerRef, CustomPageLifetime.CanDismiss));
            }
            return;
        }

        if (itemId == null) {
            return;
        }

        boolean changed = false;

        switch (action) {
            case "delete":
                changed = HytemsPlugin.playerDataManager.removePin(playerRef, itemId);
                break;
            case "moveLeft":
                changed = HytemsPlugin.playerDataManager.movePinUp(playerRef, itemId);
                break;
            case "moveRight":
                changed = HytemsPlugin.playerDataManager.movePinDown(playerRef, itemId);
                break;
        }

        if (changed) {
            HytemsPlugin.pinnedItemsHudManager.registerPlayer(playerRef, store, ref);
            this.rebuild();
        }
    }

    public static class PinsEventData {
        public static final BuilderCodec<PinsEventData> CODEC = BuilderCodec.builder(
                        PinsEventData.class, PinsEventData::new
                )
                .append(new KeyedCodec<>("Action", Codec.STRING),
                        (e, v) -> e.action = v,
                        e -> e.action)
                .add()
                .append(new KeyedCodec<>("ItemId", Codec.STRING),
                        (e, v) -> e.itemId = v,
                        e -> e.itemId)
                .add()
                .build();

        private String action;
        private String itemId;

        public PinsEventData() {
        }
    }
}
