package dev.lushbit.hytems.ui;
import dev.lushbit.hytems.asset.DropSourceParser;
import dev.lushbit.hytems.asset.PrefabDropMetadataRegistry;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class DropSourceSummaries {
    private DropSourceSummaries() {
    }

    public static List<DisplayDropSource> summarizeMobDrops(List<String> dropSources) {
        List<DisplayDropSource> summaries = summarize(dropSources);
        return summaries.stream()
                .filter(summary -> summary.kind == DropSourceParser.DropSourceKind.MOB)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static List<DisplayDropSource> summarize(List<String> dropSources) {
        Map<String, DisplayDropSource> summaries = new LinkedHashMap<>();
        if (dropSources == null) {
            return new ArrayList<>();
        }

        for (String dropSourceId : dropSources) {
            DropSourceParser.ParsedDropSource parsed = DropSourceParser.parse(dropSourceId);
            String displayName = displayName(parsed);
            String groupingKey = groupingKey(parsed, displayName);

            DisplayDropSource summary = summaries.computeIfAbsent(
                    groupingKey,
                    ignored -> new DisplayDropSource(parsed.kind, displayName, parsed)
            );

            if (parsed.kind == DropSourceParser.DropSourceKind.STRUCTURE) {
                PrefabDropMetadataRegistry.PrefabDropMetadata metadata = PrefabDropMetadataRegistry.lookup(parsed.rawName);
                if (metadata.hasStructureLabels()) {
                    summary.structureLabels.addAll(metadata.structureLabels());
                }
            }

            if (parsed.hasZoneContext()) {
                summary.zoneData.computeIfAbsent(parsed.zone, ignored -> new ArrayList<>());
                if (parsed.tier != null) {
                    summary.zoneData.get(parsed.zone).add(parsed.tier);
                }
            }
        }

        List<DisplayDropSource> ordered = suppressGenericMobEntries(new ArrayList<>(summaries.values()));
        ordered.sort((a, b) -> {
            int kindCompare = Integer.compare(kindOrder(a.kind), kindOrder(b.kind));
            if (kindCompare != 0) {
                return kindCompare;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(a.displayName, b.displayName);
        });
        return ordered;
    }

    private static int kindOrder(DropSourceParser.DropSourceKind kind) {
        switch (kind) {
            case MOB:
                return 0;
            case CROP:
                return 1;
            case STRUCTURE:
                return 2;
            case CONTAINER:
                return 3;
            case RESOURCE:
                return 4;
            case PLANT:
                return 5;
            case MATERIAL:
                return 6;
            case WOOD:
                return 7;
            case OBJECTIVE:
                return 8;
            case TRAP:
                return 9;
            case SPECIAL:
                return 10;
            default:
                return 11;
        }
    }

    private static String groupingKey(DropSourceParser.ParsedDropSource parsed, String displayName) {
        if (parsed.kind == DropSourceParser.DropSourceKind.CROP
                || parsed.kind == DropSourceParser.DropSourceKind.MOB
                || parsed.kind == DropSourceParser.DropSourceKind.STRUCTURE
                || parsed.kind == DropSourceParser.DropSourceKind.RESOURCE) {
            return parsed.kind.name() + ":" + displayName;
        }

        return parsed.kind.name() + ":" + displayName + ":" + (parsed.normalizedId != null ? parsed.normalizedId : "");
    }

    private static String displayName(DropSourceParser.ParsedDropSource parsed) {
        switch (parsed.kind) {
            case CROP:
                return TextFormatters.cropSourceName(parsed.cropType, parsed.cropZone);
            case MOB:
                return TextFormatters.mobName(parsed.mobType != null ? parsed.mobType : parsed.sourceName);
            case STRUCTURE:
                PrefabDropMetadataRegistry.PrefabDropMetadata metadata = PrefabDropMetadataRegistry.lookup(parsed.rawName);
                if (metadata.hasContainerDisplayName()) {
                    return metadata.containerDisplayName() + " Loot";
                }
                return TextFormatters.structureName(parsed.sourceName);
            case CONTAINER:
                return TextFormatters.containerName(parsed.sourceName);
            case OBJECTIVE:
                return TextFormatters.objectiveName(parsed.sourceName);
            case RESOURCE:
                return TextFormatters.resourceSourceName(parsed.sourceName);
            case PLANT:
                return TextFormatters.plantSourceName(parsed.sourceName);
            case MATERIAL:
                return TextFormatters.materialSourceName(parsed.sourceName);
            case WOOD:
                return TextFormatters.woodSourceName(parsed.sourceName);
            case TRAP:
                return TextFormatters.trapSourceName(parsed.sourceName);
            case SPECIAL:
                return TextFormatters.specialSourceName(parsed.sourceName);
            default:
                return TextFormatters.dropSourceName(parsed.rawName != null ? parsed.rawName : parsed.sourceName);
        }
    }

    public static List<DisplayDropSource> suppressGenericMobEntries(List<DisplayDropSource> summaries) {
        List<DisplayDropSource> filtered = new ArrayList<>(summaries);
        filtered.removeIf(candidate -> {
            if (candidate.kind != DropSourceParser.DropSourceKind.MOB) {
                return false;
            }

            String normalizedCandidate = candidate.displayName.toLowerCase(Locale.ENGLISH);
            if (normalizedCandidate.contains(" ")) {
                return false;
            }

            String prefix = normalizedCandidate + " ";
            int matchingVariants = 0;
            boolean hasCombatVariant = false;
            for (DisplayDropSource other : summaries) {
                if (other == candidate || other.kind != DropSourceParser.DropSourceKind.MOB) {
                    continue;
                }

                String normalizedOther = other.displayName.toLowerCase(Locale.ENGLISH);
                if (!normalizedOther.startsWith(prefix)) {
                    continue;
                }

                String suffix = normalizedOther.substring(prefix.length()).trim();
                if (suffix.isEmpty()) {
                    continue;
                }

                String firstWord = suffix.contains(" ") ? suffix.substring(0, suffix.indexOf(' ')) : suffix;
                if (isLifecycleVariant(firstWord)) {
                    return false;
                }

                matchingVariants++;
                if (isCombatVariant(firstWord)) {
                    hasCombatVariant = true;
                }
            }

            return hasCombatVariant || matchingVariants >= 3;
        });
        return filtered;
    }

    private static boolean isLifecycleVariant(String token) {
        return token.equals("foal")
                || token.equals("calf")
                || token.equals("kid")
                || token.equals("piglet")
                || token.equals("chick")
                || token.equals("lamb")
                || token.equals("sapling")
                || token.equals("seedling")
                || token.equals("sproutling")
                || token.equals("harvest")
                || token.equals("produce")
                || token.equals("poop")
                || token.equals("skeleton")
                || token.equals("undead");
    }

    private static boolean isCombatVariant(String token) {
        return token.equals("archer")
                || token.equals("archmage")
                || token.equals("fighter")
                || token.equals("footman")
                || token.equals("gunner")
                || token.equals("guard")
                || token.equals("hunter")
                || token.equals("knight")
                || token.equals("lancer")
                || token.equals("mage")
                || token.equals("marauder")
                || token.equals("praetorian")
                || token.equals("priest")
                || token.equals("ranger")
                || token.equals("scout")
                || token.equals("shaman")
                || token.equals("soldier")
                || token.equals("sorcerer")
                || token.equals("stalker")
                || token.equals("warrior")
                || token.equals("wizard");
    }

    public static final class DisplayDropSource {
        public final DropSourceParser.DropSourceKind kind;
        public final String displayName;
        public final DropSourceParser.ParsedDropSource previewSource;
        public final Map<String, List<Integer>> zoneData = new LinkedHashMap<>();
        public final Set<String> structureLabels = new LinkedHashSet<>();

        private DisplayDropSource(DropSourceParser.DropSourceKind kind, String displayName,
                                  DropSourceParser.ParsedDropSource previewSource) {
            this.kind = kind;
            this.displayName = displayName;
            this.previewSource = previewSource;
        }

        public String fullLabel() {
            if (this.structureLabels.isEmpty()) {
                return this.displayName;
            }

            List<String> labels = new ArrayList<>(this.structureLabels);
            labels.sort(String.CASE_INSENSITIVE_ORDER);
            if (labels.size() == 1) {
                return this.displayName + " (" + labels.get(0) + ")";
            }
            if (labels.size() == 2) {
                return this.displayName + " (" + labels.get(0) + ", " + labels.get(1) + ")";
            }
            return this.displayName + " (" + labels.get(0) + ", " + labels.get(1) + " +" + (labels.size() - 2) + ")";
        }

        public String primaryLabel() {
            return this.displayName;
        }

        public String secondaryLabel() {
            if (this.structureLabels.isEmpty()) {
                return "";
            }

            List<String> labels = new ArrayList<>(this.structureLabels);
            labels.sort(String.CASE_INSENSITIVE_ORDER);
            if (labels.size() <= 2) {
                return String.join(", ", labels);
            }
            return labels.get(0) + ", " + labels.get(1) + " +" + (labels.size() - 2);
        }

        public boolean hasZoneData() {
            return !this.zoneData.isEmpty();
        }
    }
}
