package dev.limucc.trashventory.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.limucc.trashventory.client.compat.ScreenNav;
import dev.limucc.trashventory.client.config.ClientConfig;
import dev.limucc.trashventory.client.config.ClientConfigManager;
import dev.limucc.trashventory.client.gui.TrashventoryScreen;
import dev.limucc.trashventory.client.gui.widget.GearButton;
import dev.limucc.trashventory.client.gui.widget.TrashIconButton;
import dev.limucc.trashventory.config.PlayerSettings;
import dev.limucc.trashventory.config.PlayerSettingsStore;
import dev.limucc.trashventory.config.TrashConfigManager;
import dev.limucc.trashventory.trash.TrashManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runtime check of every Trashventory feature (server + client) with REAL client->server packets:
 * singleplayer (integrated server), leave/rejoin persistence, and a real dedicated server in the same JVM.
 *
 * <p>Trashventory ships no mixins, so the audit only proves the environment is clean; the interesting part is the
 * server state machine (dump -> close -> grab-back window -> expiry), personal timers, the blacklist and the GUI.
 * Admin delay is set to 4 s (80 ticks) so the whole run stays short.
 */
public class TrashventoryGameTest implements FabricClientGameTest {

    private static final List<String> CHAT = new CopyOnWriteArrayList<>();

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("TRASH-GAMETEST FAIL: " + what);
        System.out.println("[TRASH-GAMETEST] PASS: " + what);
    }

    private static void info(String what) {
        System.out.println("[TRASH-GAMETEST] INFO: " + what);
    }

    // ---- helpers ---------------------------------------------------------------------------------
    /** Command as the player (like typing it in chat). */
    private static void cmdP(ClientGameTestContext ctx, String c) {
        ctx.runOnClient(mc -> mc.getConnection().sendCommand(c));
        ctx.waitTicks(4);
    }

    /** Command as the server console (op level). */
    private static void cmdS(TestServerContext s, ClientGameTestContext ctx, String c) {
        s.runCommand(c);
        ctx.waitTicks(2);
    }

    private static UUID me(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> mc.player.getUUID());
    }

    private static int countInv(ClientGameTestContext ctx, Item item) {
        return ctx.computeOnClient(mc -> {
            Inventory inv = mc.player.getInventory();
            int n = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
            }
            return n;
        });
    }

    /** Items sitting in the open container's NON-player slots (the bin). */
    private static int countBin(ClientGameTestContext ctx, Item item) {
        return ctx.computeOnClient(mc -> {
            AbstractContainerMenu menu = mc.player.containerMenu;
            int n = 0;
            for (Slot s : menu.slots) {
                if (s.container != mc.player.getInventory() && s.getItem().is(item)) n += s.getItem().getCount();
            }
            return n;
        });
    }

    /** Shift-click every stack of {@code item} from the player inventory (fromBin=false) or from the bin (true). */
    private static void quickMove(ClientGameTestContext ctx, Item item, boolean fromBin) {
        ctx.runOnClient(mc -> {
            AbstractContainerMenu menu = mc.player.containerMenu;
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot s = menu.slots.get(i);
                boolean inBin = s.container != mc.player.getInventory();
                if (inBin == fromBin && s.getItem().is(item)) {
                    mc.gameMode.handleContainerInput(menu.containerId, s.index, 0, ContainerInput.QUICK_MOVE, mc.player);
                }
            }
        });
        ctx.waitTicks(6);
    }

    private static void openBin(ClientGameTestContext ctx) {
        cmdP(ctx, "trash");
        ctx.waitFor(mc -> ScreenNav.current(mc) instanceof ContainerScreen && mc.player.containerMenu instanceof ChestMenu, 100);
        ctx.waitTicks(8);   // TrashOpenPayload -> gear button
    }

    private static void closeScreen(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> mc.player.closeContainer());
        ctx.waitTicks(8);
    }

    private static int binSlots(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> mc.player.containerMenu.slots.size());
    }

    private static boolean hasWidget(ClientGameTestContext ctx, Class<?> type) {
        return ctx.computeOnClient(mc -> {
            Screen s = ScreenNav.current(mc);
            if (s == null) return false;
            for (AbstractWidget w : Screens.getWidgets(s)) if (type.isInstance(w)) return true;
            return false;
        });
    }

    private static void click(ClientGameTestContext ctx, double guiX, double guiY) {
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(guiX * scale, guiY * scale);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(4);
    }

    private static void clickWidget(ClientGameTestContext ctx, Class<?> type) {
        double[] c = ctx.computeOnClient(mc -> {
            for (AbstractWidget w : Screens.getWidgets(ScreenNav.current(mc))) {
                if (type.isInstance(w)) return new double[]{w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0};
            }
            return null;
        });
        if (c == null) throw new AssertionError("TRASH-GAMETEST FAIL: widget not found: " + type.getSimpleName());
        click(ctx, c[0], c[1]);
    }

    private static PlayerSettings settings(ClientGameTestContext ctx) {
        return PlayerSettingsStore.get(me(ctx));
    }

    private static int effectiveDelay(TestServerContext s, ClientGameTestContext ctx) {
        UUID id = me(ctx);
        return s.computeOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            return TrashManager.effectiveDelaySeconds(p);
        });
    }

    private static int chatCount(String needle) {
        int n = 0;
        for (String m : CHAT) if (m.contains(needle)) n++;
        return n;
    }

    private static void resetClientCfg() {
        ClientConfig c = ClientConfigManager.get();
        c.blacklist.clear();
        c.personalTimer = -1;
        c.showMessage = true;
        c.inventoryButton = true;
    }

    /** Dump all diamonds from the inventory into the (already open) bin, close it, return. */
    private static void dumpDiamondsAndClose(ClientGameTestContext ctx) {
        quickMove(ctx, Items.DIAMOND, false);
        closeScreen(ctx);
    }

    // ---- entry point -----------------------------------------------------------------------------
    @Override
    public void runTest(ClientGameTestContext ctx) throws RuntimeException {
        try {
            ClientReceiveMessageEvents.GAME.register((msg, overlay) -> CHAT.add(msg.getString()));
            ctx.runOnClient(mc -> MixinEnvironment.getCurrentEnvironment().audit());
            check(true, "MixinEnvironment.audit() passed (Trashventory ships no mixins; environment clean)");
            resetClientCfg();
            ctx.getInput().resizeWindow(1280, 720);
            ctx.waitTicks(5);

            TestWorldSave save;
            try (TestSingleplayerContext w = ctx.worldBuilder().create()) {
                save = w.getWorldSave();
                singleplayer(ctx, w);
                persistencePart1(ctx, w);
            }
            ctx.waitTicks(20);
            try (TestSingleplayerContext w2 = save.open()) {
                persistencePart2(ctx, w2);
            }
            ctx.waitTicks(20);
            dedicated(ctx);
            System.out.println("[TRASH-GAMETEST] PASS: ALL Trashventory checks complete");
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =============================== SINGLEPLAYER ==================================================
    private void singleplayer(ClientGameTestContext ctx, TestSingleplayerContext w) throws Exception {
        ctx.waitTicks(40);
        TestServerContext s = w.getServer();
        cmdS(s, ctx, "gamemode survival @p");
        cmdS(s, ctx, "trash admin size double");
        cmdS(s, ctx, "trash admin timer 4");
        cmdS(s, ctx, "trash admin message on");
        cmdS(s, ctx, "trash admin clientoverride on");
        check(TrashConfigManager.get().doubleChest && TrashConfigManager.get().removeDelaySeconds == 4
                && TrashConfigManager.get().countdownEnabled && TrashConfigManager.get().showCloseMessage
                && TrashConfigManager.get().allowClientOverride, "1a admin commands set size=double, delay=4s, message on, override on");
        check(effectiveDelay(s, ctx) == 4, "1b effective delay for the player = 4s");

        // ---- 2. open the bin, gear button, dump -------------------------------------------------
        cmdS(s, ctx, "give @p minecraft:diamond 10");
        check(countInv(ctx, Items.DIAMOND) == 10, "2a player got 10 diamonds");
        openBin(ctx);
        check(binSlots(ctx) == 54 + 36, "2b /trash opens the DOUBLE chest bin (90 slots) for a plain ChestMenu");
        check(hasWidget(ctx, GearButton.class), "2c settings gear appears on the server-announced bin screen");
        ctx.takeScreenshot("trash-01-bin-open-with-gear");
        quickMove(ctx, Items.DIAMOND, false);
        check(countBin(ctx, Items.DIAMOND) == 10 && countInv(ctx, Items.DIAMOND) == 0, "2d shift-click dumps 10 diamonds into the bin");
        ctx.takeScreenshot("trash-02-dumped");
        int msgBefore = chatCount("Items will be deleted in");
        closeScreen(ctx);
        check(chatCount("Items will be deleted in") == msgBefore + 1, "2e closing the bin prints the 'will be deleted in Ns' message");
        check(CHAT.stream().anyMatch(m -> m.contains("Items will be deleted in") && m.contains("4s")), "2f message names the 4s delay");

        // ---- 3. grab-back window ---------------------------------------------------------------------
        ctx.waitTicks(25);   // 1.25 s < 4 s
        openBin(ctx);
        check(countBin(ctx, Items.DIAMOND) == 10, "3a within the delay the items are still in the bin (reopen)");
        quickMove(ctx, Items.DIAMOND, true);
        check(countInv(ctx, Items.DIAMOND) == 10 && countBin(ctx, Items.DIAMOND) == 0, "3b grab-back: all 10 diamonds returned to the inventory");
        ctx.takeScreenshot("trash-03-grabbed-back");
        closeScreen(ctx);

        // ---- 4. expiry after the delay -----------------------------------------------------------------
        openBin(ctx);
        dumpDiamondsAndClose(ctx);
        ctx.waitTicks(110);  // 4 s = 80 ticks + margin
        openBin(ctx);
        check(countBin(ctx, Items.DIAMOND) == 0 && countInv(ctx, Items.DIAMOND) == 0, "4a after 4s the dumped items were deleted for good");
        closeScreen(ctx);

        // ---- 5. personal timer: off (instant), shorter, clamped --------------------------------------------
        cmdP(ctx, "trash timer off");
        check(settings(ctx).personalTimer == 0 && effectiveDelay(s, ctx) == 0, "5a /trash timer off -> personal timer 0, effective delay 0 (instant)");
        cmdS(s, ctx, "give @p minecraft:diamond 10");
        openBin(ctx);
        int msgs = chatCount("Items will be deleted in");
        dumpDiamondsAndClose(ctx);
        openBin(ctx);
        check(countBin(ctx, Items.DIAMOND) == 0 && countInv(ctx, Items.DIAMOND) == 0, "5b instant mode: items gone the moment the bin closes");
        check(chatCount("Items will be deleted in") == msgs, "5c instant mode prints no message");
        closeScreen(ctx);

        cmdP(ctx, "trash timer 2");
        check(settings(ctx).personalTimer == 2 && effectiveDelay(s, ctx) == 2, "5d /trash timer 2 -> effective 2s (shorter than the server's 4s)");
        cmdS(s, ctx, "give @p minecraft:diamond 10");
        openBin(ctx);
        dumpDiamondsAndClose(ctx);
        ctx.waitTicks(60);  // 3 s: past the 2 s personal timer, before the 4 s server timer
        openBin(ctx);
        check(countBin(ctx, Items.DIAMOND) == 0, "5e personal 2s timer deleted the items before the server's 4s");
        closeScreen(ctx);

        cmdP(ctx, "trash timer 100");
        check(settings(ctx).personalTimer == 100 && effectiveDelay(s, ctx) == 4, "5f personal timer cannot exceed the server delay (clamped to 4s)");
        cmdP(ctx, "trash timer default");
        check(settings(ctx).personalTimer == -1 && effectiveDelay(s, ctx) == 4, "5g /trash timer default follows the server again");
        cmdP(ctx, "trash message off");
        check(!settings(ctx).showMessage, "5h /trash message off stored server-side");
        cmdS(s, ctx, "give @p minecraft:diamond 3");
        openBin(ctx);
        int m2 = chatCount("Items will be deleted in");
        dumpDiamondsAndClose(ctx);
        check(chatCount("Items will be deleted in") == m2, "5i personal message-off suppresses the close message");
        cmdP(ctx, "trash message on");
        cmdS(s, ctx, "trash admin clear @p");
        ctx.waitTicks(4);
        openBin(ctx);
        check(countBin(ctx, Items.DIAMOND) == 0, "5j /trash admin clear <player> wipes the bin");
        closeScreen(ctx);
        cmdS(s, ctx, "clear @p");

        // ---- 6. blacklist (auto-delete on pickup) -------------------------------------------------------------
        cmdP(ctx, "trash blacklist add minecraft:dirt");
        check(settings(ctx).blacklist.contains("dirt") || settings(ctx).blacklist.contains("minecraft:dirt"), "6a blacklist add stored: " + settings(ctx).blacklist);
        cmdS(s, ctx, "give @p minecraft:dirt 5");
        ctx.waitTicks(10);
        check(countInv(ctx, Items.DIRT) == 0, "6b blacklisted dirt is deleted the instant it enters the inventory");
        cmdS(s, ctx, "give @p minecraft:stone 5");
        ctx.waitTicks(10);
        check(countInv(ctx, Items.STONE) == 5, "6c non-blacklisted stone is untouched");
        cmdP(ctx, "trash blacklist remove minecraft:dirt");
        cmdS(s, ctx, "give @p minecraft:dirt 5");
        ctx.waitTicks(10);
        check(countInv(ctx, Items.DIRT) == 5, "6d after removal dirt stays");
        cmdP(ctx, "trash blacklist clear");
        cmdS(s, ctx, "clear @p");

        // ---- 7. single-chest size -----------------------------------------------------------------------------
        cmdS(s, ctx, "trash admin size single");
        openBin(ctx);
        check(binSlots(ctx) == 27 + 36, "7a admin size single -> 27-slot bin");
        ctx.takeScreenshot("trash-04-single-bin");
        closeScreen(ctx);
        cmdS(s, ctx, "trash admin size double");

        // ---- 8. settings GUI via the gear -----------------------------------------------------------------------
        openBin(ctx);
        clickWidget(ctx, GearButton.class);
        ctx.waitTicks(5);
        info("after gear click screen=" + ctx.computeOnClient(mc -> String.valueOf(ScreenNav.current(mc))));
        ctx.takeScreenshot("trash-05-after-gear-click");
        check(ctx.computeOnClient(mc -> ScreenNav.current(mc) instanceof TrashventoryScreen), "8a gear button opens the Trashventory settings screen");
        ctx.takeScreenshot("trash-05-settings-gui");
        int pl = ctx.computeOnClient(mc -> ScreenNav.current(mc).width) / 2 - 160;   // panelLeft
        click(ctx, pl + 82, 47);   // timer mode: Default -> Custom (max allowed = 4)
        ctx.waitTicks(6);
        check(settings(ctx).personalTimer == 4, "8b timer-mode button: Default -> Custom(4s) synced to the server: " + settings(ctx).personalTimer);
        click(ctx, pl + 127, 47);  // minus
        ctx.waitTicks(6);
        check(settings(ctx).personalTimer == 3, "8c timer minus: 4 -> 3");
        ctx.takeScreenshot("trash-06-settings-custom-timer");
        click(ctx, pl + 82, 47);   // Custom -> Off
        ctx.waitTicks(6);
        check(settings(ctx).personalTimer == 0, "8d timer-mode button: Custom -> Off (0)");
        click(ctx, pl + 82, 47);   // Off -> Default
        ctx.waitTicks(6);
        check(settings(ctx).personalTimer == -1, "8e timer-mode button: Off -> Default (-1)");
        click(ctx, pl + 105, 69);  // message toggle
        ctx.waitTicks(6);
        check(!settings(ctx).showMessage, "8f message toggle synced to the server (off)");
        click(ctx, pl + 105, 69);
        ctx.waitTicks(6);
        check(settings(ctx).showMessage, "8g message toggle back on");
        boolean inv0 = ClientConfigManager.get().inventoryButton;
        click(ctx, pl + 105, 91);  // inventory-button toggle (client only)
        check(ClientConfigManager.get().inventoryButton != inv0, "8h inventory-button toggle flips the client option");
        click(ctx, pl + 105, 91);
        // blacklist search + add
        click(ctx, pl + 160, 137);  // focus search
        ctx.getInput().typeChars("gravel");
        ctx.waitTicks(4);
        ctx.takeScreenshot("trash-07-settings-search");
        click(ctx, pl + 100, 162);  // first result row -> ADD
        ctx.waitTicks(6);
        check(settings(ctx).blacklist.contains("gravel"), "8i GUI search + click adds 'gravel' to the server blacklist: " + settings(ctx).blacklist);
        click(ctx, pl + 100, 162);  // click again -> REMOVE
        ctx.waitTicks(6);
        check(!settings(ctx).blacklist.contains("gravel"), "8j clicking the row again removes it");
        // Info / Back / Done
        int h = ctx.computeOnClient(mc -> ScreenNav.current(mc).height);
        click(ctx, pl + 30, h - 18);   // Info
        ctx.takeScreenshot("trash-08-settings-info");
        click(ctx, pl + 30, h - 18);   // Back
        click(ctx, pl + 160, h - 18);  // Done
        ctx.waitTicks(5);
        check(ctx.computeOnClient(mc -> ScreenNav.current(mc) == null), "8k Info/Back/Done work (Done closes the screen)");
        ctx.waitTicks(10);
        cmdP(ctx, "trash timer default");
        cmdP(ctx, "trash blacklist clear");

        // ---- 9. inventory trash icon (survival) ----------------------------------------------------------------------
        ctx.getInput().pressKey(o -> o.keyInventory);
        ctx.waitFor(mc -> ScreenNav.current(mc) instanceof InventoryScreen, 60);
        ctx.waitTicks(5);
        check(hasWidget(ctx, TrashIconButton.class), "9a survival inventory shows the one-click trash button");
        ctx.takeScreenshot("trash-09-inventory-trash-icon");
        clickWidget(ctx, TrashIconButton.class);
        ctx.waitFor(mc -> ScreenNav.current(mc) instanceof ContainerScreen && mc.player.containerMenu instanceof ChestMenu, 100);
        check(true, "9b clicking the inventory trash button opens the bin");
        closeScreen(ctx);
        // disabled via config -> no button
        ClientConfigManager.get().inventoryButton = false;
        ctx.getInput().pressKey(o -> o.keyInventory);
        ctx.waitFor(mc -> ScreenNav.current(mc) instanceof InventoryScreen, 60);
        ctx.waitTicks(5);
        check(!hasWidget(ctx, TrashIconButton.class), "9c inventoryButton=false removes the button");
        ctx.getInput().pressKey(o -> o.keyInventory);
        ctx.waitTicks(5);
        ClientConfigManager.get().inventoryButton = true;
        // creative inventory icon
        cmdS(s, ctx, "gamemode creative @p");
        ctx.getInput().pressKey(o -> o.keyInventory);
        ctx.waitTicks(15);
        ctx.takeScreenshot("trash-10-creative-inventory-icon");
        ctx.getInput().pressKey(o -> o.keyInventory);
        ctx.waitTicks(5);
        cmdS(s, ctx, "gamemode survival @p");
    }

    // =============================== PERSISTENCE (leave / rejoin) =====================================
    private void persistencePart1(ClientGameTestContext ctx, TestSingleplayerContext w) {
        TestServerContext s = w.getServer();
        cmdS(s, ctx, "clear @p");
        cmdS(s, ctx, "give @p minecraft:diamond 10");
        openBin(ctx);
        dumpDiamondsAndClose(ctx);
        check(true, "P1 dumped 10 diamonds, bin closed (countdown running); leaving the world now");
    }

    private void persistencePart2(ClientGameTestContext ctx, TestSingleplayerContext w2) {
        ctx.waitTicks(30);
        openBin(ctx);
        check(countBin(ctx, Items.DIAMOND) == 10, "P2 after leave + rejoin the bin contents survived on disk");
        ctx.takeScreenshot("trash-11-bin-after-rejoin");
        closeScreen(ctx);
        ctx.waitTicks(110);
        openBin(ctx);
        check(countBin(ctx, Items.DIAMOND) == 0, "P3 the restored countdown then deleted them");
        closeScreen(ctx);
    }

    // =============================== REAL DEDICATED SERVER ====================================================
    private void dedicated(ClientGameTestContext ctx) throws Exception {
        java.util.Properties props = new java.util.Properties();
        props.setProperty("level-type", "minecraft:flat");
        props.setProperty("spawn-protection", "0");
        try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
            try (var conn = server.connect()) {
                ctx.waitFor(mc -> mc.level != null && mc.player != null && ScreenNav.current(mc) == null, 1200);
                ctx.waitTicks(40);
                cmdS(server, ctx, "gamemode survival @p");
                cmdS(server, ctx, "trash admin timer 4");
                cmdS(server, ctx, "trash admin size double");
                cmdP(ctx, "trash timer default");
                cmdS(server, ctx, "give @p minecraft:diamond 10");
                openBin(ctx);
                check(hasWidget(ctx, GearButton.class), "D1 dedicated server: modded client gets the gear on the bin");
                quickMove(ctx, Items.DIAMOND, false);
                check(countBin(ctx, Items.DIAMOND) == 10, "D2 dedicated server: dump works");
                int before = chatCount("Items will be deleted in");
                closeScreen(ctx);
                check(chatCount("Items will be deleted in") == before + 1, "D3 dedicated server: close message received");
                ctx.waitTicks(25);
                openBin(ctx);
                check(countBin(ctx, Items.DIAMOND) == 10, "D4 dedicated server: grab-back window works");
                dumpDiamondsAndClose(ctx);   // already in the bin: nothing to move, just close -> countdown restarts
                ctx.waitTicks(110);
                openBin(ctx);
                check(countBin(ctx, Items.DIAMOND) == 0, "D5 dedicated server: items deleted after the delay");
                ctx.takeScreenshot("trash-12-dedicated-server-bin");
                closeScreen(ctx);
            }
        }
    }
}
