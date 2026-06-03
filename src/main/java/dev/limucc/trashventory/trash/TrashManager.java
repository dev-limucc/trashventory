package dev.limucc.trashventory.trash;

import dev.limucc.trashventory.config.PlayerSettings;
import dev.limucc.trashventory.config.PlayerSettingsStore;
import dev.limucc.trashventory.config.TrashConfig;
import dev.limucc.trashventory.config.TrashConfigManager;
import dev.limucc.trashventory.net.TrashConfigPayload;
import dev.limucc.trashventory.net.TrashOpenPayload;
import dev.limucc.trashventory.net.TrashSettingsPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * The whole trash lifecycle: open the bin, start/pause/resume the deletion countdown, persist across
 * disconnects, and enforce per-player blacklists. Sessions live in memory only while the player is
 * online; offline players keep their bin on disk with a frozen countdown.
 */
public final class TrashManager {

    private TrashManager() {}

    private static final Component TITLE = Component.literal("Trash Bin");

    private static final Map<UUID, TrashSession> SESSIONS = new HashMap<>();
    private static MinecraftServer server;
    private static Path dataDir;

    // ── lifecycle ──────────────────────────────────────────────────────────────
    public static void init(MinecraftServer mcServer) {
        server = mcServer;
        dataDir = mcServer.getWorldPath(LevelResource.ROOT).resolve("trashventory");
        try {
            Files.createDirectories(dataDir);
        } catch (Exception ignored) {
            // created lazily on first save otherwise
        }
        SESSIONS.clear();
        PlayerSettingsStore.init(dataDir);
    }

    public static void shutdown() {
        if (server != null) {
            // Save EVERY non-empty bin (including ones a player has open right now) so a shutdown never
            // loses items. An open bin is saved as-is and simply resumes paused on next load.
            for (Map.Entry<UUID, TrashSession> e : SESSIONS.entrySet()) {
                TrashSession s = e.getValue();
                if (!s.isEmpty()) {
                    s.paused = true;
                    TrashPersistence.save(dataDir, e.getKey(), s, server.registryAccess());
                }
            }
        }
        SESSIONS.clear();
        server = null;
    }

