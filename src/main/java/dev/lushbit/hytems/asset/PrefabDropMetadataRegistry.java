package dev.lushbit.hytems.asset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.assetstore.AssetMap;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import dev.lushbit.hytems.HytemsPlugin;
import dev.lushbit.hytems.ui.TextFormatters;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class PrefabDropMetadataRegistry {
    private static final Pattern PREFAB_DROP_ID_PATTERN = Pattern.compile("Zone\\d+_[A-Za-z]+_Tier\\d+");
    private static final PrefabDropMetadata EMPTY = new PrefabDropMetadata(null, Collections.emptyList());

    private static volatile Map<String, PrefabDropMetadata> cachedMetadata = Collections.emptyMap();
    private static volatile boolean loaded;
    private static volatile boolean loading;

    private PrefabDropMetadataRegistry() {
    }

    @Nonnull
    public static PrefabDropMetadata lookup(String dropSourceId) {
        ensureLoadedAsync();
        if (dropSourceId == null || dropSourceId.isEmpty()) {
            return EMPTY;
        }

        String normalized = normalizeDropSourceId(dropSourceId);
        PrefabDropMetadata metadata = cachedMetadata.get(normalized.toLowerCase(Locale.ENGLISH));
        return metadata != null ? metadata : EMPTY;
    }

    public static void startAsyncPreload() {
        ensureLoadedAsync();
    }

    private static void ensureLoadedAsync() {
        if (loaded || loading) return;

        synchronized (PrefabDropMetadataRegistry.class) {
            if (loaded || loading) return;
            loading = true;

            Thread loaderThread = new Thread(() -> {
                try {
                    cachedMetadata = Collections.unmodifiableMap(loadMetadata());
                } catch (Exception e) {
                    System.err.println("[Hytems] Failed to load prefab drop metadata: " + e.getMessage());
                    cachedMetadata = Collections.emptyMap();
                } finally {
                    loaded = true;
                    loading = false;
                }
            }, "Hytems-PrefabDropMetadataLoader");
            loaderThread.setDaemon(true);
            loaderThread.start();
        }
    }

    private static Map<String, PrefabDropMetadata> loadMetadata() {
        AssetMap<String, ItemDropList> dropMap = ItemDropList.getAssetMap();
        if (dropMap == null || dropMap.getAssetMap() == null || dropMap.getAssetMap().isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Path> serverRoots = collectServerRoots(dropMap);
        if (serverRoots.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> containerByDropId = new LinkedHashMap<>();
        Map<String, Set<String>> structuresByDropId = new LinkedHashMap<>();

        for (Path serverRoot : serverRoots) {
            loadSpawnerMappings(serverRoot, containerByDropId);
            loadPrefabStructureMappings(serverRoot, structuresByDropId);
        }

        Map<String, PrefabDropMetadata> metadata = new LinkedHashMap<>();
        Set<String> knownDropIds = new LinkedHashSet<>();
        knownDropIds.addAll(containerByDropId.keySet());
        knownDropIds.addAll(structuresByDropId.keySet());

        for (String dropId : knownDropIds) {
            String containerItemId = containerByDropId.get(dropId);
            String containerName = translatedItemName(containerItemId);
            List<String> structureLabels = new ArrayList<>(structuresByDropId.getOrDefault(dropId, Collections.emptySet()));
            structureLabels.sort(String.CASE_INSENSITIVE_ORDER);
            metadata.put(dropId.toLowerCase(Locale.ENGLISH), new PrefabDropMetadata(containerName, structureLabels));
        }

        return metadata;
    }

    private static Set<Path> collectServerRoots(AssetMap<String, ItemDropList> dropMap) {
        Set<Path> roots = new LinkedHashSet<>();
        for (String dropId : dropMap.getAssetMap().keySet()) {
            if (dropId == null) {
                continue;
            }
            Path path = dropMap.getPath(dropId);
            Path root = findServerRoot(path);
            if (root != null && Files.exists(root)) {
                roots.add(root);
            }
        }
        return roots;
    }

    private static Path findServerRoot(Path path) {
        if (path == null) {
            return null;
        }
        Path normalized = path.normalize();
        for (Path cursor = normalized; cursor != null; cursor = cursor.getParent()) {
            if ("Server".equalsIgnoreCase(String.valueOf(cursor.getFileName()))) {
                return cursor;
            }
        }
        return null;
    }

    private static void loadSpawnerMappings(Path serverRoot, Map<String, String> containerByDropId) {
        Path spawnersDir = serverRoot.resolve("Item").resolve("Block").resolve("Spawners").resolve("New");
        if (!Files.isDirectory(spawnersDir)) {
            return;
        }

        try (Stream<Path> files = Files.walk(spawnersDir)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase(Locale.ENGLISH).endsWith(".json"))
                    .forEach(path -> {
                        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                            JsonArray entries = root.has("Entries") ? root.getAsJsonArray("Entries") : null;
                            if (entries == null) {
                                return;
                            }

                            for (JsonElement element : entries) {
                                if (!element.isJsonObject()) {
                                    continue;
                                }

                                JsonObject object = element.getAsJsonObject();
                                String containerName = getString(object, "Name");
                                JsonObject components = getObject(object, "Components");
                                JsonObject nestedComponents = getObject(components, "Components");
                                JsonObject itemContainerBlock = getObject(nestedComponents, "ItemContainerBlock");
                                String dropListId = getString(itemContainerBlock, "Droplist");

                                if (dropListId != null && containerName != null
                                        && !dropListId.isEmpty() && !containerName.isEmpty()) {
                                    containerByDropId.put(dropListId, containerName);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static void loadPrefabStructureMappings(Path serverRoot, Map<String, Set<String>> structuresByDropId) {
        Path prefabsDir = serverRoot.resolve("Prefabs");
        if (!Files.isDirectory(prefabsDir)) {
            return;
        }

        try (Stream<Path> files = Files.walk(prefabsDir)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase(Locale.ENGLISH).endsWith(".prefab.json"))
                    .forEach(path -> {
                        try {
                            String text = Files.readString(path, StandardCharsets.UTF_8);
                            Matcher matcher = PREFAB_DROP_ID_PATTERN.matcher(text);
                            Set<String> matches = new LinkedHashSet<>();
                            while (matcher.find()) {
                                matches.add(matcher.group());
                            }

                            if (matches.isEmpty()) {
                                return;
                            }

                            String relative = serverRoot.relativize(path).toString().replace('\\', '/');
                            String structureLabel = structureLabelFromPrefabPath(relative);
                            if (structureLabel == null || structureLabel.isEmpty()) {
                                return;
                            }

                            for (String dropId : matches) {
                                structuresByDropId.computeIfAbsent(dropId, ignored -> new LinkedHashSet<>()).add(structureLabel);
                            }
                        } catch (Exception ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static String translatedItemName(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }

        if (HytemsPlugin.ITEMS != null) {
            String exact = itemId;
            if (HytemsPlugin.ITEMS.containsKey(exact)) {
                return TextFormatters.itemName(exact);
            }

            String namespaced = itemId.contains(":") ? itemId : "Hytale:" + itemId;
            if (HytemsPlugin.ITEMS.containsKey(namespaced)) {
                return TextFormatters.itemName(namespaced);
            }
        }

        return TextFormatters.itemName(itemId);
    }

    private static String structureLabelFromPrefabPath(String path) {
        String normalized = path.replace('\\', '/');
        String[] parts = normalized.split("/");
        if (parts.length < 2) {
            return null;
        }

        if (normalized.contains("/Dungeon/Labyrinth/")) {
            return "Labyrinth";
        }
        if (normalized.contains("/Mineshaft/")) {
            return "Mineshaft";
        }
        if (normalized.contains("/Monuments/Incidental/Treasure_Rooms/")) {
            return "Treasure Room";
        }
        if (normalized.contains("/Monuments/Encounter/")) {
            if (normalized.contains("/Outpost")) {
                return "World Encounter - Outpost";
            }
            if (normalized.contains("/Camp/") || normalized.contains("_Camp_")) {
                return "World Encounter - Camp";
            }
            return "World Encounter - Monument";
        }
        if (normalized.contains("/Npc/")) {
            String faction = segmentAfter(parts, "Npc");
            String label = normalized.contains("/Outpost/") ? "Outpost" : "Camp";
            return faction == null ? "NPC Structure" : TextFormatters.dropSourceName(faction) + " " + label;
        }

        return null;
    }

    private static String segmentAfter(String[] parts, String segmentName) {
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].equalsIgnoreCase(segmentName)) {
                return parts[i + 1];
            }
        }
        return null;
    }

    private static String normalizeDropSourceId(String dropSourceId) {
        String normalized = dropSourceId;
        if (normalized.contains("/")) {
            normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        }
        if (normalized.contains("\\")) {
            normalized = normalized.substring(normalized.lastIndexOf('\\') + 1);
        }
        if (normalized.toLowerCase(Locale.ENGLISH).endsWith(".json")) {
            normalized = normalized.substring(0, normalized.length() - 5);
        }
        return normalized;
    }

    private static JsonObject getObject(JsonObject parent, String memberName) {
        if (parent == null || memberName == null || !parent.has(memberName) || !parent.get(memberName).isJsonObject()) {
            return null;
        }
        return parent.getAsJsonObject(memberName);
    }

    private static String getString(JsonObject parent, String memberName) {
        if (parent == null || memberName == null || !parent.has(memberName)) {
            return null;
        }
        JsonElement element = parent.get(memberName);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    public static final class PrefabDropMetadata {
        private final String containerDisplayName;
        private final List<String> structureLabels;

        private PrefabDropMetadata(String containerDisplayName, List<String> structureLabels) {
            this.containerDisplayName = containerDisplayName;
            this.structureLabels = structureLabels;
        }

        public String containerDisplayName() {
            return containerDisplayName;
        }

        public List<String> structureLabels() {
            return structureLabels;
        }

        public boolean hasContainerDisplayName() {
            return containerDisplayName != null && !containerDisplayName.isEmpty();
        }

        public boolean hasStructureLabels() {
            return structureLabels != null && !structureLabels.isEmpty();
        }
    }
}
