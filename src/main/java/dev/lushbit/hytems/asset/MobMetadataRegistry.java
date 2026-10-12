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
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderInfo;
import com.hypixel.hytale.server.npc.asset.builder.BuilderManager;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.spawning.assets.spawnmarker.config.SpawnMarker;
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

public final class MobMetadataRegistry {
    private static final MobMetadata EMPTY = new MobMetadata("", "Unknown", null,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    private static final String MISSING_ITEM_ID = "__HYTEMS_MISSING_ITEM__";
    private static final Map<String, MobMetadata> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> ITEM_ID_LOOKUP = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Map<String, List<String>> PORTRAIT_HINTS = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> FLOCK_MEMBERS = new ConcurrentHashMap<>();
    private static volatile Set<String> deprecatedRoles;
    private static volatile Set<String> deprecatedRoleKeys;
    private static volatile Map<String, String> portalWorlds;
    private static volatile Map<String, String> environmentBiomes;
    private static final Set<String> ROLE_STRUCTURE_KEYS = Set.of("Type", "Reference", "Parameters", "Modify");
    public static final String PLACED_SPAWNS_TITLE = "Structures & Dungeons";
    private static final Map<String, String> MOB_ID_ALIASES = Map.of(
            "spectrevoid", "Void_Spectre",
            "spawnvoid", "Void_Spawn_Entombed"
    );
    private static volatile Map<String, List<String>> mobsByDropList = Collections.emptyMap();
    private static volatile Map<String, WorldNPCSpawn> worldNpcSpawns = Collections.emptyMap();
    private static volatile Map<String, BeaconNPCSpawn> beaconNpcSpawns = Collections.emptyMap();

    private MobMetadataRegistry() {
    }

    @Nonnull
    public static MobMetadata lookup(String mobId) {
        if (mobId == null || mobId.isEmpty()) {
            return EMPTY;
        }

        String normalized = canonicalMobId(mobId);
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

        String normalized = canonicalMobId(mobId);
        if (CACHE.containsKey(normalized)) {
            return;
        }

        ensureLoadedAsync(normalized);
    }

    public static void markNpcDataDirty() {
        mobsByDropList = Collections.emptyMap();
        PORTRAIT_HINTS.clear();
        FLOCK_MEMBERS.clear();
        deprecatedRoles = null;
        deprecatedRoleKeys = null;
        portalWorlds = null;
        environmentBiomes = null;
        BossRegistry.invalidate();
        for (String mobId : new ArrayList<>(CACHE.keySet())) {
            ensureLoadedAsync(mobId);
        }
    }

    public static void invalidateAll() {
        CACHE.clear();
        ITEM_ID_LOOKUP.clear();
        PORTRAIT_HINTS.clear();
        BossRegistry.invalidate();
        mobsByDropList = Collections.emptyMap();
    }

    /**
     * Ids the game itself ties a role to, most specific first: its Reference chain, then the
     * authored Appearance (portrait icons are named after models) and MemoriesNameOverride. This
     * lets variants like Coffer_Goblin_Miner, Risen_Knight or the Gobliterator phases reuse the
     * portrait of the mob they are built from.
     */
    @Nonnull
    public static List<String> portraitHints(String mobId) {
        if (mobId == null || mobId.isEmpty()) return Collections.emptyList();
        return PORTRAIT_HINTS.computeIfAbsent(normalizeId(mobId), MobMetadataRegistry::loadPortraitHints);
    }

    /** Hytale parks retired content (the Goblin Duke, old goblin variants) in _Deprecated folders. */
    public static boolean isDeprecatedRole(String roleId) {
        Set<String> keys = deprecatedRoleKeys;
        if (keys == null) {
            Set<String> built = new LinkedHashSet<>();
            for (String id : deprecatedRoleIds()) built.add(normalizeKey(id));
            deprecatedRoleKeys = keys = Collections.unmodifiableSet(built);
        }
        return keys.contains(normalizeKey(roleId));
    }

    /**
     * Spawn files named after a retired role belong to it, e.g. the Goblin_Duke_Phase_1 beacon that
     * sent reinforcements during the Goblin Duke fight.
     */
    private static boolean belongsToDeprecatedRole(String spawnId) {
        String key = normalizeId(spawnId).toLowerCase(Locale.ENGLISH);
        for (String roleId : deprecatedRoleIds()) {
            String role = roleId.toLowerCase(Locale.ENGLISH);
            if (key.equals(role) || key.startsWith(role + "_")) return true;
        }
        return false;
    }

    private static Set<String> deprecatedRoleIds() {
        Set<String> cached = deprecatedRoles;
        if (cached != null) return cached;
        Set<String> ids = new LinkedHashSet<>();
        for (String roleId : allRoleIds()) {
            if (roleDirectory(roleId).toLowerCase(Locale.ENGLISH).contains("_deprecated")) ids.add(roleId);
        }
        deprecatedRoles = Collections.unmodifiableSet(ids);
        return deprecatedRoles;
    }

    /** Every NPC role id, including the ones the mob browser hides. */
    @Nonnull
    public static Set<String> allRoleIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        collectBuilderMobIds(ids);
        return ids;
    }

