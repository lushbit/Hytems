package dev.lushbit.hytems.asset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Boss fights read from the game's EncounterManager files. An encounter that shows a boss bar is a
 * boss; its states are the fight phases, and the macros or actions that move between states carry
 * the health thresholds and the role the boss switches to. Each phase role's combat config lists
 * the attacks used in that phase. Everything is data driven, so new bosses show up automatically;
 * nicer wording can be layered on through server.lang (see BossText).
 */
public final class BossRegistry {
    private static final Set<String> NON_PHASE_STATES = Set.of(
            "resetting", "waiting", "idle", "intro", "bossdead", "complete", "dead", "teardown");
    private static final int MAX_DEPTH = 6;

    private static volatile Data data;

    private BossRegistry() {
    }

    public static void invalidate() {
        data = null;
    }

    @Nonnull
    public static List<BossInfo> all() {
        return data().bosses;
    }

    /** The boss a mob belongs to (its listed entry or any of its phase roles), or null. */
    public static BossInfo forMob(String mobId) {
        if (mobId == null) return null;
        return data().byMember.get(key(mobId));
    }

    public static boolean isRepresentative(String mobId) {
        BossInfo boss = forMob(mobId);
        return boss != null && boss.representativeMobId().equalsIgnoreCase(mobId);
    }

    /** Phase roles of a boss other than the one the mob browser lists. */
    public static boolean isHiddenMember(String mobId) {
        BossInfo boss = forMob(mobId);
        return boss != null && !boss.representativeMobId().equalsIgnoreCase(mobId);
    }

    private static Data data() {
        Data current = data;
        if (current != null) return current;
        synchronized (BossRegistry.class) {
            if (data == null) {
                try {
                    data = load();
                } catch (Exception e) {
                    System.err.println("[Hytems] Failed to load boss encounters: " + e.getMessage());
                    data = new Data(Collections.emptyList(), Collections.emptyMap());
                }
            }
            return data;
        }
    }

    private static Data load() {
        Map<String, ServerAssetRoots.JsonFile> encounterFiles = ServerAssetRoots.loadJsonFiles("EncounterManager");
        Map<String, ServerAssetRoots.JsonFile> combatConfigs = ServerAssetRoots.loadJsonFiles("NPC/Balancing");
        Set<String> roleIds = MobMetadataRegistry.allRoleIds();
        if (roleIds.isEmpty()) return new Data(Collections.emptyList(), Collections.emptyMap());

        Map<String, List<BossVariant>> variantsByBoss = new LinkedHashMap<>();
        Map<String, String> descriptionByBoss = new LinkedHashMap<>();
        Map<String, String> nameKeyByBoss = new LinkedHashMap<>();
        Map<String, String> rewardByBoss = new LinkedHashMap<>();

        for (ServerAssetRoots.JsonFile file : encounterFiles.values()) {
            JsonObject json = file.json();
            if (!"EncounterManager".equals(string(json.get("Class"))) || "Abstract".equals(string(json.get("Type")))) continue;
            if (isExampleOrTest(file)) continue;

            Encounter encounter = resolveEncounter(file.name(), encounterFiles, 0);
            if (encounter == null || !usesBossBar(encounter, encounterFiles)) continue;

            List<BossPhase> phases = readPhases(encounter, encounterFiles, combatConfigs);
            if (phases.isEmpty()) continue;
            String firstRole = phases.stream().map(BossPhase::roleId).filter(id -> id != null).findFirst().orElse(null);
            if (firstRole == null) continue;

            String bossKey = bossKey(firstRole);
            boolean hard = isHardMode(file.name(), encounter.params);
            variantsByBoss.computeIfAbsent(bossKey, ignored -> new ArrayList<>())
                    .add(new BossVariant(file.name(), hard, phases));
            if (!hard || !descriptionByBoss.containsKey(bossKey)) descriptionByBoss.put(bossKey, encounter.comment);
            String nameKey = bossBarName(encounter, encounterFiles);
            if (nameKey != null && (!hard || !nameKeyByBoss.containsKey(bossKey))) nameKeyByBoss.put(bossKey, nameKey);
            String reward = findActionValue(encounter, "GrantReward", "Reward");
            if (reward != null) rewardByBoss.putIfAbsent(bossKey, reward);
        }

        List<BossInfo> bosses = new ArrayList<>();
        for (Map.Entry<String, List<BossVariant>> entry : variantsByBoss.entrySet()) {
            List<BossVariant> variants = new ArrayList<>(entry.getValue());
            variants.sort(Comparator.comparing(BossVariant::hardMode).thenComparing(BossVariant::encounterId));
            String representative = variants.get(0).phases().stream()
                    .map(BossPhase::roleId).filter(id -> id != null).findFirst().orElseThrow();
            Set<String> members = collectMembers(entry.getKey(), variants, roleIds);
            bosses.add(new BossInfo(entry.getKey(), representative, nameKeyByBoss.get(entry.getKey()),
                    descriptionByBoss.get(entry.getKey()), rewardByBoss.get(entry.getKey()),
                    Collections.unmodifiableList(variants), Collections.unmodifiableSet(members)));
        }

        Map<String, BossInfo> byMember = new LinkedHashMap<>();
        for (BossInfo boss : bosses) {
            for (String member : boss.memberMobIds()) byMember.putIfAbsent(key(member), boss);
        }
        return new Data(Collections.unmodifiableList(bosses), Collections.unmodifiableMap(byMember));
    }

