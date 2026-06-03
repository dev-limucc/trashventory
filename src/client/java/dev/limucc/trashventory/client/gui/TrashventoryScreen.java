package dev.limucc.trashventory.client.gui;

import dev.limucc.trashventory.client.config.ClientConfig;
import dev.limucc.trashventory.client.config.ClientConfigManager;
import dev.limucc.trashventory.client.gui.widget.FlatButton;
import dev.limucc.trashventory.client.net.ClientNet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Smooth, flat (Sodium-inspired) settings + blacklist editor. Built on GuiGraphicsExtractor draws and
 * manual hit-testing (no vanilla button textures). Edits are pushed to the server, which is authoritative,
 * so the GUI always matches the /trash commands. Block items render in 3D via {@code g.item(...)}.
 */
public class TrashventoryScreen extends Screen {

    private record Entry(Item item, ItemStack stack, String idStr, String path, String namespace) {}

    private static List<Entry> ALL;

    private static final String[] INFO = {
            "§lTrashventory",
            "A /trash bin that deletes dumped items after a delay.",
            "",
            "§eTimer  §8(Default / Custom / Off)",
            "• §fDefault§r — follow the server's delay.",
            "• §fCustom§r — your own delay (only §fshorter§r than the server's).",
            "• §fOff§r — your items are deleted instantly (no message).",
            "",
            "§eBlacklist",
            "• Type to search, click a row to §aADD§r; empty search lists",
            "   your blacklist — click to §cREMOVE§r.",
            "• Blacklisted items are deleted the instant they enter your",
            "   inventory (server must run Trashventory).",
            "",
            "§eNo GUI? Use commands",
            "• /trash timer default|off|<seconds>",
            "• /trash message on|off",
            "• /trash blacklist add|remove|list|clear",
            "",
            "§eCredits",
            "• Made by §fdev-limucc§r  —  §7github.com/dev-limucc",
    };

    private final Screen parent;
    private EditBox search;
    private boolean showInfo = false;

    private final List<Entry> filtered = new ArrayList<>();
    private int scroll = 0;

    private int panelLeft, panelRight, listTop, listBottom;
    private static final int ROW_H = 20;

    private final FlatButton timerMode = new FlatButton(0, 0, 0, 0, "");
    private final FlatButton timerMinus = new FlatButton(0, 0, 0, 0, "-");
    private final FlatButton timerPlus = new FlatButton(0, 0, 0, 0, "+");
    private final FlatButton msgToggle = new FlatButton(0, 0, 0, 0, "");
    private final FlatButton invToggle = new FlatButton(0, 0, 0, 0, "");
    private final FlatButton doneBtn = new FlatButton(0, 0, 0, 0, "Done");
    private final FlatButton infoBtn = new FlatButton(0, 0, 0, 0, "Info");

    private int settingsHeaderY, timerRowY, msgRowY, invRowY, blacklistHeaderY, hintY, valueCenterX;

    public TrashventoryScreen(Screen parent) {
        super(Component.literal("Trashventory"));
        this.parent = parent;
    }

