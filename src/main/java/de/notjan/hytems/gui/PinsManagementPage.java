package de.notjan.hytems.gui;

import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
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
import java.util.List;

public class PinsManagementPage extends InteractiveCustomUIPage<PinsManagementPage.PinsEventData> {

    private final PlayerRef playerRef;

    public PinsManagementPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, PinsEventData.CODEC);
        this.playerRef = playerRef;
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
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append("hytems/PinsManagement.ui");

        List<String> pinnedItems = HytemsPlugin.pinnedItemsManager.getPinnedItems(playerRef);

        if (pinnedItems.isEmpty()) {
            cmd.set("#EmptyState.Visible", true);
            cmd.set("#PinnedItemsContainer.Visible", false);
        } else {
            cmd.set("#EmptyState.Visible", false);
            cmd.set("#PinnedItemsContainer.Visible", true);

            for (int i = 0; i < pinnedItems.size(); i++) {
                String itemId = pinnedItems.get(i);
                Item item = HytemsPlugin.ITEMS.get(itemId);
                String translatedName = getTranslatedName(item, itemId);

                buildPinnedItemCard(cmd, events, itemId, translatedName, i, pinnedItems.size());

                if (i < pinnedItems.size() - 1) {
                    cmd.appendInline("#PinnedItemsList", "Group { Anchor: (Width: 30); }");
                }
            }
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
        String rarityBg = getRarityBackground(item);
        String rarityColor = getRarityColor(item);

        boolean isFirst = (index == 0);
        boolean isLast = (index == totalItems - 1);

        int childIndex = index * 2;
        cmd.append("#PinnedItemsList", HytemsUiTemplates.PINS_MANAGEMENT_CARD);

        String cardSelector = "#PinnedItemsList[" + childIndex + "]";
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

        if (itemId == null) {
            return;
        }

        boolean changed = false;

        switch (action) {
            case "delete":
                changed = HytemsPlugin.pinnedItemsManager.removePin(playerRef, itemId);
                break;
            case "moveLeft":
                changed = HytemsPlugin.pinnedItemsManager.movePinUp(playerRef, itemId);
                break;
            case "moveRight":
                changed = HytemsPlugin.pinnedItemsManager.movePinDown(playerRef, itemId);
                break;
        }

        if (changed) {
            HytemsPlugin.pinnedItemsHudManager.registerPlayer(playerRef, store, ref);
            this.rebuild();
        }
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