    private static String bossKey(String roleId) {
        String memoryName = MobMetadataRegistry.inheritedRoleString(roleId, "MemoriesNameOverride");
        return memoryName != null ? memoryName : roleId;
    }

    /** Phase roles plus helper roles of the same boss (Gobliterator intro and legs) found by their shared memory name. */
    private static Set<String> collectMembers(String bossKey, List<BossVariant> variants, Set<String> roleIds) {
        Set<String> members = new LinkedHashSet<>();
        Set<String> directories = new LinkedHashSet<>();
        for (BossVariant variant : variants) {
            for (BossPhase phase : variant.phases()) {
                if (phase.roleId() == null) continue;
                members.add(phase.roleId());
                directories.add(MobMetadataRegistry.roleDirectory(phase.roleId()));
            }
        }
        for (String roleId : roleIds) {
            if (members.contains(roleId) || roleId.toLowerCase(Locale.ENGLISH).contains("template")) continue;
            if (!directories.contains(MobMetadataRegistry.roleDirectory(roleId))) continue;
            if (bossKey.equalsIgnoreCase(MobMetadataRegistry.inheritedRoleString(roleId, "MemoriesNameOverride"))) {
                members.add(roleId);
            }
        }
        return members;
    }

    private static boolean isExampleOrTest(ServerAssetRoots.JsonFile file) {
        String path = file.relativePath().toLowerCase(Locale.ENGLISH);
        String name = file.name().toLowerCase(Locale.ENGLISH);
        return path.contains("/examples/") || name.startsWith("example_") || name.startsWith("test_");
    }

    private static boolean isHardMode(String encounterId, Map<String, JsonElement> params) {
        JsonElement hardFlag = params.get("BossBarHardMode");
        return encounterId.toLowerCase(Locale.ENGLISH).contains("hard")
                || (hardFlag != null && hardFlag.isJsonPrimitive() && hardFlag.getAsJsonPrimitive().isBoolean()
                && hardFlag.getAsBoolean());
    }

    // ---- Encounter resolution -------------------------------------------------------------------

    private static Encounter resolveEncounter(String name, Map<String, ServerAssetRoots.JsonFile> files, int depth) {
        ServerAssetRoots.JsonFile file = files.get(name);
        if (file == null || depth > MAX_DEPTH) return null;
        JsonObject json = file.json();
        String reference = string(json.get("Reference"));
        if ("Variant".equals(string(json.get("Type"))) && reference != null) {
            Encounter base = resolveEncounter(reference, files, depth + 1);
            if (base == null) return null;
            Map<String, JsonElement> params = new LinkedHashMap<>(base.params);
            JsonObject modify = object(json.get("Modify"));
            if (modify != null) modify.entrySet().forEach(entry -> params.put(entry.getKey(), entry.getValue()));
            String comment = string(json.get("$Comment"));
            return new Encounter(params, base.instructions, comment != null ? comment : base.comment);
        }
        JsonArray instructions = json.has("Instructions") && json.get("Instructions").isJsonArray()
                ? json.getAsJsonArray("Instructions") : new JsonArray();
        return new Encounter(parameterValues(json), instructions, string(json.get("$Comment")));
    }

