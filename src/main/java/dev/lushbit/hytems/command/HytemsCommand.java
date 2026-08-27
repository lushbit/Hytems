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
import dev.lushbit.hytems.ui.page.HytemsNavigationPage;

import javax.annotation.Nonnull;

public class HytemsCommand extends AbstractPlayerCommand {

    public HytemsCommand() {
        super("hytems", "Opens the Hytems menu");
        this.addAliases("h");
        this.requireNoPermission();

        this.addSubCommand(new BrowserSubCommand());
        this.addSubCommand(new PinsSubCommand());
        this.addSubCommand(new MobsSubCommand());
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

        player.getPageManager().openCustomPage(ref, store, new HytemsNavigationPage(playerRef));
    }
}
