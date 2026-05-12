package dev.lushbit.hytems.ui;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MobPortraitResolver {
    private static final String BASE_PATH = "hytems/ui/Assets/MobPortraits/";
    public static final String FALLBACK_PORTRAIT_PATH = BASE_PATH + "Construction_Sign.png";
    private static final String RESOURCE_BASE_PATH = "Common/UI/Custom/" + BASE_PATH;
    private static final Path DEV_RESOURCE_BASE_PATH = Path.of("src/main/resources/Common/UI/Custom").resolve(BASE_PATH);

    private MobPortraitResolver() {
    }

    public static String resolvePortraitPath(String mobType) {
        if (mobType == null || mobType.isEmpty()) {
            return null;
        }

        List<String> candidates = buildCandidates(mobType);
        if (candidates.isEmpty()) {
            return null;
        }

        for (String candidate : candidates) {
            String path = BASE_PATH + candidate + ".png";
            if (portraitExists(path)) {
                return path;
            }
        }
        return null;
    }

    private static boolean portraitExists(String uiPath) {
        String resourcePath = RESOURCE_BASE_PATH + uiPath.substring(BASE_PATH.length());
        ClassLoader loader = MobPortraitResolver.class.getClassLoader();
        URL resource = loader == null ? ClassLoader.getSystemResource(resourcePath) : loader.getResource(resourcePath);
        if (resource != null) {
            return true;
        }

        Path devPath = DEV_RESOURCE_BASE_PATH.resolve(uiPath.substring(BASE_PATH.length()));
        return Files.isRegularFile(devPath);
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

    private static void addCandidate(List<String> candidates, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        String normalized = normalizeCandidate(value);
        if (!normalized.isEmpty() && !candidates.contains(normalized)) {
            candidates.add(normalized);
        }
    }

    private static String normalizeCandidate(String value) {
        String raw = stripNamespace(value).replace('\\', '/').trim();
        if (raw.isEmpty()) {
            return "";
        }

        if (raw.contains("/")) {
            String[] parts = raw.split("/");
            raw = parts[parts.length - 1];
        }

        return raw.replaceAll("[^A-Za-z0-9_]", "");
    }

    private static String stripNamespace(String value) {
        return value != null && value.contains(":")
                ? value.substring(value.indexOf(':') + 1)
                : value;
    }
}
