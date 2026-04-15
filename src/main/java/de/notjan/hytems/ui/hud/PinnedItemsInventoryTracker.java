package de.notjan.hytems.ui.hud;

import com.buuz135.mhud.MultipleHUD;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
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
    
    public static void onInventoryChange(int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer, InventoryChangeEvent event) {
        Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
        if (ref == null || !ref.isValid()) return;
        
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        
        if (player == null || playerRef == null) return;
        if (HytemsPlugin.pinnedItemsManager.getPinnedCount(playerRef) == 0) return;
        
        UUID playerId = playerRef.getUuid();
        if (!shouldUpdate(playerId)) return;
        
        Map<String, Integer> current = scanPlayerInventory(store, ref);
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
    
    public static Map<String, Integer> scanPlayerInventory(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        Map<String, Integer> items = new HashMap<>();
        
        InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING).forEach((slot, stack) -> {
            if (stack != null && stack.getItemId() != null) {
                items.merge(stack.getItemId(), stack.getQuantity(), Integer::sum);
            }
        });
        
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
