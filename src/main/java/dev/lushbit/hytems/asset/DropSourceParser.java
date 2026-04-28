package dev.lushbit.hytems.asset;

import dev.lushbit.hytems.ui.TextFormatters;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DropSourceParser {
    private static final Pattern ZONE_TIER_PATTERN = Pattern.compile(
            "(?i)(zone\\d+)[_\\s]+(.+?)[_\\s]+tier(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZONE_ONLY_PATTERN = Pattern.compile(
            "(?i)(zone\\d+)[_\\s]+(.+)", Pattern.CASE_INSENSITIVE);
    private static final List<String> CROP_SUFFIX_TOKENS = Arrays.asList(
            "harvest",
            "block",
            "final"
    );

    private DropSourceParser() {
    }

    public static ParsedDropSource parse(String dropSourceId) {
        ParsedDropSource result = new ParsedDropSource();
        if (dropSourceId == null) {
            return result;
        }

        String normalized = normalize(dropSourceId);
        String[] parts = normalized.split("/");
        String rawName = parts.length == 0 ? normalized : parts[parts.length - 1];
        String simplifiedName = simplifyGeneratedName(rawName);

        result.rawId = dropSourceId;
        result.normalizedId = normalized;
        result.rawName = rawName;
        result.topLevelCategory = parts.length > 1 ? parts[0] : null;
        result.subCategory = parts.length > 2 ? parts[1] : null;
        result.sourceName = simplifiedName;

        if (isCropSource(parts, simplifiedName)) {
            parseCrop(result, simplifiedName);
            return result;
        }

        if (isNpcSource(parts) || looksLikeMob(simplifiedName)) {
            result.kind = DropSourceKind.MOB;
            result.mobType = stripDropPrefix(simplifiedName);
            result.sourceName = result.mobType;
            return result;
        }

        if (isPrefabSource(parts, simplifiedName) || looksLikeStructure(simplifiedName)) {
            result.kind = DropSourceKind.STRUCTURE;
            applyZoneTierInfo(result, simplifiedName);
            return result;
        }

        if (isItemSource(parts, simplifiedName)) {
            result.kind = DropSourceKind.CONTAINER;
            result.sourceName = stripContainerPrefix(stripDropPrefix(stripDropsPrefix(simplifiedName)));
            return result;
        }

        if (isObjectiveSource(parts)) {
            result.kind = DropSourceKind.OBJECTIVE;
            result.sourceName = simplifiedName;
            return result;
        }

        if (isRockSource(parts, simplifiedName)) {
            result.kind = DropSourceKind.RESOURCE;
            applyZoneTierInfo(result, stripDropsPrefix(simplifiedName));
            if (result.sourceName == null || result.sourceName.isEmpty()) {
                result.sourceName = simplifiedName;
            }
            return result;
        }

        if (isPlantSource(parts, simplifiedName)) {
            result.kind = DropSourceKind.PLANT;
            result.sourceName = stripPlantPrefix(stripDropsPrefix(simplifiedName));
            return result;
        }

        if (isIngredientSource(parts)) {
            result.kind = DropSourceKind.MATERIAL;
            result.sourceName = simplifiedName;
            return result;
        }

        if (isWoodSource(parts, simplifiedName)) {
            result.kind = DropSourceKind.WOOD;
            result.sourceName = stripWoodPrefix(stripDropsPrefix(simplifiedName));
            return result;
        }

        if (isTrapSource(parts, simplifiedName)) {
            result.kind = DropSourceKind.TRAP;
            result.sourceName = stripDropsPrefix(simplifiedName);
            return result;
        }

        if (isSpecialSource(parts)) {
            result.kind = DropSourceKind.SPECIAL;
            result.sourceName = stripDropPrefix(simplifiedName);
            return result;
        }

        result.kind = DropSourceKind.UNKNOWN;
        result.sourceName = simplifiedName;
        return result;
    }

    public static int zoneNumber(@Nonnull String zone) {
        String zoneDigits = zone.replaceAll("[^0-9]", "");
        if (zoneDigits.isEmpty()) {
            return -1;
        }

        try {
            return Integer.parseInt(zoneDigits);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public static int compareZones(@Nonnull String a, @Nonnull String b) {
        String numA = a.replaceAll("[^0-9]", "");
        String numB = b.replaceAll("[^0-9]", "");
        if (numA.isEmpty()) {
            return 1;
        }
        if (numB.isEmpty()) {
            return -1;
        }
        return Integer.compare(Integer.parseInt(numA), Integer.parseInt(numB));
    }

    private static String stripNamespace(String value) {
        return value.contains(":") ? value.substring(value.indexOf(":") + 1) : value;
    }

    private static String normalize(String value) {
        String normalized = stripNamespace(value).replace('\\', '/');
        normalized = normalized.replaceAll("(?i)^server/drops/", "");
        normalized = normalized.replaceAll("(?i)\\.json$", "");
        return normalized;
    }

    private static String simplifyGeneratedName(String rawName) {
        String simplified = rawName;
        String[] suffixMarkers = new String[] {
                "_State_Definitions",
                "_Gathering_Breaking_DropList",
                "_Breaking_DropList",
                "_DropList_Container",
                "_DropList"
        };

        for (String marker : suffixMarkers) {
            int markerIndex = simplified.indexOf(marker);
            if (markerIndex > 0) {
                simplified = simplified.substring(0, markerIndex);
                break;
            }
        }

        return simplified;
    }

    private static boolean isCropSource(String[] parts, String name) {
        return startsWithIgnoreCase(parts, "Crop")
                || startsWithIgnoreCase(parts, "Crops")
                || name.toLowerCase(Locale.ENGLISH).startsWith("drops_plant_crop_")
                || name.toLowerCase(Locale.ENGLISH).startsWith("plant_crop_");
    }

    private static boolean isNpcSource(String[] parts) {
        return startsWithIgnoreCase(parts, "NPCs");
    }

    private static boolean isPrefabSource(String[] parts, String name) {
        return startsWithIgnoreCase(parts, "Prefabs")
                || name.toLowerCase(Locale.ENGLISH).startsWith("portals_")
                || ZONE_TIER_PATTERN.matcher(name).find();
    }

    private static boolean isItemSource(String[] parts, String name) {
        String lowerName = name.toLowerCase(Locale.ENGLISH);
        return startsWithIgnoreCase(parts, "Items")
                || lowerName.startsWith("container_")
                || lowerName.startsWith("furniture_")
                || lowerName.equals("barrels")
                || lowerName.equals("spider_cocoon")
                || lowerName.equals("iron_stack")
                || lowerName.equals("empty")
                || lowerName.contains("chest")
                || lowerName.contains("coffin")
                || lowerName.contains("wardrobe")
                || lowerName.contains("barrel");
    }

    private static boolean isObjectiveSource(String[] parts) {
        return startsWithIgnoreCase(parts, "Objectives");
    }

    private static boolean isRockSource(String[] parts, String name) {
        String lowerName = name.toLowerCase(Locale.ENGLISH);
        return startsWithIgnoreCase(parts, "Rock")
                || lowerName.startsWith("rock_")
                || lowerName.startsWith("rubble_")
                || lowerName.startsWith("soil_")
                || lowerName.startsWith("ore_");
    }

    private static boolean isPlantSource(String[] parts, String name) {
        return startsWithIgnoreCase(parts, "Plant")
                || name.toLowerCase(Locale.ENGLISH).startsWith("plant_");
    }

    private static boolean isIngredientSource(String[] parts) {
        return startsWithIgnoreCase(parts, "Ingredients");
    }

    private static boolean isWoodSource(String[] parts, String name) {
        String lowerName = name.toLowerCase(Locale.ENGLISH);
        return startsWithIgnoreCase(parts, "Wood")
                || lowerName.startsWith("wood_")
                || lowerName.startsWith("tree_")
                || lowerName.startsWith("bark");
    }

    private static boolean isTrapSource(String[] parts, String name) {
        return startsWithIgnoreCase(parts, "Traps")
                || name.toLowerCase(Locale.ENGLISH).startsWith("drops_fishing_trap_");
    }

    private static boolean isSpecialSource(String[] parts) {
        return startsWithIgnoreCase(parts, "NPCs", "Inventory")
                || startsWithIgnoreCase(parts, "NPCs", "Loadouts");
    }

    private static boolean looksLikeMob(String name) {
        String lowerName = name.toLowerCase(Locale.ENGLISH);
        return lowerName.startsWith("drop_") && !lowerName.startsWith("drops_");
    }

    private static boolean looksLikeStructure(String name) {
        String lowerName = name.toLowerCase(Locale.ENGLISH);
        return lowerName.startsWith("portals_") || ZONE_TIER_PATTERN.matcher(name).find();
    }

    private static boolean startsWithIgnoreCase(String[] parts, String... expectedParts) {
        if (parts.length < expectedParts.length) {
            return false;
        }

        for (int i = 0; i < expectedParts.length; i++) {
            if (!parts[i].equalsIgnoreCase(expectedParts[i])) {
                return false;
            }
        }

        return true;
    }

    private static void parseCrop(ParsedDropSource result, String rawName) {
        result.kind = DropSourceKind.CROP;

        String stripped = stripDropsPrefix(rawName).replaceFirst("(?i)^plant_crop_", "");
        List<String> tokens = Arrays.asList(stripped.split("_"));
        StringBuilder cropType = new StringBuilder();
        String cropStage = null;
        String cropVariant = null;

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            String lowerToken = token.toLowerCase(Locale.ENGLISH);

            if ("stage".equals(lowerToken)) {
                if (i + 1 < tokens.size()) {
                    cropStage = tokens.get(i + 1);
                    i++;
                }
                continue;
            }

            if (lowerToken.startsWith("stage")) {
                cropStage = token.substring("stage".length());
                if (cropStage.isEmpty()) {
                    cropStage = "Final";
                }
                continue;
            }

            if ("eternal".equals(lowerToken) || "wet".equals(lowerToken)
                    || "winter".equals(lowerToken) || "burnt".equals(lowerToken)) {
                cropVariant = TextFormatters.dropSourceName(token);
                continue;
            }

            if (CROP_SUFFIX_TOKENS.contains(lowerToken)) {
                continue;
            }

            if (cropType.length() > 0) {
                cropType.append("_");
            }
            cropType.append(token);
        }

        result.cropType = cropType.toString().replaceAll("_+$", "");
        result.cropStage = cropStage;
        result.cropZone = cropVariant;
        result.sourceName = result.cropType;
    }

    private static void applyZoneTierInfo(ParsedDropSource result, String sourceName) {
        Matcher zoneTierMatcher = ZONE_TIER_PATTERN.matcher(sourceName);
        if (zoneTierMatcher.find()) {
            result.zone = zoneTierMatcher.group(1);
            result.sourceName = zoneTierMatcher.group(2).trim();
            result.tier = Integer.parseInt(zoneTierMatcher.group(3));
            return;
        }

        Matcher zoneOnlyMatcher = ZONE_ONLY_PATTERN.matcher(sourceName);
        if (zoneOnlyMatcher.find()) {
            result.zone = zoneOnlyMatcher.group(1);
            result.sourceName = zoneOnlyMatcher.group(2).trim();
            return;
        }

        result.sourceName = stripDropsPrefix(sourceName);
    }

    private static String stripDropPrefix(String value) {
        return value.replaceFirst("(?i)^drop_", "").replaceFirst("(?i)^drops_", "");
    }

    private static String stripDropsPrefix(String value) {
        return value.replaceFirst("(?i)^drops?_", "");
    }

    private static String stripContainerPrefix(String value) {
        return value.replaceFirst("(?i)^container_", "");
    }

    private static String stripPlantPrefix(String value) {
        return value.replaceFirst("(?i)^plant_", "");
    }

    private static String stripWoodPrefix(String value) {
        return value.replaceFirst("(?i)^wood_", "");
    }

    public enum DropSourceKind {
        MOB("Mob"),
        CROP("Crop"),
        STRUCTURE("Structure"),
        CONTAINER("Container"),
        OBJECTIVE("Objective"),
        RESOURCE("Resource"),
        PLANT("Plant"),
        MATERIAL("Material"),
        WOOD("Wood"),
        TRAP("Trap"),
        SPECIAL("Special"),
        UNKNOWN("Unknown");

        private final String label;

        DropSourceKind(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }
    }

    public static final class ParsedDropSource {
        public DropSourceKind kind = DropSourceKind.UNKNOWN;
        public String rawId;
        public String normalizedId;
        public String rawName;
        public String topLevelCategory;
        public String subCategory;
        public String sourceName;
        public String mobType;
        public String zone;
        public Integer tier;
        public String cropType;
        public String cropZone;
        public String cropStage;

        public boolean isMobSource() {
            return this.kind == DropSourceKind.MOB;
        }

        public boolean isCropSource() {
            return this.kind == DropSourceKind.CROP;
        }

        public boolean hasZoneContext() {
            return this.zone != null && !this.zone.isEmpty();
        }
    }
}
