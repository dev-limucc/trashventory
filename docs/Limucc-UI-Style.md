# Limucc UI — flat menu style (Fabric, MC 26.1.x)

A reusable, Sodium-inspired flat GUI style by **dev-limucc**. Dark translucent panels, no vanilla button
textures, accent-blue hover, everything hand-drawn and hit-tested. This is the look used in **Trashventory**
(`TrashventoryScreen` + `FlatButton`); copy the two pieces below into any future mod.

> Reference implementation in this repo:
> - `src/client/java/dev/limucc/trashventory/client/gui/widget/FlatButton.java`
> - `src/client/java/dev/limucc/trashventory/client/gui/TrashventoryScreen.java`

---

## 1. Principles

- **One draw method.** Don't add vanilla `Button` widgets. Draw the whole screen yourself in
  `extractRenderState(...)` and detect clicks in `mouseClicked(...)`. This gives total control over the look
  and keeps it consistent.
- **Flat, dark, translucent.** A single rounded-feel panel (`g.fill` rectangles), 1px top highlight + bottom
  shade for subtle depth, accent-blue fill on hover. No gradients except where intentional.
- **Only one vanilla widget is OK:** `EditBox` for text entry (search boxes). It blends in fine.
- **Item icons are free 3D.** `g.item(stack, x, y)` renders block items in 3D and flat items in 2D — use it
  for any item/block list.
- **Color via ARGB ints.** `0xAARRGGBB`. Translucency is the alpha byte.

---

## 2. Palette (exact values)

```text
Panel background     0xC0121214   (≈75% opaque near-black)
Panel top edge       0x22FFFFFF   (faint white highlight line)

Button idle          0xFF26262B
Button hover         0xFF3A6EA5   (accent blue — the signature color)
Button disabled      0xFF1C1C20
Button top highlight 0x22FFFFFF
Button bottom shade  0x44000000

Text (normal)        0xFFFFFFFF
Text (disabled)      0xFF6A6A70
Header label         0xFFB0B0B0   (use "§7" prefix too)
Sub / hint text      0xFFA0A0A0
Dim note text        0xFF808080   ("§8" for inline)
Accent value text    0xFFFFFF80   (pale yellow, for the "current value")

List background      0x90000000
List row hover       0x22FFFFFF
List row selected    0x3055FF55   (faint green = "active/listed")
Scrollbar            0xFFAAAAAA
Add action (green)   0xFF60FF60
Remove action (red)  0xFFFF6060
```

Tip: in any drawn string you can also use Minecraft `§` codes (`§a` green, `§7` gray, `§8` dark gray,
`§e` yellow, `§l` bold, `§r` reset) — `g.text(...)` honours them.

---

## 3. The `FlatButton` kit (copy verbatim)

Not a real widget — a tiny struct you draw + hit-test. Re-namespace the package for your mod.

```java
package <yourmod>.client.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Sodium-style flat button: drawn by the owning screen, hit-tested in mouseClicked. */
public class FlatButton {
    public int x, y, w, h;
    public String label;

    private static final int BG       = 0xFF26262B;
    private static final int BG_HOVER = 0xFF3A6EA5;  // accent
    private static final int BG_OFF   = 0xFF1C1C20;
    private static final int TEXT     = 0xFFFFFFFF;
    private static final int TEXT_OFF = 0xFF6A6A70;

    public FlatButton(int x, int y, int w, int h, String label) {
        this.x = x; this.y = y; this.w = w; this.h = h; this.label = label;
    }
    public void setBounds(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
    public boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    public void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, boolean enabled) {
        boolean hovered = enabled && contains(mouseX, mouseY);
        int bg = !enabled ? BG_OFF : (hovered ? BG_HOVER : BG);
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, 0x22FFFFFF);          // top highlight
        g.fill(x, y + h - 1, x + w, y + h, 0x44000000);  // bottom shade
        int tw = font.width(label);
        g.text(font, label, x + (w - tw) / 2, y + (h - 8) / 2, enabled ? TEXT : TEXT_OFF);
    }
}
```

**Toggle pattern:** keep the value in your config and set the label each frame, e.g.
`btn.label = cfg.on ? "§aON" : "§7OFF";` then `btn.render(...)`. Flip it in `mouseClicked`.

---

## 4. Screen skeleton (copy & fill in)

```java
public class MyScreen extends net.minecraft.client.gui.screens.Screen {
    private final net.minecraft.client.gui.screens.Screen parent;
    private int panelLeft, panelRight;
    private final FlatButton doneBtn = new FlatButton(0,0,0,0,"Done");

    public MyScreen(net.minecraft.client.gui.screens.Screen parent) {
        super(net.minecraft.network.chat.Component.literal("My Mod"));
        this.parent = parent;
    }

    @Override protected void init() {
        int cx = this.width / 2;
        panelLeft = cx - 160; panelRight = cx + 160;
        doneBtn.setBounds(cx - 50, this.height - 28, 100, 20);
        // EditBox / other layout here, added with addRenderableWidget(...)
    }

    @Override public void extractRenderState(
            net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        g.fill(panelLeft - 10, 4, panelRight + 10, this.height - 4, 0xC0121214);   // panel
        g.fill(panelLeft - 10, 4, panelRight + 10, 5, 0x22FFFFFF);                  // top edge
        int tw = this.font.width(this.title);
        g.text(this.font, this.title, this.width / 2 - tw / 2, 10, 0xFFFFFFFF);     // title
        g.text(this.font, "§7Section header", panelLeft, 26, 0xFFB0B0B0);
        doneBtn.render(g, this.font, mouseX, mouseY, true);
        // ... draw the rest ...
    }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        if (super.mouseClicked(e, dbl)) return true;        // lets EditBox etc. work first
        if (e.button() != 0) return false;
        if (doneBtn.contains(e.x(), e.y())) { this.onClose(); return true; }
        // ... other buttons / rows ...
        return false;
    }

    @Override public void onClose() { this.minecraft.setScreen(this.parent); }
}
```

