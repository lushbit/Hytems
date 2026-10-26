package dev.lushbit.hytems.ui;

import com.hypixel.hytale.protocol.AbilityDescriptionValue;
import com.hypixel.hytale.protocol.DoubleParamValue;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.IntParamValue;
import com.hypixel.hytale.protocol.ParamValue;
import com.hypixel.hytale.protocol.StringParamValue;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CoreItemAbility;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemAbility;
import com.hypixel.hytale.server.core.asset.type.item.config.SupportItemAbility;
import com.hypixel.hytale.server.core.asset.type.item.config.effectsTooltip.ItemEffectsTooltip;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.util.MessageUtil;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

final class ItemDescriptionResolver {
    private static final int MAX_DEPTH = 8;

    private ItemDescriptionResolver() {
    }

    static String resolveDescription(String language, Item item) {
        String text = "";
        String key = item.getDescriptionTranslationKey();
        if (key != null && !key.isEmpty()) {
            Map<String, ParamValue> abilityParams = abilityParams(item.getAbility());
            Message message = item.getDescriptionTranslationMessage();
            text = message != null ? resolve(language, message.getFormattedMessage(), abilityParams, 0) : "";
            if (text.equals(key)) text = "";
        }

        String effects = effectsTooltip(language, item);
        if (effects.isEmpty()) return text;
        return text.isEmpty() ? effects : text + "\n\n" + effects;
    }

    private static String effectsTooltip(String language, Item item) {
        ItemEffectsTooltip tooltip = item.getEffectsTooltip();
        if (tooltip == null || tooltip.effects() == null) return "";
        StringJoiner lines = new StringJoiner("\n");
        for (Message effect : tooltip.effects()) {
            if (effect == null) continue;
            String line = resolve(language, effect.getFormattedMessage(), Map.of(), 0).trim();
            if (!line.isEmpty()) lines.add(line);
        }
        return lines.toString();
    }

    private static Map<String, ParamValue> abilityParams(ItemAbility ability) {
        Map<String, AbilityDescriptionValue> values = null;
        if (ability instanceof CoreItemAbility core) values = core.getDescriptionValues();
        else if (ability instanceof SupportItemAbility support) values = support.getDescriptionValues();
        if (values == null) return Map.of();

        Map<String, ParamValue> params = new HashMap<>();
        values.forEach((name, value) -> {
            if (name == null || value == null) return;
            ParamValue param = number(value.value);
            params.put(name, param);
            params.putIfAbsent(name.toLowerCase(Locale.ROOT), param);
        });
        return params;
    }

    private static String resolve(String language, FormattedMessage message, Map<String, ParamValue> inherited, int depth) {
        if (message == null || depth > MAX_DEPTH) return "";

        StringBuilder out = new StringBuilder();
        if (message.messageId != null && !message.messageId.isEmpty()) {
            Map<String, ParamValue> params = new HashMap<>(inherited);
            if (message.params != null) {
                message.params.forEach((name, value) -> params.put(name, normalizeNumber(value)));
            }
            if (message.messageParams != null) {
                message.messageParams.forEach((name, nested) ->
                        params.put(name, new StringParamValue(resolve(language, nested, Map.of(), depth + 1))));
            }
            String template = I18nModule.get().getMessage(language, message.messageId);
            out.append(template == null ? message.messageId : MessageUtil.formatText(template, params, Map.of()));
        } else if (message.rawText != null) {
            out.append(message.rawText);
        }

        if (message.children != null) {
            for (FormattedMessage child : message.children) {
                out.append(resolve(language, child, inherited, depth + 1));
            }
        }
        return out.toString();
    }

    private static ParamValue number(double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        if (rounded == Math.rint(rounded) && Math.abs(rounded) <= Integer.MAX_VALUE) {
            return new IntParamValue((int) rounded);
        }
        return new DoubleParamValue(rounded);
    }

    private static ParamValue normalizeNumber(ParamValue value) {
        return value instanceof DoubleParamValue d ? number(d.value) : value;
    }
}
