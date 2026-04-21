package dev.lushbit.hytems.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextFormatters {
    private TextFormatters() {
    }

    public static String resourceTypeName(String resourceTypeId) {
        if (resourceTypeId == null) return "Unknown";
        return humanize(stripNamespace(resourceTypeId));
    }

    public static String mobName(String mobType) {
        return mobType == null ? "Unknown" : humanize(mobType);
    }

    public static String cropName(String cropType) {
        return cropType == null ? "Unknown Crop" : humanize(cropType) + " Crop";
    }

    public static String dropSourceName(String dropSourceId) {
        if (dropSourceId == null) return "Unknown";

        String name = stripNamespace(dropSourceId);
        if (name.toLowerCase(Locale.ENGLISH).startsWith("drop_")) {
            name = name.substring(5);
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
}