    /** A string value authored on the role or inherited from its Reference chain, e.g. "_CombatConfig". */
    public static String inheritedRoleString(String roleId, String key) {
        BuilderInfo info = tryGetBuilderInfo(roleId);
        Object value = inheritedRoleValue(info, loadRoleConfig(info), key);
        return value instanceof String text && !text.isEmpty() ? text : null;
    }

    /** The role's folder below NPC/Roles, e.g. "Boss/Goblin_Gobliterator". */
    public static String roleDirectory(String roleId) {
        String[] parts = rolePathParts(tryGetBuilderInfo(roleId));
        return parts.length <= 1 ? "" : String.join("/", java.util.Arrays.copyOf(parts, parts.length - 1));
    }

    private static List<String> loadPortraitHints(String mobId) {
        List<String> hints = new ArrayList<>();
        String appearance = null;
        String memoryName = null;
        BuilderInfo cursor = tryGetBuilderInfo(mobId);
        for (int depth = 0; depth < 5 && cursor != null; depth++) {
            JsonObject config = loadRoleConfig(cursor);
            if (appearance == null) appearance = jsonString(config, "Appearance");
            if (memoryName == null) memoryName = jsonString(config, "MemoriesNameOverride");
            String referenced = referencedTemplateId(cursor);
            if (referenced == null) break;
            hints.add(referenced);
            cursor = tryGetBuilderInfo(referenced);
        }
        if (appearance != null) hints.add(appearance);
        if (memoryName != null) hints.add(memoryName);
        return Collections.unmodifiableList(hints);
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
        List<String> sorted = new ArrayList<>(ids);
        // A boss is listed once (its first phase); its other phase roles are shown on the boss page.
        // Dungeon-only bosses like Dungeon_Feastmaster_Boss stay visible despite the name filters.
        sorted.removeIf(id -> isDeprecatedRole(id) || BossRegistry.isHiddenMember(id)
                || (shouldHideFromMobBrowser(id) && !BossRegistry.isRepresentative(id)));
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

        // Script helpers and difficulty-specific hunt roles are implementation details, not
        // distinct bestiary entries. Their data is driven by the hunt/encounter itself.
        if (normalized.endsWith("marker")
                || normalized.matches("bearvoidtakend[123]")
                || normalized.equals("bearvoidtakententacle")) {
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

    private static void addMobId(Set<String> ids, String value) {
        String id = canonicalMobId(value);
        if (!id.isEmpty()) {
            ids.add(id);
        }
    }

    @Nonnull
    public static List<String> mobIdsForDropList(String dropListId) {
        if (dropListId == null || dropListId.isEmpty()) return Collections.emptyList();
        Map<String, List<String>> index = mobsByDropList;
        if (index.isEmpty()) {
            synchronized (MobMetadataRegistry.class) {
                if (mobsByDropList.isEmpty()) mobsByDropList = buildDropListMobIndex();
                index = mobsByDropList;
            }
        }
        return index.getOrDefault(normalizeDropListKey(dropListId), Collections.emptyList());
    }

    public static int mappedDropListCount() {
        mobIdsForDropList("__initialize__");
        return mobsByDropList.size();
    }

    private static Map<String, List<String>> buildDropListMobIndex() {
        Map<String, List<String>> index = new LinkedHashMap<>();
        for (String mobId : knownMobIds()) {
            // Boss loot usually sits on a later phase role (Gobliterator Phase 3), so credit the boss entry.
            BossRegistry.BossInfo boss = BossRegistry.forMob(mobId);
            Set<String> roles = boss != null ? boss.memberMobIds() : Set.of(mobId);
            Set<String> dropKeys = new LinkedHashSet<>();
            for (String roleId : roles) {
                String dropListId = dropListIdForRole(roleId);
                if (!dropListId.equalsIgnoreCase("Empty")) dropKeys.add(normalizeDropListKey(dropListId));
            }
            for (String dropKey : dropKeys) {
                index.computeIfAbsent(dropKey, ignored -> new ArrayList<>()).add(mobId);
            }
        }
        index.replaceAll((key, value) -> Collections.unmodifiableList(value));
        return Collections.unmodifiableMap(index);
    }

    private static String dropListIdForRole(String roleId) {
        BuilderInfo info = tryGetBuilderInfo(roleId);
        return resolveDropListId(roleId, info, info != null ? info.getBuilder() : null, loadRoleConfig(info));
    }

    private static MobMetadata load(String mobId) {
        BuilderInfo builderInfo = tryGetBuilderInfo(mobId);
        Object builder = builderInfo != null ? builderInfo.getBuilder() : null;
        JsonObject roleConfig = loadRoleConfig(builderInfo);
        List<AttributeRow> attributes = new ArrayList<>();
        addFamilyAttributes(attributes, mobId, builderInfo);
        addRoleConfigAttributes(attributes, builderInfo, roleConfig);
        addBuilderAttributes(attributes, builder);
        addModelAttributes(attributes, builder);

        String dropListId = resolveDropListId(mobId, builderInfo, builder, roleConfig);
        addAttribute(attributes, "DropList", TextFormatters.dropSourceName(dropListId));

        List<DropEntry> drops = loadDrops(dropListId);
        List<VariantEntry> variants = loadVariants(builder, roleConfig);
        List<SpawnGroup> spawns = loadSpawns(mobId);

        return new MobMetadata(mobId, TextFormatters.mobName(mobId), MobPortraitPath(mobId), dedupeAttributes(attributes), drops, variants, spawns);
    }

    private static String resolveDropListId(String mobId, BuilderInfo info, Object builder, JsonObject roleConfig) {
        String configured = firstNonBlank(jsonString(roleConfig, "DropList"),
                stringValue(readAny(builder, "dropList", "dropListId", "drops", "droplist")));
        if (configured != null && !configured.isEmpty()) return configured;

        // Role files frequently only reference a template. Read that template's authored
        // DropList directly when the runtime builder exposes it through a ValueHolder.
        String referenced = referencedTemplateId(info);
        if (referenced != null) {
            BuilderInfo referencedInfo = tryGetBuilderInfo(referenced);
            JsonObject referencedConfig = loadRoleConfig(referencedInfo);
            configured = firstNonBlank(jsonString(referencedConfig, "DropList"),
                    stringValue(readAny(referencedInfo != null ? referencedInfo.getBuilder() : null,
                            "dropList", "dropListId", "drops", "droplist")));
            if (configured != null && !configured.isEmpty()) return configured;
        }
        return "Drop_" + mobId;
    }

    private static String referencedTemplateId(BuilderInfo info) {
        if (info == null || info.getPath() == null || !Files.isRegularFile(info.getPath())) return null;
        try {
            JsonObject raw = JsonParser.parseString(Files.readString(info.getPath(), StandardCharsets.UTF_8)).getAsJsonObject();
            return jsonPrimitiveString(raw.get("Reference"));
        } catch (Exception ignored) {
            return null;
        }
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

    private static void addRoleConfigAttributes(List<AttributeRow> rows, BuilderInfo info, JsonObject roleConfig) {
        if (roleConfig == null) return;
        for (String key : List.of("MaxHealth", "Health", "AttackDistance", "TargetRange", "DesiredAttackDistanceRange",
                "CombatBehaviorDistance", "HearingRange", "ViewSector", "IsMemory", "MemoriesCategory",
                "MemoriesNameOverride", "BreathesInWater", "BreathesInAir", "WanderRadius", "Patrol",
                "FollowPatrolPath", "ApplySeparation", "WakingPeriod", "LeashMinPlayerDistance",
                "LeashDistance", "HardLeashDistance", "Attack", "RootInteraction", "Appearance")) {
            addAttribute(rows, humanLabel(key), jsonValue(roleConfig, key));
            if (key.equals("MemoriesNameOverride")) addMemoryUnlockAttributes(rows, info, roleConfig);
        }
        String[] weapons = jsonStringArray(roleConfig, "Weapons");
        if (weapons.length > 0) addAttribute(rows, "Weapons", joinHumanized(weapons));
    }

    private static void addMemoryUnlockAttributes(List<AttributeRow> rows, BuilderInfo info, JsonObject roleConfig) {
        if (!Boolean.TRUE.equals(jsonValue(roleConfig, "IsMemory"))) return;
        addAttribute(rows, humanLabel("MemoriesUnlockedBy"), inheritedRoleValue(info, roleConfig, "MemoriesUnlockedBy"));
        // A kill range of 0 means "server default", which tells the player nothing.
        Object killRange = inheritedRoleValue(info, roleConfig, "MemoriesKillRange");
        if (killRange instanceof Number number && number.doubleValue() > 0) {
            addAttribute(rows, humanLabel("MemoriesKillRange"), killRange);
        }
    }

    /** Variants usually leave these to their template, so walk up the Reference chain. */
    private static Object inheritedRoleValue(BuilderInfo info, JsonObject roleConfig, String key) {
        BuilderInfo cursor = info;
        JsonObject config = roleConfig;
        for (int depth = 0; depth < 5 && cursor != null; depth++) {
            Object value = jsonValue(config, key);
            if (value != null) return value;
            String referenced = referencedTemplateId(cursor);
            if (referenced == null) return null;
            cursor = tryGetBuilderInfo(referenced);
            config = loadRoleConfig(cursor);
        }
        return null;
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
        extractDrops(list.getContainer(), merged, new HashSet<>(), 1.0d, true);
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

    private static void extractDrops(ItemDropContainer container, Map<String, DropAccumulator> out,
                                     Set<String> seenDropLists, double chance, boolean applyOwnWeight) {
        if (container == null) return;
        double weightedChance = chance * (applyOwnWeight ? normalizeChanceWeight(container.getWeight()) : 1.0d);
        if (container instanceof SingleItemDropContainer single) {
            addDrop(out, single.getDrop(), weightedChance);
            return;
        }
        if (container instanceof MultipleItemDropContainer) {
            Object children = readField(container, "containers");
            forEachArrayOrCollection(children, child -> {
                if (!(child instanceof ItemDropContainer childContainer)) return;
                Map<String, DropAccumulator> childDrops = new LinkedHashMap<>();
                extractDrops(childContainer, childDrops, new HashSet<>(seenDropLists), 1.0d, true);
                int minCount = positiveInt(readField(container, "minCount"), 1);
                int maxCount = Math.max(minCount, positiveInt(readField(container, "maxCount"), minCount));
                mergeRepeatedDrops(out, childDrops, weightedChance, minCount, maxCount);
            });
            return;
        }
        if (container instanceof ChoiceItemDropContainer) {
            Object weighted = readField(container, "containers");
            if (weighted instanceof IWeightedMap<?> map) {
                List<WeightedContainer> choices = new ArrayList<>();
                map.forEachEntry((child, weight) -> {
                    if (child instanceof ItemDropContainer childContainer && weight > 0) {
                        choices.add(new WeightedContainer(childContainer, weight));
                    }
                });
                double totalWeight = choices.stream().mapToDouble(WeightedContainer::weight).sum();
                int rollsMin = positiveInt(readField(container, "rollsMin"), 1);
                int rollsMax = Math.max(rollsMin, positiveInt(readField(container, "rollsMax"), rollsMin));
                for (WeightedContainer choice : choices) {
                    Map<String, DropAccumulator> childDrops = new LinkedHashMap<>();
                    extractDrops(choice.container(), childDrops, new HashSet<>(seenDropLists), 1.0d, false);
                    double share = totalWeight <= 0 ? 0 : choice.weight() / totalWeight;
                    mergeChoiceDrops(out, childDrops, weightedChance, share, rollsMin, rollsMax);
                }
            }
            return;
        }
        if (container instanceof DroplistItemDropContainer) {
            String referenced = stringValue(readField(container, "droplistId"));
            if (referenced != null && seenDropLists.add(referenced)) {
                ItemDropList referencedList = findDropList(referenced);
                if (referencedList != null) extractDrops(referencedList.getContainer(), out, seenDropLists, weightedChance, true);
                seenDropLists.remove(referenced);
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
        acc.chance = combineChance(acc.chance, chance);
    }

    private static void mergeRepeatedDrops(Map<String, DropAccumulator> out, Map<String, DropAccumulator> source,
                                           double parentChance, int minCount, int maxCount) {
        for (DropAccumulator child : source.values()) {
            DropAccumulator target = out.computeIfAbsent(child.itemId, DropAccumulator::new);
            target.min = Math.min(target.min, child.min * minCount);
            target.max = Math.max(target.max, child.max * maxCount);
            target.chance = combineChance(target.chance,
                    parentChance * averageRepeatedChance(child.chance, minCount, maxCount));
        }
    }

    private static void mergeChoiceDrops(Map<String, DropAccumulator> out, Map<String, DropAccumulator> source,
                                         double parentChance, double choiceShare, int rollsMin, int rollsMax) {
        for (DropAccumulator child : source.values()) {
            DropAccumulator target = out.computeIfAbsent(child.itemId, DropAccumulator::new);
            target.min = Math.min(target.min, child.min);
            target.max = Math.max(target.max, child.max * rollsMax);
            double chancePerRoll = choiceShare * child.chance;
            target.chance = combineChance(target.chance,
                    parentChance * averageRepeatedChance(chancePerRoll, rollsMin, rollsMax));
        }
    }

    static double averageRepeatedChance(double chance, int minRolls, int maxRolls) {
        if (chance <= 0) return 0;
        double total = 0;
        for (int rolls = minRolls; rolls <= maxRolls; rolls++) {
            total += 1.0d - Math.pow(1.0d - Math.min(1.0d, chance), rolls);
        }
        return total / Math.max(1, maxRolls - minRolls + 1);
    }

    static double combineChance(double existing, double additional) {
        return 1.0d - ((1.0d - Math.min(1.0d, existing)) * (1.0d - Math.min(1.0d, additional)));
    }

    private static int positiveInt(Object value, int fallback) {
        return value instanceof Number number && number.intValue() > 0 ? number.intValue() : fallback;
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
        List<SpawnGroup> groups = new ArrayList<>();
        if (!entries.isEmpty()) groups.add(new SpawnGroup("Spawn & Habitat", entries));
        List<SpawnEntry> placed = loadMarkerSpawns(mobId);
        if (!placed.isEmpty()) groups.add(new SpawnGroup(PLACED_SPAWNS_TITLE, placed));
        return groups;
    }

    /**
     * Spawn markers are hand-placed in structures and dungeons. Bosses, camps and dungeon mobs
     * only spawn this way, so world/beacon spawns alone leave their overview empty.
     */
    @Nonnull
    public static List<SpawnEntry> loadMarkerSpawns(String mobId) {
        Map<String, Set<String>> detailsByLocation = new LinkedHashMap<>();
        String target = normalizeKey(mobId);
        for (SpawnMarker marker : MobMetadataRegistry.<SpawnMarker>safeMap(SpawnMarker.getAssetMap()).values()) {
            if (marker == null || marker.getWeightedConfigurations() == null || isIgnoredMarker(marker)) continue;
            List<SpawnMarker.SpawnConfiguration> matching = new ArrayList<>();
            double[] totalWeight = {0};
            marker.getWeightedConfigurations().forEach(config -> {
                if (config == null) return;
                totalWeight[0] += Math.max(0, config.getWeight());
                if (normalizeKey(config.getNpc()).equals(target)) matching.add(config);
            });
            if (matching.isEmpty()) continue;

            List<String> details = markerDetails(marker, matching, totalWeight[0]);
            List<String> locations = PrefabDropMetadataRegistry.markerLocations(marker.getId());
            if (locations.isEmpty()) {
                // A marker without a known structure only tells "respawns after a day", which reads as noise.
                String instance = markerInstanceLocation(marker);
                if (instance == null) continue;
                locations = List.of(instance);
            }
            for (String location : locations) {
                detailsByLocation.computeIfAbsent(location, ignored -> new LinkedHashSet<>()).addAll(details);
            }
        }

        List<SpawnEntry> entries = new ArrayList<>();
        detailsByLocation.forEach((location, details) -> entries.add(new SpawnEntry(location, new ArrayList<>(details))));
        entries.sort(Comparator.comparing(SpawnEntry::title, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private static boolean isIgnoredMarker(SpawnMarker marker) {
        String path = markerAssetPath(marker);
        String id = normalizeKey(marker.getId());
        return id.contains("test") || id.contains("debug")
                || path.contains("/tests/") || path.contains("/_deprecated/");
    }

    private static List<String> markerDetails(SpawnMarker marker, List<SpawnMarker.SpawnConfiguration> matching,
                                              double totalWeight) {
        List<String> details = new ArrayList<>();
        if (marker.isManualTrigger()) details.add("Spawned by an event or encounter");
        for (SpawnMarker.SpawnConfiguration config : matching) {
            if (marker.isRealtimeRespawn() && config.getRealtimeRespawnTime() > 0) {
                details.add("Respawns after " + formatSeconds(config.getRealtimeRespawnTime()) + " (real time)");
            } else if (config.getSpawnAfterGameTime() != null && !config.getSpawnAfterGameTime().isZero()) {
                details.add("Respawns after " + formatGameDuration(config.getSpawnAfterGameTime()) + " (in-game)");
            }
            if (totalWeight > 0 && config.getWeight() < totalWeight) {
                details.add("Spawn chance: " + Math.round(config.getWeight() * 100 / totalWeight) + "%");
            }
        }
        return details;
    }

    /** Markers that only live in instance chunk data are grouped by instance, e.g. Instance/Forgotten_Temple. */
    private static String markerInstanceLocation(SpawnMarker marker) {
        String path = originalMarkerPath(marker);
        int index = path.toLowerCase(Locale.ENGLISH).indexOf("/instance/");
        if (index >= 0) {
            String rest = path.substring(index + "/instance/".length());
            int slash = rest.indexOf('/');
            // Marker folders can be named after instances that no longer exist (Instance/Dungeon_Goblin).
            if (slash > 0 && PrefabDropMetadataRegistry.isKnownInstance(rest.substring(0, slash))) {
                return TextFormatters.dropSourceName(rest.substring(0, slash));
            }
        }
        return null;
    }

    private static String markerAssetPath(SpawnMarker marker) {
        return originalMarkerPath(marker).toLowerCase(Locale.ENGLISH);
    }

    private static String originalMarkerPath(SpawnMarker marker) {
        try {
            Path path = SpawnMarker.getAssetMap().getPath(marker.getId());
            return path == null ? "" : path.toString().replace('\\', '/');
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String formatGameDuration(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes >= 1440 && minutes % 1440 == 0) return plural(minutes / 1440, "day");
        if (minutes >= 60 && minutes % 60 == 0) return plural(minutes / 60, "hour");
        if (minutes > 0) return plural(minutes, "minute");
        return plural(duration.toSeconds(), "second");
    }

    private static String formatSeconds(double seconds) {
        return seconds >= 60 && seconds % 60 == 0 ? plural((long) seconds / 60, "minute") : plural(Math.round(seconds), "second");
    }

    private static String plural(long value, String unit) {
        return value + " " + unit + (value == 1 ? "" : "s");
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
                if (!spawnsMob(mobId, npc.getId())) continue;
                HabitatLabel label = habitatLabelForSpawn(spawn);
                String spawnId = normalizeId(spawn.getId());
                boolean portalWorld = portalWorldName(spawn.getEnvironments()) != null;
                if (!portalWorld && (isIgnoredSpawnId(spawnId) || isEventSpawnLabel(label.zone()))) continue;
                if (belongsToDeprecatedRole(spawnId)) continue;
                String elementalCircle = elementalCircleLabel(spawnId);
                if (isTriggeredBeacon(spawn, spawnId)) {
                    // Beacons without environments are fired by events (hunt sites, encounters), not by the
                    // world; their light/height values are relative to the trigger and mean nothing here.
                    HabitatLabel eventLabel = new HabitatLabel("Event Spawn", null, null);
                    aggregates.computeIfAbsent(eventLabel.zone() + "|null|null",
                                    ignored -> new SpawnHabitatAggregate(eventLabel.zone(), null, null))
                            .specialLabels.add("Triggered by an event or encounter");
                    continue;
                }

                String key = label.zone() + "|" + label.biome() + "|" + label.region();
                HabitatLabel aggregateLabel = label;
                SpawnHabitatAggregate aggregate = aggregates.computeIfAbsent(key,
                        ignored -> new SpawnHabitatAggregate(aggregateLabel.zone(), aggregateLabel.biome(), aggregateLabel.region()));

                if (elementalCircle != null) {
                    aggregate.specialLabels.add(elementalCircle);
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
                addSpawnConditions(aggregate, spawn);
            }
        }
        for (SpawnHabitatAggregate aggregate : aggregates.values()) {
            entries.add(new SpawnEntry(aggregate.zone(), aggregate.detailLines()));
        }
    }

    private static void addSpawnConditions(SpawnHabitatAggregate aggregate, NPCSpawn spawn) {
        if (spawn == null) return;
        Set<String> conditions = aggregate.conditions;
        double[] lightRange = spawn.getLightRange(LightType.Light);
        if (lightRange != null && lightRange.length > 0
                && !java.util.Arrays.equals(lightRange, NPCSpawn.FULL_LIGHT_RANGE)) {
            conditions.add("Light level: " + range(lightRange));
        }

        double[] dayTime = spawn.getDayTimeRange();
        if (dayTime != null && dayTime.length > 0
                && !java.util.Arrays.equals(dayTime, NPCSpawn.DEFAULT_DAY_TIME_RANGE)) {
            conditions.add("Time: " + formatDayTimeRange(dayTime));
        }
        if (spawn instanceof BeaconNPCSpawn beacon) {
            // Several beacons often share one habitat (void surge tiers), so merge their weathers into one line.
            // Beacon height ranges are offsets around the beacon, not world heights, so they are not shown.
            if (beacon.getWeatherIds() != null) {
                for (String weather : beacon.getWeatherIds()) {
                    if (weather != null && !weather.isEmpty()) aggregate.weathers.add(TextFormatters.dropSourceName(weather));
                }
            }
        }
    }

    private static HabitatLabel habitatLabelForSpawn(NPCSpawn spawn) {
        String[] environments = spawn == null ? null : spawn.getEnvironments();
        String portalWorld = portalWorldName(environments);
        if (portalWorld != null) {
            if (environments.length != 1) return new HabitatLabel(portalWorld, null, null);
            String area = portalArea(environments[0]);
            return new HabitatLabel(portalWorld, environmentBiome(environments[0]), area == null ? null : TextFormatters.humanize(area));
        }
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

    private static final java.util.regex.Pattern PORTAL_ENVIRONMENT =
            java.util.regex.Pattern.compile("(?i)^Env_Portals?_([A-Za-z]+?)(?:_(.+))?$");

    /**
     * Portal worlds (the Guttering Hollows behind a Goblin Breach) have their own environments named
     * after the portal instance: Env_Portal_Goblin_Cave belongs to instance Portals/Portals_Goblins,
     * whose PortalType carries the display name.
     */
    private static String portalWorldName(String[] environments) {
        if (environments == null) return null;
        for (String environment : environments) {
            if (environment == null) continue;
            java.util.regex.Matcher matcher = PORTAL_ENVIRONMENT.matcher(normalizeId(environment));
            if (!matcher.matches()) continue;
            String name = portalWorldNames().get(portalToken(matcher.group(1)));
            return name != null ? name : TextFormatters.dropSourceName(matcher.group(1)) + " Portal World";
        }
        return null;
    }

    /** The part of a portal world an environment covers, e.g. Env_Portal_Goblin_Cave_Deep -> "Cave_Deep" (formatted later). */
    private static String portalArea(String environment) {
        java.util.regex.Matcher matcher = PORTAL_ENVIRONMENT.matcher(normalizeId(environment));
        return matcher.matches() ? matcher.group(2) : null;
    }

    /** First biome tag of an environment, e.g. Env_Portal_Goblin_Surface is tagged Zone1: [Forests]. */
    private static String environmentBiome(String environment) {
        Map<String, String> biomes = environmentBiomes;
        if (biomes == null) {
            Map<String, String> loaded = new LinkedHashMap<>();
            for (ServerAssetRoots.JsonFile file : ServerAssetRoots.loadJsonFiles("Environments").values()) {
                JsonElement tags = file.json().get("Tags");
                if (tags == null || !tags.isJsonObject()) continue;
                for (Map.Entry<String, JsonElement> tag : tags.getAsJsonObject().entrySet()) {
                    // A "Portals" tag only repeats the portal name.
                    if (tag.getKey().equalsIgnoreCase("Portals") || !tag.getValue().isJsonArray()
                            || tag.getValue().getAsJsonArray().isEmpty()) continue;
                    String biome = jsonPrimitiveString(tag.getValue().getAsJsonArray().get(0));
                    if (biome != null) loaded.put(normalizeKey(file.name()), TextFormatters.humanize(biome));
                    break;
                }
            }
            environmentBiomes = biomes = Collections.unmodifiableMap(loaded);
        }
        return biomes.get(normalizeKey(environment));
    }

    private static String portalToken(String value) {
        return value.toLowerCase(Locale.ENGLISH).replaceFirst("s$", "");
    }

    private static Map<String, String> portalWorldNames() {
        Map<String, String> cached = portalWorlds;
        if (cached != null) return cached;
        Map<String, String> names = new LinkedHashMap<>();
        for (ServerAssetRoots.JsonFile file : ServerAssetRoots.loadJsonFiles("PortalTypes").values()) {
            String instanceId = jsonPrimitiveString(file.json().get("InstanceId"));
            JsonElement description = file.json().get("Description");
            String nameKey = description != null && description.isJsonObject()
                    ? jsonPrimitiveString(description.getAsJsonObject().get("DisplayName")) : null;
            if (instanceId == null) continue;
            String instance = instanceId.substring(instanceId.lastIndexOf('/') + 1).replaceFirst("(?i)^Portals?_", "");
            String name = null;
            try {
                if (nameKey != null) name = I18nModule.get().getMessage(I18nModule.DEFAULT_LANGUAGE, nameKey);
            } catch (Exception ignored) {
            }
            if (name == null || name.equals(nameKey)) name = TextFormatters.dropSourceName(file.name());
            names.put(portalToken(instance), name);
        }
        portalWorlds = Collections.unmodifiableMap(names);
        return portalWorlds;
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
        copyRoleConfigTopLevel(raw, flattened);
        copyRoleConfigSection(raw, "Modify", flattened, false);
        return flattened;
    }

    /**
     * Generic and Abstract roles author DropList, Appearance, MemoriesNameOverride, ... at the top
     * level instead of in Modify (e.g. the Gobliterator boss templates since 0.7.0).
     */
    private static void copyRoleConfigTopLevel(JsonObject source, JsonObject target) {
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            String key = entry.getKey();
            if (ROLE_STRUCTURE_KEYS.contains(key) || key.startsWith("$") || target.has(key)) continue;
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive()) {
                target.add(key, value);
            } else if (value.isJsonObject() && value.getAsJsonObject().has("Compute")) {
                String computedKey = jsonPrimitiveString(value.getAsJsonObject().get("Compute"));
                if (computedKey != null && target.has(computedKey)) {
                    target.add(key, target.get(computedKey));
                }
            }
        }
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

    /**
     * World and beacon spawns name exact roles. A mob also spawns as part of another role's herd when
     * that role lists it in its FlockArray (Cow spawns with Cow_Calf), so babies keep their habitat.
     */
    private static boolean spawnsMob(String mobId, String spawnedRole) {
        String target = normalizeKey(mobId);
        if (normalizeKey(spawnedRole).equals(target)) return true;
        return FLOCK_MEMBERS.computeIfAbsent(normalizeKey(spawnedRole), ignored -> flockMembers(spawnedRole)).contains(target);
    }

    private static Set<String> flockMembers(String roleId) {
        Object flock = inheritedRoleValue(tryGetBuilderInfo(roleId), loadRoleConfig(tryGetBuilderInfo(roleId)), "FlockArray");
        if (!(flock instanceof List<?> members)) return Set.of();
        Set<String> keys = new LinkedHashSet<>();
        for (Object member : members) if (member instanceof String id) keys.add(normalizeKey(id));
        return keys;
    }

    private static boolean isTriggeredBeacon(NPCSpawn spawn, String spawnId) {
        return spawn instanceof BeaconNPCSpawn
                && (spawn.getEnvironments() == null || spawn.getEnvironments().length == 0)
                && zoneFromPath(spawnId) == null;
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

    static String formatDayTimeRange(double[] values) {
        if (values == null || values.length == 0) return null;
        if (values.length == 1) return formatClockTime(values[0]);
        return formatClockTime(values[0]) + "-" + formatClockTime(values[values.length - 1]);
    }

    private static String formatClockTime(double value) {
        double hours = Math.abs(value) <= 1.0d ? value * NPCSpawn.HOURS_PER_DAY : value;
        int totalMinutes = (int) Math.round(hours * 60.0d);
        if (totalMinutes == (int) NPCSpawn.HOURS_PER_DAY * 60) return "24:00";
        totalMinutes = Math.floorMod(totalMinutes, (int) NPCSpawn.HOURS_PER_DAY * 60);
        return String.format(Locale.ENGLISH, "%02d:%02d", totalMinutes / 60, totalMinutes % 60);
    }

    private static String joinHumanized(String[] values) {
        if (values == null || values.length == 0) return null;
        List<String> labels = new ArrayList<>();
        for (String value : values) labels.add(TextFormatters.dropSourceName(value));
        return String.join(", ", labels);
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

    private static String canonicalMobId(String value) {
        String id = normalizeId(value);
        return MOB_ID_ALIASES.getOrDefault(normalizeKey(id), id);
    }

    private static String normalizeDropListKey(String value) {
        return normalizeKey(value).replaceFirst("^drops?", "");
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

    private record WeightedContainer(ItemDropContainer container, double weight) {
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
        private final Set<String> conditions = new LinkedHashSet<>();
        private final Set<String> weathers = new LinkedHashSet<>();

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
            // Biome and region are already readable; formatting them again doubles the spaces.
            if (this.biome != null && !this.biome.isEmpty()) details.add("Biome: " + this.biome);
            if (this.region != null && !this.region.isEmpty()) details.add("Region: " + this.region);
            if (!this.structures.isEmpty()) {
                addDetail(details, "Structure", String.join(", ", this.structures));
            }
            details.addAll(this.conditions);
            if (!this.weathers.isEmpty()) details.add("Weather: " + String.join(", ", this.weathers));
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
