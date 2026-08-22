package dev.lushbit.hytems.ui;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HytemsBookManager {
    public static final String LEXICON_ITEM_ID = "Item_Lexicon";
    public static final String CLOSE_ROOT_INTERACTION_ID = "Item_Lexicon_Close_Root";
    public static final String OPEN_BROWSER_PAGE_SUPPLIER_ID = "hytems:item_browser";
    public static final String OPEN_BROWSER_INTERACTION_TYPE = "HytemsLexiconBrowser";
    public static final String OPEN_INTERACTION_TYPE = "HytemsLexiconOpen";
    public static final String UNLOCK_INTERACTION_TYPE = "HytemsLexiconUnlock";
    private static final Map<SessionKey, LexiconSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Set<SessionKey> CLOSING = ConcurrentHashMap.newKeySet();

    private HytemsBookManager() {
    }

    public static LexiconSession createSession(@Nonnull Ref<EntityStore> ref,
                                               @Nonnull ComponentAccessor<EntityStore> accessor,
                                               @Nullable UUID playerId) {
        InventoryComponent.Hotbar hotbar = accessor.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null || playerId == null) {
            return LexiconSession.inactive();
        }

        byte activeSlot = hotbar.getActiveSlot();
        ItemStack heldItem = hotbar.getInventory().getItemStack(activeSlot);
        if (!isLexicon(heldItem)) {
            return LexiconSession.inactive();
        }

        SessionKey key = new SessionKey(playerId, activeSlot);
        LexiconSession session = new LexiconSession(key, activeSlot, false);
        SESSIONS.put(key, session);
        return session;
    }

    @Nullable
    public static LexiconSession getSession(@Nonnull UUID playerId, byte slot) {
        return SESSIONS.get(new SessionKey(playerId, slot));
    }

    public static void endSession(@Nullable LexiconSession session) {
        if (session != null && session.key != null) {
            SESSIONS.remove(session.key, session);
        }
    }

    public static void markClosing(@Nullable LexiconSession session) {
        if (session != null && session.key != null) {
            CLOSING.add(session.key);
        }
    }

    public static boolean isClosing(@Nullable UUID playerId, byte slot) {
        return playerId != null && CLOSING.contains(new SessionKey(playerId, slot));
    }

    public static void endClosing(@Nullable UUID playerId, byte slot) {
        if (playerId != null) {
            CLOSING.remove(new SessionKey(playerId, slot));
        }
    }

    public static boolean isLexicon(@Nullable ItemStack itemStack) {
        return itemStack != null && !itemStack.isEmpty() && LEXICON_ITEM_ID.equals(itemStack.getItemId());
    }

    public static final class LexiconSession {
        private final SessionKey key;
        private final byte slot;
        private volatile boolean dismissed;

        private LexiconSession(@Nullable SessionKey key, byte slot, boolean dismissed) {
            this.key = key;
            this.slot = slot;
            this.dismissed = dismissed;
        }

        private static LexiconSession inactive() {
            return new LexiconSession(null, (byte) -1, true);
        }

        public boolean isActive() {
            return this.slot >= 0 && this.key != null;
        }

        public byte getSlot() {
            return this.slot;
        }

        public boolean isDismissed() {
            return this.dismissed;
        }

        public void markDismissed() {
            this.dismissed = true;
        }
    }

    private record SessionKey(UUID playerId, byte slot) {
    }
}
