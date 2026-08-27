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
import dev.lushbit.hytems.ui.page.MobBrowserPage;

import javax.annotation.Nonnull;

public class MobsSubCommand extends AbstractPlayerCommand {

    public MobsSubCommand() {
        super("mobs", "Opens the Hytems mob browser");
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

        player.getPageManager().openCustomPage(ref, store, new MobBrowserPage(playerRef, true));
    }
}
