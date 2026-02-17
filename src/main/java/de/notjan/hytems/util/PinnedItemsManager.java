package de.notjan.hytems.util;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.*;

public class PinnedItemsManager {
    private static final int MAX_PINNED_ITEMS = 3;
    
    private final Map<PlayerRef, LinkedHashSet<String>> playerPinnedItems = new HashMap<>();
    
    public boolean togglePin(PlayerRef playerRef, String itemId) {
        LinkedHashSet<String> pinnedItems = playerPinnedItems.computeIfAbsent(playerRef, k -> new LinkedHashSet<>());
        
        if (pinnedItems.contains(itemId)) {
            pinnedItems.remove(itemId);
            return false;
        } else {
            if (pinnedItems.size() >= MAX_PINNED_ITEMS) {
                return false;
            }
            pinnedItems.add(itemId);
            return true;
        }
    }
    
    public boolean isPinned(PlayerRef playerRef, String itemId) {
        LinkedHashSet<String> pinnedItems = playerPinnedItems.get(playerRef);
        return pinnedItems != null && pinnedItems.contains(itemId);
    }
    
    public List<String> getPinnedItems(PlayerRef playerRef) {
        LinkedHashSet<String> pinnedItems = playerPinnedItems.get(playerRef);
        return pinnedItems != null ? new ArrayList<>(pinnedItems) : new ArrayList<>();
    }
    
    public int getPinnedCount(PlayerRef playerRef) {
        LinkedHashSet<String> pinnedItems = playerPinnedItems.get(playerRef);
        return pinnedItems != null ? pinnedItems.size() : 0;
    }
    
    public boolean canPin(PlayerRef playerRef) {
        return getPinnedCount(playerRef) < MAX_PINNED_ITEMS;
    }
}
