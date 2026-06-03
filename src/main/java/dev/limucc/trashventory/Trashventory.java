package dev.limucc.trashventory;

import dev.limucc.trashventory.command.TrashCommands;
import dev.limucc.trashventory.config.PlayerSettingsStore;
import dev.limucc.trashventory.config.TrashConfigManager;
import dev.limucc.trashventory.net.TrashConfigPayload;
import dev.limucc.trashventory.net.TrashOpenPayload;
import dev.limucc.trashventory.net.TrashPrefsPayload;
import dev.limucc.trashventory.net.TrashSettingsPayload;
import dev.limucc.trashventory.trash.TrashManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (server + client) entry point. Everything that must exist on a dedicated server lives here:
 * config, commands, the trash state machine, persistence and the C2S/S2C payload registration.
 * The whole /trash feature works for vanilla clients because the bin is a standard {@code ChestMenu}.
 */
public class Trashventory implements ModInitializer {

    public static final String MOD_ID = "trashventory";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        TrashConfigManager.load();

        // Payload types must be registered on both logical sides.
        PayloadTypeRegistry.serverboundPlay().register(TrashPrefsPayload.TYPE, TrashPrefsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TrashConfigPayload.TYPE, TrashConfigPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TrashOpenPayload.TYPE, TrashOpenPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TrashSettingsPayload.TYPE, TrashSettingsPayload.CODEC);

        // The client GUI pushes the player's full settings here; the server store is authoritative.
        ServerPlayNetworking.registerGlobalReceiver(TrashPrefsPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server != null) server.execute(() ->
                    PlayerSettingsStore.applyFromClient(player.getUUID(),
                            payload.blacklist(), payload.personalTimer(), payload.showMessage()));
        });

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> TrashCommands.register(dispatcher, registryAccess));

        ServerLifecycleEvents.SERVER_STARTED.register(TrashManager::init);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> TrashManager.shutdown());
        ServerTickEvents.END_SERVER_TICK.register(TrashManager::tick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                TrashManager.onJoin(handler.player, server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                TrashManager.onDisconnect(handler.player));

        LOGGER.info("Trashventory loaded.");
    }
}
