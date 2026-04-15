package de.notjan.hytems.pin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import de.notjan.hytems.ui.hud.PinnedItemsInventoryTracker;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PinnedItemsManager {
    private static final int MAX_PINNED_ITEMS = 3;
    private static final int MAX_FAVORITE_ITEMS = 7;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final Map<UUID, LinkedHashSet<String>> playerPinnedItems = new ConcurrentHashMap<>();
    private final Map<UUID, LinkedHashSet<String>> playerFavoriteItems = new ConcurrentHashMap<>();
    private final Set<UUID> loadedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Path dataDirectory;
    
    public void setDataDirectory(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        try {
            Files.createDirectories(dataDirectory.resolve("players"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadData(PlayerRef playerRef) {
        if (dataDirectory == null) return;
        UUID uuid = playerRef.getUuid();
        Path playerFile = dataDirectory.resolve("players").resolve(uuid.toString() + ".json");

        loadedPlayers.add(uuid);
        
        if (Files.exists(playerFile)) {
            try (Reader reader = Files.newBufferedReader(playerFile)) {
                PlayerData data = GSON.fromJson(reader, PlayerData.class);
                if (data != null) {
                    if (data.pinnedItems != null && !data.pinnedItems.isEmpty()) {
                        playerPinnedItems.put(uuid, new LinkedHashSet<>(data.pinnedItems));
                    }
                    if (data.favoriteItems != null && !data.favoriteItems.isEmpty()) {
                        playerFavoriteItems.put(uuid, new LinkedHashSet<>(data.favoriteItems));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void saveData(PlayerRef playerRef) {
        if (dataDirectory == null) return;
        UUID uuid = playerRef.getUuid();
        if (loadedPlayers.contains(uuid)) {
            saveDataForUuid(uuid);
        }
    }

    private void saveDataForUuid(UUID uuid) {
        if (dataDirectory == null) return;
        if (!loadedPlayers.contains(uuid)) return; // CRITICAL: Don't save if not loaded

        LinkedHashSet<String> pinned = playerPinnedItems.get(uuid);
        LinkedHashSet<String> favorites = playerFavoriteItems.get(uuid);

        Path playerFile = dataDirectory.resolve("players").resolve(uuid.toString() + ".json");

        if ((pinned == null || pinned.isEmpty()) && (favorites == null || favorites.isEmpty())) {
            try {
                Files.deleteIfExists(playerFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        PlayerData data = new PlayerData();
        data.pinnedItems = pinned != null ? new ArrayList<>(pinned) : new ArrayList<>();
        data.favoriteItems = favorites != null ? new ArrayList<>(favorites) : new ArrayList<>();
        
        try (Writer writer = Files.newBufferedWriter(playerFile)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveAll() {
        if (dataDirectory == null) return;
        loadedPlayers.forEach(this::saveDataForUuid);
    }
    
    public boolean togglePin(PlayerRef playerRef, String itemId) {
        boolean result = toggleItem(playerRef, itemId, playerPinnedItems, MAX_PINNED_ITEMS);
        saveData(playerRef);
        return result;
    }
    
    public boolean isPinned(PlayerRef playerRef, String itemId) {
        LinkedHashSet<String> pinnedItems = playerPinnedItems.get(playerRef.getUuid());
        return pinnedItems != null && pinnedItems.contains(itemId);
    }
    
    public List<String> getPinnedItems(PlayerRef playerRef) {
        return getItems(playerRef, playerPinnedItems);
    }
    
    public void setPinnedItems(PlayerRef playerRef, Collection<String> items) {
        setItems(playerRef, items, playerPinnedItems);
    }

    public boolean toggleFavorite(PlayerRef playerRef, String itemId) {
        boolean result = toggleItem(playerRef, itemId, playerFavoriteItems, MAX_FAVORITE_ITEMS);
        saveData(playerRef);
        return result;
    }

    public boolean isFavorite(PlayerRef playerRef, String itemId) {
        LinkedHashSet<String> favoriteItems = playerFavoriteItems.get(playerRef.getUuid());
        return favoriteItems != null && favoriteItems.contains(itemId);
    }

    public List<String> getFavoriteItems(PlayerRef playerRef) {
        return getItems(playerRef, playerFavoriteItems);
    }

    public void setFavoriteItems(PlayerRef playerRef, Collection<String> items) {
        setItems(playerRef, items, playerFavoriteItems);
    }
    
    public int getPinnedCount(PlayerRef playerRef) {
        LinkedHashSet<String> pinnedItems = playerPinnedItems.get(playerRef.getUuid());
        return pinnedItems != null ? pinnedItems.size() : 0;
    }
    
    public boolean canPin(PlayerRef playerRef) {
        return getPinnedCount(playerRef) < MAX_PINNED_ITEMS;
    }
    
    public boolean removePin(PlayerRef playerRef, String itemId) {
        UUID uuid = playerRef.getUuid();
        LinkedHashSet<String> pinnedItems = playerPinnedItems.get(uuid);
        if (pinnedItems != null && pinnedItems.contains(itemId)) {
            pinnedItems.remove(itemId);
            saveData(playerRef);
            return true;
        }
        return false;
    }
    
    public boolean movePinUp(PlayerRef playerRef, String itemId) {
        return movePin(playerRef, itemId, -1);
    }
    
    public boolean movePinDown(PlayerRef playerRef, String itemId) {
        return movePin(playerRef, itemId, 1);
    }

    private boolean toggleItem(PlayerRef playerRef, String itemId,
                               Map<UUID, LinkedHashSet<String>> itemMap, int maxItems) {
        UUID uuid = playerRef.getUuid();
        loadedPlayers.add(uuid);
        LinkedHashSet<String> items = itemMap.computeIfAbsent(uuid, k -> new LinkedHashSet<>());

        if (items.remove(itemId)) {
            return false;
        }

        if (items.size() >= maxItems) {
            return false;
        }

        items.add(itemId);
        return true;
    }

    private List<String> getItems(PlayerRef playerRef, Map<UUID, LinkedHashSet<String>> itemMap) {
        LinkedHashSet<String> items = itemMap.get(playerRef.getUuid());
        return items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    private void setItems(PlayerRef playerRef, Collection<String> items, Map<UUID, LinkedHashSet<String>> itemMap) {
        UUID uuid = playerRef.getUuid();
        loadedPlayers.add(uuid);
        itemMap.put(uuid, new LinkedHashSet<>(items));
        saveData(playerRef);
    }

    private boolean movePin(PlayerRef playerRef, String itemId, int offset) {
        UUID uuid = playerRef.getUuid();
        LinkedHashSet<String> pinnedItems = playerPinnedItems.get(uuid);
        if (pinnedItems == null || !pinnedItems.contains(itemId)) {
            return false;
        }
        
        List<String> itemList = new ArrayList<>(pinnedItems);
        int index = itemList.indexOf(itemId);
        
        int targetIndex = index + offset;
        if (targetIndex < 0 || targetIndex >= itemList.size()) {
            return false;
        }
        
        Collections.swap(itemList, index, targetIndex);
        pinnedItems.clear();
        pinnedItems.addAll(itemList);
        saveData(playerRef);
        return true;
    }

    public void cleanup(PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        saveDataForUuid(uuid);
        playerPinnedItems.remove(uuid);
        playerFavoriteItems.remove(uuid);
        loadedPlayers.remove(uuid);
        PinnedItemsInventoryTracker.clearCache(uuid);
    }

    private static class PlayerData {
        List<String> pinnedItems;
        List<String> favoriteItems;
    }
}
