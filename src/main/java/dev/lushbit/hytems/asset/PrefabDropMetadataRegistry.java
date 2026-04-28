package dev.lushbit.hytems.asset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lushbit.hytems.ui.TextFormatters;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PrefabDropMetadataRegistry {
    private static final Pattern PREFAB_DROP_ID_PATTERN = Pattern.compile("Zone\\d+_[A-Za-z]+_Tier\\d+");
    private static final PrefabDropMetadata EMPTY = new PrefabDropMetadata(null, Collections.emptyList());

    private static volatile Map<String, PrefabDropMetadata> cachedMetadata = Collections.emptyMap();
    private static volatile boolean loaded;

    private PrefabDropMetadataRegistry() {
    }

    @Nonnull
    public static PrefabDropMetadata lookup(String dropSourceId) {
        ensureLoaded();
        if (dropSourceId == null || dropSourceId.isEmpty()) {
            return EMPTY;
        }

        String normalized = normalizeDropSourceId(dropSourceId);
        PrefabDropMetadata metadata = cachedMetadata.get(normalized.toLowerCase(Locale.ENGLISH));
        return metadata != null ? metadata : EMPTY;
    }

    public static void prewarm() {
        ensureLoaded();
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        synchronized (PrefabDropMetadataRegistry.class) {
            if (loaded) {
                return;
            }

            try {
                cachedMetadata = Collections.unmodifiableMap(loadMetadata());
            } catch (Exception e) {
                System.err.println("[Hytems] Failed to load prefab drop metadata: " + e.getMessage());
                cachedMetadata = Collections.emptyMap();
            }
            loaded = true;
        }
    }

    private static Map<String, PrefabDropMetadata> loadMetadata() throws IOException {
        Path assetsPath = resolveAssetsZipPath();
        if (assetsPath == null) {
            return Collections.emptyMap();
        }

        Map<String, String> containerByDropId = new LinkedHashMap<>();
        Map<String, String> itemTranslations = new LinkedHashMap<>();
        Map<String, Set<String>> structuresByDropId = new LinkedHashMap<>();

        try (ZipFile zip = new ZipFile(assetsPath.toFile())) {
            loadItemTranslations(zip, itemTranslations);
            loadSpawnerMappings(zip, containerByDropId);
            loadPrefabStructureMappings(zip, structuresByDropId);
        }

        Map<String, PrefabDropMetadata> metadata = new LinkedHashMap<>();
        Set<String> knownDropIds = new LinkedHashSet<>();
        knownDropIds.addAll(containerByDropId.keySet());
        knownDropIds.addAll(structuresByDropId.keySet());

        for (String dropId : knownDropIds) {
            String containerItemId = containerByDropId.get(dropId);
            String containerName = translatedItemName(itemTranslations, containerItemId);
            List<String> structureLabels = new ArrayList<>(structuresByDropId.getOrDefault(dropId, Collections.emptySet()));
            structureLabels.sort(String.CASE_INSENSITIVE_ORDER);
            metadata.put(dropId.toLowerCase(Locale.ENGLISH), new PrefabDropMetadata(containerName, structureLabels));
        }

        return metadata;
    }

    private static Path resolveAssetsZipPath() {
        List<Path> candidates = List.of(
                Paths.get("server", "Assets.zip"),
                Paths.get("Assets.zip")
        );

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static void loadItemTranslations(ZipFile zip, Map<String, String> itemTranslations) throws IOException {
        ZipEntry entry = zip.getEntry("Server/Languages/en-US/server.lang");
        if (entry == null) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int equalsIndex = line.indexOf('=');
                if (equalsIndex < 0) {
                    continue;
                }

                String key = line.substring(0, equalsIndex).trim();
                if (!key.startsWith("items.") || !key.endsWith(".name")) {
                    continue;
                }

                String itemId = key.substring("items.".length(), key.length() - ".name".length());
                String value = line.substring(equalsIndex + 1).trim();
                if (!itemId.isEmpty() && !value.isEmpty()) {
                    itemTranslations.put(itemId.toLowerCase(Locale.ENGLISH), value);
                }
            }
        }
    }

    private static void loadSpawnerMappings(ZipFile zip, Map<String, String> containerByDropId) throws IOException {
        for (ZipEntry entry : Collections.list(zip.entries())) {
            String path = entry.getName();
            if (!path.startsWith("Server/Item/Block/Spawners/New/") || !path.endsWith(".json")) {
                continue;
            }

            try (InputStream input = zip.getInputStream(entry);
                 InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray entries = root.has("Entries") ? root.getAsJsonArray("Entries") : null;
                if (entries == null) {
                    continue;
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

                    if (dropListId != null && containerName != null && !dropListId.isEmpty() && !containerName.isEmpty()) {
                        containerByDropId.put(dropListId, containerName);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void loadPrefabStructureMappings(ZipFile zip, Map<String, Set<String>> structuresByDropId) throws IOException {
        for (ZipEntry entry : Collections.list(zip.entries())) {
            String path = entry.getName();
            if (!path.startsWith("Server/Prefabs/") || !path.endsWith(".prefab.json")) {
                continue;
            }

            String text;
            try (InputStream input = zip.getInputStream(entry);
                 InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                text = readAll(reader);
            }

            Matcher matcher = PREFAB_DROP_ID_PATTERN.matcher(text);
            Set<String> matches = new LinkedHashSet<>();
            while (matcher.find()) {
                matches.add(matcher.group());
            }

            if (matches.isEmpty()) {
                continue;
            }

            String structureLabel = structureLabelFromPrefabPath(path);
            if (structureLabel == null || structureLabel.isEmpty()) {
                continue;
            }

            for (String dropId : matches) {
                structuresByDropId.computeIfAbsent(dropId, ignored -> new LinkedHashSet<>()).add(structureLabel);
            }
        }
    }

    private static String translatedItemName(Map<String, String> itemTranslations, String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }

        String translated = itemTranslations.get(itemId.toLowerCase(Locale.ENGLISH));
        return translated != null ? translated : TextFormatters.itemName(itemId);
    }

    private static String structureLabelFromPrefabPath(String path) {
        String normalized = path.replace('\\', '/');
        String[] parts = normalized.split("/");
        if (parts.length < 3) {
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
                return "Encounter Outpost";
            }
            return "Encounter Monument";
        }
        if (normalized.contains("/Npc/")) {
            String faction = segmentAfter(parts, "Npc");
            String label = normalized.contains("/Outpost/") ? "Outpost" : "Camp";
            return faction == null ? "NPC Structure" : TextFormatters.dropSourceName(faction) + " " + label;
        }

        return null;
    }

    private static boolean pathContainsAny(String path, String... fragments) {
        for (String fragment : fragments) {
            if (path.contains(fragment)) {
                return true;
            }
        }
        return false;
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

    private static String readAll(InputStreamReader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            builder.append(buffer, 0, read);
        }
        return builder.toString();
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
