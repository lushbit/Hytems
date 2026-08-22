package dev.lushbit.hytems.ui.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.lushbit.hytems.ui.HytemsBookManager;

/** Runs after Close and re-enables the book's secondary interaction. */
public class HytemsLexiconUnlockInteraction extends SimpleInteraction {
    public static final BuilderCodec<HytemsLexiconUnlockInteraction> CODEC = BuilderCodec
            .builder(HytemsLexiconUnlockInteraction.class, HytemsLexiconUnlockInteraction::new, SimpleInteraction.CODEC)
            .build();

    @Override
    protected void tick0(boolean firstRun, float dt, InteractionType type, InteractionContext context, CooldownHandler cooldownHandler) {
        if (!firstRun) {
            return;
        }

        Ref<EntityStore> ref = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            HytemsBookManager.endClosing(playerRef.getUuid(), context.getHeldItemSlot());
        }
    }
}
