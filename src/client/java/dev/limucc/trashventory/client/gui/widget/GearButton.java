package dev.limucc.trashventory.client.gui.widget;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;

/** Marker subclass so the settings gear on the trash chest is easy to detect (idempotent add). */
public class GearButton extends ImageButton {
    public GearButton(int x, int y, int w, int h, WidgetSprites sprites, Button.OnPress onPress) {
        super(x, y, w, h, sprites, onPress);
    }
}
