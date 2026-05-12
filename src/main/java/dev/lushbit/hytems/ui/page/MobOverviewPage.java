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
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.lushbit.hytems.HytemsPlugin;
import dev.lushbit.hytems.asset.MobMetadataRegistry;
import dev.lushbit.hytems.asset.MobMetadataRegistry.AttributeRow;
import dev.lushbit.hytems.asset.MobMetadataRegistry.DropEntry;
import dev.lushbit.hytems.asset.MobMetadataRegistry.MobMetadata;
import dev.lushbit.hytems.asset.MobMetadataRegistry.SpawnEntry;
import dev.lushbit.hytems.asset.MobMetadataRegistry.SpawnGroup;
import dev.lushbit.hytems.asset.MobMetadataRegistry.VariantEntry;
import dev.lushbit.hytems.ui.HytemsUiTemplates;
import dev.lushbit.hytems.ui.ItemUiSupport;
import dev.lushbit.hytems.ui.MobPortraitResolver;

import javax.annotation.Nonnull;
import java.util.List;

public class MobOverviewPage extends InteractiveCustomUIPage<MobOverviewPage.MobOverviewData> {
    private static final int ATTRIBUTE_ROW_HEIGHT = 28;
    private static final int ITEM_ROW_HEIGHT = 54;
    private static final int SPAWN_ENTRY_BASE_HEIGHT = 52;
    private static final int SPAWN_ENTRY_TALL_HEIGHT = 64;
    private static final int SPAWN_GROUP_BASE_HEIGHT = 36;

    private final PlayerRef playerRef;
    private final String mobId;

