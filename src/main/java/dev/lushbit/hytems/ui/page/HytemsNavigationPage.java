package dev.lushbit.hytems.ui.page;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.lushbit.hytems.ui.HytemsBookManager;
import dev.lushbit.hytems.ui.HytemsUiTemplates;
import dev.lushbit.hytems.ui.ItemUiSupport;

import javax.annotation.Nonnull;

public class HytemsNavigationPage extends InteractiveCustomUIPage<HytemsNavigationPage.NavigationData> {
    private final PlayerRef playerRef;
    private final HytemsBookManager.LexiconSession lexiconSession;
    private boolean keepLexiconOpenOnDismiss;

    public HytemsNavigationPage(@Nonnull PlayerRef playerRef) {
        this(playerRef, null);
    }

    public HytemsNavigationPage(@Nonnull PlayerRef playerRef, HytemsBookManager.LexiconSession lexiconSession) {
        super(playerRef, CustomPageLifetime.CanDismiss, NavigationData.CODEC);
        this.playerRef = playerRef;
        this.lexiconSession = lexiconSession;
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        super.onDismiss(ref, store);
        if (!this.keepLexiconOpenOnDismiss && this.lexiconSession != null) {
            this.lexiconSession.markDismissed();
        }
        this.keepLexiconOpenOnDismiss = false;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append(HytemsUiTemplates.NAVIGATION_MENU);
        cmd.set("#PinsIcon.Background", ItemUiSupport.ICON_NAV_PIN);
        cmd.set("#BrowserIcon.Background", ItemUiSupport.ICON_NAV_ITEM_BROWSER);
        cmd.set("#MobsIcon.Background", ItemUiSupport.ICON_NAV_MOB_BROWSER);

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#PinsButton",
                EventData.of("NavAction", "pins"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#BrowserButton",
                EventData.of("NavAction", "browser"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#MobsButton",
                EventData.of("NavAction", "mobs"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("NavAction", "close"),
                false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull NavigationData data) {
        if ("close".equals(data.navAction)) {
            this.close();
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || data.navAction == null || data.navAction.isEmpty()) {
            return;
        }

        this.keepLexiconOpenOnDismiss = this.lexiconSession != null;
        if ("pins".equals(data.navAction)) {
            player.getPageManager().openCustomPage(ref, store, new PinsManagementPage(this.playerRef, true, this.lexiconSession));
        } else if ("browser".equals(data.navAction)) {
            player.getPageManager().openCustomPage(ref, store, new HytemsBrowserPage(this.playerRef, CustomPageLifetime.CanDismiss, true, this.lexiconSession));
        } else if ("mobs".equals(data.navAction)) {
            player.getPageManager().openCustomPage(ref, store, new MobBrowserPage(this.playerRef, true, this.lexiconSession));
        }
    }

    public static class NavigationData {
        public static final BuilderCodec<NavigationData> CODEC = BuilderCodec.builder(
                        NavigationData.class,
                        NavigationData::new
                )
                .addField(
                        new KeyedCodec<>("NavAction", Codec.STRING),
                        (data, value) -> data.navAction = value,
                        data -> data.navAction
                )
                .build();

        private String navAction;
    }
}
