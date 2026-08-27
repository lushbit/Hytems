package dev.lushbit.hytems.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.lushbit.hytems.ui.page.PinsManagementPage;

import javax.annotation.Nonnull;

public class PinsSubCommand extends AbstractPlayerCommand {

    public PinsSubCommand() {
        super("pins", "Manage your pinned items");
        this.requireNoPermission();
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef, @Nonnull World world) {

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Error: Could not get player"));
            return;
        }

        PinsManagementPage page = new PinsManagementPage(playerRef, true);
        player.getPageManager().openCustomPage(ref, store, page);
    }
}