    private static Map<String, JsonElement> parameterValues(JsonObject json) {
        Map<String, JsonElement> values = new LinkedHashMap<>();
        JsonObject parameters = object(json.get("Parameters"));
        if (parameters == null) return values;
        for (Map.Entry<String, JsonElement> entry : parameters.entrySet()) {
            JsonObject parameter = object(entry.getValue());
            values.put(entry.getKey(), parameter != null && parameter.has("Value") ? parameter.get("Value") : entry.getValue());
        }
        return values;
    }

    private static JsonElement compute(JsonElement value, Map<String, JsonElement> params) {
        for (int i = 0; i < MAX_DEPTH; i++) {
            JsonObject object = object(value);
            if (object == null || !object.has("Compute")) return value;
            value = params.get(string(object.get("Compute")));
        }
        return value;
    }

    private static boolean usesBossBar(Encounter encounter, Map<String, ServerAssetRoots.JsonFile> files) {
        return bossBarName(encounter, files) != null || containsType(encounter.instructions, "SetEncounterBossBar", files, 0);
    }

    private static boolean containsType(JsonElement root, String type, Map<String, ServerAssetRoots.JsonFile> files, int depth) {
        boolean[] found = {false};
        walk(root, node -> {
            if (found[0]) return;
            if (type.equals(string(node.get("Type")))) {
                found[0] = true;
                return;
            }
            String reference = string(node.get("Reference"));
            ServerAssetRoots.JsonFile macro = reference == null ? null : files.get(reference);
            if (macro != null && depth < 3 && "Instruction".equals(string(macro.json().get("Class")))
                    && containsType(macro.json(), type, files, depth + 1)) {
                found[0] = true;
            }
        });
        return found[0];
    }

    /** Localization key of the boss bar, set inline or through an encounter parameter. */
    private static String bossBarName(Encounter encounter, Map<String, ServerAssetRoots.JsonFile> files) {
        String[] name = {null};
        walk(encounter.instructions, node -> {
            if (name[0] == null && "SetEncounterBossBar".equals(string(node.get("Type")))) {
                name[0] = string(compute(node.get("Name"), encounter.params));
            }
        });
        if (name[0] == null) name[0] = string(compute(encounter.params.get("BossBarName"), encounter.params));
        return name[0];
    }

    private static String findActionValue(Encounter encounter, String type, String field) {
        String[] value = {null};
        walk(encounter.instructions, node -> {
            if (value[0] == null && type.equals(string(node.get("Type")))) {
                value[0] = string(compute(node.get(field), encounter.params));
            }
        });
        return value[0];
    }

    // ---- Phases ---------------------------------------------------------------------------------

    private static List<BossPhase> readPhases(Encounter encounter, Map<String, ServerAssetRoots.JsonFile> files,
                                              Map<String, ServerAssetRoots.JsonFile> combatConfigs) {
        Map<String, JsonObject> stateNodes = new LinkedHashMap<>();
        walk(encounter.instructions, node -> {
            JsonObject sensor = object(node.get("Sensor"));
            if (sensor != null && "State".equals(string(sensor.get("Type"))) && node.has("Instructions")) {
                String state = string(sensor.get("State"));
                if (state != null) stateNodes.putIfAbsent(state, node);
            }
        });

        Map<String, List<Transition>> incoming = new LinkedHashMap<>();
        Map<String, List<Transition>> outgoing = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> state : stateNodes.entrySet()) {
            for (Transition transition : transitions(state.getKey(), state.getValue(), encounter, files)) {
                incoming.computeIfAbsent(transition.target, ignored -> new ArrayList<>()).add(transition);
                outgoing.computeIfAbsent(transition.source, ignored -> new ArrayList<>()).add(transition);
            }
        }