    private static List<Entry> all() {
        if (ALL == null) {
            List<Entry> list = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                if (id == null) continue;
                list.add(new Entry(item, new ItemStack(item), id.toString(), id.getPath(), id.getNamespace()));
            }
            list.sort((a, b) -> a.path.compareTo(b.path));
            ALL = list;
        }
        return ALL;
    }

    // ── server-limit helpers ────────────────────────────────────────────────────
    private static boolean serverInstant()  { return ClientNet.serverHasMod && !ClientNet.countdownEnabled; }
    private static boolean overrideBlocked() { return ClientNet.serverHasMod && !ClientNet.allowClientOverride; }
    private static boolean timerEditable()   { return !serverInstant() && !overrideBlocked(); }
    private static int maxTimer()            { return ClientNet.serverHasMod ? Math.max(1, ClientNet.serverTimerSeconds) : 3600; }

    @Override
    protected void init() {
        int cx = this.width / 2;
        this.panelLeft = cx - 160;
        this.panelRight = cx + 160;

        this.settingsHeaderY = 26;
        this.timerRowY = 38;
        this.msgRowY = 60;
        this.invRowY = 82;
        this.blacklistHeaderY = 106;
        this.hintY = 116;
        int searchY = 128;
        this.listTop = 152;
        this.listBottom = Math.max(this.listTop + ROW_H, this.height - 36);

        timerMode.setBounds(panelLeft + 50, timerRowY, 64, 18);
        timerMinus.setBounds(panelLeft + 118, timerRowY, 18, 18);
        timerPlus.setBounds(panelLeft + 168, timerRowY, 18, 18);
        this.valueCenterX = panelLeft + 152;

        msgToggle.setBounds(panelLeft + 60, msgRowY, 90, 18);
        invToggle.setBounds(panelLeft + 60, invRowY, 90, 18);

        infoBtn.setBounds(panelLeft, this.height - 28, 60, 20);
        doneBtn.setBounds(cx - 50, this.height - 28, 100, 20);

        this.search = new EditBox(this.font, panelLeft, searchY, 320, 18, Component.literal("Search"));
        this.search.setHint(Component.literal("Search items to blacklist…"));
        this.search.setMaxLength(100);
        this.search.setResponder(s -> refresh());
        this.search.setVisible(!showInfo);
        this.addRenderableWidget(this.search);
        if (!showInfo) this.setInitialFocus(this.search);

        refresh();
    }

    // ── data ──────────────────────────────────────────────────────────────────────
    private static String keyOf(Entry e) {
        return e.namespace.equals("minecraft") ? e.path : e.idStr;
    }

    private boolean isBlacklisted(Entry e) {
        return ClientConfigManager.get().blacklist.contains(keyOf(e));
    }

    private void toggle(Entry e) {
        List<String> bl = ClientConfigManager.get().blacklist;
        String key = keyOf(e);
        if (!bl.remove(key)) bl.add(key);
        saveAndSync();
        if (search.getValue().trim().isEmpty()) refresh();
    }

    private void refresh() {
        filtered.clear();
        String q = (search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT));
        if (q.isEmpty()) {
            for (Entry e : all()) if (isBlacklisted(e)) filtered.add(e);
        } else {
            int cap = 400;
            for (Entry e : all()) {
                if (e.path.contains(q) || e.idStr.contains(q)) {
                    filtered.add(e);
                    if (filtered.size() >= cap) break;
                }
            }
        }
        scroll = 0;
    }

    private void saveAndSync() {
        ClientConfigManager.save();
        ClientNet.sendPrefs();
    }

    // ── timer (Default / Custom / Off) ──────────────────────────────────────────────
    private void cycleTimerMode() {
        ClientConfig c = ClientConfigManager.get();
        if (c.personalTimer < 0) c.personalTimer = maxTimer();   // Default → Custom (start at the max allowed)
        else if (c.personalTimer > 0) c.personalTimer = 0;       // Custom → Off
        else c.personalTimer = -1;                               // Off → Default
        saveAndSync();
    }

    private void stepTimer(int delta) {
        ClientConfig c = ClientConfigManager.get();
        if (c.personalTimer <= 0) return;                        // only adjustable in Custom
        int max = maxTimer();
        c.personalTimer = Math.max(1, Math.min(max, Math.min(c.personalTimer, max) + delta));
        saveAndSync();
    }

    // ── rendering ─────────────────────────────────────────────────────────────────
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);

        g.fill(panelLeft - 10, 4, panelRight + 10, this.height - 4, 0xC0121214);
        g.fill(panelLeft - 10, 4, panelRight + 10, 5, 0x22FFFFFF);

        int tw = this.font.width(this.title);
        g.text(this.font, this.title, this.width / 2 - tw / 2, 10, 0xFFFFFFFF);

        doneBtn.render(g, this.font, mouseX, mouseY, true);
        infoBtn.render(g, this.font, mouseX, mouseY, true);

        if (showInfo) {
            int y = 28;
            for (String line : INFO) {
                g.text(this.font, line, panelLeft, y, 0xFFFFFFFF);
                y += 11;
            }
            return;
        }

        ClientConfig c = ClientConfigManager.get();
        g.text(this.font, "§7Settings", panelLeft, settingsHeaderY, 0xFFB0B0B0);

        // Timer row
        g.text(this.font, "Timer", panelLeft, timerRowY + 5, 0xFFE0E0E0);
        boolean editable = timerEditable();
        boolean custom = c.personalTimer > 0;
        timerMode.label = c.personalTimer < 0 ? "Default" : (c.personalTimer == 0 ? "§7Off" : "§aCustom");
        timerMode.render(g, this.font, mouseX, mouseY, editable);
        timerMinus.render(g, this.font, mouseX, mouseY, editable && custom);
        timerPlus.render(g, this.font, mouseX, mouseY, editable && custom);

        String def = ClientNet.serverHasMod ? ClientNet.serverTimerSeconds + "s" : "server";
        String valStr = c.personalTimer < 0 ? "=" + def
                : c.personalTimer == 0 ? "instant"
                : Math.min(c.personalTimer, maxTimer()) + "s";
        int vtw = this.font.width(valStr);
        g.text(this.font, valStr, valueCenterX - vtw / 2, timerRowY + 5, 0xFFFFFF80);

        String note;
        if (serverInstant())        note = "§8server: instant";
        else if (overrideBlocked()) note = "§8server: " + ClientNet.serverTimerSeconds + "s fixed";
        else if (ClientNet.serverHasMod) note = "§8max " + maxTimer() + "s";
        else                        note = "§8local";
        g.text(this.font, note, panelRight - this.font.width(note), timerRowY + 5, 0xFF808080);

        // Message row
        g.text(this.font, "Message", panelLeft, msgRowY + 5, 0xFFE0E0E0);
        msgToggle.label = c.showMessage ? "§aShown" : "§7Hidden";
        msgToggle.render(g, this.font, mouseX, mouseY, true);

        // Inventory-button row
        g.text(this.font, "Inv icon", panelLeft, invRowY + 5, 0xFFE0E0E0);
        invToggle.label = c.inventoryButton ? "§aShown" : "§7Hidden";
        invToggle.render(g, this.font, mouseX, mouseY, true);

        // Blacklist
        g.text(this.font, "§7Blacklist  §8(auto-delete on pickup)", panelLeft, blacklistHeaderY, 0xFFB0B0B0);
        String hint = search.getValue().trim().isEmpty()
                ? "Your blacklist — type below to search & add more"
                : filtered.size() + " match(es) — click a row to add/remove";
        g.text(this.font, hint, panelLeft, hintY, 0xFFA0A0A0);

        g.fill(panelLeft - 2, listTop - 2, panelRight + 2, listBottom + 2, 0x90000000);
        g.enableScissor(panelLeft - 2, listTop, panelRight + 2, listBottom);
        int first = Math.max(0, scroll / ROW_H);
        int visible = (listBottom - listTop) / ROW_H + 2;
        for (int i = first; i < Math.min(filtered.size(), first + visible); i++) {
            Entry e = filtered.get(i);
            int y = listTop + i * ROW_H - scroll;
            boolean hovered = mouseX >= panelLeft && mouseX <= panelRight
                    && mouseY >= y && mouseY < y + ROW_H && mouseY >= listTop && mouseY < listBottom;
            boolean listed = isBlacklisted(e);

            if (listed)        g.fill(panelLeft, y, panelRight, y + ROW_H, 0x3055FF55);
            else if (hovered)  g.fill(panelLeft, y, panelRight, y + ROW_H, 0x22FFFFFF);

            g.item(e.stack, panelLeft + 2, y + 2);           // 3D for block items, flat for the rest
            String name = e.namespace.equals("minecraft") ? e.path : e.namespace + ":" + e.path;
            g.text(this.font, name, panelLeft + 24, y + 6, 0xFFFFFFFF);

            String action = listed ? "[- remove]" : "[+ add]";
            int color = listed ? 0xFFFF6060 : 0xFF60FF60;
            g.text(this.font, action, panelRight - this.font.width(action) - 4, y + 6, color);
        }
        g.disableScissor();

        int total = filtered.size() * ROW_H;
        int view = listBottom - listTop;
        if (total > view) {
            int barH = Math.max(15, view * view / total);
            int barY = listTop + scroll * (view - barH) / (total - view);
            g.fill(panelRight + 2, barY, panelRight + 5, barY + barH, 0xFFAAAAAA);
        }
    }

    // ── input ─────────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x(), my = event.y();
        if (event.button() != 0) return false;

        if (doneBtn.contains(mx, my)) { this.onClose(); return true; }
        if (infoBtn.contains(mx, my)) {
            showInfo = !showInfo;
            this.search.setVisible(!showInfo);
            return true;
        }
        if (showInfo) return false;

        boolean editable = timerEditable();
        ClientConfig c = ClientConfigManager.get();
        boolean custom = c.personalTimer > 0;
        if (editable && timerMode.contains(mx, my)) { cycleTimerMode(); return true; }
        if (editable && custom && timerMinus.contains(mx, my)) { stepTimer(-1); return true; }
        if (editable && custom && timerPlus.contains(mx, my)) { stepTimer(1); return true; }
        if (msgToggle.contains(mx, my)) { c.showMessage = !c.showMessage; saveAndSync(); return true; }
        if (invToggle.contains(mx, my)) { c.inventoryButton = !c.inventoryButton; saveAndSync(); return true; }

        if (mx >= panelLeft && mx <= panelRight && my >= listTop && my < listBottom) {
            int idx = (int) ((my - listTop + scroll) / ROW_H);
            if (idx >= 0 && idx < filtered.size()) { toggle(filtered.get(idx)); return true; }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!showInfo && mx >= panelLeft - 2 && mx <= panelRight + 5 && my >= listTop && my < listBottom) {
            int total = filtered.size() * ROW_H;
            int view = listBottom - listTop;
            int max = Math.max(0, total - view);
            scroll = Math.max(0, Math.min(max, scroll - (int) (scrollY * ROW_H)));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        saveAndSync();
        this.minecraft.setScreen(this.parent);
    }
}
