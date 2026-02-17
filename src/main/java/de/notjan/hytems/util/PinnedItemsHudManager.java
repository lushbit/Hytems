package de.notjan.hytems.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import de.notjan.hytems.gui.PinnedItemsInventoryTracker;

public class PinnedItemsHudManager {
    
    public void registerPlayer(PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> ref) {
        PinnedItemsInventoryTracker.refreshHud(playerRef, store, ref);
    }
}