    public MobOverviewPage(@Nonnull PlayerRef playerRef, @Nonnull String mobId) {
        super(playerRef, CustomPageLifetime.CanDismiss, MobOverviewData.CODEC);
        this.playerRef = playerRef;
        this.mobId = mobId;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append(HytemsUiTemplates.MOB_OVERVIEW);
        render(cmd, events);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull MobOverviewData data) {
        if ("close".equals(data.action)) {
            this.close();
            return;
        }

        if (data.itemId == null || data.itemId.isEmpty() || !HytemsPlugin.ITEMS.containsKey(data.itemId)) {
            return;
        }

        HytemsPlugin.playerDataManager.recordSearchHistoryItem(this.playerRef, data.itemId);
        HytemsPlugin.playerDataManager.setLastViewedItem(this.playerRef, data.itemId);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().openCustomPage(ref, store, new HytemsBrowserPage(this.playerRef, CustomPageLifetime.CanDismiss));
        }
    }

    private void render(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        MobMetadata metadata = MobMetadataRegistry.lookup(this.mobId);
        cmd.set("#MobName.Text", metadata.displayName());
        setPortrait(cmd);
        renderAttributes(cmd, metadata.attributes());
        renderDrops(cmd, events, metadata.drops());
        renderVariants(cmd, events, metadata.variants());
        renderSpawns(cmd, metadata.spawnGroups());
        updateSectionHeights(cmd, metadata);
        bindCloseButton(events);
    }

    private void updateSectionHeights(@Nonnull UICommandBuilder cmd, @Nonnull MobMetadata metadata) {
        int dropsRows = metadata.drops().isEmpty() ? 1 : metadata.drops().size();
        int variantRows = metadata.variants().isEmpty() ? 1 : metadata.variants().size();
        int dropsVariantsHeight = 28 + 8 + 24 + (dropsRows * ITEM_ROW_HEIGHT) + 12 + 24 + (variantRows * ITEM_ROW_HEIGHT) + 36;
        setHeight(cmd, "#DropsVariantsSection", Math.max(250, dropsVariantsHeight));

        int spawnHeight = 28 + 8 + 80;
        if (!metadata.spawnGroups().isEmpty()) {
            spawnHeight = 28 + 8;
            for (SpawnGroup group : metadata.spawnGroups()) {
                if (!"Spawn & Habitat".equals(group.title())) {
                    spawnHeight += SPAWN_GROUP_BASE_HEIGHT;
                }
                for (SpawnEntry entry : group.entries()) {
                    int lineCount = Math.max(1, entry.details().size());
                    spawnHeight += SPAWN_ENTRY_BASE_HEIGHT + Math.max(0, lineCount - 1) * 16 + 6;
                }
            }
            spawnHeight += 18;
        }
        setHeight(cmd, "#SpawnSection", Math.max(190, spawnHeight));
    }

    private void bindCloseButton(@Nonnull UIEventBuilder events) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("Action", "close"),
                false
        );
    }

    private void setPortrait(@Nonnull UICommandBuilder cmd) {
        String path = MobPortraitResolver.resolvePortraitPath(this.mobId);
        boolean hasPortrait = path != null && !path.isEmpty();
        cmd.set("#MobPortrait.Visible", true);
        cmd.set("#PortraitFallback.Visible", false);
        cmd.set("#MobPortrait.Background", hasPortrait ? path : MobPortraitResolver.FALLBACK_PORTRAIT_PATH);
    }

    private void renderAttributes(@Nonnull UICommandBuilder cmd, @Nonnull List<AttributeRow> rows) {
        cmd.clear("#AttributesList");
        cmd.set("#NoAttributesLabel.Visible", rows.isEmpty());
        for (int i = 0; i < rows.size(); i++) {
            AttributeRow row = rows.get(i);
            cmd.append("#AttributesList", HytemsUiTemplates.MOB_ATTRIBUTE_ROW);
            String selector = "#AttributesList[" + i + "]";
            cmd.set(selector + " #AttributeLabel.Text", row.label() + ":");
            cmd.set(selector + " #AttributeValue.Text", trim(row.value(), 58));
            setHeight(cmd, selector, ATTRIBUTE_ROW_HEIGHT + Math.max(0, row.value().length() - 34) / 30 * 14);
        }
    }

    private void renderDrops(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nonnull List<DropEntry> drops) {
        cmd.clear("#DropsList");
        cmd.set("#NoDropsLabel.Visible", drops.isEmpty());
        for (int i = 0; i < drops.size(); i++) {
            DropEntry drop = drops.get(i);
            appendItemRow(cmd, events, "#DropsList", i, drop.itemId(), drop.displayName(), drop.quantityLabel(), drop.chanceLabel());
        }
    }

    private void renderVariants(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nonnull List<VariantEntry> variants) {
        cmd.clear("#VariantsList");
        cmd.set("#NoVariantsLabel.Visible", variants.isEmpty());
        for (int i = 0; i < variants.size(); i++) {
            VariantEntry variant = variants.get(i);
            appendItemRow(cmd, events, "#VariantsList", i, variant.itemId(), variant.displayName(), "", "");
        }
    }

    private void appendItemRow(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events,
                               @Nonnull String listSelector, int index, @Nonnull String itemId,
                               @Nonnull String displayName, @Nonnull String quantity, @Nonnull String chance) {
        cmd.append(listSelector, HytemsUiTemplates.MOB_ITEM_ROW);
        String selector = listSelector + "[" + index + "]";
        Item item = HytemsPlugin.ITEMS.get(itemId);
        String friendlyName = friendlyItemName(item, itemId, displayName);
        cmd.set(selector + " #QuantityLabel.Text", quantity);
        cmd.set(selector + " #ChanceLabel.Text", chance);
        cmd.set(selector + " #ItemIcon.ItemId", itemId);
        cmd.set(selector + " #ItemName.Text", trim(friendlyName, 44));
        cmd.set(selector + " #IconBackground.Background", ItemUiSupport.rarityBackground(item));
        setHeight(cmd, selector, friendlyName.length() > 32 ? ITEM_ROW_HEIGHT + 12 : ITEM_ROW_HEIGHT);
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #InteractButton",
                EventData.of("ItemId", itemId),
                false
        );
    }

    private String friendlyItemName(Item item, String itemId, String fallback) {
        String translated = ItemUiSupport.translatedName(this.playerRef, item, itemId);
        if (translated != null && !translated.isEmpty() && !translated.equals(itemId)) {
            return translated;
        }
        return fallback == null || fallback.isEmpty() ? itemId : fallback;
    }

    private void renderSpawns(@Nonnull UICommandBuilder cmd, @Nonnull List<SpawnGroup> groups) {
        cmd.clear("#SpawnGroups");
        cmd.set("#NoSpawnLabel.Visible", groups.isEmpty());
        int groupIndex = 0;
        for (SpawnGroup group : groups) {
            if ("Spawn & Habitat".equals(group.title())) {
                for (int entryIndex = 0; entryIndex < group.entries().size(); entryIndex++) {
                    appendSpawnEntry(cmd, "#SpawnGroups", entryIndex, group.entries().get(entryIndex));
                }
                continue;
            }
            cmd.append("#SpawnGroups", HytemsUiTemplates.MOB_SPAWN_GROUP);
            String groupSelector = "#SpawnGroups[" + groupIndex + "]";
            cmd.set(groupSelector + " #GroupTitle.Text", group.title());
            int height = SPAWN_GROUP_BASE_HEIGHT;
            for (int entryIndex = 0; entryIndex < group.entries().size(); entryIndex++) {
                SpawnEntry entry = group.entries().get(entryIndex);
                int entryHeight = appendSpawnEntry(cmd, groupSelector + " #Entries", entryIndex, entry);
                height += entryHeight + 6;
            }
            setHeight(cmd, groupSelector, height);
            groupIndex++;
        }
    }

    private int appendSpawnEntry(@Nonnull UICommandBuilder cmd, @Nonnull String listSelector, int index,
                                 @Nonnull SpawnEntry entry) {
        cmd.append(listSelector, HytemsUiTemplates.MOB_SPAWN_ENTRY);
        String entrySelector = listSelector + "[" + index + "]";
        List<String> detailLines = entry.details();
        String details = String.join("\n", detailLines);
        int lineCount = Math.max(1, details.isEmpty() ? 1 : details.split("\n", -1).length);
        int detailsHeight = isElementalCircleDetails(detailLines)
                ? 20 + Math.max(0, lineCount - 1) * 12
                : 20 + Math.max(0, lineCount - 1) * 16;
        int entryHeight = SPAWN_ENTRY_BASE_HEIGHT + Math.max(0, detailsHeight - 20);
        if (details.length() > 110) {
            entryHeight += 10;
        }
        cmd.set(entrySelector + " #EntryTitle.Text", trim(entry.title(), 64));
        cmd.set(entrySelector + " #EntryTitle.Visible", true);
        cmd.set(entrySelector + " #EntryDetails.Text", trim(details, 260));
        cmd.set(entrySelector + " #EntryDetails.Visible", !details.isEmpty());
        setHeight(cmd, entrySelector + " #EntryDetails", detailsHeight);
        setHeight(cmd, entrySelector, entryHeight);
        return entryHeight;
    }

    private boolean isElementalCircleDetails(@Nonnull List<String> detailLines) {
        return !detailLines.isEmpty() && detailLines.stream().allMatch(line -> line.startsWith("Elemental Circle Tier "));
    }

    private void setHeight(@Nonnull UICommandBuilder cmd, @Nonnull String selector, int height) {
        Anchor anchor = new Anchor();
        anchor.setHeight(Value.of(height));
        cmd.setObject(selector + ".Anchor", anchor);
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    public static class MobOverviewData {
        public static final BuilderCodec<MobOverviewData> CODEC = BuilderCodec.builder(
                        MobOverviewData.class,
                        MobOverviewData::new
                )
                .addField(
                        new KeyedCodec<>("ItemId", Codec.STRING),
                        (data, value) -> data.itemId = value,
                        data -> data.itemId
                )
                .addField(
                        new KeyedCodec<>("Action", Codec.STRING),
                        (data, value) -> data.action = value,
                        data -> data.action
                )
                .build();

        private String itemId;
        private String action;
    }
}
