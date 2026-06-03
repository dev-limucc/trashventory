package dev.limucc.trashventory.config;

/**
 * Server-side, admin-controlled settings (config/trashventory.json).
 * A client-mod player can only make their own timer SHORTER than {@link #removeDelaySeconds}
 * (or turn it off → instant), never longer.
 */
public class TrashConfig {

    /** true = large/double chest (54 slots), false = small/single chest (27). Default large. */
    public boolean doubleChest = true;

    /** Seconds an item waits in the bin after closing before it is deleted forever. */
    public int removeDelaySeconds = 10;

    /**
     * Master countdown switch. When false the bin deletes instantly on close for everyone
     * (no retrieval window, no message) — i.e. the admin turned the timer off globally.
     */
    public boolean countdownEnabled = true;

    /** Global toggle for the "items will be deleted in Ns" chat message. */
    public boolean showCloseMessage = true;

    /** Whether a modded client's personal (shorter / off) timer is honoured. */
    public boolean allowClientOverride = true;

    public int chestSize() {
        return doubleChest ? 54 : 27;
    }
}
