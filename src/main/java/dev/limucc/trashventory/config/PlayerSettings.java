package dev.limucc.trashventory.config;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's personal preferences, stored server-side so they work from commands (even on vanilla
 * clients) and stay identical whether changed via command or the client GUI.
 */
public class PlayerSettings {

    /** Personal countdown: -1 = follow the server default, 0 = off (instant), &gt;0 = custom seconds. */
    public int personalTimer = -1;

    /** Whether this player wants the "deleted in Ns" message. */
    public boolean showMessage = true;

    /** Item ids to auto-delete on pickup (without the "minecraft:" namespace for vanilla items). */
    public List<String> blacklist = new ArrayList<>();
}
