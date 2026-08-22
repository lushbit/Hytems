package dev.lushbit.hytems.ui.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.lushbit.hytems.ui.HytemsBookManager;
import dev.lushbit.hytems.ui.page.HytemsBrowserPage;

import javax.annotation.Nonnull;

public class HytemsLexiconBrowserInteraction extends SimpleInteraction {
    public static final BuilderCodec<HytemsLexiconBrowserInteraction> CODEC = BuilderCodec
            .builder(HytemsLexiconBrowserInteraction.class, HytemsLexiconBrowserInteraction::new, SimpleInteraction.CODEC)
            .build();

    public HytemsLexiconBrowserInteraction() {
    }

    @Override
    protected void tick0(boolean firstRun, float dt, InteractionType type, InteractionContext context, CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        InventoryComponent.Hotbar hotbar = commandBuffer.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        byte slot = hotbar != null ? hotbar.getActiveSlot() : -1;
        HytemsBookManager.LexiconSession session = slot >= 0
                ? HytemsBookManager.getSession(playerRef.getUuid(), slot)
                : null;

        if (firstRun) {
            session = openBrowserPage(ref, commandBuffer, playerRef);
            System.out.println("[HytemsLexicon] Browser started: slot=" + (session != null ? session.getSlot() : -1));
        }

        if (session == null || !session.isActive()) {
            System.out.println("[HytemsLexicon] Browser ended: no active session");
            context.getState().state = InteractionState.Failed;
            return;
        }

        if (!isStillHoldingLexicon(ref, commandBuffer, session.getSlot())) {
            System.out.println("[HytemsLexicon] Browser ended: held item changed");
            HytemsBookManager.endSession(session);
            context.getState().state = InteractionState.ItemChanged;
            return;
        }

        if (session.isDismissed()) {
            System.out.println("[HytemsLexicon] Browser dismissed: starting close");
            HytemsBookManager.markClosing(session);
            HytemsBookManager.endSession(session);
            InteractionContext closeContext = InteractionContext.forInteraction(
                    context.getInteractionManager(),
                    ref,
                    type,
                    session.getSlot(),
                    commandBuffer
            );
            InteractionChain closeChain = context.getInteractionManager().initChain(
                    type,
                    closeContext,
                    RootInteraction.getRootInteractionOrUnknown(HytemsBookManager.CLOSE_ROOT_INTERACTION_ID),
                    false
            );
            context.getInteractionManager().queueExecuteChain(closeChain);
            // The close chain owns the final visual state. Abort this infinite HoldOpen
            // interaction so its effect cannot be synchronized again after Close finishes.
            context.getState().state = InteractionState.Failed;
            context.getState().progress = dt;
            return;
        }

        context.getState().state = InteractionState.NotFinished;
        context.getState().progress = dt;
    }

    private static HytemsBookManager.LexiconSession openBrowserPage(@Nonnull Ref<EntityStore> ref,
                                                                    @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                                                    @Nonnull PlayerRef playerRef) {
        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null || player.getPageManager().getCustomPage() != null) {
            return null;
        }

        HytemsBookManager.LexiconSession session = HytemsBookManager.createSession(ref, commandBuffer, playerRef.getUuid());
        if (!session.isActive()) {
            return null;
        }

        player.getPageManager().openCustomPage(
                ref,
                commandBuffer.getStore(),
                new HytemsBrowserPage(playerRef, CustomPageLifetime.CanDismiss, session)
        );
        return session;
    }

    private static boolean isStillHoldingLexicon(@Nonnull Ref<EntityStore> ref,
                                                 @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                                 byte slot) {
        InventoryComponent.Hotbar hotbar = commandBuffer.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null || hotbar.getActiveSlot() != slot) {
            return false;
        }

        ItemStack heldItem = hotbar.getInventory().getItemStack(slot);
        return HytemsBookManager.isLexicon(heldItem);
    }
}
