package dev.lushbit.hytems.ui.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class PinnedItemsHudManager {
    
    public void registerPlayer(PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> ref) {
        PinnedItemsInventoryTracker.refreshHud(playerRef, store, ref);
    }
}
