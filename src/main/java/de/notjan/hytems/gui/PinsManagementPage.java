package de.notjan.hytems.gui;

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
                    cmd.appendInline("#PinnedItemsList", "Group { Anchor: (Width: 20); }");
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
        boolean isFirst = (index == 0);
        boolean isLast = (index == totalItems - 1);

        StringBuilder uiBuilder = new StringBuilder();
        uiBuilder.append("Group #PinnedCard").append(index).append(" {\n");
        uiBuilder.append("  LayoutMode: Top;\n");
        if (!isFirst) {
            uiBuilder.append("  Anchor: (Width: 240, Left: 10);\n");
        } else {
            uiBuilder.append("  Anchor: (Width: 240);\n");
        }
        uiBuilder.append("  Background: #1a2836(0.85);\n");
        uiBuilder.append("  Padding: (Left: 8, Right: 8, Top: 8, Bottom: 0);\n");
        uiBuilder.append("\n");
        
        uiBuilder.append("  Group {\n");
        uiBuilder.append("    LayoutMode: Center;\n");
        uiBuilder.append("    Anchor: (Height: 80);\n");
        uiBuilder.append("    ItemIcon #ItemIcon {\n");
        uiBuilder.append("      Anchor: (Width: 80, Height: 80);\n");
        uiBuilder.append("      Visible: true;\n");
        uiBuilder.append("    }\n");
        uiBuilder.append("  }\n");
        uiBuilder.append("\n");
        
        uiBuilder.append("  Group {\n");
        uiBuilder.append("    Anchor: (Height: 20);\n");
        uiBuilder.append("  }\n");
        uiBuilder.append("\n");
        
        uiBuilder.append("  Label #ItemName {\n");
        uiBuilder.append("    Anchor: (Height: 20);\n");
        uiBuilder.append("    Style: (\n");
        uiBuilder.append("      FontSize: 15,\n");
        uiBuilder.append("      TextColor: #ffffff,\n");
        uiBuilder.append("      HorizontalAlignment: Center,\n");
        uiBuilder.append("      RenderBold: true\n");
        uiBuilder.append("    );\n");
        uiBuilder.append("  }\n");
        uiBuilder.append("\n");
        
        uiBuilder.append("  Group {\n");
        uiBuilder.append("    Anchor: (Height: 36);\n");
        uiBuilder.append("  }\n");
        uiBuilder.append("\n");
        
        uiBuilder.append("  Group #ButtonRow {\n");
        uiBuilder.append("    LayoutMode: Center;\n");
        uiBuilder.append("    Anchor: (Height: 36);\n");
        uiBuilder.append("  }\n");
        
        uiBuilder.append("}\n");

        cmd.appendInline("#PinnedItemsList", uiBuilder.toString());

        String cardSelector = "#PinnedCard" + index;
        cmd.set(cardSelector + " #ItemIcon.ItemId", itemId);
        cmd.set(cardSelector + " #ItemName.Text", translatedName);

        String buttonRowSelector = cardSelector + " #ButtonRow";
        
        if (!isFirst) {
            cmd.appendInline(buttonRowSelector,
                "Button #MoveLeftBtn" + index + " {\n" +
                "  Anchor: (Width: 50, Height: 36, Right: 8);\n" +
                "  Background: #3d5973;\n" +
                "  Style: ButtonStyle(\n" +
                "    Default: (Background: #3d5973),\n" +
                "    Hovered: (Background: #4d6983),\n" +
                "    Pressed: (Background: #2d4963)\n" +
                "  );\n" +
                "  Label {\n" +
                "    Text: \"<\";\n" +
                "    Style: (\n" +
                "      FontSize: 16,\n" +
                "      TextColor: #ffffff,\n" +
                "      HorizontalAlignment: Center,\n" +
                "      VerticalAlignment: Center\n" +
                "    );\n" +
                "  }\n" +
                "}\n"
            );
            
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#MoveLeftBtn" + index,
                    new EventData()
                            .append("Action", "moveLeft")
                            .append("ItemId", itemId),
                    false
            );
        } else {
            cmd.appendInline(buttonRowSelector, "Group { Anchor: (Width: 58); }");
        }

        cmd.appendInline(buttonRowSelector,
            "Button #DeleteBtn" + index + " {\n" +
            "  Anchor: (Width: 100, Height: 36);\n" +
            "  Background: #c44c4c;\n" +
            "  Style: ButtonStyle(\n" +
            "    Default: (Background: #c44c4c),\n" +
            "    Hovered: (Background: #d45c5c),\n" +
            "    Pressed: (Background: #b43c3c)\n" +
            "  );\n" +
            "  Label {\n" +
            "    Text: \"Remove\";\n" +
            "    Style: (\n" +
            "      FontSize: 14,\n" +
            "      TextColor: #ffffff,\n" +
            "      HorizontalAlignment: Center,\n" +
            "      VerticalAlignment: Center\n" +
            "    );\n" +
            "  }\n" +
            "}\n"
        );
        
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#DeleteBtn" + index,
                new EventData()
                        .append("Action", "delete")
                        .append("ItemId", itemId),
                false
        );
        
        if (!isLast) {
            cmd.appendInline(buttonRowSelector,
                "Button #MoveRightBtn" + index + " {\n" +
                "  Anchor: (Width: 50, Height: 36, Left: 8);\n" +
                "  Background: #3d5973;\n" +
                "  Style: ButtonStyle(\n" +
                "    Default: (Background: #3d5973),\n" +
                "    Hovered: (Background: #4d6983),\n" +
                "    Pressed: (Background: #2d4963)\n" +
                "  );\n" +
                "  Label {\n" +
                "    Text: \">\";\n" +
                "    Style: (\n" +
                "      FontSize: 16,\n" +
                "      TextColor: #ffffff,\n" +
                "      HorizontalAlignment: Center,\n" +
                "      VerticalAlignment: Center\n" +
                "    );\n" +
                "  }\n" +
                "}\n"
            );
            
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#MoveRightBtn" + index,
                    new EventData()
                            .append("Action", "moveRight")
                            .append("ItemId", itemId),
                    false
            );
        } else {
            cmd.appendInline(buttonRowSelector, "Group { Anchor: (Width: 58); }");
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