Wire it to ModMenu by returning `MyScreen::new` from a `ModMenuApi.getModConfigScreenFactory()`, and/or open
it from a keybind with `mc.setScreen(new MyScreen(null))`.

---

## 5. Scrollable list with item icons (the blacklist pattern)

```java
private static final int ROW_H = 20;
private int scroll = 0, listTop, listBottom;   // set listTop/listBottom in init()

// in extractRenderState():
g.fill(panelLeft - 2, listTop - 2, panelRight + 2, listBottom + 2, 0x90000000);
g.enableScissor(panelLeft - 2, listTop, panelRight + 2, listBottom);
int first = Math.max(0, scroll / ROW_H);
int visible = (listBottom - listTop) / ROW_H + 2;
for (int i = first; i < Math.min(rows.size(), first + visible); i++) {
    int y = listTop + i * ROW_H - scroll;
    boolean hovered = mouseX >= panelLeft && mouseX <= panelRight && mouseY >= y && mouseY < y + ROW_H
            && mouseY >= listTop && mouseY < listBottom;
    if (selected(i))   g.fill(panelLeft, y, panelRight, y + ROW_H, 0x3055FF55);
    else if (hovered)  g.fill(panelLeft, y, panelRight, y + ROW_H, 0x22FFFFFF);
    g.item(stackFor(i), panelLeft + 2, y + 2);                       // 3D for blocks, free
    g.text(this.font, labelFor(i), panelLeft + 24, y + 6, 0xFFFFFFFF);
}
g.disableScissor();

// scrollbar:
int total = rows.size() * ROW_H, view = listBottom - listTop;
if (total > view) {
    int barH = Math.max(15, view * view / total);
    int barY = listTop + scroll * (view - barH) / (total - view);
    g.fill(panelRight + 2, barY, panelRight + 5, barY + barH, 0xFFAAAAAA);
}

// mouseScrolled(double mx,double my,double sx,double sy):
int max = Math.max(0, total - view);
scroll = Math.max(0, Math.min(max, scroll - (int)(sy * ROW_H)));

// click a row in mouseClicked(): int idx = (int)((my - listTop + scroll) / ROW_H);
```

Show ids without the namespace for vanilla items: `id.getNamespace().equals("minecraft") ? id.getPath() :
id.getNamespace() + ":" + id.getPath()`.

---

## 6. MC 26.1.x draw API cheatsheet (`GuiGraphicsExtractor g`)

| Call | Use |
| --- | --- |
| `g.fill(x0,y0,x1,y1, argb)` | filled rectangle (panels, buttons, highlights) |
| `g.fillGradient(x0,y0,x1,y1, argb1, argb2)` | vertical gradient |
| `g.text(font, str/Component, x, y, argb)` | text (honours `§` codes); add `, false` to drop shadow |
| `g.item(stack, x, y)` | item icon — **3D for block items**, 2D otherwise |
| `g.enableScissor(x0,y0,x1,y1)` / `g.disableScissor()` | clip a scroll region |
| `g.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h)` | draw a sprite from `textures/gui/sprites/` |

Input overrides on `Screen`:
- `extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a)` — draw here.
- `mouseClicked(MouseButtonEvent e, boolean doubleClick)` — `e.x()`, `e.y()`, `e.button()` (0 = left).
- `mouseScrolled(double mx, double my, double scrollX, double scrollY)`.
- Custom widgets override `extractContents(GuiGraphicsExtractor, int, int, float)` (e.g. an `ImageButton`).

---

## 7. Gotchas / version notes (MC 26.1.x mappings)

- Mappings are Mojang-style but `ResourceLocation` is named **`Identifier`**, text is **`Component`**, and the
  render context is **`GuiGraphicsExtractor`** (not `GuiGraphics`) via `extractRenderState`.
- Always call `super.mouseClicked(...)` first so any `EditBox` keeps focus/clicks.
- For an icon button, put the PNG under `assets/<mod>/textures/gui/sprites/<name>.png` and use
  `new WidgetSprites(Identifier.fromNamespaceAndPath("<mod>","<name>"))` with `ImageButton` — it renders the
  sprite for you (no manual blit). Black-on-transparent icons read well on light vanilla backgrounds and stay
  readable under black-and-white resource packs.
- Hit-tested `FlatButton`s have no keyboard focus/narration. That's fine for config GUIs; if you need
  controller/keyboard nav, use real widgets instead.

---

*Style by dev-limucc — github.com/dev-limucc. Reuse freely across your mods.*