    // ── open / close ─────────────────────────────────────────────────────────────
    public static void open(ServerPlayer player) {
        TrashSession s = getOrLoad(player.getUUID());
        int rows = s.size / 9;
        MenuType<?> type = s.size >= 54 ? MenuType.GENERIC_9x6 : MenuType.GENERIC_9x3;

        s.reopening = true;
        OptionalInt containerId = player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new TrashMenu(id, inv, s.container, rows, type, () -> onClosed(player)),
                TITLE));
        s.reopening = false;
        s.open = true;
        s.countingDown = false;      // countdown is paused while the bin is open
        s.remainingTicks = 0;

        // Tell the modded client this exact container is the trash bin (so it can show the settings gear,
        // and never confuse it with an ordinary chest that merely shares the title).
        containerId.ifPresent(id -> {
            if (ServerPlayNetworking.canSend(player, TrashOpenPayload.TYPE)) {
                ServerPlayNetworking.send(player, new TrashOpenPayload(id));
            }
        });
    }

    private static void onClosed(ServerPlayer player) {
        UUID id = player.getUUID();
        TrashSession s = SESSIONS.get(id);
        if (s == null || s.reopening) return;   // ignore the close caused by an immediate re-open

        s.open = false;
        if (s.isEmpty()) {
            remove(id);
            return;
        }

        int delay = effectiveDelaySeconds(player);
        if (delay <= 0) {                        // timer off → instant deletion, no message
            s.container.clearContent();
            remove(id);
            return;
        }

        s.remainingTicks = delay * 20;
        s.countingDown = true;
        if (!player.hasDisconnected() && showMessage(player, delay)) {
            player.sendSystemMessage(Component.literal(
                    "§7[Trash] Items will be deleted in §e" + delay + "s§7. Use §a/trash§7 to get them back."));
        }
        if (server != null) TrashPersistence.save(dataDir, id, s, server.registryAccess());
    }

    // ── connection events ─────────────────────────────────────────────────────────
    public static void onJoin(ServerPlayer player, MinecraftServer mcServer) {
        UUID id = player.getUUID();
        if (!SESSIONS.containsKey(id)) {
            TrashSession loaded = TrashPersistence.load(dataDir, id, mcServer.registryAccess());
            if (loaded != null) {
                loaded.paused = false;           // online again → countdown resumes
                loaded.open = false;
                SESSIONS.put(id, loaded);
            }
        }
        // Push the server's limits AND this player's authoritative settings, so the client GUI matches
        // whatever commands set (works even after the player customised from a vanilla client earlier).
        if (ServerPlayNetworking.canSend(player, TrashConfigPayload.TYPE)) {
            ServerPlayNetworking.send(player, configPayload());
        }
        sendSettings(player);
    }

    /** Push a player's authoritative settings to their client (on join / after a command change). */
    public static void sendSettings(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, TrashSettingsPayload.TYPE)) return;
        PlayerSettings ps = PlayerSettingsStore.get(player.getUUID());
        ServerPlayNetworking.send(player,
                new TrashSettingsPayload(new java.util.ArrayList<>(ps.blacklist), ps.personalTimer, ps.showMessage));
    }

    public static void onDisconnect(ServerPlayer player) {
        UUID id = player.getUUID();
        TrashSession s = SESSIONS.get(id);
        if (s != null) {
            // Disconnecting while browsing counts as a close (no message), so items aren't lost.
            if (s.open && !s.countingDown && !s.isEmpty()) {
                int delay = effectiveDelaySeconds(player);
                if (delay <= 0) {
                    s.container.clearContent();
                } else {
                    s.remainingTicks = delay * 20;
                    s.countingDown = true;
                }
            }
            s.open = false;

            if (s.countingDown && !s.isEmpty()) {
                s.paused = true;
                if (server != null) TrashPersistence.save(dataDir, id, s, server.registryAccess());
                SESSIONS.remove(id);             // free memory; bin lives on disk, frozen
            } else {
                remove(id);                      // empty → nothing to keep
            }
        }
        // Player settings persist (server-authoritative); nothing to drop on disconnect.
    }

    // ── per-tick work ─────────────────────────────────────────────────────────────
    public static void tick(MinecraftServer mcServer) {
        if (!SESSIONS.isEmpty()) {
            List<UUID> expired = null;
            for (Map.Entry<UUID, TrashSession> e : SESSIONS.entrySet()) {
                TrashSession s = e.getValue();
                if (!s.countingDown || s.paused || s.open) continue;
                if (--s.remainingTicks <= 0) {
                    s.container.clearContent();
                    if (expired == null) expired = new ArrayList<>();
                    expired.add(e.getKey());
                }
            }
            if (expired != null) for (UUID id : expired) remove(id);
        }

        // Blacklist enforcement — only players who actually have a non-empty blacklist.
        for (Map.Entry<UUID, Set<Item>> e : PlayerSettingsStore.resolvedBlacklists().entrySet()) {
            ServerPlayer p = mcServer.getPlayerList().getPlayer(e.getKey());
            if (p != null) enforceBlacklist(p, e.getValue());
        }
    }

    private static void enforceBlacklist(ServerPlayer p, Set<Item> blacklist) {
        Inventory inv = p.getInventory();
        NonNullList<ItemStack> items = inv.getNonEquipmentItems();   // 36 main slots (where pickups land)
        boolean changed = false;
        for (int i = 0; i < items.size(); i++) {
            ItemStack st = items.get(i);
            if (!st.isEmpty() && blacklist.contains(st.getItem())) {
                inv.setItem(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) p.containerMenu.broadcastChanges();
    }

    // ── helpers ────────────────────────────────────────────────────────────────────
    private static TrashSession getOrLoad(UUID id) {
        TrashSession s = SESSIONS.get(id);
        if (s == null) {
            if (server != null) s = TrashPersistence.load(dataDir, id, server.registryAccess());
            if (s == null) s = new TrashSession(TrashConfigManager.get().chestSize());
            s.paused = false;
            SESSIONS.put(id, s);
        }
        return s;
    }

    private static void remove(UUID id) {
        SESSIONS.remove(id);
        if (dataDir != null) TrashPersistence.delete(dataDir, id);
    }

    /** Effective deletion delay for this player: server default, clamped by the player's shorter timer. */
    public static int effectiveDelaySeconds(ServerPlayer player) {
        TrashConfig c = TrashConfigManager.get();
        if (!c.countdownEnabled) return 0;
        int delay = Math.max(0, c.removeDelaySeconds);
        if (!c.allowClientOverride) return delay;
        int personal = PlayerSettingsStore.get(player.getUUID()).personalTimer;
        if (personal < 0) return delay;            // follow server default
        return Math.min(delay, personal);          // 0 = instant; >0 = shorter only
    }

    private static boolean showMessage(ServerPlayer player, int delay) {
        if (delay <= 0 || !TrashConfigManager.get().showCloseMessage) return false;
        return PlayerSettingsStore.get(player.getUUID()).showMessage;
    }

    public static TrashConfigPayload configPayload() {
        TrashConfig c = TrashConfigManager.get();
        return new TrashConfigPayload(c.removeDelaySeconds, c.countdownEnabled, c.doubleChest,
                c.showCloseMessage, c.allowClientOverride);
    }

    /** Admin "/trash admin clear <player>": wipe a player's bin immediately. */
    public static void clear(ServerPlayer target) {
        UUID id = target.getUUID();
        TrashSession s = SESSIONS.get(id);
        if (s != null) s.container.clearContent();
        remove(id);
    }
}
