package dev.lushbit.hytems.ui;

import java.util.ArrayList;
import java.util.List;

public final class MobPortraitResolver {
    private static final String BASE_PATH = "hytems/ui/Assets/MobPortraits/";

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

        return BASE_PATH + candidates.get(0) + ".png";
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
