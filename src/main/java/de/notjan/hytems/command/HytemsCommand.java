package de.notjan.hytems.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import de.notjan.hytems.ui.page.HytemsBrowserPage;

import javax.annotation.Nonnull;

public class HytemsCommand extends AbstractPlayerCommand {

    public HytemsCommand() {
        super("hytems", "Opens the Hytems item browser");
        this.addAliases("h");

        this.addSubCommand(new PinsSubCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
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

        HytemsBrowserPage page = new HytemsBrowserPage(playerRef, CustomPageLifetime.CanDismiss);
        player.getPageManager().openCustomPage(ref, store, page);
    }
}
