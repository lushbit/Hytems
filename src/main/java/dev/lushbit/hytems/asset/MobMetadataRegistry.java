package dev.lushbit.hytems.asset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.common.map.IWeightedMap;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.asset.type.item.config.container.ChoiceItemDropContainer;
import com.hypixel.hytale.server.core.asset.type.item.config.container.DroplistItemDropContainer;
import com.hypixel.hytale.server.core.asset.type.item.config.container.ItemDropContainer;
import com.hypixel.hytale.server.core.asset.type.item.config.container.MultipleItemDropContainer;
import com.hypixel.hytale.server.core.asset.type.item.config.container.SingleItemDropContainer;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderInfo;
import com.hypixel.hytale.server.npc.asset.builder.BuilderManager;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.spawning.assets.spawns.LightType;
import com.hypixel.hytale.server.spawning.assets.spawns.config.BeaconNPCSpawn;
import com.hypixel.hytale.server.spawning.assets.spawns.config.NPCSpawn;
import com.hypixel.hytale.server.spawning.assets.spawns.config.RoleSpawnParameters;
import com.hypixel.hytale.server.spawning.assets.spawns.config.WorldNPCSpawn;
import dev.lushbit.hytems.HytemsPlugin;
import dev.lushbit.hytems.ui.TextFormatters;

