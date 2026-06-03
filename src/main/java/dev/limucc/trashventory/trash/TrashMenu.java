package dev.limucc.trashventory.trash;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * A plain chest menu (so vanilla clients can open it) that notifies us once when the player closes it.
 * {@link #removed(Player)} fires on Esc/E and on disconnect, which is exactly our "start countdown" trigger.
 */
public class TrashMenu extends ChestMenu {

    private final Runnable onClose;
    private boolean fired = false;

    public TrashMenu(int containerId, Inventory playerInventory, Container container, int rows,
                     MenuType<?> type, Runnable onClose) {
        super(type, containerId, playerInventory, container, rows);
        this.onClose = onClose;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!fired) {
            fired = true;
            onClose.run();
        }
    }
}
