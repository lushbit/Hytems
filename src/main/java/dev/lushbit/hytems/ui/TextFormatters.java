package dev.lushbit.hytems.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextFormatters {
    private static final Map<String, String> SPECIAL_CROP_NAMES = createSpecialCropNames();
    private static final Map<String, String> PREFAB_SOURCE_NAMES = createPrefabSourceNames();

    private TextFormatters() {
    }

    public static String resourceTypeName(String resourceTypeId) {
        if (resourceTypeId == null) return "Unknown";
        return humanize(stripNamespace(resourceTypeId));
    }

    public static String itemName(String itemId) {
        if (itemId == null) return "Unknown Item";
        String name = stripNamespace(itemId);
        if (name.startsWith("Bench_")) {
            return benchName(name.substring("Bench_".length()));
        }
        return humanize(trimGeneratedSuffix(name));
    }

    public static String benchName(String benchCore) {
        if (benchCore == null || benchCore.isEmpty()) return "Unknown Bench";
        if (benchCore.equalsIgnoreCase("Workbench") || benchCore.equalsIgnoreCase("WorkBench")) {
            return "Workbench";
        }
        return humanize(benchCore) + " Bench";
    }

    public static String mobName(String mobType) {
        return mobType == null ? "Unknown" : humanize(trimGeneratedSuffix(mobType));
    }

    public static String cropName(String cropType) {
        return cropSourceName(cropType, null);
    }

    public static String cropSourceName(String cropType, String cropVariant) {
        if (cropType == null) return "Unknown Crop";
        String normalizedType = trimGeneratedSuffix(stripNamespace(cropType));
        String base = SPECIAL_CROP_NAMES.getOrDefault(normalizedType.toLowerCase(Locale.ENGLISH), humanize(normalizedType));
        if (cropVariant == null || cropVariant.isEmpty()) {
            return base;
        }
        return humanize(cropVariant) + " " + base;
    }

    public static String structureName(String sourceName) {
        if (sourceName == null || sourceName.isEmpty()) return "Unknown Structure";

        String value = trimGeneratedSuffix(stripNamespace(sourceName));
        if (value.toLowerCase(Locale.ENGLISH).startsWith("portals_")) {
            return humanize(value.substring("Portals_".length())) + " Portal";
        }
        String mappedName = PREFAB_SOURCE_NAMES.get(value.toLowerCase(Locale.ENGLISH));
        if (mappedName != null) {
            return mappedName;
        }
        return humanize(value) + " Chest";
    }

    public static String containerName(String sourceName) {
        if (sourceName == null || sourceName.isEmpty()) return "Unknown Container";

        String value = trimGeneratedSuffix(stripNamespace(sourceName));
        value = value.replaceFirst("(?i)^container_", "");
        value = value.replaceFirst("(?i)^furniture_", "");
        if (value.equalsIgnoreCase("Empty")) {
            return "Empty Container";
        }
        if (value.startsWith("Pot_")) {
            return sizeAwareLabel(value.substring("Pot_".length()), "Pot");
        }
        if (value.startsWith("Jar_")) {
            return sizeAwareLabel(value.substring("Jar_".length()), "Jar");
        }
        return humanize(value);
    }

    public static String objectiveName(String sourceName) {
        if (sourceName == null || sourceName.isEmpty()) return "Unknown Objective";
        return humanize(trimGeneratedSuffix(stripNamespace(sourceName)));
    }

    public static String resourceSourceName(String sourceName) {
        if (sourceName == null || sourceName.isEmpty()) return "Unknown Resource";

        String value = trimGeneratedSuffix(stripNamespace(sourceName));
        if (value.startsWith("Rubble_")) {
            return sizeAwareLabel(value.substring("Rubble_".length()), "Rubble");
        }
        if (value.startsWith("Rock_Crystal_")) {
            return sizeAwareLabel(value.substring("Rock_Crystal_".length()), "Crystal");
        }
        if (value.startsWith("Rock_")) {
            return sizeAwareLabel(value.substring("Rock_".length()), "Rock");
        }
        if (value.startsWith("Soil_")) {
            return sizeAwareLabel(value.substring("Soil_".length()), "Soil");
        }
        return humanize(value);
    }

    public static String plantSourceName(String sourceName) {
        if (sourceName == null || sourceName.isEmpty()) return "Unknown Plant";

        String value = trimGeneratedSuffix(stripNamespace(sourceName)).replaceFirst("(?i)^plant_", "");
        value = value.replaceAll("(?i)_harvest$", "");
        if (value.startsWith("Bush_Berry_")) {
            return humanize(value.substring("Bush_Berry_".length())) + " Berry Bush";
        }
        return humanize(value);
    }

    public static String materialSourceName(String sourceName) {
        if (sourceName == null || sourceName.isEmpty()) return "Unknown Material";

        String value = trimGeneratedSuffix(stripNamespace(sourceName));
        value = value.replaceAll("(?i)_harvest$", "");
        value = value.replaceAll("_[0-9]+$", "");
        return humanize(value);
    }

    public static String woodSourceName(String sourceName) {
        if (sourceName == null || sourceName.isEmpty()) return "Unknown Wood Source";

        String value = trimGeneratedSuffix(stripNamespace(sourceName));
        if (value.equalsIgnoreCase("Bark")) {
            return "Tree Bark";
        }
        if (value.startsWith("Wood_")) {
            value = value.substring("Wood_".length());
        }
        if (value.startsWith("Tree_")) {
            value = value.substring("Tree_".length());
        }
        if (value.equalsIgnoreCase("Leaves_Physics")) {
            return "Leaves";
        }
        return humanize(value);
    }

    public static String trapSourceName(String sourceName) {
        if (sourceName == null || sourceName.isEmpty()) return "Unknown Trap";

        String value = trimGeneratedSuffix(stripNamespace(sourceName));
        if (value.toLowerCase(Locale.ENGLISH).startsWith("drops_fishing_trap_")) {
            String suffix = value.substring("Drops_Fishing_Trap_".length());
            return suffix.isEmpty() ? "Fishing Trap" : humanize(suffix) + " Fishing Trap";
        }
        return humanize(value);
    }

    public static String specialSourceName(String sourceName) {
        if (sourceName == null || sourceName.isEmpty()) return "Unknown Source";

        String value = trimGeneratedSuffix(stripNamespace(sourceName));
        if (value.toLowerCase(Locale.ENGLISH).contains("inventory")) {
            return "NPC Inventory";
        }
        return humanize(value);
    }

    public static String compactStructureLootName(String containerDisplayName) {
        if (containerDisplayName == null || containerDisplayName.isEmpty()) {
            return "Structure Loot";
        }

        String value = containerDisplayName.trim();
        value = value.replaceAll("(?i)\\s+loot$", "");
        value = value.replaceAll("(?i)^(small|medium|large|epic|rare|common|uncommon|legendary)\\s+", "");
        value = value.replaceAll("(?i)\\s+(small|medium|large|epic|rare|common|uncommon|legendary)$", "");

        return value.isEmpty() ? "Structure Loot" : value;
    }

    public static String dropSourceName(String dropSourceId) {
        if (dropSourceId == null) return "Unknown";

        String name = trimGeneratedSuffix(stripNamespace(dropSourceId));
        if (name.toLowerCase(Locale.ENGLISH).startsWith("drop_")) {
            name = name.substring(5);
        }
        if (name.toLowerCase(Locale.ENGLISH).startsWith("drops_")) {
            name = name.substring(6);
        }

        return humanize(name);
    }

    public static String zoneName(String zone) {
        if (zone == null || zone.equalsIgnoreCase("Unknown")) return "Unknown Zone";

        Matcher matcher = Pattern.compile("(?i)zone(\\d+)").matcher(zone);
        return matcher.find() ? "Zone " + matcher.group(1) : zone;
    }

    public static String tierRange(List<Integer> tiers) {
        if (tiers == null || tiers.isEmpty()) return "";

        Set<Integer> uniqueTiers = new TreeSet<>();
        for (Integer tier : tiers) {
            if (tier != null) {
                uniqueTiers.add(tier);
            }
        }
        List<Integer> sorted = new ArrayList<>(uniqueTiers);
        if (sorted.isEmpty()) return "";
        if (sorted.size() == 1) {
            return "Tier " + sorted.get(0);
        }
        return "Tier " + sorted.get(0) + "-" + sorted.get(sorted.size() - 1);
    }

    public static String stageInfo(List<String> stages) {
        if (stages == null || stages.isEmpty()) return "";

        Set<String> uniqueStages = new LinkedHashSet<>(stages);
        if (uniqueStages.size() == 1) {
            return "Stage " + stages.get(0);
        }
        return "Stages: " + String.join(", ", uniqueStages);
    }

    private static String stripNamespace(String value) {
        return value.contains(":") ? value.substring(value.indexOf(":") + 1) : value;
    }

    private static String trimGeneratedSuffix(String value) {
        String trimmed = value;
        String[] markers = new String[] {
                "_State_Definitions",
                "_Gathering_Breaking_DropList",
                "_Breaking_DropList",
                "_DropList_Container",
                "_DropList"
        };

        for (String marker : markers) {
            int index = trimmed.indexOf(marker);
            if (index > 0) {
                trimmed = trimmed.substring(0, index);
                break;
            }
        }

        return trimmed;
    }

    private static String sizeAwareLabel(String value, String suffix) {
        String[] tokens = value.split("_");
        if (tokens.length == 0) {
            return suffix;
        }

        String lastToken = tokens[tokens.length - 1];
        boolean hasSizeSuffix = lastToken.equalsIgnoreCase("Small")
                || lastToken.equalsIgnoreCase("Medium")
                || lastToken.equalsIgnoreCase("Large")
                || lastToken.equalsIgnoreCase("Tall");

        if (!hasSizeSuffix) {
            return humanize(value) + " " + suffix;
        }

        StringBuilder base = new StringBuilder();
        for (int i = 0; i < tokens.length - 1; i++) {
            if (i > 0) {
                base.append('_');
            }
            base.append(tokens[i]);
        }

        return humanize(lastToken + "_" + base) + " " + suffix;
    }

    private static String humanize(String value) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '_' || c == '-') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else if (Character.isUpperCase(c) && i > 0 && !Character.isUpperCase(value.charAt(i - 1))) {
                result.append(' ').append(c);
            } else {
                result.append(c);
            }
        }

        return result.toString().trim();
    }

    private static Map<String, String> createSpecialCropNames() {
        Map<String, String> names = new HashMap<>();
        names.put("apple", "Apple");
        names.put("aubergine", "Aubergine");
        names.put("berry", "Berry Bush");
        names.put("carrot", "Carrot");
        names.put("cauliflower", "Cauliflower");
        names.put("chilli", "Chilli");
        names.put("corn", "Corn");
        names.put("cotton", "Cotton");
        names.put("mana1", "Azure Fern");
        names.put("mana2", "Azurecap Mushroom");
        names.put("mana3", "Azure Kelp");
        names.put("health1", "Blood Rose");
        names.put("health2", "Bloodcap Mushroom");
        names.put("health3", "Blood Leaf");
        names.put("lettuce", "Lettuce");
        names.put("onion", "Onion");
        names.put("potato", "Potato");
        names.put("pumpkin", "Pumpkin");
        names.put("rice", "Rice");
        names.put("stamina1", "Storm Thistle");
        names.put("stamina2", "Stormcap Mushroom");
        names.put("stamina3", "Storm Rush");
        names.put("tomato", "Tomato");
        names.put("turnip", "Turnip");
        names.put("wheat", "Wheat");
        return Collections.unmodifiableMap(names);
    }

    private static Map<String, String> createPrefabSourceNames() {
        Map<String, String> names = new HashMap<>();
        names.put("encounters", "Wooden Chest");
        names.put("undead", "Small Ancient Chest");
        names.put("goblin", "Goblin Chest");
        names.put("kweebec", "Small Kweebec Chest");
        names.put("feran", "Small Feran Chest");
        names.put("outlander", "Small Lost Civilization Chest");
        names.put("trork", "Small Lumberjack Chest");
        return Collections.unmodifiableMap(names);
    }
}
