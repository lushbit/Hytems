package de.notjan.hytems.gui;

import com.buuz135.mhud.MultipleHUD;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import de.notjan.hytems.HytemsPlugin;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PinnedItemsInventoryTracker {
    
    private static final long UPDATE_THROTTLE = 250L;
    private static final Map<UUID, Long> updateTimestamps = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Integer>> cachedInventories = new ConcurrentHashMap<>();
    
    public static void onInventoryChange(LivingEntityInventoryChangeEvent event) {
        Ref<EntityStore> ref = event.getEntity().getReference();
        if (ref == null || !ref.isValid()) return;
        
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        
        if (player == null || playerRef == null) return;
        if (HytemsPlugin.pinnedItemsManager.getPinnedCount(playerRef) == 0) return;
        
        UUID playerId = playerRef.getUuid();
        if (!shouldUpdate(playerId)) return;
        
        Map<String, Integer> current = scanPlayerInventory(player);
        Map<String, Integer> cached = cachedInventories.get(playerId);
        
        if (cached == null || !current.equals(cached)) {
            updateTimestamps.put(playerId, System.currentTimeMillis());
            cachedInventories.put(playerId, current);
            refreshHud(playerRef, store, ref);
        }
    }
    
    private static boolean shouldUpdate(UUID playerId) {
        Long lastUpdate = updateTimestamps.get(playerId);
        return lastUpdate == null || (System.currentTimeMillis() - lastUpdate) >= UPDATE_THROTTLE;
    }
    
    public static Map<String, Integer> scanPlayerInventory(@Nonnull Player player) {
        Map<String, Integer> items = new HashMap<>();
        Inventory inv = player.getInventory();
        
        if (inv != null) {
            inv.getCombinedEverything().forEach((slot, stack) -> {
                if (stack != null && stack.getItemId() != null) {
                    items.merge(stack.getItemId(), stack.getQuantity(), Integer::sum);
                }
            });
        }
        
        return items;
    }
    
    public static void refreshHud(PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> ref) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            PinnedItemsHud hud = new PinnedItemsHud(playerRef, HytemsPlugin.pinnedItemsManager, store, ref);
            MultipleHUD.getInstance().setCustomHud(player, playerRef, "hytems_pinned_items", hud);
        }
    }
    
    public static void clearCache(UUID playerId) {
        updateTimestamps.remove(playerId);
        cachedInventories.remove(playerId);
    }
}
