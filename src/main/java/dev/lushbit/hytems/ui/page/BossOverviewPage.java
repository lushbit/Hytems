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
import dev.lushbit.hytems.asset.BossRegistry;
import dev.lushbit.hytems.asset.BossRegistry.BossAttack;
import dev.lushbit.hytems.asset.BossRegistry.BossInfo;
import dev.lushbit.hytems.asset.BossRegistry.BossPhase;
import dev.lushbit.hytems.asset.BossRegistry.BossVariant;
import dev.lushbit.hytems.asset.MobMetadataRegistry;
import dev.lushbit.hytems.asset.MobMetadataRegistry.AttributeRow;
import dev.lushbit.hytems.asset.MobMetadataRegistry.DropEntry;
import dev.lushbit.hytems.asset.MobMetadataRegistry.SpawnEntry;
import dev.lushbit.hytems.asset.MobMetadataRegistry.SpawnGroup;
import dev.lushbit.hytems.ui.BossText;
import dev.lushbit.hytems.ui.HytemsUiTemplates;
import dev.lushbit.hytems.ui.ItemUiSupport;
import dev.lushbit.hytems.ui.MobPortraitResolver;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Phase-by-phase overview of a boss fight, opened from the mob browser or a boss drop source. */
public class BossOverviewPage extends InteractiveCustomUIPage<BossOverviewPage.BossOverviewData> {
    private static final int ITEM_ROW_HEIGHT = 54;
    private static final int ATTACK_ROW_HEIGHT = 22;
    private static final int INFO_ROW_HEIGHT = 28;
    private static final int SPAWN_ENTRY_BASE_HEIGHT = 52;
    private static final int SPAWN_GROUP_BASE_HEIGHT = 36;
    // Rough characters per line for wrapped labels in the right panel and the left description.
    private static final int PHASE_TEXT_CHARS_PER_LINE = 112;
    private static final int ATTACK_TEXT_CHARS_PER_LINE = 92;
    private static final int DESCRIPTION_CHARS_PER_LINE = 40;
    private static final String NORMAL_ACCENT = "#7a9cc6";
    private static final String TRANSITION_ACCENT = "#5a6a7a";
    private static final String HARD_ACCENT = "#c0503a";

    private final PlayerRef playerRef;
    private final String mobId;
    private final MobBrowserPage.State browserState;
    private int variantIndex;

    public BossOverviewPage(@Nonnull PlayerRef playerRef, @Nonnull String mobId, MobBrowserPage.State browserState) {
        super(playerRef, CustomPageLifetime.CanDismiss, BossOverviewData.CODEC);
        this.playerRef = playerRef;
        this.mobId = mobId;
        this.browserState = browserState;
    }

    /** Opens the boss page for bosses and the regular mob overview for everything else. */
    public static InteractiveCustomUIPage<?> forMob(@Nonnull PlayerRef playerRef, @Nonnull String mobId) {
        return forMob(playerRef, mobId, null);
    }

