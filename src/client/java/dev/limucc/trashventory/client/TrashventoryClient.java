package dev.limucc.trashventory.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.limucc.trashventory.Trashventory;
import dev.limucc.trashventory.client.config.ClientConfigManager;
import dev.limucc.trashventory.client.gui.TrashventoryScreen;
import dev.limucc.trashventory.client.gui.widget.GearButton;
import dev.limucc.trashventory.client.gui.widget.TrashIconButton;
import dev.limucc.trashventory.client.net.ClientNet;
import dev.limucc.trashventory.net.TrashOpenPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ChestMenu;

public class TrashventoryClient implements ClientModInitializer {

    /** Options → Controls → Trashventory. */
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Trashventory.MOD_ID, "main"));

    public static KeyMapping OPEN_TRASH_KEY;
    public static KeyMapping OPEN_SETTINGS_KEY;

    // Two-sprite WidgetSprites: second sprite (accent-blue recolor) shows on hover/focus automatically.
    private static final WidgetSprites TRASH_SPRITES = new WidgetSprites(
            Identifier.fromNamespaceAndPath(Trashventory.MOD_ID, "trash"),
            Identifier.fromNamespaceAndPath(Trashventory.MOD_ID, "trash_hover"));
    private static final WidgetSprites GEAR_SPRITES = new WidgetSprites(
            Identifier.fromNamespaceAndPath(Trashventory.MOD_ID, "gear"),
            Identifier.fromNamespaceAndPath(Trashventory.MOD_ID, "gear_hover"));

    /** Container id of the bin the server last opened for us (-1 = none). Set via TrashOpenPayload. */
    private static int trashContainerId = -1;

    @Override
    public void onInitializeClient() {
        ClientConfigManager.load();
        ClientNet.registerReceivers();

        // The server tells us which container is the trash bin, so we never mistake an ordinary chest for it.
        ClientPlayNetworking.registerGlobalReceiver(TrashOpenPayload.TYPE, (payload, context) -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                trashContainerId = payload.containerId();
                addGearIfTrash(mc, mc.screen);   // bin may already be on screen when this arrives
            });
        });

        OPEN_TRASH_KEY = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.trashventory.open_trash", InputConstants.UNKNOWN.getValue(), CATEGORY));
        OPEN_SETTINGS_KEY = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.trashventory.open_settings", InputConstants.UNKNOWN.getValue(), CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_TRASH_KEY.consumeClick()) {
                if (mc.screen == null && mc.getConnection() != null) mc.getConnection().sendCommand("trash");
            }
            while (OPEN_SETTINGS_KEY.consumeClick()) {
                if (mc.screen == null) mc.setScreen(new TrashventoryScreen(null));
            }
            // Forget the trash container id once that bin is no longer the open screen, so a later chest
            // that happens to reuse the same numeric id can't inherit the gear.
            if (trashContainerId != -1 && !isTrashScreen(mc.screen)) trashContainerId = -1;
        });

        // The server pushes our authoritative settings on join, so we don't send anything here.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientNet.resetServerState();
            trashContainerId = -1;
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (addGearIfTrash(client, screen)) return;     // settings gear on our trash chest
            if (!ClientConfigManager.get().inventoryButton) return;

            // Survival inventory: glue the trash icon to the RIGHT of the recipe-book toggle so it stays
            // reachable even when the recipe book is open (which shifts the whole inventory to the right).
            if (screen instanceof InventoryScreen) {
                AbstractWidget recipeButton = null;
                for (AbstractWidget w : Screens.getWidgets(screen)) {
                    if (w instanceof ImageButton && !(w instanceof TrashIconButton)) { recipeButton = w; break; }
                }
                if (recipeButton != null) {
                    Screens.getWidgets(screen).add(
                            new TrashIconButton(recipeButton, TRASH_SPRITES, b -> openTrash(client)));
                    return;
                }
            }

            // Creative inventory (no recipe book): place it just left of the panel.
            if (screen instanceof CreativeModeInventoryScreen) {
                int left = (scaledWidth - 195) / 2;
                int top = (scaledHeight - 136) / 2;
                Screens.getWidgets(screen).add(new ImageButton(Math.max(2, left - 20), top + 4, 16, 16,
                        TRASH_SPRITES, b -> openTrash(client)));
            }
        });

        Trashventory.LOGGER.info("Trashventory client ready. Bind keys under Options → Controls → Trashventory.");
    }

    private static boolean isTrashScreen(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> acs
                && acs.getMenu() instanceof ChestMenu chest
                && chest.containerId == trashContainerId;
    }

    /** Adds the settings gear to the trash chest (top-centre of the title bar). Idempotent. */
    private static boolean addGearIfTrash(Minecraft client, Screen screen) {
        if (!isTrashScreen(screen)) return false;
        ChestMenu chest = (ChestMenu) ((AbstractContainerScreen<?>) screen).getMenu();
        for (AbstractWidget w : Screens.getWidgets(screen)) {
            if (w instanceof GearButton) return true;       // already added (e.g. payload + AFTER_INIT)
        }
        int imageWidth = 176;
        int imageHeight = 114 + chest.getRowCount() * 18;   // 3 rows -> 168, 6 rows -> 222
        int left = (screen.width - imageWidth) / 2;
        int top = (screen.height - imageHeight) / 2;
        Screens.getWidgets(screen).add(new GearButton(
                left + imageWidth / 2 - 6, top + 3, 13, 13, GEAR_SPRITES,   // 1:1 with the 13px sprite (crisp)
                b -> client.setScreen(new TrashventoryScreen(null))));
        return true;
    }

    public static void openTrash(Minecraft mc) {
        if (mc.getConnection() != null) mc.getConnection().sendCommand("trash");
    }
}
