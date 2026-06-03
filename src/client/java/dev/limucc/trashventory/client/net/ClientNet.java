package dev.limucc.trashventory.client.net;

import dev.limucc.trashventory.client.config.ClientConfig;
import dev.limucc.trashventory.client.config.ClientConfigManager;
import dev.limucc.trashventory.net.TrashConfigPayload;
import dev.limucc.trashventory.net.TrashPrefsPayload;
import dev.limucc.trashventory.net.TrashSettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;

/**
 * Client networking. The server is the source of truth: it pushes our settings on join and after any
 * command change ({@link TrashSettingsPayload}), and we push GUI edits back ({@link TrashPrefsPayload}).
 * It also tells us the server's limits ({@link TrashConfigPayload}) so the GUI can clamp the timer.
 */
public final class ClientNet {

    private ClientNet() {}

    // Server limits — defaults until a TrashConfigPayload arrives (vanilla servers never send one).
    public static volatile boolean serverHasMod = false;
    public static volatile int serverTimerSeconds = 10;
    public static volatile boolean countdownEnabled = true;
    public static volatile boolean allowClientOverride = true;
    public static volatile boolean serverDouble = true;

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(TrashConfigPayload.TYPE, (payload, context) -> {
            serverHasMod = true;
            serverTimerSeconds = payload.serverTimerSeconds();
            countdownEnabled = payload.countdownEnabled();
            allowClientOverride = payload.allowClientOverride();
            serverDouble = payload.doubleChest();
        });

        // Server-authoritative settings → adopt into our local cache (so the GUI matches commands).
        // Applied on the client thread; we do NOT echo back, to avoid a sync loop.
        ClientPlayNetworking.registerGlobalReceiver(TrashSettingsPayload.TYPE, (payload, context) ->
                Minecraft.getInstance().execute(() -> {
                    ClientConfig c = ClientConfigManager.get();
                    c.blacklist = new ArrayList<>(payload.blacklist());
                    c.personalTimer = payload.personalTimer();
                    c.showMessage = payload.showMessage();
                    ClientConfigManager.save();
                }));
    }

    public static void resetServerState() {
        serverHasMod = false;
    }

    /** Push our current settings to the server (called after a GUI edit). */
    public static void sendPrefs() {
        if (Minecraft.getInstance().getConnection() == null) return;
        if (!ClientPlayNetworking.canSend(TrashPrefsPayload.TYPE)) return;
        ClientConfig c = ClientConfigManager.get();
        ClientPlayNetworking.send(new TrashPrefsPayload(
                new ArrayList<>(c.blacklist), c.personalTimer, c.showMessage));
    }
}
