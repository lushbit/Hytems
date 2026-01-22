package de.notjan.hytems;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.logger.HytaleLogger;
import de.notjan.hytems.commands.HytemsCommand;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Hytems - A Hytale server plugin.
 *
 * @author NotJan
 * @version 1.0.0
 */
public class HytemsPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static HytemsPlugin instance;

    public HytemsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static HytemsPlugin getInstance() {
        return instance;
    }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("[Hytems] Setting up...");

        // Register commands
        this.getCommandRegistry().registerCommand(new HytemsCommand());

        LOGGER.at(Level.INFO).log("[Hytems] Setup complete!");
    }

    @Override
    protected void start() {
        LOGGER.at(Level.INFO).log("[Hytems] Started!");
    }

    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("[Hytems] Shutting down...");
        instance = null;
    }
}
