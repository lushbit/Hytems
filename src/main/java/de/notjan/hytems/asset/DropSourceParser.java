package de.notjan.hytems.asset;

import javax.annotation.Nonnull;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DropSourceParser {
    private static final Pattern CROP_PATTERN = Pattern.compile(
            "(?i)drops?_?plant_?crop_(.+?)_(eternal_)?stage(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZONE_TIER_PATTERN = Pattern.compile(
            "(?i)(zone\\d+)\\s*(.+?)\\s*tier(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZONE_ONLY_PATTERN = Pattern.compile(
            "(?i)(zone\\d+)\\s*(.+)", Pattern.CASE_INSENSITIVE);

    private DropSourceParser() {
    }

    public static ParsedDropSource parse(String dropSourceId) {
        ParsedDropSource result = new ParsedDropSource();
        if (dropSourceId == null) return result;

        String name = stripNamespace(dropSourceId);
        Matcher cropMatcher = CROP_PATTERN.matcher(name);
        if (cropMatcher.find()) {
            result.cropType = cropMatcher.group(1);
            result.cropStage = cropMatcher.group(3);
            result.cropZone = "Eternal";
            return result;
        }

        Matcher zoneTierMatcher = ZONE_TIER_PATTERN.matcher(name);
        if (zoneTierMatcher.find()) {
            result.zone = zoneTierMatcher.group(1);
            result.mobType = zoneTierMatcher.group(2).trim();
            result.tier = Integer.parseInt(zoneTierMatcher.group(3));
            return result;
        }

        Matcher zoneOnlyMatcher = ZONE_ONLY_PATTERN.matcher(name);
        if (zoneOnlyMatcher.find()) {
            result.zone = zoneOnlyMatcher.group(1);
            result.mobType = zoneOnlyMatcher.group(2).trim();
        }

        return result;
    }

    public static int zoneNumber(@Nonnull String zone) {
        String zoneDigits = zone.replaceAll("[^0-9]", "");
        if (zoneDigits.isEmpty()) return -1;

        try {
            return Integer.parseInt(zoneDigits);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public static int compareZones(@Nonnull String a, @Nonnull String b) {
        String numA = a.replaceAll("[^0-9]", "");
        String numB = b.replaceAll("[^0-9]", "");
        if (numA.isEmpty()) return 1;
        if (numB.isEmpty()) return -1;
        return Integer.compare(Integer.parseInt(numA), Integer.parseInt(numB));
    }

    private static String stripNamespace(String value) {
        return value.contains(":") ? value.substring(value.indexOf(":") + 1) : value;
    }

    public static final class ParsedDropSource {
        public String mobType;
        public String zone;
        public Integer tier;
        public String cropType;
        public String cropZone;
        public String cropStage;

        public boolean isMobSource() {
            return mobType != null;
        }

        public boolean isCropSource() {
            return cropType != null;
        }
    }
}
