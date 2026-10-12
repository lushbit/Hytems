package dev.lushbit.hytems.asset;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.assetstore.AssetMap;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Locates the {@code Server} folder of every loaded asset pack (base game, other mods), both for
 * the dedicated server and singleplayer, where packs may live inside zip file systems.
 */
public final class ServerAssetRoots {
    private ServerAssetRoots() {
    }

    public static Set<Path> serverRoots() {
        AssetMap<String, ItemDropList> dropMap = ItemDropList.getAssetMap();
        if (dropMap == null || dropMap.getAssetMap() == null) return Collections.emptySet();
        Set<Path> roots = new LinkedHashSet<>();
        for (String dropId : dropMap.getAssetMap().keySet()) {
            if (dropId == null) continue;
            Path root = findServerRoot(dropMap.getPath(dropId));
            if (root != null && Files.exists(root)) roots.add(root);
        }
        return roots;
    }

    static Path findServerRoot(Path path) {
        if (path == null) return null;
        for (Path cursor = path.normalize(); cursor != null; cursor = cursor.getParent()) {
            if ("Server".equalsIgnoreCase(String.valueOf(cursor.getFileName()))) return cursor;
        }
        return null;
    }

    /** All JSON documents below {@code Server/<relativeDir>} of every pack, keyed by file name without extension. */
    public static Map<String, JsonFile> loadJsonFiles(String relativeDir) {
        Map<String, JsonFile> files = new LinkedHashMap<>();
        for (Path root : serverRoots()) {
            Path dir = root.resolve(relativeDir);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> stream = Files.walk(dir)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().toLowerCase(Locale.ENGLISH).endsWith(".json"))
                        .forEach(path -> {
                            JsonObject json = readJson(path);
                            if (json == null) return;
                            String name = path.getFileName().toString().replaceFirst("(?i)\\.json$", "");
                            String relative = root.relativize(path).toString().replace('\\', '/');
                            files.put(name, new JsonFile(name, relative, json));
                        });
            } catch (IOException ignored) {
            }
        }
        return files;
    }

    static JsonObject readJson(Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** @param relativePath path below the pack's Server folder, with forward slashes */
    public record JsonFile(String name, String relativePath, JsonObject json) {
    }
}
