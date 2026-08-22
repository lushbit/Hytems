package dev.lushbit.hytems.ui.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;

public class HytemsLexiconOpenInteraction extends SimpleInteraction {
    public static final BuilderCodec<HytemsLexiconOpenInteraction> CODEC = BuilderCodec
            .builder(HytemsLexiconOpenInteraction.class, HytemsLexiconOpenInteraction::new, SimpleInteraction.CODEC)
            .build();

    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void tick0(boolean firstRun, float dt, InteractionType type, InteractionContext context, CooldownHandler cooldownHandler) {
    }
}