        List<BossPhase> phases = new ArrayList<>();
        Double previousThreshold = null;
        for (Map.Entry<String, JsonObject> state : stateNodes.entrySet()) {
            String stateId = state.getKey();
            if (NON_PHASE_STATES.contains(stateId.toLowerCase(Locale.ENGLISH))) continue;
            List<Transition> into = incoming.getOrDefault(stateId, List.of());
            String role = into.stream().map(t -> t.role).filter(r -> r != null).findFirst().orElse(null);
            boolean invulnerable = into.stream().anyMatch(t -> t.invulnerable);

            Double entryThreshold = into.stream().filter(t -> t.healthMax != null && !t.once)
                    .map(t -> t.healthMax).max(Double::compare).orElse(null);
            Double exitThreshold = outgoing.getOrDefault(stateId, List.of()).stream()
                    .filter(t -> t.healthMax != null && !t.once && !NON_PHASE_STATES.contains(t.target.toLowerCase(Locale.ENGLISH)))
                    .map(t -> t.healthMax).max(Double::compare).orElse(null);

            String healthWindow = null;
            if (!invulnerable && (entryThreshold != null || exitThreshold != null || previousThreshold != null)) {
                double start = entryThreshold != null ? entryThreshold : previousThreshold != null ? previousThreshold : 1.0;
                double end = exitThreshold != null ? exitThreshold : 0.0;
                healthWindow = percent(start) + " - " + percent(end) + " health";
            }
            if (entryThreshold != null) previousThreshold = entryThreshold;

            phases.add(new BossPhase(stateId, role, triggerLabel(into, encounter), healthWindow, invulnerable,
                    string(state.getValue().get("$Comment")), role == null ? List.of() : readAttacks(role, combatConfigs)));
        }
        return phases;
    }

    private static List<Transition> transitions(String source, JsonObject stateNode, Encounter encounter,
                                                Map<String, ServerAssetRoots.JsonFile> files) {
        List<Transition> transitions = new ArrayList<>();
        walk(stateNode.get("Instructions"), node -> {
            JsonObject modify = object(node.get("Modify"));
            String reference = string(node.get("Reference"));
            if (modify != null && reference != null && modify.has("_ExportStates") && modify.get("_ExportStates").isJsonArray()
                    && !modify.getAsJsonArray("_ExportStates").isEmpty()) {
                Map<String, JsonElement> macro = new LinkedHashMap<>();
                ServerAssetRoots.JsonFile macroFile = files.get(reference);
                if (macroFile != null) macro.putAll(parameterValues(macroFile.json()));
                modify.entrySet().forEach(entry -> macro.put(entry.getKey(), compute(entry.getValue(), encounter.params)));
                double[] health = range(compute(macro.get("HealthRange"), encounter.params));
                transitions.add(new Transition(source, string(modify.getAsJsonArray("_ExportStates").get(0)),
                        string(compute(macro.get("NextRole"), encounter.params)),
                        health == null ? null : health[health.length - 1],
                        string(compute(macro.get("TimerName"), encounter.params)),
                        bool(compute(macro.get("Invulnerable"), encounter.params)),
                        macro.get("GateFlag") != null));
                return;
            }
            JsonArray actions = node.has("Actions") && node.get("Actions").isJsonArray() ? node.getAsJsonArray("Actions") : null;
            if (actions == null) return;
            String target = null;
            String role = null;
            for (JsonElement action : actions) {
                JsonObject object = object(action);
                if (object == null) continue;
                String type = string(object.get("Type"));
                if ("State".equals(type) && target == null) target = string(object.get("State"));
                if ("ChangeTargetRole".equals(type) && role == null) role = string(compute(object.get("Role"), encounter.params));
            }
            if (target != null && !target.equals(source)) {
                transitions.add(new Transition(source, target, role, null, null, false, false));
            }
        });
        return transitions;
    }

    private static String triggerLabel(List<Transition> into, Encounter encounter) {
        List<String> labels = new ArrayList<>();
        for (Transition transition : into) {
            String label = null;
            if (transition.healthMax != null) {
                label = "At " + percent(transition.healthMax) + " health" + (transition.once ? " (once)" : "");
            } else if (transition.timer != null) {
                Double seconds = timerSeconds(encounter, transition.timer);
                label = seconds != null ? "Every " + formatNumber(seconds) + "s" : "On a timer";
            }
            if (label != null && !labels.contains(label)) labels.add(label);
        }
        return labels.isEmpty() ? null : String.join(" or ", labels);
    }

    private static Double timerSeconds(Encounter encounter, String timerName) {
        Double[] seconds = {null};
        walk(encounter.instructions, node -> {
            if (seconds[0] == null && "TimerStart".equals(string(node.get("Type"))) && timerName.equals(string(node.get("Name")))) {
                double[] range = range(compute(node.has("RestartValueRange") ? node.get("RestartValueRange") : node.get("StartValueRange"),
                        encounter.params));
                if (range != null) seconds[0] = range[0];
            }
        });
        return seconds[0];
    }

    // ---- Attacks --------------------------------------------------------------------------------

    private static List<BossAttack> readAttacks(String roleId, Map<String, ServerAssetRoots.JsonFile> combatConfigs) {
        String configId = MobMetadataRegistry.inheritedRoleString(roleId, "_CombatConfig");
        ServerAssetRoots.JsonFile config = configId == null ? null : combatConfigs.get(configId);
        JsonObject evaluator = config == null ? null : object(config.json().get("CombatActionEvaluator"));
        JsonObject actions = evaluator == null ? null : object(evaluator.get("AvailableActions"));
        if (actions == null) return List.of();
        List<BossAttack> attacks = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : actions.entrySet()) {
            JsonObject action = object(entry.getValue());
            if (action == null || !"Ability".equals(string(action.get("Type")))) continue;
            attacks.add(new BossAttack(entry.getKey(), firstSentence(string(action.get("Description")))));
        }
        return Collections.unmodifiableList(attacks);
    }

    /** Combat config descriptions append AI scoring notes after the first sentence. */
    private static String firstSentence(String text) {
        if (text == null) return null;
        int end = text.indexOf(". ");
        return end > 0 ? text.substring(0, end + 1) : text;
    }

    // ---- JSON helpers ---------------------------------------------------------------------------

    private static void walk(JsonElement element, Consumer<JsonObject> visitor) {
        if (element == null) return;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            visitor.accept(object);
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) walk(entry.getValue(), visitor);
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) walk(child, visitor);
        }
    }

    private static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String string(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) return null;
        String value = element.getAsString();
        return value.isEmpty() ? null : value;
    }

    private static boolean bool(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() && element.getAsBoolean();
    }

    private static double[] range(JsonElement element) {
        if (element == null) return null;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) return new double[]{element.getAsDouble()};
        if (!element.isJsonArray() || element.getAsJsonArray().isEmpty()) return null;
        JsonArray array = element.getAsJsonArray();
        double[] values = new double[array.size()];
        for (int i = 0; i < array.size(); i++) {
            if (!array.get(i).isJsonPrimitive()) return null;
            values[i] = array.get(i).getAsDouble();
        }
        return values;
    }

    private static String percent(double ratio) {
        return Math.round(ratio * 100) + "%";
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ENGLISH, "%.1f", value);
    }

    private static String key(String mobId) {
        return mobId.toLowerCase(Locale.ENGLISH);
    }

    // ---- Model ----------------------------------------------------------------------------------

    private record Data(List<BossInfo> bosses, Map<String, BossInfo> byMember) {
    }

    private record Encounter(Map<String, JsonElement> params, JsonArray instructions, String comment) {
    }

    private record Transition(String source, String target, String role, Double healthMax, String timer,
                              boolean invulnerable, boolean once) {
    }

    /**
     * @param key            stable id used for server.lang overrides, usually the memory name (Goblin_Gobliterator)
     * @param nameKey        localization key of the boss bar name, if the encounter shows one
     * @param encounterNotes developer notes on the encounter, shown when no description override exists
     */
    public record BossInfo(String key, String representativeMobId, String nameKey, String encounterNotes,
                           String rewardId, List<BossVariant> variants, Set<String> memberMobIds) {
    }

    public record BossVariant(String encounterId, boolean hardMode, List<BossPhase> phases) {
    }

    /**
     * @param trigger      what starts this phase, e.g. "At 67% health" or "Every 60s"
     * @param healthWindow boss health range this phase covers, e.g. "67% - 34% health"
     */
    public record BossPhase(String stateId, String roleId, String trigger, String healthWindow,
                            boolean invulnerable, String notes, List<BossAttack> attacks) {
    }

    public record BossAttack(String key, String description) {
    }
}
