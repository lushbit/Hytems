package de.notjan.hytems;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import de.notjan.hytems.commands.HytemsCommand;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class HytemsPlugin extends JavaPlugin {

    public static Map<String, Item> ITEMS = new HashMap<>();
    public static final RecipeManager recipeManager = new RecipeManager();

    public HytemsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        super.setup();

        this.getCommandRegistry().registerCommand(new HytemsCommand());
        this.getEventRegistry().register(LoadedAssetsEvent.class, Item.class, this::onItemAssetLoad);
        this.getEventRegistry().register(LoadedAssetsEvent.class, CraftingRecipe.class, this::onRecipeAssetLoad);
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
}
