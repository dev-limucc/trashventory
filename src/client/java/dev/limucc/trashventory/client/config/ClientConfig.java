package dev.limucc.trashventory.client.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache of the player's settings. The server is authoritative: these are overwritten by the
 * server's settings on join (so they always match commands), and pushed back to the server on GUI edits.
 */
public class ClientConfig {

    /** Item ids to auto-delete on pickup (without "minecraft:" for vanilla items). */
    public List<String> blacklist = new ArrayList<>();

    /** -1 = follow server default, 0 = off (instant), &gt;0 = custom seconds (clamped ≤ server). */
    public int personalTimer = -1;

    /** Whether I want the "deleted in Ns" message. */
    public boolean showMessage = true;

    /** Purely client-side: show the one-click trash button on the inventory screen. */
    public boolean inventoryButton = true;
}
