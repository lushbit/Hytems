package de.notjan.hytems.gui;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import de.notjan.hytems.HytemsPlugin;

import javax.annotation.Nonnull;
import java.util.List;

public class PinnedItemsHud extends CustomUIHud {
    
    private final PinnedItemsManager pinnedItemsManager;
    private final PlayerRef playerRef;
    
    public PinnedItemsHud(@Nonnull PlayerRef playerRef, @Nonnull PinnedItemsManager pinnedItemsManager) {
        super(playerRef);
        this.playerRef = playerRef;
        this.pinnedItemsManager = pinnedItemsManager;
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
            Item item = HytemsPlugin.ITEMS.get(itemId);
            String translatedName = getTranslatedName(item, itemId);
            
            StringBuilder uiBuilder = new StringBuilder();
            uiBuilder.append("Group #PinnedItem").append(index).append(" {\n");
            uiBuilder.append("  LayoutMode: Left;\n");
            uiBuilder.append("  Anchor: (Width: 280, Height: 44);\n");
            uiBuilder.append("  Background: #1e1e1e(0.6);\n");
            uiBuilder.append("  Padding: (Full: 8);\n");
            uiBuilder.append("\n");
            uiBuilder.append("  ItemIcon #ItemIcon {\n");
            uiBuilder.append("    Anchor: (Width: 28, Height: 28);\n");
            uiBuilder.append("    Visible: true;\n");
            uiBuilder.append("  }\n");
            uiBuilder.append("\n");
            uiBuilder.append("  Group {\n");
            uiBuilder.append("    Anchor: (Width: 6);\n");
            uiBuilder.append("  }\n");
            uiBuilder.append("\n");
            uiBuilder.append("  Label #ItemName {\n");
            uiBuilder.append("    FlexWeight: 1;\n");
            uiBuilder.append("    Style: (\n");
            uiBuilder.append("      FontSize: 12,\n");
            uiBuilder.append("      TextColor: #ffffff,\n");
            uiBuilder.append("      VerticalAlignment: Center\n");
            uiBuilder.append("    );\n");
            uiBuilder.append("  }\n");
            uiBuilder.append("}\n");
            
            cmd.appendInline("#PinnedItemsList", uiBuilder.toString());
            
            String selector = "#PinnedItem" + index;
            cmd.set(selector + " #ItemIcon.ItemId", itemId);
            cmd.set(selector + " #ItemName.Text", translatedName);
            
            if (index < pinnedItems.size() - 1) {
                cmd.appendInline("#PinnedItemsList", "Group {\n  Anchor: (Height: 6);\n}\n");
            }
            
            index++;
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
}
