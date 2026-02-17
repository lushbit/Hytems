package de.notjan.hytems;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import de.notjan.hytems.command.HytemsCommand;
import de.notjan.hytems.gui.PinnedItemsInventoryTracker;
import de.notjan.hytems.util.PinnedItemsHudManager;
import de.notjan.hytems.util.PinnedItemsManager;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class HytemsPlugin extends JavaPlugin {

    public static Map<String, Item> ITEMS = new HashMap<>();
    public static final RecipeManager recipeManager = new RecipeManager();
    public static final DropListRegistry dropListRegistry = new DropListRegistry();
    public static final PinnedItemsManager pinnedItemsManager = new PinnedItemsManager();
    public static final PinnedItemsHudManager pinnedItemsHudManager = new PinnedItemsHudManager();

    public HytemsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        super.setup();

        this.getCommandRegistry().registerCommand(new HytemsCommand());
        this.getEventRegistry().register(LoadedAssetsEvent.class, Item.class, this::onItemAssetLoad);
        this.getEventRegistry().register(LoadedAssetsEvent.class, CraftingRecipe.class, this::onRecipeAssetLoad);
        this.getEventRegistry().register(LoadedAssetsEvent.class, ItemDropList.class, this::onDropListAssetLoad);
        this.getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, PinnedItemsInventoryTracker::onInventoryChange);
    }

    private void onItemAssetLoad(LoadedAssetsEvent<String, Item, DefaultAssetMap<String, Item>> event) {
        ITEMS = event.getAssetMap().getAssetMap();

        this.getLogger().at(Level.INFO).log("Loaded %d items for Hytems browser", ITEMS.size());
    }

    private void onRecipeAssetLoad(LoadedAssetsEvent<String, CraftingRecipe, DefaultAssetMap<String, CraftingRecipe>> event) {
        Map<String, CraftingRecipe> recipes = event.getAssetMap().getAssetMap();
        recipeManager.initialize(recipes);

        this.getLogger().at(Level.INFO).log("Loaded %d recipes for Hytems browser", recipeManager.getTotalRecipeCount());
    }

    private void onDropListAssetLoad(LoadedAssetsEvent<String, ItemDropList, DefaultAssetMap<String, ItemDropList>> event) {
        Map<String, ItemDropList> dropLists = event.getAssetMap().getAssetMap();
        if (dropLists != null && !dropLists.isEmpty()) {
            dropListRegistry.reload(dropLists);
            this.getLogger().at(Level.INFO).log("Loaded %d drop lists for Hytems browser", dropListRegistry.size());
        } else {
            this.getLogger().at(Level.WARNING).log("[Hytems] No drop lists in LoadedAssetsEvent");
        }
    }
}
