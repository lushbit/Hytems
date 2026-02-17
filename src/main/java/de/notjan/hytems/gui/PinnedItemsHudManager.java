package de.notjan.hytems.gui;

import com.buuz135.mhud.MultipleHUD;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import de.notjan.hytems.HytemsPlugin;

public class PinnedItemsHudManager {
    
    public void registerPlayer(PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> ref) {
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                PinnedItemsHud pinnedHud = new PinnedItemsHud(playerRef, HytemsPlugin.pinnedItemsManager, store, ref);
                MultipleHUD.getInstance().setCustomHud(player, playerRef, "hytems_pinned_items", pinnedHud);
            }
        } catch (Exception e) {
            System.err.println("[Hytems] Failed to register pinned items HUD: " + e.getMessage());
        }
    }
}