    public static InteractiveCustomUIPage<?> forMob(@Nonnull PlayerRef playerRef, @Nonnull String mobId,
                                                    MobBrowserPage.State browserState) {
        return BossRegistry.forMob(mobId) != null
                ? new BossOverviewPage(playerRef, mobId, browserState)
                : new MobOverviewPage(playerRef, mobId, browserState);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append(HytemsUiTemplates.BOSS_OVERVIEW);
        BossInfo boss = BossRegistry.forMob(this.mobId);
        if (boss == null) {
            cmd.set("#BossName.Text", "Unknown boss");
            bindButtons(events);
            return;
        }
        render(cmd, events, boss);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull BossOverviewData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if ("close".equals(data.action)) {
            this.close();
            return;
        }
        if ("back".equals(data.action)) {
            if (player != null) player.getPageManager().openCustomPage(ref, store, new MobBrowserPage(this.playerRef, true, this.browserState));
            return;
        }
        if (data.action != null && data.action.startsWith("variant:")) {
            BossInfo boss = BossRegistry.forMob(this.mobId);
            if (boss == null) return;
            try {
                this.variantIndex = Math.floorMod(Integer.parseInt(data.action.substring("variant:".length())), boss.variants().size());
            } catch (NumberFormatException ignored) {
                return;
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            renderDifficulty(cmd, events, boss);
            renderPhases(cmd, boss, boss.variants().get(this.variantIndex));
            renderAttributes(cmd, boss.variants().get(this.variantIndex));
            this.sendUpdate(cmd, events, false);
            return;
        }

        if (data.itemId == null || data.itemId.isEmpty() || !HytemsPlugin.ITEMS.containsKey(data.itemId)) return;
        HytemsPlugin.playerDataManager.recordSearchHistoryItem(this.playerRef, data.itemId);
        HytemsPlugin.playerDataManager.setLastViewedItem(this.playerRef, data.itemId);
        if (player != null) {
            player.getPageManager().openCustomPage(ref, store, new HytemsBrowserPage(this.playerRef, CustomPageLifetime.CanDismiss, true));
        }
    }

    private void render(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nonnull BossInfo boss) {
        String portrait = MobPortraitResolver.resolvePortraitPath(boss.representativeMobId());
        cmd.set("#BossPortrait.Background", portrait != null ? portrait : MobPortraitResolver.FALLBACK_PORTRAIT_PATH);
        cmd.set("#BossName.Text", BossText.name(BossText.language(this.playerRef), boss));

        List<SpawnGroup> spawnGroups = addArenaLocation(boss, collectSpawns(boss));
        renderInfo(cmd, boss, spawnGroups);
        String description = BossText.description(this.playerRef, boss);
        cmd.set("#BossDescription.Text", description == null ? "" : description);
        setHeight(cmd, "#BossDescription", Math.max(20, lines(description, DESCRIPTION_CHARS_PER_LINE) * 16 + 4));

        renderDifficulty(cmd, events, boss);
        renderPhases(cmd, boss, boss.variants().get(this.variantIndex));
        renderAttributes(cmd, boss.variants().get(this.variantIndex));
        renderDrops(cmd, events, boss);
        renderSpawns(cmd, spawnGroups);
        bindButtons(events);
    }

    private void renderInfo(@Nonnull UICommandBuilder cmd, @Nonnull BossInfo boss, @Nonnull List<SpawnGroup> spawnGroups) {
        List<String[]> rows = new ArrayList<>();
        String location = BossText.location(this.playerRef, boss);
        if (location == null) location = locationFromSpawns(spawnGroups);
        if (location != null) rows.add(new String[]{"Location", location});
        rows.add(new String[]{"Phases", Integer.toString(boss.variants().get(0).phases().size())});
        if (boss.variants().size() > 1) rows.add(new String[]{"Difficulties", boss.variants().size() + ""});

        cmd.clear("#InfoList");
        for (int i = 0; i < rows.size(); i++) {
            cmd.append("#InfoList", HytemsUiTemplates.MOB_ATTRIBUTE_ROW);
            String selector = "#InfoList[" + i + "]";
            cmd.set(selector + " #AttributeLabel.Text", rows.get(i)[0] + ":");
            cmd.set(selector + " #AttributeValue.Text", rows.get(i)[1]);
            setHeight(cmd, selector, INFO_ROW_HEIGHT + Math.max(0, rows.get(i)[1].length() - 26) / 24 * 14);
        }
    }

    /** Spawn entry titles are locations ("The Deepworks"); use them when the boss has no location text. */
    private String locationFromSpawns(@Nonnull List<SpawnGroup> spawnGroups) {
        Set<String> locations = new LinkedHashSet<>();
        for (SpawnGroup group : spawnGroups) {
            if (!MobMetadataRegistry.PLACED_SPAWNS_TITLE.equals(group.title())) continue;
            for (SpawnEntry entry : group.entries()) locations.add(entry.title());
        }
        return locations.isEmpty() ? null : String.join(", ", locations);
    }

    private void renderDifficulty(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nonnull BossInfo boss) {
        boolean hasChoice = boss.variants().size() > 1;
        cmd.set("#DifficultyRow.Visible", hasChoice);
        setHeight(cmd, "#DifficultyRow", hasChoice ? 44 : 0);
        setHeight(cmd, "#DifficultySpacer", hasChoice ? 10 : 0);
        if (!hasChoice) return;

        int hardIndex = -1;
        for (int i = 0; i < boss.variants().size(); i++) if (boss.variants().get(i).hardMode()) hardIndex = i;
        int normalIndex = hardIndex == 0 ? 1 : 0;
        if (hardIndex < 0) hardIndex = 1;
        boolean hardSelected = this.variantIndex == hardIndex;
        cmd.set("#NormalButtonActive.Visible", !hardSelected);
        cmd.set("#NormalButton.Visible", hardSelected);
        cmd.set("#HardButtonActive.Visible", hardSelected);
        cmd.set("#HardButton.Visible", !hardSelected);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NormalButton",
                EventData.of("Action", "variant:" + normalIndex), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#HardButton",
                EventData.of("Action", "variant:" + hardIndex), false);
    }

