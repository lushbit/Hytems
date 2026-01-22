package de.notjan.hytems.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import de.notjan.hytems.gui.HytemsBrowserPage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Command to open the Hytems item browser GUI.
 *
 * @author NotJan
 */
public class HytemsCommand extends AbstractCommand {

    public HytemsCommand() {
        super("hytems", "Opens the Hytems item browser", false);
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        CommandSender sender = context.sender();

        if (!(sender instanceof Player)) {
            return CompletableFuture.completedFuture(null);
        }

        Player player = (Player) sender;
        Ref<EntityStore> ref = player.getReference();

        if (ref == null || !ref.isValid()) {
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();

        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

            if (playerRef != null) {
                HytemsBrowserPage browserPage = new HytemsBrowserPage(playerRef, CustomPageLifetime.CanDismiss);
                player.getPageManager().openCustomPage(ref, store, browserPage);
            }
        }, world);
    }
}
