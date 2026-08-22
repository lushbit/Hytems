package dev.lushbit.hytems;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.npc.AllNPCsLoadedEvent;
import com.hypixel.hytale.server.spawning.assets.spawns.config.BeaconNPCSpawn;
import com.hypixel.hytale.server.spawning.assets.spawns.config.WorldNPCSpawn;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.lushbit.hytems.asset.DropListRegistry;
import dev.lushbit.hytems.asset.ItemSearchService;
import dev.lushbit.hytems.asset.MobMetadataRegistry;
import dev.lushbit.hytems.asset.PrefabDropMetadataRegistry;
import dev.lushbit.hytems.asset.RecipeManager;
import dev.lushbit.hytems.command.HytemsCommand;
import dev.lushbit.hytems.data.PlayerDataManager;
import dev.lushbit.hytems.ui.HytemsBookManager;
import dev.lushbit.hytems.ui.ItemUiSupport;
import dev.lushbit.hytems.ui.hud.PinnedItemsHudManager;
import dev.lushbit.hytems.ui.hud.PinnedItemsInventoryTracker;
import dev.lushbit.hytems.ui.interaction.HytemsLexiconBrowserInteraction;
import dev.lushbit.hytems.ui.interaction.HytemsLexiconCloseInteraction;
import dev.lushbit.hytems.ui.interaction.HytemsLexiconOpenInteraction;
import dev.lushbit.hytems.ui.interaction.HytemsLexiconUnlockInteraction;
import dev.lushbit.hytems.ui.page.HytemsNavigationPage;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class HytemsPlugin extends JavaPlugin {

    public static Map<String, Item> ITEMS = new HashMap<>();
    public static Map<String, ItemQuality> QUALITIES = new HashMap<>();
    public static final RecipeManager recipeManager = new RecipeManager();
    public static final DropListRegistry dropListRegistry = new DropListRegistry();
    public static final PlayerDataManager playerDataManager = new PlayerDataManager();
    public static final PinnedItemsHudManager pinnedItemsHudManager = new PinnedItemsHudManager();

    public HytemsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        super.setup();

        playerDataManager.setDataDirectory(this.getDataDirectory());

        OpenCustomUIInteraction.registerCustomPageSupplier(
                this,
                HytemsBookManager.class,
                HytemsBookManager.OPEN_BROWSER_PAGE_SUPPLIER_ID,
                (ref, accessor, playerRef, interactionContext) -> {
                    return new HytemsNavigationPage(
                            playerRef,
                            HytemsBookManager.createSession(ref, accessor, playerRef.getUuid())
                    );
                }
        );
        this.getCodecRegistry(Interaction.CODEC).register(
                HytemsBookManager.OPEN_BROWSER_INTERACTION_TYPE,
                HytemsLexiconBrowserInteraction.class,
                HytemsLexiconBrowserInteraction.CODEC
        );
        this.getCodecRegistry(Interaction.CODEC).register(
                HytemsBookManager.OPEN_INTERACTION_TYPE,
                HytemsLexiconOpenInteraction.class,
                HytemsLexiconOpenInteraction.CODEC
        );
        this.getCodecRegistry(Interaction.CODEC).register(
                HytemsBookManager.CLOSE_INTERACTION_TYPE,
                HytemsLexiconCloseInteraction.class,
                HytemsLexiconCloseInteraction.CODEC
        );
        this.getCodecRegistry(Interaction.CODEC).register(
                HytemsBookManager.UNLOCK_INTERACTION_TYPE,
                HytemsLexiconUnlockInteraction.class,
                HytemsLexiconUnlockInteraction.CODEC
        );

        this.getCommandRegistry().registerCommand(new HytemsCommand());
        this.getEventRegistry().register(LoadedAssetsEvent.class, Item.class, this::onItemAssetLoad);
        this.getEventRegistry().register(LoadedAssetsEvent.class, ItemQuality.class, this::onQualityAssetLoad);
        this.getEventRegistry().register(LoadedAssetsEvent.class, CraftingRecipe.class, this::onRecipeAssetLoad);
        this.getEventRegistry().register(LoadedAssetsEvent.class, ItemDropList.class, this::onDropListAssetLoad);
        this.getEventRegistry().register(LoadedAssetsEvent.class, WorldNPCSpawn.class, this::onWorldNpcSpawnAssetLoad);
        this.getEventRegistry().register(LoadedAssetsEvent.class, BeaconNPCSpawn.class, this::onBeaconNpcSpawnAssetLoad);
        this.getEventRegistry().registerGlobal(AllNPCsLoadedEvent.class, event -> MobMetadataRegistry.markNpcDataDirty());

        this.getEventRegistry().register(PlayerConnectEvent.class, event -> {
            playerDataManager.loadData(event.getPlayerRef());
        });

        this.getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            playerDataManager.cleanup(event.getPlayerRef());
        });

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Player player = event.getPlayer();
            if (player == null) return;

            Ref<EntityStore> ref = event.getPlayerRef();
            if (ref == null) return;

            Store<EntityStore> store = ref.getStore();
            if (store == null) return;

            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

            if (playerRef != null && playerDataManager.getPinnedCount(playerRef) > 0) {
                pinnedItemsHudManager.registerPlayer(playerRef, store, ref);
            }
            if (playerRef != null) {
                ItemUiSupport.prewarmTranslations(playerRef, ITEMS);
                ItemSearchService.prewarmBaseResults(playerRef, ITEMS);
            }
        });

        this.getEntityStoreRegistry().registerSystem(new EntityEventSystem<EntityStore, InventoryChangeEvent>(InventoryChangeEvent.class) {
            @Override
            public void handle(int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer, InventoryChangeEvent event) {
                PinnedItemsInventoryTracker.onInventoryChange(entityId, chunk, store, buffer, event);
            }

            @Override
            public Query<EntityStore> getQuery() {
                return Player.getComponentType();
            }
        });

    }

    @Override
    protected void shutdown() {
        playerDataManager.saveAll();
        super.shutdown();
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
            PrefabDropMetadataRegistry.startAsyncPreload();
            this.getLogger().at(Level.INFO).log("Loaded %d drop lists for Hytems browser", dropListRegistry.size());
        } else {
            this.getLogger().at(Level.WARNING).log("[Hytems] No drop lists in LoadedAssetsEvent");
        }
    }

    private void onQualityAssetLoad(LoadedAssetsEvent<String, ItemQuality, IndexedLookupTableAssetMap<String, ItemQuality>> event) {
        QUALITIES = event.getAssetMap().getAssetMap();
        this.getLogger().at(Level.INFO).log("Loaded %d item qualities for Hytems browser", QUALITIES.size());
    }

    private void onWorldNpcSpawnAssetLoad(LoadedAssetsEvent<String, WorldNPCSpawn, IndexedLookupTableAssetMap<String, WorldNPCSpawn>> event) {
        MobMetadataRegistry.reloadWorldNpcSpawns(event.getAssetMap().getAssetMap());
        this.getLogger().at(Level.INFO).log("Loaded %d world NPC spawns for Hytems mob overview", event.getAssetMap().getAssetMap().size());
    }

    private void onBeaconNpcSpawnAssetLoad(LoadedAssetsEvent<String, BeaconNPCSpawn, IndexedLookupTableAssetMap<String, BeaconNPCSpawn>> event) {
        MobMetadataRegistry.reloadBeaconNpcSpawns(event.getAssetMap().getAssetMap());
        this.getLogger().at(Level.INFO).log("Loaded %d beacon NPC spawns for Hytems mob overview", event.getAssetMap().getAssetMap().size());
    }

}