    private void renderPhases(@Nonnull UICommandBuilder cmd, @Nonnull BossInfo boss, @Nonnull BossVariant variant) {
        cmd.clear("#PhasesList");
        int sectionHeight = 28 + 8;
        int fightNumber = 0;
        int index = 0;
        for (BossPhase phase : variant.phases()) {
            cmd.append("#PhasesList", HytemsUiTemplates.BOSS_PHASE);
            String selector = "#PhasesList[" + index + "]";
            String accent = phase.invulnerable() ? TRANSITION_ACCENT : variant.hardMode() ? HARD_ACCENT : NORMAL_ACCENT;
            cmd.set(selector + " #PhaseAccent.Background", accent);
            cmd.set(selector + " #PhaseIndex.Text", phase.invulnerable() ? "" : Integer.toString(++fightNumber));
            cmd.set(selector + " #PhaseName.Text", BossText.phaseName(this.playerRef, boss, phase));
            cmd.set(selector + " #PhaseWindow.Text", phase.invulnerable() ? "Invulnerable"
                    : phase.healthWindow() != null ? phase.healthWindow() : "");

            String trigger = phase.trigger() != null ? "Starts: " + phase.trigger() : "";
            cmd.set(selector + " #PhaseTrigger.Text", trigger);
            cmd.set(selector + " #PhaseTrigger.Visible", !trigger.isEmpty());
            int height = 16 + 24 + (trigger.isEmpty() ? 0 : 18);

            String description = BossText.phaseDescription(this.playerRef, boss, phase);
            boolean hasDescription = description != null && !description.isEmpty();
            cmd.set(selector + " #PhaseDescription.Text", hasDescription ? description : "");
            cmd.set(selector + " #PhaseDescription.Visible", hasDescription);
            int descriptionHeight = hasDescription ? lines(description, PHASE_TEXT_CHARS_PER_LINE) * 16 + 4 : 0;
            setHeight(cmd, selector + " #PhaseDescription", descriptionHeight);
            height += descriptionHeight;

            cmd.clear(selector + " #Attacks");
            List<BossAttack> attacks = phase.attacks();
            cmd.set(selector + " #PhaseAttackSpacer.Visible", !attacks.isEmpty());
            setHeight(cmd, selector + " #PhaseAttackSpacer", attacks.isEmpty() ? 0 : 6);
            if (!attacks.isEmpty()) height += 6;
            for (int a = 0; a < attacks.size(); a++) {
                BossAttack attack = attacks.get(a);
                cmd.append(selector + " #Attacks", HytemsUiTemplates.BOSS_ATTACK_ROW);
                String row = selector + " #Attacks[" + a + "]";
                String attackDescription = BossText.attackDescription(this.playerRef, boss, attack);
                cmd.set(row + " #AttackName.Text", BossText.attackName(this.playerRef, boss, attack));
                cmd.set(row + " #AttackDescription.Text", attackDescription == null ? "" : attackDescription);
                int rowHeight = Math.max(ATTACK_ROW_HEIGHT, lines(attackDescription, ATTACK_TEXT_CHARS_PER_LINE) * 15 + 7);
                setHeight(cmd, row, rowHeight);
                height += rowHeight;
            }
            // Each phase card carries an 8px gap below it.
            setHeight(cmd, selector, height + 8);
            sectionHeight += height + 8;
            index++;
        }
        setHeight(cmd, "#PhasesSection", sectionHeight + 8);
    }

    private void renderDrops(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nonnull BossInfo boss) {
        Map<String, DropEntry> drops = new LinkedHashMap<>();
        for (String member : boss.memberMobIds()) {
            for (DropEntry drop : MobMetadataRegistry.lookup(member).drops()) drops.putIfAbsent(drop.itemId(), drop);
        }
        String reward = BossText.reward(this.playerRef, boss.rewardId());
        cmd.set("#RewardLabel.Text", reward == null ? "" : "Completion reward: " + reward);
        cmd.set("#RewardLabel.Visible", reward != null);

        cmd.clear("#DropsList");
        cmd.set("#NoDropsLabel.Visible", drops.isEmpty() && reward == null);
        int height = 28 + 8 + (reward != null ? 24 : 0) + (drops.isEmpty() && reward == null ? 28 : 0);
        int index = 0;
        for (DropEntry drop : drops.values()) {
            height += appendItemRow(cmd, events, index++, drop);
        }
        setHeight(cmd, "#DropsSection", height + 8);
    }

