package dev.lushbit.hytems.ui.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.lushbit.hytems.ui.HytemsBookManager;

/** Rejects a new book open while its close animation owns the interaction slot. */
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
        Ref<EntityStore> ref = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        InventoryComponent.Hotbar hotbar = commandBuffer.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (playerRef != null && hotbar != null && HytemsBookManager.isClosing(playerRef.getUuid(), hotbar.getActiveSlot())) {
            context.getState().state = InteractionState.Failed;
            context.getState().progress = dt;
            return;
        }

    }
}
