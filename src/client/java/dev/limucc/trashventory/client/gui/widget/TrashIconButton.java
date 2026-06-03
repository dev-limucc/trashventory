package dev.limucc.trashventory.client.gui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;

/**
 * A trash icon button that stays glued to the right edge of the inventory's recipe-book toggle.
 * The recipe book moves the toggle (and shifts the inventory) when it opens WITHOUT re-running init(),
 * so we re-anchor every frame in {@link #extractContents} rather than positioning once.
 */
public class TrashIconButton extends ImageButton {

    private static final int GAP = 2;
    private final AbstractWidget anchor;

    public TrashIconButton(AbstractWidget anchor, WidgetSprites sprites, Button.OnPress onPress) {
        super(anchor.getX() + anchor.getWidth() + GAP, anchor.getY() + 1, 16, 16, sprites, onPress);
        this.anchor = anchor;
    }

    private void reanchor() {
        this.setX(anchor.getX() + anchor.getWidth() + GAP);
        this.setY(anchor.getY() + 1);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        reanchor();
        super.extractContents(graphics, mouseX, mouseY, a);
    }
}
