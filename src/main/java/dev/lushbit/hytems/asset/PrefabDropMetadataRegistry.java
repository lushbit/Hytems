package dev.lushbit.hytems.asset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    private static final Pattern SPAWN_MARKER_PATTERN = Pattern.compile("\"SpawnMarker\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MARKER_TYPE_PATTERN = Pattern.compile("\"MarkerType\"\\s*:\\s*\"([^\"]+)\"");
    private static final PrefabDropMetadata EMPTY = new PrefabDropMetadata(null, Collections.emptyList());

    private static volatile Map<String, PrefabDropMetadata> cachedMetadata = Collections.emptyMap();
    private static volatile Map<String, List<String>> markerLocations = Collections.emptyMap();
    private static final Set<String> instanceNames = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile boolean loaded;
    private static volatile boolean loading;
    private static volatile long generation;

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

    /** Structures and instances a spawn marker is placed in, e.g. "Scarak Hives" or "The Deepworks". */
    @Nonnull
    public static List<String> markerLocations(String markerId) {
        ensureLoadedAsync();
        if (markerId == null || markerId.isEmpty()) return Collections.emptyList();
        return markerLocations.getOrDefault(markerId.toLowerCase(Locale.ENGLISH), Collections.emptyList());
    }

    /** Whether an instance (dungeon world) with this folder name exists, e.g. "Forgotten_Temple". */
    public static boolean isKnownInstance(String folderName) {
        return folderName != null && instanceNames.contains(folderName.toLowerCase(Locale.ENGLISH));
    }

    public static void startAsyncPreload() {
        ensureLoadedAsync();
    }

    public static synchronized void invalidate() {
        cachedMetadata = Collections.emptyMap();
        markerLocations = Collections.emptyMap();
        loaded = false;
        loading = false;
        generation++;
    }

    public static synchronized void invalidateAndReload() {
        invalidate();
        ensureLoadedAsync();
    }

    private static void ensureLoadedAsync() {
        if (loaded || loading) return;

        synchronized (PrefabDropMetadataRegistry.class) {
            if (loaded || loading) return;
            loading = true;
            long loadGeneration = generation;

            Thread loaderThread = new Thread(() -> {
                try {
                    Map<String, Set<String>> markers = new LinkedHashMap<>();
                    Map<String, PrefabDropMetadata> metadata = Collections.unmodifiableMap(loadMetadata(markers));
                    if (loadGeneration == generation) {
                        cachedMetadata = metadata;
                        markerLocations = sortedLocations(markers);
                        // Mob overviews cached before the scan finished have no marker locations yet.
                        MobMetadataRegistry.markNpcDataDirty();
                    }
                } catch (Exception e) {
                    System.err.println("[Hytems] Failed to load prefab drop metadata: " + e.getMessage());
                    if (loadGeneration == generation) cachedMetadata = Collections.emptyMap();
                } finally {
                    if (loadGeneration == generation) {
                        loaded = true;
                        loading = false;
                    }
                }
            }, "Hytems-PrefabDropMetadataLoader");
            loaderThread.setDaemon(true);
            loaderThread.start();
        }
    }

    private static Map<String, PrefabDropMetadata> loadMetadata(Map<String, Set<String>> markers) {
        Set<Path> serverRoots = ServerAssetRoots.serverRoots();
        if (serverRoots.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> containerByDropId = new LinkedHashMap<>();
        Map<String, Set<String>> structuresByDropId = new LinkedHashMap<>();

        for (Path serverRoot : serverRoots) {
            loadSpawnerMappings(serverRoot, containerByDropId);
            loadPrefabStructureMappings(serverRoot, structuresByDropId, markers);
            loadInstanceMarkerMappings(serverRoot, markers);
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

    private static void loadPrefabStructureMappings(Path serverRoot, Map<String, Set<String>> structuresByDropId,
                                                    Map<String, Set<String>> markers) {
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
                            String relativePath = serverRoot.relativize(path).toString().replace('\\', '/');
                            collectMarkerPlacements(text, relativePath, markers);
                            Matcher matcher = PREFAB_DROP_ID_PATTERN.matcher(text);
                            Set<String> matches = new LinkedHashSet<>();
                            while (matcher.find()) {
                                matches.add(matcher.group());
                            }

                            if (matches.isEmpty()) {
                                return;
                            }

                            String structureLabel = structureLabelFromPrefabPath(relativePath);
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

    private static void collectMarkerPlacements(String text, String relativePath, Map<String, Set<String>> markers) {
        if (!text.contains("\"SpawnMarker\"")) return;
        String location = markerLocationFromPrefabPath(relativePath);
        if (location == null) return;
        Matcher matcher = SPAWN_MARKER_PATTERN.matcher(text);
        while (matcher.find()) {
            markers.computeIfAbsent(matcher.group(1).toLowerCase(Locale.ENGLISH), ignored -> new LinkedHashSet<>()).add(location);
        }
    }

    /** Instances (dungeons like The Deepworks) trigger their markers from trigger volumes. */
    private static void loadInstanceMarkerMappings(Path serverRoot, Map<String, Set<String>> markers) {
        Path instancesDir = serverRoot.resolve("Instances");
        if (!Files.isDirectory(instancesDir)) return;
        try (Stream<Path> files = Files.walk(instancesDir)) {
            files.forEach(path -> {
                String fileName = path.getFileName().toString();
                if (fileName.equalsIgnoreCase("instance.bson") && path.getParent() != null) {
                    instanceNames.add(path.getParent().getFileName().toString().toLowerCase(Locale.ENGLISH));
                    return;
                }
                if (!fileName.equalsIgnoreCase("TriggerVolumeData.json")) return;
                Path resources = path.getParent();
                Path instanceDir = resources == null ? null : resources.getParent();
                if (instanceDir == null) return;
                String location = TextFormatters.dropSourceName(instanceDir.getFileName().toString());
                try {
                    Matcher matcher = MARKER_TYPE_PATTERN.matcher(Files.readString(path, StandardCharsets.UTF_8));
                    while (matcher.find()) {
                        markers.computeIfAbsent(matcher.group(1).toLowerCase(Locale.ENGLISH),
                                ignored -> new LinkedHashSet<>()).add(location);
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static Map<String, List<String>> sortedLocations(Map<String, Set<String>> markers) {
        Map<String, List<String>> sorted = new LinkedHashMap<>();
        markers.forEach((markerId, locations) -> {
            List<String> list = new ArrayList<>(locations);
            list.sort(String.CASE_INSENSITIVE_ORDER);
            sorted.put(markerId, Collections.unmodifiableList(list));
        });
        return Collections.unmodifiableMap(sorted);
    }

    /** Readable name for the structure a prefab belongs to, e.g. Prefabs/Npc/Scarak/Scarak_Hives/... -> "Scarak Hives". */
    static String markerLocationFromPrefabPath(String path) {
        String[] parts = path.replace('\\', '/').split("/");
        int start = 0;
        while (start < parts.length && !parts[start].equalsIgnoreCase("Prefabs")) start++;
        if (start + 2 >= parts.length) return null;
        String category = parts[start + 1];
        String first = parts[start + 2];
        String second = start + 3 < parts.length - 1 ? parts[start + 3] : null;
        switch (category.toLowerCase(Locale.ENGLISH)) {
            case "testing":
                return null;
            case "mineshaft":
                return "Mineshaft";
            case "dungeon": {
                String name = TextFormatters.dropSourceName(first);
                return name.matches("(?i).*(Lair|Temple|Crypt|Ruins|Labyrinth)") ? name : name + " Dungeon";
            }
            case "monuments": {
                String structure = structureLabelFromPrefabPath(path);
                if (structure != null) return structure;
                if (first.equalsIgnoreCase("Incidental") && second != null) {
                    return second.equalsIgnoreCase("Shipwrecks") ? "Shipwreck" : TextFormatters.dropSourceName(second) + " Ruins";
                }
                if (second == null) return "Monument";
                if (second.equalsIgnoreCase("Temple")) return "Portal Temple";
                if (second.equalsIgnoreCase("Start_Den")) return "Starting Den";
                return TextFormatters.dropSourceName(second);
            }
            case "npc": {
                String faction = TextFormatters.dropSourceName(first);
                if (first.equalsIgnoreCase("Dragons")) {
                    return second == null ? "Dragon Lair" : TextFormatters.dropSourceName(second) + " Dragon Lair";
                }
                if (second == null) return faction + " Camp";
                String lower = second.toLowerCase(Locale.ENGLISH);
                if (lower.contains(first.toLowerCase(Locale.ENGLISH))) return TextFormatters.dropSourceName(second);
                if (lower.contains("fort")) return faction + " Fort";
                if (lower.contains("outpost")) return faction + " Outpost";
                if (lower.contains("portal")) return faction + " Portal";
                if (lower.contains("gate")) return faction + " Gate";
                if (lower.contains("throne")) return faction + " Throne";
                return first.equalsIgnoreCase("Kweebec") ? "Kweebec Village" : faction + " Camp";
            }
            case "orbis":
                return orbisLocation(first, second);
            case "spawn":
                return "Starting Area";
            case "cave":
                return first.equalsIgnoreCase("Nodes") ? "Cave" : TextFormatters.dropSourceName(first) + " Cave";
            case "plants":
                return first.equalsIgnoreCase("Coral") ? "Coral Reef" : null;
            default:
                return TextFormatters.dropSourceName(category);
        }
    }

    /** Update 7 Orbis content, e.g. Orbis/Goblins/Encounter_Surface_Objective_Outpost_Void -> "Goblin Outpost (Void)". */
    private static String orbisLocation(String group, String folder) {
        if (folder == null) return TextFormatters.dropSourceName(group);
        String lower = folder.toLowerCase(Locale.ENGLISH);
        if (lower.contains("debug") || lower.startsWith("temp_")) return null;
        if (group.equalsIgnoreCase("WorldEvent")) return TextFormatters.dropSourceName(folder.replaceFirst("(?i)s$", "")) + " World Event";
        if (lower.contains("voidanomaly")) return "Void Anomaly";
        if (lower.contains("enkindled_quarry")) return "Enkindled Quarry";
        if (lower.contains("enkindled_ruin")) return "Enkindled Ruin";
        if (lower.contains("mountain_statue")) return "Goblin Mountain Statue";
        for (String structure : new String[]{"Outpost", "Tower", "Shack"}) {
            int index = lower.indexOf("_" + structure.toLowerCase(Locale.ENGLISH) + "_");
            if (index >= 0) {
                String variant = folder.substring(index + structure.length() + 2);
                return "Goblin " + structure + (variant.equalsIgnoreCase("Goblin") ? "" : " (" + TextFormatters.dropSourceName(variant) + ")");
            }
        }
        String voidSuffix = lower.contains("void") ? " (Void)" : "";
        if (lower.contains("slum_dungeon")) return "Goblin Slum Dungeon";
        if (lower.contains("slum")) return "Goblin Slum";
        if (lower.contains("cave_mine")) return "Goblin Mine";
        if (lower.contains("cave_crypt")) return "Goblin Crypt";
        if (lower.contains("cave_spider")) return "Goblin Spider Cave";
        if (lower.startsWith("incidental_cave")) return "Goblin Cave Camp" + voidSuffix;
        return TextFormatters.dropSourceName(group.replaceFirst("(?i)s$", "")) + " "
                + TextFormatters.dropSourceName(folder.replaceFirst("(?i)^(Encounter|Incidental)_", ""));
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
