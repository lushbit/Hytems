package dev.lushbit.hytems.ui;

import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.lushbit.hytems.asset.BossRegistry.BossAttack;
import dev.lushbit.hytems.asset.BossRegistry.BossInfo;
import dev.lushbit.hytems.asset.BossRegistry.BossPhase;

/**
 * Boss page wording. Everything has a generated fallback from the game files; entries under
 * {@code hytems.boss.<BossKey>.*} in server.lang replace it where the generated text reads poorly:
 * {@code name}, {@code description}, {@code location},
 * {@code phase.<State>.name}, {@code phase.<State>.desc},
 * {@code attack.<AttackKey>.name}, {@code attack.<AttackKey>.desc},
 * and {@code hytems.reward.<RewardId>} for encounter rewards.
 */
public final class BossText {
    private BossText() {
    }

    public static String name(String language, BossInfo boss) {
        String override = message(language, "hytems.boss." + boss.key() + ".name");
        if (override != null) return override;
        if (boss.nameKey() != null) {
            String translated = message(language, boss.nameKey().replaceFirst("^server\\.", ""));
            if (translated != null) return translated;
        }
        String roleName = message(language, "npcRoles." + boss.representativeMobId() + ".name");
        return roleName != null ? roleName : TextFormatters.humanize(boss.key());
    }

    /** English name for places without a player context, like sorting the mob browser. */
    public static String defaultName(BossInfo boss) {
        return name(I18nModule.DEFAULT_LANGUAGE, boss);
    }

    public static String description(PlayerRef player, BossInfo boss) {
        String override = message(language(player), "hytems.boss." + boss.key() + ".description");
        return override != null ? override : boss.encounterNotes();
    }

    public static String location(PlayerRef player, BossInfo boss) {
        return message(language(player), "hytems.boss." + boss.key() + ".location");
    }

    public static String phaseName(PlayerRef player, BossInfo boss, BossPhase phase) {
        String override = message(language(player), "hytems.boss." + boss.key() + ".phase." + phase.stateId() + ".name");
        return override != null ? override : TextFormatters.humanize(phase.stateId().replaceAll("(?<=[a-z])(?=[A-Z])|(?<=[A-Za-z])(?=\\d)", "_"));
    }

    public static String phaseDescription(PlayerRef player, BossInfo boss, BossPhase phase) {
        String override = message(language(player), "hytems.boss." + boss.key() + ".phase." + phase.stateId() + ".desc");
        if (override != null) return override;
        if (phase.notes() != null) return phase.notes();
        boolean singlePhase = boss.variants().stream().allMatch(variant -> variant.phases().size() == 1);
        return singlePhase ? "Fights in a single phase without transitions." : null;
    }

    public static String attackName(PlayerRef player, BossInfo boss, BossAttack attack) {
        String override = message(language(player), "hytems.boss." + boss.key() + ".attack." + attack.key() + ".name");
        return override != null ? override : TextFormatters.humanize(attack.key());
    }

    public static String attackDescription(PlayerRef player, BossInfo boss, BossAttack attack) {
        String override = message(language(player), "hytems.boss." + boss.key() + ".attack." + attack.key() + ".desc");
        return override != null ? override : attack.description();
    }

    public static String reward(PlayerRef player, String rewardId) {
        if (rewardId == null) return null;
        String override = message(language(player), "hytems.reward." + rewardId);
        return override != null ? override : TextFormatters.humanize(rewardId.replaceFirst("(?i)^Reward_", ""));
    }

    public static String language(PlayerRef player) {
        try {
            String language = player == null ? null : player.getLanguage();
            return language != null ? language : I18nModule.DEFAULT_LANGUAGE;
        } catch (Exception ignored) {
            return I18nModule.DEFAULT_LANGUAGE;
        }
    }

    private static String message(String language, String key) {
        try {
            String fullKey = "server." + key;
            String value = I18nModule.get().getMessage(language, fullKey);
            if ((value == null || value.equals(fullKey)) && !I18nModule.DEFAULT_LANGUAGE.equals(language)) {
                value = I18nModule.get().getMessage(I18nModule.DEFAULT_LANGUAGE, fullKey);
            }
            return value == null || value.isEmpty() || value.equals(fullKey) ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }
}
