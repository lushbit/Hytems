package dev.lushbit.hytems.ui;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class MobPortraitResolver {
    private static final String ARCHIVE_PREFIX = "Common/UI/Custom/Pages/Memories/npcs/";
    private static final String UI_PREFIX = "Pages/Memories/npcs/";

    private static final Object LOAD_LOCK = new Object();
    private static volatile boolean loaded;
    private static final Map<String, String> portraitsByNormalizedId = new LinkedHashMap<>();

    private MobPortraitResolver() {
    }

    public static String resolvePortraitPath(String mobType) {
        if (mobType == null || mobType.isEmpty()) {
            return null;
        }

        ensureLoaded();
        if (portraitsByNormalizedId.isEmpty()) {
            return null;
        }

        for (String candidate : buildCandidates(mobType)) {
            String directMatch = portraitsByNormalizedId.get(normalizeKey(candidate));
            if (directMatch != null) {
                return directMatch;
            }
        }

        String bestMatch = findPrefixMatch(buildCandidates(mobType));
        return bestMatch;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        synchronized (LOAD_LOCK) {
            if (loaded) {
                return;
            }

            Path assetsZip = locateAssetsZip();
            if (assetsZip != null) {
                try (ZipFile zipFile = new ZipFile(assetsZip.toFile())) {
                    for (ZipEntry entry : java.util.Collections.list(zipFile.entries())) {
                        String entryName = entry.getName();
                        if (!entryName.startsWith(ARCHIVE_PREFIX) || !entryName.toLowerCase(Locale.ENGLISH).endsWith(".png")) {
                            continue;
                        }

                        String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                        String stem = fileName.substring(0, fileName.length() - 4);
                        portraitsByNormalizedId.putIfAbsent(normalizeKey(stem), UI_PREFIX + stem + ".png");
                    }
                } catch (Exception ignored) {
                }
            }

            loaded = true;
        }
    }

    private static Path locateAssetsZip() {
        for (Path candidate : candidateAssetsZipPaths()) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static List<Path> candidateAssetsZipPaths() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Paths.get("Assets.zip"));
        candidates.add(Paths.get("server", "Assets.zip"));
        candidates.add(Paths.get("..", "Assets.zip"));
        candidates.add(Paths.get("..", "server", "Assets.zip"));
        return candidates;
    }

    private static List<String> buildCandidates(String mobType) {
        List<String> candidates = new ArrayList<>();
        addCandidate(candidates, mobType);

        String stripped = stripNamespace(mobType);
        addCandidate(candidates, stripped);
        addCandidate(candidates, stripped.replaceFirst("(?i)^drop_", ""));
        addCandidate(candidates, stripped.replaceFirst("(?i)^drops_", ""));

        String simplified = stripped;
        simplified = simplified.replaceFirst("(?i)_temp_.*$", "");
        simplified = simplified.replaceFirst("(?i)_random_.*$", "");
        simplified = simplified.replaceFirst("(?i)_(harvest|produce|poop|drop|drops)$", "");
        addCandidate(candidates, simplified);

        int underscore = simplified.lastIndexOf('_');
        while (underscore > 0) {
            simplified = simplified.substring(0, underscore);
            addCandidate(candidates, simplified);
            underscore = simplified.lastIndexOf('_');
        }

        return candidates;
    }

    private static void addCandidate(Collection<String> candidates, String value) {
        if (value == null || value.isEmpty() || candidates.contains(value)) {
            return;
        }
        candidates.add(value);
    }

    private static String findPrefixMatch(List<String> candidates) {
        String bestPath = null;
        int bestScore = -1;

        for (String candidate : candidates) {
            String normalizedCandidate = normalizeKey(candidate);
            if (normalizedCandidate.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, String> entry : portraitsByNormalizedId.entrySet()) {
                String normalizedPortrait = entry.getKey();
                if (!normalizedCandidate.startsWith(normalizedPortrait) && !normalizedPortrait.startsWith(normalizedCandidate)) {
                    continue;
                }
                int score = Math.min(normalizedCandidate.length(), normalizedPortrait.length());
                if (score > bestScore) {
                    bestScore = score;
                    bestPath = entry.getValue();
                }
            }
        }

        return bestPath;
    }

    private static String stripNamespace(String value) {
        if (value == null) {
            return "";
        }
        return value.contains(":") ? value.substring(value.indexOf(':') + 1) : value;
    }

    private static String normalizeKey(@Nonnull String value) {
        String stripped = stripNamespace(value).toLowerCase(Locale.ENGLISH);
        StringBuilder normalized = new StringBuilder(stripped.length());
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                normalized.append(c);
            }
        }
        return normalized.toString();
    }
}