import javax.annotation.Nonnull;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class MobMetadataRegistry {
    private static final MobMetadata EMPTY = new MobMetadata("", "Unknown", null,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    private static final String MISSING_ITEM_ID = "__HYTEMS_MISSING_ITEM__";
    private static final String PORTRAIT_RESOURCE_DIR = "Common/UI/Custom/hytems/ui/Assets/MobPortraits/";
    private static final Path DEV_PORTRAIT_DIR = Path.of("src/main/resources").resolve(PORTRAIT_RESOURCE_DIR);
    private static final Map<String, MobMetadata> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> ITEM_ID_LOOKUP = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static volatile Map<String, WorldNPCSpawn> worldNpcSpawns = Collections.emptyMap();
    private static volatile Map<String, BeaconNPCSpawn> beaconNpcSpawns = Collections.emptyMap();

    private MobMetadataRegistry() {
    }

    @Nonnull
    public static MobMetadata lookup(String mobId) {
        if (mobId == null || mobId.isEmpty()) {
            return EMPTY;
        }

        String normalized = normalizeId(mobId);
        MobMetadata cached = CACHE.get(normalized);
        if (cached != null) {
            return cached;
        }

        return CACHE.computeIfAbsent(normalized, MobMetadataRegistry::loadSafely);
    }

    public static void preload(String mobId) {
        if (mobId == null || mobId.isEmpty()) {
            return;
        }

        String normalized = normalizeId(mobId);
        if (CACHE.containsKey(normalized)) {
            return;
        }

        ensureLoadedAsync(normalized);
    }

    public static void markNpcDataDirty() {
        for (String mobId : new ArrayList<>(CACHE.keySet())) {
            ensureLoadedAsync(mobId);
        }
    }

    public static void reloadWorldNpcSpawns(Map<String, WorldNPCSpawn> spawns) {
        worldNpcSpawns = spawns == null ? Collections.emptyMap() : new LinkedHashMap<>(spawns);
        markNpcDataDirty();
    }

    public static void reloadBeaconNpcSpawns(Map<String, BeaconNPCSpawn> spawns) {
        beaconNpcSpawns = spawns == null ? Collections.emptyMap() : new LinkedHashMap<>(spawns);
        markNpcDataDirty();
    }

    @Nonnull
    public static List<String> knownMobIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        collectBuilderMobIds(ids);
        collectSpawnMobIds(ids, currentWorldNpcSpawns());
        collectSpawnMobIds(ids, currentBeaconNpcSpawns());
        collectPortraitMobIds(ids);

        List<String> sorted = new ArrayList<>(ids);
        sorted.removeIf(MobMetadataRegistry::shouldHideFromMobBrowser);
        sorted.sort(Comparator.comparing(TextFormatters::mobName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private static boolean shouldHideFromMobBrowser(String mobId) {
        if (mobId == null || mobId.isEmpty()) {
            return true;
        }

        String normalized = normalizeKey(mobId);
        if (normalized.equals("self") || normalized.equals("player")) {
            return true;
        }

        // Wander_Circle / Wander_Rect / Wander_Simple are movement components, not mobs.
        if (normalized.startsWith("wander")) {
            return true;
        }

        return normalized.contains("test")
                || normalized.equals("blanktemplate")
                || normalized.contains("component")
                || normalized.contains("dungeon")
                || normalized.contains("edible")
                || normalized.equals("emptyrole")
                || normalized.contains("goblindukephase")
                || normalized.contains("tamed")
                || normalized.contains("static")
                || normalized.contains("template")
                || normalized.contains("temple");
    }

    private static void ensureLoadedAsync(String mobId) {
        if (!LOADING.add(mobId)) {
            return;
        }

        Thread loader = new Thread(() -> {
            try {
                CACHE.put(mobId, load(mobId));
            } catch (Exception e) {
                System.err.println("[Hytems] Failed to load mob metadata for " + mobId + ": " + e.getMessage());
                CACHE.put(mobId, fallback(mobId));
            } finally {
                LOADING.remove(mobId);
            }
        }, "Hytems-MobMetadata-" + mobId);
        loader.setDaemon(true);
        loader.start();
    }

    private static MobMetadata loadSafely(String mobId) {
        try {
            return load(mobId);
        } catch (Exception e) {
            System.err.println("[Hytems] Failed to load mob metadata for " + mobId + ": " + e.getMessage());
            return fallback(mobId);
        }
    }

    private static void collectBuilderMobIds(Set<String> ids) {
        try {
            NPCPlugin plugin = NPCPlugin.get();
            BuilderManager manager = plugin == null ? null : plugin.getBuilderManager();
            if (manager == null) return;

            for (String name : plugin.getRoleTemplateNames(false)) {
                addMobId(ids, name);
            }
            for (String name : manager.getTemplateNames()) {
                if (isRoleBuilderName(manager, name)) {
                    addMobId(ids, name);
                }
            }
            for (String name : manager.getNameToIndexMap().keySet()) {
                if (isRoleBuilderName(manager, name)) {
                    addMobId(ids, name);
                }
            }
            manager.getAllBuilders().values().forEach(info -> {
                if (isRoleBuilder(info)) {
                    addMobId(ids, info.getKeyName());
                    if (info.getPath() != null) {
                        addMobId(ids, info.getPath().getFileName().toString());
                    }
                }
            });
        } catch (Exception ignored) {
        }
    }

    /**
     * The BuilderManager does not only hold NPC roles: since 0.6.0 it also holds EncounterManager
     * configs (Example_Boss, Example_Encounter, Encounter_Macro_*, ...) plus the action/sensor
     * component builders. Only entries whose builder produces a Role are actual mobs - filtering by
     * category keeps future asset types out of the browser without chasing their names.
     */
    private static boolean isRoleBuilder(BuilderInfo info) {
        if (info == null) return false;
        try {
            Builder<?> builder = info.getBuilder();
            return builder != null && builder.category() == Role.class;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isRoleBuilderName(BuilderManager manager, String name) {
        if (name == null || name.isEmpty()) return false;
        try {
            var nameToIndex = manager.getNameToIndexMap();
            return nameToIndex.containsKey(name)
                    && isRoleBuilder(manager.tryGetBuilderInfo(nameToIndex.getInt(name)));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void collectSpawnMobIds(Set<String> ids, Map<String, ? extends NPCSpawn> map) {
        for (NPCSpawn spawn : map.values()) {
            if (spawn == null || spawn.getNPCs() == null) continue;
            for (RoleSpawnParameters npc : spawn.getNPCs()) {
                addMobId(ids, npc.getId());
            }
        }
    }

    /**
     * Adds mobs that ship a portrait but have no NPC role of their own. Reads the plugin's own
     * code source so this behaves the same on a real server (jar) as in development (classes
     * directory) - a plain relative source path only ever resolved in the dev workspace.
     */
    private static void collectPortraitMobIds(Set<String> ids) {
        for (String name : listPortraitNames()) {
            if (!name.equalsIgnoreCase("Construction_Sign")) {
                addMobId(ids, name);
            }
        }
    }

    private static List<String> listPortraitNames() {
        List<String> names = new ArrayList<>();
        try {
            URL location = MobMetadataRegistry.class.getProtectionDomain().getCodeSource().getLocation();
            Path source = Path.of(location.toURI());

            if (Files.isRegularFile(source)) {
                try (ZipFile zip = new ZipFile(source.toFile())) {
                    zip.stream()
                            .map(ZipEntry::getName)
                            .filter(name -> name.startsWith(PORTRAIT_RESOURCE_DIR))
                            .map(name -> name.substring(PORTRAIT_RESOURCE_DIR.length()))
                            .forEach(name -> addPortraitName(names, name));
                }
            } else if (Files.isDirectory(source)) {
                collectPortraitNamesFrom(source.resolve(PORTRAIT_RESOURCE_DIR), names);
            }
        } catch (Exception ignored) {
        }

        if (names.isEmpty()) {
            collectPortraitNamesFrom(DEV_PORTRAIT_DIR, names);
        }
        return names;
    }

    private static void collectPortraitNamesFrom(Path directory, List<String> names) {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (var stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .forEach(name -> addPortraitName(names, name));
        } catch (Exception ignored) {
        }
    }

    private static void addPortraitName(List<String> names, String fileName) {
        if (fileName == null || fileName.indexOf('/') >= 0
                || !fileName.toLowerCase(Locale.ENGLISH).endsWith(".png")) {
            return;
        }
        String name = fileName.substring(0, fileName.length() - 4);
        if (!name.isEmpty() && !names.contains(name)) {
            names.add(name);
        }
    }

    private static void addMobId(Set<String> ids, String value) {
        String id = normalizeId(value);
        if (!id.isEmpty()) {
            ids.add(id);
        }
    }

    private static MobMetadata load(String mobId) {
        BuilderInfo builderInfo = tryGetBuilderInfo(mobId);
        Object builder = builderInfo != null ? builderInfo.getBuilder() : null;
        JsonObject roleConfig = loadRoleConfig(builderInfo);
        List<AttributeRow> attributes = new ArrayList<>();
        addFamilyAttributes(attributes, mobId, builderInfo);
        addRoleConfigAttributes(attributes, roleConfig);
        addBuilderAttributes(attributes, builder);
        addModelAttributes(attributes, builder);

        String dropListId = firstNonBlank(jsonString(roleConfig, "DropList"),
                stringValue(readAny(builder, "dropList", "dropListId", "drops", "droplist")));
        if (dropListId == null || dropListId.isEmpty()) {
            dropListId = "Drop_" + mobId;
        }
        addAttribute(attributes, "DropList", TextFormatters.dropSourceName(dropListId));

        List<DropEntry> drops = loadDrops(dropListId);
        List<VariantEntry> variants = loadVariants(builder, roleConfig);
        List<SpawnGroup> spawns = loadSpawns(mobId);

        return new MobMetadata(mobId, TextFormatters.mobName(mobId), MobPortraitPath(mobId), dedupeAttributes(attributes), drops, variants, spawns);
    }

    private static void addFamilyAttributes(List<AttributeRow> rows, String mobId, BuilderInfo info) {
        String[] pathParts = rolePathParts(info);
        if (pathParts.length >= 1) addAttribute(rows, "Family", TextFormatters.dropSourceName(pathParts[0]));
        if (pathParts.length >= 2) addAttribute(rows, "Subfamily", TextFormatters.dropSourceName(pathParts[1]));
        if (pathParts.length >= 3) addAttribute(rows, "Category", TextFormatters.dropSourceName(pathParts[2]));
        if (pathParts.length >= 4) addAttribute(rows, "Class", TextFormatters.dropSourceName(pathParts[3]));

        if (pathParts.length == 0) {
            String[] tokens = mobId.split("_");
            if (tokens.length > 0) addAttribute(rows, "Family", TextFormatters.dropSourceName(tokens[0]));
            if (tokens.length > 1) addAttribute(rows, "Category", TextFormatters.dropSourceName(tokens[0] + "_" + tokens[1]));
            if (tokens.length > 2) addAttribute(rows, "Class", TextFormatters.dropSourceName(tokens[tokens.length - 1]));
        }
    }

    private static String[] rolePathParts(BuilderInfo info) {
        if (info == null || info.getPath() == null) {
            return new String[0];
        }
        String path = info.getPath().toString().replace('\\', '/');
        int marker = path.toLowerCase(Locale.ENGLISH).indexOf("/npc/roles/");
        if (marker < 0) {
            marker = path.toLowerCase(Locale.ENGLISH).indexOf("server/npc/roles/");
            if (marker >= 0) marker += "server/npc/roles/".length() - 1;
        } else {
            marker += "/npc/roles/".length() - 1;
        }
        if (marker < 0 || marker + 1 >= path.length()) {
            return new String[0];
        }
        String relative = path.substring(marker + 1).replaceFirst("(?i)\\.json$", "");
        String[] raw = relative.split("/");
        List<String> parts = new ArrayList<>();
        for (String part : raw) {
            if (!part.isEmpty() && !part.equalsIgnoreCase("Roles")) {
                parts.add(part);
            }
        }
        return parts.toArray(new String[0]);
    }

    private static void addBuilderAttributes(List<AttributeRow> rows, Object builder) {
        if (builder == null) return;
        for (String name : List.of("MaxHealth", "Health", "AttackDistance", "TargetRange", "DesiredAttackDistanceRange",
                "CombatBehaviorDistance", "HearingRange", "ViewSector", "WanderRadius", "Patrol", "FollowPatrolPath",
                "WakingPeriod", "LeashMinPlayerDistance", "LeashDistance", "HardLeashDistance")) {
            Object value = readAny(builder, name, decapitalize(name));
            addAttribute(rows, humanLabel(name), value);
        }
    }

    private static void addRoleConfigAttributes(List<AttributeRow> rows, JsonObject roleConfig) {
        if (roleConfig == null) return;
        for (String key : List.of("MaxHealth", "Health", "AttackDistance", "TargetRange", "DesiredAttackDistanceRange",
                "CombatBehaviorDistance", "HearingRange", "ViewSector", "IsMemory", "MemoriesCategory",
                "MemoriesNameOverride", "BreathesInWater", "BreathesInAir", "WanderRadius", "Patrol",
                "FollowPatrolPath", "ApplySeparation", "WakingPeriod", "LeashMinPlayerDistance",
                "LeashDistance", "HardLeashDistance", "Attack", "RootInteraction", "Appearance")) {
            addAttribute(rows, humanLabel(key), jsonValue(roleConfig, key));
        }
        String[] weapons = jsonStringArray(roleConfig, "Weapons");
        if (weapons.length > 0) addAttribute(rows, "Weapons", joinHumanized(weapons));
    }

    private static void addModelAttributes(List<AttributeRow> rows, Object builder) {
        String modelId = stringValue(readAny(builder, "model", "modelAsset", "appearanceModel"));
        ModelAsset model = findModel(modelId);
        if (model == null) return;
        addAttribute(rows, "Model", model.getId());
        addAttribute(rows, "Scale", range(model.getMinScale(), model.getMaxScale()));
        addAttribute(rows, "Eye Height", model.getEyeHeight());
    }

    private static List<DropEntry> loadDrops(String dropListId) {
        ItemDropList list = findDropList(dropListId);
        if (list == null || list.getContainer() == null) {
            return Collections.emptyList();
        }

        Map<String, DropAccumulator> merged = new LinkedHashMap<>();
        extractDrops(list.getContainer(), merged, new HashSet<>(), 1.0d);
        List<DropEntry> drops = new ArrayList<>();
        for (DropAccumulator acc : merged.values()) {
            String known = findKnownItemId(acc.itemId);
            if (known == null) continue;
            if (shouldHideMobDropItem(known)) continue;
            drops.add(new DropEntry(known, TextFormatters.itemName(known), quantity(acc.min, acc.max), chance(acc.chance)));
        }
        drops.sort(Comparator.comparing(DropEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        return drops;
    }

    private static boolean shouldHideMobDropItem(String itemId) {
        String normalized = normalizeKey(itemId);
        return normalized.contains("ediblerat")
                || normalized.contains("ediblegoblinscrapper")
                || normalized.contains("goblinscrapper");
    }

    private static void extractDrops(ItemDropContainer container, Map<String, DropAccumulator> out, Set<String> seenDropLists, double chance) {
        if (container == null) return;
        double weightedChance = chance * normalizeChanceWeight(container.getWeight());
        if (container instanceof SingleItemDropContainer single) {
            addDrop(out, single.getDrop(), weightedChance);
            return;
        }
        if (container instanceof MultipleItemDropContainer) {
            Object children = readField(container, "containers");
            forEachArrayOrCollection(children, child -> {
                if (child instanceof ItemDropContainer childContainer) extractDrops(childContainer, out, seenDropLists, weightedChance);
            });
            return;
        }
        if (container instanceof ChoiceItemDropContainer) {
            Object weighted = readField(container, "containers");
            if (weighted instanceof IWeightedMap<?> map) {
                map.forEachEntry((child, weight) -> {
                    if (child instanceof ItemDropContainer childContainer) {
                        extractDrops(childContainer, out, seenDropLists, weightedChance * normalizeChanceWeight(weight));
                    }
                });
            }
            return;
        }
        if (container instanceof DroplistItemDropContainer) {
            String referenced = stringValue(readField(container, "droplistId"));
            if (referenced != null && seenDropLists.add(referenced)) {
                ItemDropList referencedList = findDropList(referenced);
                if (referencedList != null) extractDrops(referencedList.getContainer(), out, seenDropLists, weightedChance);
            }
            return;
        }

        for (ItemDrop drop : container.getAllDrops(new ArrayList<>())) {
            addDrop(out, drop, weightedChance);
        }
    }

    private static double normalizeChanceWeight(double weight) {
        if (weight <= 0.0d) {
            return 0.0d;
        }
        if (weight <= 1.0d) {
            return weight;
        }
        return Math.min(1.0d, weight / 100.0d);
    }

    private static void addDrop(Map<String, DropAccumulator> out, ItemDrop drop, double chance) {
        if (drop == null || drop.getItemId() == null) return;
        String id = normalizeId(drop.getItemId());
        DropAccumulator acc = out.computeIfAbsent(id, DropAccumulator::new);
        acc.min = Math.min(acc.min, drop.getQuantityMin());
        acc.max = Math.max(acc.max, drop.getQuantityMax());
        acc.chance = Math.max(acc.chance, chance);
    }

    private static List<VariantEntry> loadVariants(Object builder, JsonObject roleConfig) {
        LinkedHashSet<String> itemIds = new LinkedHashSet<>();
        collectItems(itemIds, readAny(builder, "weapons", "weapon", "armor", "tools", "hotbarItems", "offHandItems", "inventoryItems", "equipment"));
        for (String key : List.of("Weapons", "Weapon", "Armor", "HotbarItems", "OffHandItems", "Tools", "InventoryItems")) {
            collectItems(itemIds, jsonValue(roleConfig, key));
        }

        String modelId = stringValue(readAny(builder, "model", "modelAsset", "appearanceModel"));
        ModelAsset model = findModel(modelId);
        if (model != null) {
            ModelAttachment[] attachments = model.getDefaultAttachments();
            if (attachments != null) {
                for (ModelAttachment attachment : attachments) collectItems(itemIds, attachment);
            }
            collectItems(itemIds, model.getRandomAttachmentSets());
        }

        List<VariantEntry> variants = new ArrayList<>();
        for (String id : itemIds) {
            String known = findKnownItemId(id);
            if (known != null) variants.add(new VariantEntry(known, TextFormatters.itemName(known)));
        }
        variants.sort(Comparator.comparing(VariantEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        return variants;
    }

    private static void collectItems(Set<String> itemIds, Object value) {
        if (value == null) return;
        if (value instanceof String text) {
            String known = findKnownItemId(text);
            if (known != null) itemIds.add(known);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(v -> collectItems(itemIds, v));
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(v -> collectItems(itemIds, v));
            return;
        }
        if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) collectItems(itemIds, Array.get(value, i));
            return;
        }
        for (Method method : value.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || !method.getName().startsWith("get")) continue;
            String name = method.getName().toLowerCase(Locale.ENGLISH);
            if (!name.contains("item") && !name.contains("weapon") && !name.contains("armor") && !name.contains("attachment")) continue;
            collectItems(itemIds, call(value, method.getName()));
        }
    }

    private static List<SpawnGroup> loadSpawns(String mobId) {
        List<SpawnEntry> entries = new ArrayList<>();
        addNpcSpawns(mobId, entries, currentWorldNpcSpawns());
        addNpcSpawns(mobId, entries, currentBeaconNpcSpawns());
        entries.sort(Comparator.comparing(SpawnEntry::title, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> String.join(" ", entry.details()), String.CASE_INSENSITIVE_ORDER));
        return entries.isEmpty() ? Collections.emptyList() : List.of(new SpawnGroup("Spawn & Habitat", entries));
    }

    private static Map<String, WorldNPCSpawn> currentWorldNpcSpawns() {
        if (!worldNpcSpawns.isEmpty()) return worldNpcSpawns;
        return safeMap(WorldNPCSpawn.getAssetMap());
    }

    private static Map<String, BeaconNPCSpawn> currentBeaconNpcSpawns() {
        if (!beaconNpcSpawns.isEmpty()) return beaconNpcSpawns;
        return safeMap(BeaconNPCSpawn.getAssetMap());
    }

    private static void addNpcSpawns(String mobId, List<SpawnEntry> entries, Map<String, ? extends NPCSpawn> map) {
        Map<String, SpawnHabitatAggregate> aggregates = new LinkedHashMap<>();
        for (NPCSpawn spawn : map.values()) {
            if (spawn == null || spawn.getNPCs() == null) continue;
            for (RoleSpawnParameters npc : spawn.getNPCs()) {
                if (!matchesMob(mobId, npc.getId())) continue;
                HabitatLabel label = habitatLabelForSpawn(spawn);
                String spawnId = normalizeId(spawn.getId());
                if (isIgnoredSpawnId(spawnId) || isEventSpawnLabel(label.zone())) continue;
                String elementalCircle = elementalCircleLabel(spawnId);
                String goblinDukePhase = goblinDukePhaseLabel(spawnId, npc.getId());
                if (goblinDukePhase != null) {
                    label = new HabitatLabel("Goblin Duke", null, null);
                }

                String key = label.zone() + "|" + label.biome() + "|" + label.region();
                HabitatLabel aggregateLabel = label;
                SpawnHabitatAggregate aggregate = aggregates.computeIfAbsent(key,
                        ignored -> new SpawnHabitatAggregate(aggregateLabel.zone(), aggregateLabel.biome(), aggregateLabel.region()));

                if (elementalCircle != null) {
                    aggregate.specialLabels.add(elementalCircle);
                    continue;
                }

                if (goblinDukePhase != null) {
                    aggregate.specialLabels.add(goblinDukePhase);
                    continue;
                }

                String runtimeStructure = structureFromRuntimeSpawn(spawn, label);
                if (runtimeStructure != null) {
                    aggregate.structures.add(runtimeStructure);
                }
                if (spawn instanceof BeaconNPCSpawn beacon) {
                    String beaconStructure = structureFromPath(normalizeId(beacon.getId()).toLowerCase(Locale.ENGLISH));
                    if (beaconStructure != null) {
                        aggregate.structures.add(beaconStructure);
                    }
                }
            }
        }
        for (SpawnHabitatAggregate aggregate : aggregates.values()) {
            entries.add(new SpawnEntry(aggregate.zone(), aggregate.detailLines()));
        }
    }

    private static HabitatLabel habitatLabelForSpawn(NPCSpawn spawn) {
        String[] environments = spawn == null ? null : spawn.getEnvironments();
        String zone = zoneFromEnvironmentNames(environments);
        if (zone == null && spawn != null) {
            zone = zoneFromPath(normalizeId(spawn.getId()));
        }

        String biome = biomeFromEnvironmentNames(environments);
        if (biome == null && spawn != null) {
            biome = biomeFromPath(normalizeId(spawn.getId()).toLowerCase(Locale.ENGLISH));
        }

        String region = regionFromEnvironmentNames(environments);
        if ((region == null || region.isEmpty()) && spawn != null) {
            region = subBiomeFromPath(normalizeId(spawn.getId()).toLowerCase(Locale.ENGLISH));
        }

        String title = zone != null ? zone : (spawn != null ? TextFormatters.dropSourceName(spawn.getId()) : "Unknown");
        return new HabitatLabel(title, biome, region);
    }

    private static String zoneFromEnvironmentNames(String[] environments) {
        if (environments == null) return null;
        for (String environment : environments) {
            if (environment == null) continue;
            String normalized = normalizeId(environment);
            for (String token : normalized.split("_")) {
                if (token.matches("(?i)Zone\\d+")) {
                    return formatZoneLabel(token);
                }
            }
        }
        return null;
    }

    private static String biomeFromEnvironmentNames(String[] environments) {
        List<String> tokens = environmentTokens(environments);
        return tokens.isEmpty() ? null : TextFormatters.dropSourceName(tokens.get(0));
    }

    private static String regionFromEnvironmentNames(String[] environments) {
        List<String> tokens = environmentTokens(environments);
        if (tokens.size() <= 1) return null;
        List<String> regions = new ArrayList<>();
        for (int i = 1; i < tokens.size(); i++) {
            regions.add(TextFormatters.dropSourceName(tokens.get(i)));
        }
        return joinDistinct(regions);
    }

    private static List<String> environmentTokens(String[] environments) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        if (environments == null) return new ArrayList<>(tokens);
        for (String environment : environments) {
            if (environment == null) continue;
            String normalized = normalizeId(environment).replaceFirst("(?i)^Env_", "");
            String[] parts = normalized.split("_");
            for (String part : parts) {
                if (part.isEmpty()
                        || part.equalsIgnoreCase("Env")
                        || part.matches("(?i)Zone\\d+")
                        || part.matches("(?i)T\\d+")) {
                    continue;
                }
                tokens.add(part);
            }
        }
        return new ArrayList<>(tokens);
    }

    private static String zoneFromPath(String path) {
        String[] parts = path.replace('\\', '/').split("/");
        for (String part : parts) {
            if (part.matches("(?i)Zone\\d+.*")) {
                return formatZoneLabel(part);
            }
        }
        return null;
    }

    private static String formatZoneLabel(String value) {
        if (value == null || value.isEmpty()) return null;
        String normalized = normalizeId(value);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)^Zone(\\d+)").matcher(normalized);
        if (matcher.find()) {
            return "Zone " + matcher.group(1);
        }
        return TextFormatters.dropSourceName(normalized);
    }

    private static boolean isEventSpawnLabel(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.ENGLISH);
        return lower.contains("event") || lower.contains("portal") || lower.contains("test");
    }

    private static boolean isIgnoredSpawnId(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.ENGLISH);
        return lower.contains("portal") || lower.contains("event") || lower.contains("test");
    }

    private static String elementalCircleLabel(String value) {
        if (value == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)elemental[_\\s-]*circle[_\\s-]*tier[_\\s-]*(\\d+)")
                .matcher(normalizeId(value));
        if (matcher.find()) {
            return "Elemental Circle Tier " + matcher.group(1);
        }
        return null;
    }

    private static String goblinDukePhaseLabel(String spawnId, String npcId) {
        String combined = normalizeId(spawnId) + "_" + normalizeId(npcId);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)goblin[_\\s-]*duke[_\\s-]*phase[_\\s-]*(\\d+)")
                .matcher(combined);
        if (matcher.find()) {
            return "Goblin Duke Phase " + matcher.group(1);
        }
        return null;
    }

    private static String biomeFromPath(String lowerPath) {
        if (lowerPath.contains("glacial")) return "Glacial";
        if (lowerPath.contains("tundra")) return "Tundra";
        if (lowerPath.contains("forests")) return "Forests";
        if (lowerPath.contains("volcanic") || lowerPath.contains("volcano")) return "Volcanic";
        if (lowerPath.contains("desert")) return "Desert";
        if (lowerPath.contains("savanna")) return "Savanna";
        if (lowerPath.contains("swamp")) return "Swamp";
        if (lowerPath.contains("plains")) return "Plains";
        if (lowerPath.contains("scrub")) return "Scrub";
        if (lowerPath.contains("cave")) return "Cave";
        return null;
    }

    private static String subBiomeFromPath(String lowerPath) {
        if (lowerPath.contains("player_local")) return "Player Local";
        if (lowerPath.contains("outpost")) return "Outpost";
        if (lowerPath.contains("camp")) return "Camp";
        return null;
    }

    private static String structureFromPath(String lowerPath) {
        if (lowerPath.contains("mineshaft")) return "Mineshaft";
        if (lowerPath.contains("henges")) return "Henges";
        if (lowerPath.contains("cave")) return "Cave";
        return null;
    }

    private static String structureFromRuntimeSpawn(NPCSpawn spawn, HabitatLabel label) {
        String lower = normalizeId(spawn.getId()).toLowerCase(Locale.ENGLISH);
        String fromPath = structureFromPath(lower);
        if (fromPath != null) return fromPath;
        if (label != null && label.biome() != null && label.biome().equalsIgnoreCase("Caves")) return "Cave";
        return null;
    }

    private static JsonObject loadRoleConfig(BuilderInfo info) {
        if (info == null || info.getPath() == null) return null;
        Path path = info.getPath();
        try {
            if (Files.isRegularFile(path)) {
                JsonObject raw = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                return flattenRoleConfig(raw);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static JsonObject flattenRoleConfig(JsonObject raw) {
        JsonObject flattened = new JsonObject();
        copyRoleConfigSection(raw, "Parameters", flattened, true);
        copyRoleConfigSection(raw, "Modify", flattened, false);
        return flattened;
    }

    private static void copyRoleConfigSection(JsonObject source, String sectionName, JsonObject target, boolean parameterSection) {
        if (source == null || !source.has(sectionName) || !source.get(sectionName).isJsonObject()) return;
        JsonObject section = source.getAsJsonObject(sectionName);
        for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
            JsonElement value = entry.getValue();
            if (parameterSection && value.isJsonObject() && value.getAsJsonObject().has("Value")) {
                target.add(entry.getKey(), value.getAsJsonObject().get("Value"));
            } else if (value.isJsonObject() && value.getAsJsonObject().has("Compute")) {
                String computedKey = jsonPrimitiveString(value.getAsJsonObject().get("Compute"));
                if (computedKey != null && target.has(computedKey)) {
                    target.add(entry.getKey(), target.get(computedKey));
                }
            } else {
                target.add(entry.getKey(), value);
            }
        }
    }

    private static String jsonPrimitiveString(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) return null;
        try {
            return element.getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String jsonString(JsonObject object, String key) {
        Object value = jsonValue(object, key);
        return value instanceof String text && !text.isEmpty() ? normalizeId(text) : null;
    }

    private static String[] jsonStringArray(JsonObject object, String key) {
        Object value = jsonValue(object, key);
        if (value instanceof String[] array) return array;
        if (value instanceof String text && !text.isEmpty()) return new String[]{text};
        return new String[0];
    }

    private static Object jsonValue(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key)) return null;
        return jsonValue(object.get(key));
    }

    private static Object jsonValue(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isBoolean()) return element.getAsBoolean();
            if (element.getAsJsonPrimitive().isNumber()) return element.getAsNumber();
            return element.getAsString();
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            List<Object> values = new ArrayList<>();
            for (JsonElement child : array) values.add(jsonValue(child));
            return values;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("Value")) return jsonValue(object.get("Value"));
            if (object.has("Size")) return jsonValue(object.get("Size"));
            if (object.has("Id")) return jsonValue(object.get("Id"));
            if (object.has("Name")) return jsonValue(object.get("Name"));
            return object.toString();
        }
        return null;
    }

    private static List<String> dedupeDetailLines(List<String> details) {
        return new ArrayList<>(new LinkedHashSet<>(details));
    }

    private static String joinDistinct(List<String> values) {
        List<String> filtered = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isEmpty() && !filtered.contains(value)) filtered.add(value);
        }
        return String.join(", ", filtered);
    }

    private static BuilderInfo tryGetBuilderInfo(String mobId) {
        try {
            NPCPlugin plugin = NPCPlugin.get();
            if (plugin == null) return null;
            int index = plugin.getIndex(mobId);
            BuilderInfo info = plugin.getRoleBuilderInfo(index);
            if (info != null) return info;
            BuilderManager manager = plugin.getBuilderManager();
            return manager == null ? null : manager.tryGetBuilderInfo(index);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ItemDropList findDropList(String id) {
        if (id == null || id.isEmpty()) return null;
        Map<String, ItemDropList> map = safeMap(ItemDropList.getAssetMap());
        List<String> candidates = List.of(id, normalizeId(id), "Drop_" + normalizeId(id), "Drops_" + normalizeId(id));
        for (String candidate : candidates) {
            ItemDropList exact = map.get(candidate);
            if (exact != null) return exact;
        }
        String normalized = normalizeKey(id).replaceFirst("^drop", "").replaceFirst("^drops", "");
        for (Map.Entry<String, ItemDropList> entry : map.entrySet()) {
            String key = normalizeKey(entry.getKey()).replaceFirst("^drop", "").replaceFirst("^drops", "");
            if (key.equals(normalized) || key.endsWith(normalized)) return entry.getValue();
        }
        return null;
    }

    private static ModelAsset findModel(String id) {
        if (id == null || id.isEmpty()) return null;
        Map<String, ModelAsset> map = safeMap(ModelAsset.getAssetMap());
        ModelAsset exact = map.get(id);
        if (exact != null) return exact;
        String normalized = normalizeKey(id);
        for (Map.Entry<String, ModelAsset> entry : map.entrySet()) if (normalizeKey(entry.getKey()).equals(normalized)) return entry.getValue();
        return null;
    }

    private static <T> Map<String, T> safeMap(Object assetMap) {
        if (assetMap == null) return Collections.emptyMap();
        try {
            Object map = assetMap.getClass().getMethod("getAssetMap").invoke(assetMap);
            if (map instanceof Map<?, ?> raw) {
                Map<String, T> typed = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : raw.entrySet()) typed.put(String.valueOf(entry.getKey()), (T) entry.getValue());
                return typed;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyMap();
    }

    private static boolean matchesMob(String mobId, String candidate) {
        String target = normalizeKey(mobId);
        String value = normalizeKey(candidate);
        return value.equals(target)
                || value.startsWith(target)
                || target.startsWith(value)
                || isTokenSubsetMatch(target, value);
    }

    private static boolean isTokenSubsetMatch(String first, String second) {
        if (first.length() < 4 || second.length() < 4) return false;
        return first.contains(second) || second.contains(first);
    }

    private static String findKnownItemId(String id) {
        if (id == null || HytemsPlugin.ITEMS == null) return null;
        String normalized = normalizeKey(id);
        if (ITEM_ID_LOOKUP.containsKey(normalized)) {
            String cached = ITEM_ID_LOOKUP.get(normalized);
            return MISSING_ITEM_ID.equals(cached) ? null : cached;
        }
        for (Map.Entry<String, Item> entry : HytemsPlugin.ITEMS.entrySet()) {
            if (normalizeKey(entry.getKey()).equals(normalized)) {
                ITEM_ID_LOOKUP.put(normalized, entry.getKey());
                return entry.getKey();
            }
        }
        ITEM_ID_LOOKUP.put(normalized, MISSING_ITEM_ID);
        return null;
    }

    private static void addGroup(List<SpawnGroup> groups, String title, List<SpawnEntry> entries) {
        if (!entries.isEmpty()) groups.add(new SpawnGroup(title, entries));
    }

    private static void addDetail(List<String> details, String label, Object value) {
        String text = formatValue(value);
        if (text != null && !text.isEmpty() && !"N/A".equalsIgnoreCase(text)) details.add(label + ": " + text);
    }

    private static void addAttribute(List<AttributeRow> rows, String label, Object value) {
        String text = formatValue(value);
        if (text != null && !text.isEmpty() && !"false".equalsIgnoreCase(text) && !"N/A".equalsIgnoreCase(text)) {
            rows.add(new AttributeRow(label, text));
        }
    }

    private static List<AttributeRow> dedupeAttributes(List<AttributeRow> rows) {
        Map<String, AttributeRow> deduped = new LinkedHashMap<>();
        for (AttributeRow row : rows) deduped.putIfAbsent(row.label().toLowerCase(Locale.ENGLISH), row);
        return new ArrayList<>(deduped.values());
    }

    private static Object readAny(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            Object value = call(target, "get" + capitalize(name));
            if (value == null) value = call(target, "is" + capitalize(name));
            if (value == null) value = readField(target, name);
            if (value != null && !isUnreadableBuilderValue(value)) return value;
        }
        return null;
    }

    /**
     * NPC builders keep their fields in ValueHolder wrappers (IntHolder, AssetHolder, ...) whose
     * value can only be resolved with an ExecutionContext that is not available here. Without this
     * guard formatValue() falls back to the class name and the overview shows rows like
     * "Max Health: Int Holder". The authored value still comes through the role config JSON.
     */
    private static boolean isUnreadableBuilderValue(Object value) {
        try {
            value.getClass().getMethod("getExpressionString");
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Object call(Object target, String methodName) {
        if (target == null || methodName == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            if (method.getParameterCount() == 0) return method.invoke(target);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Object readField(Object target, String fieldName) {
        for (Class<?> cursor = target.getClass(); cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Field field = cursor.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void forEachArrayOrCollection(Object value, java.util.function.Consumer<Object> consumer) {
        if (value instanceof Collection<?> collection) collection.forEach(consumer);
        else if (value != null && value.getClass().isArray()) for (int i = 0; i < Array.getLength(value); i++) consumer.accept(Array.get(value, i));
    }

    private static String formatValue(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean bool) return bool ? "Yes" : "No";
        if (value instanceof String text) {
            if (text.isEmpty()) return null;
            if (text.matches("P(T.*|\\d+D.*)")) {
                try {
                    return formatDuration(Duration.parse(text));
                } catch (Exception ignored) {
                }
            }
            return TextFormatters.dropSourceName(text);
        }
        if (value instanceof Duration duration) return formatDuration(duration);
        if (value instanceof double[] doubles) return range(doubles);
        if (value instanceof int[] ints) return range(ints);
        if (value instanceof Float || value instanceof Double) {
            double number = ((Number) value).doubleValue();
            if (Math.abs(number - Math.rint(number)) < 0.001d) return Integer.toString((int) Math.rint(number));
            return String.format(Locale.ENGLISH, "%.2f", number).replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        if (value instanceof Number number) return String.valueOf(number);
        if (value.getClass().isArray()) {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) {
                String part = formatValue(Array.get(value, i));
                if (part != null) parts.add(part);
            }
            return String.join(", ", parts);
        }
        if (value instanceof Collection<?> collection) {
            List<String> parts = new ArrayList<>();
            collection.forEach(v -> {
                String part = formatValue(v);
                if (part != null) parts.add(part);
            });
            return String.join(", ", parts);
        }
        return simpleClassName(value);
    }

    private static String stringValue(Object value) {
        return value instanceof String text && !text.isEmpty() ? normalizeId(text) : null;
    }

    private static String range(float min, float max) {
        return Math.abs(min - max) < 0.001f ? formatValue(min) : formatValue(min) + "-" + formatValue(max);
    }

    private static String range(double[] values) {
        return values == null || values.length == 0 ? null : values.length == 1 ? formatValue(values[0]) : formatValue(values[0]) + "-" + formatValue(values[values.length - 1]);
    }

    private static String range(int[] values) {
        return values == null || values.length == 0 ? null : values.length == 1 ? formatValue(values[0]) : values[0] + "-" + values[values.length - 1];
    }

    private static String quantity(int min, int max) {
        return min == max ? min + "x" : min + "-" + max + "x";
    }

    private static String chance(double value) {
        if (value <= 0 || value > 1) return "";
        return formatValue(value * 100.0d) + "%";
    }

    private static String formatDuration(Duration duration) {
        if (duration == null || duration.isZero()) return null;
        long seconds = duration.toSeconds();
        return seconds < 60 ? seconds + "s" : (seconds / 60) + "m";
    }

    private static String joinHumanized(String[] values) {
        if (values == null || values.length == 0) return null;
        List<String> labels = new ArrayList<>();
        for (String value : values) labels.add(TextFormatters.dropSourceName(value));
        return String.join(", ", labels);
    }

    private static String formatLightRanges(NPCSpawn spawn) {
        List<String> parts = new ArrayList<>();
        for (LightType type : LightType.values()) {
            String range = range(spawn.getLightRange(type));
            if (range != null && !"0-1".equals(range)) parts.add(TextFormatters.dropSourceName(type.name()) + " " + range);
        }
        return String.join(", ", parts);
    }

    private static String simpleClassName(Object value) {
        if (value == null) return null;
        String name = value instanceof Class<?> clazz ? clazz.getSimpleName() : value.getClass().getSimpleName();
        return TextFormatters.dropSourceName(name.replaceFirst("\\$.*$", ""));
    }

    private static String humanLabel(String value) {
        return TextFormatters.dropSourceName(value);
    }

    private static String normalizeId(String value) {
        if (value == null) return "";
        String normalized = value.replace('\\', '/');
        if (normalized.contains(":")) normalized = normalized.substring(normalized.indexOf(':') + 1);
        if (normalized.contains("/")) normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        return normalized.replaceFirst("(?i)\\.json$", "");
    }

    private static String normalizeKey(String value) {
        return normalizeId(value).replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ENGLISH);
    }

    private static String capitalize(String value) {
        return value == null || value.isEmpty() ? "" : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String decapitalize(String value) {
        return value == null || value.isEmpty() ? "" : Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isEmpty()) return value;
        return null;
    }

    private static String MobPortraitPath(String mobId) {
        return "hytems/ui/Assets/MobPortraits/" + mobId + ".png";
    }

    private static MobMetadata fallback(String mobId) {
        return new MobMetadata(mobId, TextFormatters.mobName(mobId), MobPortraitPath(mobId),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private static final class DropAccumulator {
        private final String itemId;
        private int min = Integer.MAX_VALUE;
        private int max;
        private double chance;

        private DropAccumulator(String itemId) {
            this.itemId = itemId;
        }
    }

    public record MobMetadata(String mobId, String displayName, String portraitPath, List<AttributeRow> attributes,
                              List<DropEntry> drops, List<VariantEntry> variants, List<SpawnGroup> spawnGroups) {
    }

    public record AttributeRow(String label, String value) {
    }

    public record DropEntry(String itemId, String displayName, String quantityLabel, String chanceLabel) {
    }

    public record VariantEntry(String itemId, String displayName) {
    }

    public record SpawnGroup(String title, List<SpawnEntry> entries) {
    }

    public record SpawnEntry(String title, List<String> details) {
    }

    private record HabitatLabel(String zone, String biome, String region) {
    }

    private static final class SpawnHabitatAggregate {
        private final String zone;
        private final String biome;
        private final String region;
        private final Set<String> specialLabels = new LinkedHashSet<>();
        private final Set<String> structures = new LinkedHashSet<>();

        private SpawnHabitatAggregate(String zone, String biome, String region) {
            this.zone = zone;
            this.biome = biome;
            this.region = region;
        }

        private String zone() {
            return this.zone == null || this.zone.isEmpty() ? "Unknown" : this.zone;
        }

        private List<String> detailLines() {
            List<String> details = new ArrayList<>();
            details.addAll(sortedSpecialLabels(this.specialLabels));
            addDetail(details, "Biome", this.biome);
            addDetail(details, "Region", this.region);
            if (!this.structures.isEmpty()) {
                addDetail(details, "Structure", String.join(", ", this.structures));
            }
            return dedupeDetailLines(details);
        }
    }

    private static List<String> sortedSpecialLabels(Set<String> labels) {
        List<String> sorted = new ArrayList<>(labels);
        sorted.sort(Comparator.comparingInt(MobMetadataRegistry::trailingNumber)
                .thenComparing(String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private static int trailingNumber(String value) {
        if (value == null) {
            return Integer.MAX_VALUE;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)\\s*$").matcher(value);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : Integer.MAX_VALUE;
    }

}
