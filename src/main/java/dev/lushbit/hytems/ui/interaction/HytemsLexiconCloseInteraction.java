package dev.lushbit.hytems.ui.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.lushbit.hytems.ui.HytemsBookManager;

/** Cancels the close animation and releases the book as soon as its slot changes. */
public class HytemsLexiconCloseInteraction extends SimpleInteraction {
    public static final BuilderCodec<HytemsLexiconCloseInteraction> CODEC = BuilderCodec
            .builder(HytemsLexiconCloseInteraction.class, HytemsLexiconCloseInteraction::new, SimpleInteraction.CODEC)
            .build();

    @Override
    protected void tick0(boolean firstRun, float dt, InteractionType type, InteractionContext context, CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        InventoryComponent.Hotbar hotbar = commandBuffer.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        byte closeSlot = context.getHeldItemSlot();

        if (playerRef == null || hotbar == null
                || hotbar.getActiveSlot() != closeSlot
                || !HytemsBookManager.isLexicon(hotbar.getInventory().getItemStack(closeSlot))) {
            if (playerRef != null) {
                HytemsBookManager.endClosing(playerRef.getUuid(), closeSlot);
            }
            context.getState().state = InteractionState.ItemChanged;
            context.getState().progress = dt;
        }
    }
}
