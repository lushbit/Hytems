package dev.lushbit.hytems.ui.interaction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.lushbit.hytems.ui.page.HytemsNavigationPage;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class HytemsLexiconOpenInteraction extends SimpleInteraction {
    public static final String TYPE = "HytemsLexiconOpen";
    private static final String CLOSE_ROOT_INTERACTION = "Item_Lexicon_Close_Root";
    private static final String OPEN_ANIMATION_PATH = "Common/Characters/Animations/Items/Item_Lexicon_Open.blockyanim";
    private static final Path DEV_OPEN_ANIMATION_PATH = Path.of("src/main/resources").resolve(OPEN_ANIMATION_PATH);
    private static final double ANIMATION_FRAMES_PER_SECOND = 60.0;
    private static final double OPEN_SECONDS = readLastKeyframeSeconds();
    private static final MetaKey<Boolean> MENU_SHOWN = Interaction.META_REGISTRY.registerMetaObject();

    public static final BuilderCodec<HytemsLexiconOpenInteraction> CODEC = BuilderCodec
            .builder(HytemsLexiconOpenInteraction.class, HytemsLexiconOpenInteraction::new, SimpleInteraction.CODEC)
            .build();

    @Override
    protected void tick0(boolean firstRun, float elapsed, InteractionType type, InteractionContext context, CooldownHandler cooldownHandler) {
        context.getState().state = InteractionState.NotFinished;
        if (elapsed < OPEN_SECONDS) {
            return;
        }

        Ref<EntityStore> ref = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        if (!context.getInstanceStore().hasMetaObject(MENU_SHOWN)) {
            context.getInstanceStore().putMetaObject(MENU_SHOWN, Boolean.TRUE);
            player.getPageManager().openCustomPage(ref, commandBuffer.getStore(), new HytemsNavigationPage(playerRef));
            return;
        }

        if (player.getPageManager().getCustomPage() == null) {
            runCloseChain(context, type);
            context.getState().state = InteractionState.Failed;
        }
    }

    private static void runCloseChain(InteractionContext context, InteractionType type) {
        InteractionManager manager = context.getInteractionManager();
        InteractionContext closeContext = InteractionContext.forInteraction(
                manager,
                context.getEntity(),
                type,
                context.getHeldItemSlot(),
                context.getCommandBuffer()
        );
        manager.queueExecuteChain(manager.initChain(
                type,
                closeContext,
                RootInteraction.getRootInteractionOrUnknown(CLOSE_ROOT_INTERACTION),
                false
        ));
    }

    private static double readLastKeyframeSeconds() {
        JsonObject animation = readAnimation();
        if (animation == null || !animation.has("nodeAnimations")) {
            return 0;
        }

        double lastFrame = 0;
        for (Map.Entry<String, JsonElement> node : animation.getAsJsonObject("nodeAnimations").entrySet()) {
            if (!node.getValue().isJsonObject()) {
                continue;
            }
            for (Map.Entry<String, JsonElement> channel : node.getValue().getAsJsonObject().entrySet()) {
                if (!channel.getValue().isJsonArray()) {
                    continue;
                }
                for (JsonElement keyframe : channel.getValue().getAsJsonArray()) {
                    if (keyframe.isJsonObject() && keyframe.getAsJsonObject().has("time")) {
                        lastFrame = Math.max(lastFrame, keyframe.getAsJsonObject().get("time").getAsDouble());
                    }
                }
            }
        }
        return lastFrame / ANIMATION_FRAMES_PER_SECOND;
    }

    private static JsonObject readAnimation() {
        ClassLoader loader = HytemsLexiconOpenInteraction.class.getClassLoader();
        try (InputStream stream = loader == null
                ? ClassLoader.getSystemResourceAsStream(OPEN_ANIMATION_PATH)
                : loader.getResourceAsStream(OPEN_ANIMATION_PATH)) {
            if (stream != null) {
                return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (Exception ignored) {
        }

        try {
            if (Files.isRegularFile(DEV_OPEN_ANIMATION_PATH)) {
                return JsonParser.parseString(Files.readString(DEV_OPEN_ANIMATION_PATH, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
