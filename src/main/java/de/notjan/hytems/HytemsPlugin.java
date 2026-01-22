package de.notjan.hytems;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import de.notjan.hytems.commands.HytemsCommand;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class HytemsPlugin extends JavaPlugin {

    // Store all game items
    public static Map<String, Item> ITEMS = new HashMap<>();

    public HytemsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        super.setup();

        // Register command
        this.getCommandRegistry().registerCommand(new HytemsCommand());

        // Listen for item assets loading
        this.getEventRegistry().register(LoadedAssetsEvent.class, Item.class, this::onItemAssetLoad);
    }

    private void onItemAssetLoad(LoadedAssetsEvent<String, Item, DefaultAssetMap<String, Item>> event) {
        // Store all items from the game
        ITEMS = event.getAssetMap().getAssetMap();

        // Correct logging method for HytaleLogger
        this.getLogger().at(Level.INFO).log("Loaded %d items for Hytems browser", ITEMS.size());
    }
}