    private int appendItemRow(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, int index, @Nonnull DropEntry drop) {
        cmd.append("#DropsList", HytemsUiTemplates.MOB_ITEM_ROW);
        String selector = "#DropsList[" + index + "]";
        Item item = HytemsPlugin.ITEMS.get(drop.itemId());
        String name = ItemUiSupport.translatedName(this.playerRef, item, drop.itemId());
        if (name == null || name.isEmpty() || name.equals(drop.itemId())) name = drop.displayName();
        cmd.set(selector + " #QuantityLabel.Text", drop.quantityLabel());
        cmd.set(selector + " #ChanceLabel.Text", drop.chanceLabel());
        cmd.set(selector + " #ItemIcon.ItemId", drop.itemId());
        cmd.set(selector + " #ItemName.Text", name);
        cmd.set(selector + " #IconBackground.Background", ItemUiSupport.rarityBackground(item));
        int height = name.length() > 32 ? ITEM_ROW_HEIGHT + 12 : ITEM_ROW_HEIGHT;
        setHeight(cmd, selector, height);
        events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #InteractButton",
                EventData.of("ItemId", drop.itemId()), false);
        return height;
    }

    /** Spawns of every phase role, merged by group title and location. */
    private List<SpawnGroup> collectSpawns(@Nonnull BossInfo boss) {
        Map<String, Map<String, Set<String>>> merged = new LinkedHashMap<>();
        for (String member : boss.memberMobIds()) {
            for (SpawnGroup group : MobMetadataRegistry.lookup(member).spawnGroups()) {
                Map<String, Set<String>> entries = merged.computeIfAbsent(group.title(), ignored -> new LinkedHashMap<>());
                for (SpawnEntry entry : group.entries()) {
                    entries.computeIfAbsent(entry.title(), ignored -> new LinkedHashSet<>()).addAll(entry.details());
                }
            }
        }
        List<SpawnGroup> groups = new ArrayList<>();
        merged.forEach((title, entries) -> {
            List<SpawnEntry> list = new ArrayList<>();
            entries.forEach((entryTitle, details) -> list.add(new SpawnEntry(entryTitle, new ArrayList<>(details))));
            groups.add(new SpawnGroup(title, list));
        });
        return groups;
    }

    /** Same attribute rows as the mob overview, for the first phase of the selected difficulty. */
    private void renderAttributes(@Nonnull UICommandBuilder cmd, @Nonnull BossVariant variant) {
        String roleId = variant.phases().stream().map(BossPhase::roleId).filter(id -> id != null).findFirst().orElse(this.mobId);
        List<AttributeRow> rows = MobMetadataRegistry.lookup(roleId).attributes();
        cmd.set("#AttributesTitle.Text", variant.phases().size() > 1 ? "Attributes (first phase)" : "Attributes");
        cmd.clear("#AttributesList");
        cmd.set("#NoAttributesLabel.Visible", rows.isEmpty());
        for (int i = 0; i < rows.size(); i++) {
            AttributeRow row = rows.get(i);
            cmd.append("#AttributesList", HytemsUiTemplates.MOB_ATTRIBUTE_ROW);
            String selector = "#AttributesList[" + i + "]";
            cmd.set(selector + " #AttributeLabel.Text", row.label() + ":");
            cmd.set(selector + " #AttributeValue.Text", row.value());
            // Same sizing as the mob overview's attribute column.
            setHeight(cmd, selector, INFO_ROW_HEIGHT + Math.max(0, row.value().length() - 34) / 30 * 14);
        }
    }

    /**
     * Encounter bosses are fought in an arena rather than spawned by world rules, so the spawn data
     * alone can be empty (the Gobliterator). Describe the arena under the boss location.
     */
    private List<SpawnGroup> addArenaLocation(@Nonnull BossInfo boss, @Nonnull List<SpawnGroup> groups) {
        boolean encounter = boss.variants().stream().anyMatch(variant -> !variant.encounterId().equals(boss.representativeMobId()));
        if (!encounter) return groups;
        List<String> details = new ArrayList<>();
        details.add("Boss arena encounter with a boss bar");
        if (boss.variants().size() > 1) details.add("Normal and Hard mode");

        String location = BossText.location(this.playerRef, boss);
        if (location == null) location = locationFromSpawns(groups);
        if (location == null) location = "Boss Arena";

        List<SpawnGroup> result = new ArrayList<>();
        boolean merged = false;
        for (SpawnGroup group : groups) {
            List<SpawnEntry> entries = new ArrayList<>();
            for (SpawnEntry entry : group.entries()) {
                if (!merged && entry.title().equalsIgnoreCase(location)) {
                    List<String> combined = new ArrayList<>(details);
                    entry.details().forEach(line -> { if (!combined.contains(line)) combined.add(line); });
                    entries.add(new SpawnEntry(entry.title(), combined));
                    merged = true;
                } else {
                    entries.add(entry);
                }
            }
            result.add(new SpawnGroup(group.title(), entries));
        }
        if (!merged) result.add(0, new SpawnGroup("Boss Arena", List.of(new SpawnEntry(location, details))));
        return result;
    }

    private void renderSpawns(@Nonnull UICommandBuilder cmd, @Nonnull List<SpawnGroup> groups) {
        cmd.clear("#SpawnGroups");
        cmd.set("#NoSpawnLabel.Visible", groups.isEmpty());
        int sectionHeight = 28 + 8 + (groups.isEmpty() ? 28 : 0);
        for (int g = 0; g < groups.size(); g++) {
            SpawnGroup group = groups.get(g);
            cmd.append("#SpawnGroups", HytemsUiTemplates.MOB_SPAWN_GROUP);
            String groupSelector = "#SpawnGroups[" + g + "]";
            cmd.set(groupSelector + " #GroupTitle.Text", group.title());
            int groupHeight = SPAWN_GROUP_BASE_HEIGHT;
            for (int e = 0; e < group.entries().size(); e++) {
                SpawnEntry entry = group.entries().get(e);
                cmd.append(groupSelector + " #Entries", HytemsUiTemplates.MOB_SPAWN_ENTRY);
                String entrySelector = groupSelector + " #Entries[" + e + "]";
                String details = String.join("\n", entry.details());
                int detailLines = Math.max(1, entry.details().size());
                cmd.set(entrySelector + " #EntryTitle.Text", entry.title());
                cmd.set(entrySelector + " #EntryDetails.Text", details);
                cmd.set(entrySelector + " #EntryDetails.Visible", !details.isEmpty());
                setHeight(cmd, entrySelector + " #EntryDetails", 20 + (detailLines - 1) * 16);
                int entryHeight = SPAWN_ENTRY_BASE_HEIGHT + (detailLines - 1) * 16;
                setHeight(cmd, entrySelector, entryHeight);
                groupHeight += entryHeight + 6;
            }
            setHeight(cmd, groupSelector, groupHeight);
            sectionHeight += groupHeight;
        }
        setHeight(cmd, "#SpawnSection", sectionHeight + 12);
    }

    private void bindButtons(@Nonnull UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton", EventData.of("Action", "back"), false);
    }

    private static int lines(String text, int charsPerLine) {
        if (text == null || text.isEmpty()) return 0;
        int lines = 0;
        for (String paragraph : text.split("\n", -1)) lines += Math.max(1, (paragraph.length() + charsPerLine - 1) / charsPerLine);
        return lines;
    }

    private void setHeight(@Nonnull UICommandBuilder cmd, @Nonnull String selector, int height) {
        Anchor anchor = new Anchor();
        anchor.setHeight(Value.of(height));
        cmd.setObject(selector + ".Anchor", anchor);
    }

    public static class BossOverviewData {
        public static final BuilderCodec<BossOverviewData> CODEC = BuilderCodec.builder(
                        BossOverviewData.class,
                        BossOverviewData::new
                )
                .append(
                        new KeyedCodec<>("ItemId", Codec.STRING),
                        (data, value) -> data.itemId = value,
                        data -> data.itemId
                ).add()
                .append(
                        new KeyedCodec<>("Action", Codec.STRING),
                        (data, value) -> data.action = value,
                        data -> data.action
                ).add()
                .build();

        private String itemId;
        private String action;
    }
}
