package de.notjan.hytems.ui;

import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public final class ItemUiSupport {
    public static final String ICON_PIN_EMPTY = "hytems/textures/unpinned.png";
    public static final String ICON_PIN_FILLED = "hytems/textures/pinned.png";
    public static final String ICON_STAR_EMPTY = "hytems/textures/star.png";
    public static final String ICON_STAR_FILLED = "hytems/textures/star_filled.png";
    public static final String RARITY_DEFAULT_BACKGROUND = "hytems/textures/rarity_default.png";

    private ItemUiSupport() {
    }

    public static String translatedName(@Nonnull PlayerRef playerRef, Item item, @Nonnull String itemId) {
        try {
            if (item == null || item.getTranslationKey() == null || item.getTranslationKey().isEmpty()) {
                return itemId;
            }

            String translated = I18nModule.get().getMessage(playerRef.getLanguage(), item.getTranslationKey());
            return translated != null && !translated.equals(item.getTranslationKey()) ? translated : itemId;
        } catch (Exception e) {
            return itemId;
        }
    }

    public static String rarityBackground(Item item) {
        ItemQuality quality = itemQuality(item);
        if (quality != null && quality.getSlotTexture() != null) {
            String texture = quality.getSlotTexture();
            if (texture.contains("SlotCommon")) return "hytems/textures/rarity_common.png";
            if (texture.contains("SlotUncommon")) return "hytems/textures/rarity_uncommon.png";
            if (texture.contains("SlotRare")) return "hytems/textures/rarity_rare.png";
            if (texture.contains("SlotEpic")) return "hytems/textures/rarity_epic.png";
            if (texture.contains("SlotLegendary")) return "hytems/textures/rarity_legendary.png";
        }
        return RARITY_DEFAULT_BACKGROUND;
    }

    public static String rarityColor(Item item) {
        ItemQuality quality = itemQuality(item);
        if (quality != null && quality.getTextColor() != null) {
            Color color = quality.getTextColor();
            return String.format("#%02x%02x%02x", color.red & 0xFF, color.green & 0xFF, color.blue & 0xFF);
        }
        return "#ffffff";
    }

    public static void setBinaryIconState(@Nonnull UICommandBuilder cmd, @Nonnull String emptySelector,
                                          @Nonnull String filledSelector, boolean filled) {
        cmd.set(emptySelector + ".Visible", !filled);
        cmd.set(filledSelector + ".Visible", filled);
    }

    public static void setButtonIcon(@Nonnull UICommandBuilder cmd, @Nonnull String selector, @Nonnull String iconPath) {
        cmd.set(selector + ".Style.Default.Background", iconPath);
        cmd.set(selector + ".Style.Hovered.Background", iconPath);
        cmd.set(selector + ".Style.Pressed.Background", iconPath);
    }

    public static void setButtonIconHoverOnly(@Nonnull UICommandBuilder cmd, @Nonnull String selector, @Nonnull String iconPath) {
        cmd.set(selector + ".Style.Hovered.Background", iconPath);
        cmd.set(selector + ".Style.Pressed.Background", iconPath);
    }

    private static ItemQuality itemQuality(Item item) {
        if (item == null) return null;
        try {
            return ItemQuality.getAssetMap().getAsset(item.getQualityIndex());
        } catch (Exception e) {
            return null;
        }
    }
}
