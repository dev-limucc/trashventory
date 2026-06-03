package dev.limucc.trashventory.trash;

import net.minecraft.world.SimpleContainer;

/** One player's trash bin plus its countdown state. Held in memory only while the player is online. */
public class TrashSession {

    public final int size;                 // 27 or 54
    public final SimpleContainer container;

    public int remainingTicks = 0;         // ticks left before deletion (only meaningful while counting)
    public boolean countingDown = false;   // closed, timer running
    public boolean open = false;           // chest currently open (countdown paused)
    public boolean paused = false;         // player offline (countdown frozen)
    public boolean reopening = false;      // transient: ignore the close that /trash causes when re-opening

    public TrashSession(int size) {
        this.size = size;
        this.container = new SimpleContainer(size);
    }

    public boolean isEmpty() {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) return false;
        }
        return true;
    }
}
