# Trashventory

A Fabric mod for **Minecraft 26.1.2** that adds a personal `/trash` bin. Drop items you don't want,
close the bin, and after a short delay they're deleted forever — with a window to grab them back if you
change your mind.

The whole `/trash` feature is **server-side**, so it works for players on **vanilla (unmodded) clients**.
Installing the mod **on the client** adds quality-of-life extras.

## Features

### Server (works for everyone, no client mod needed)
- `/trash` (alias `/tr`) opens your private trash bin — a normal chest GUI.
- Drop items, close it, and after the configured delay they're gone forever.
- Re-open with `/trash` before the timer ends to take items back — the countdown pauses while it's open
  and restarts when you close it again.
- A relog or server restart never deletes your items: the countdown freezes while you're offline and
  resumes when you return.
- Admins configure everything (see below). Turning the timer **off** = instant deletion on close.

### Client (optional — adds convenience)
- A one-click trash icon on your inventory screen, plus configurable hotkeys
  (Options → Controls → Trashventory).
- A smooth, flat settings GUI (also reachable from ModMenu):
  - Set your **own** countdown — only **shorter** than the server's (or off → instant for you).
  - Toggle the "deleted in Ns" message.
  - **Blacklist** items by searching and clicking them; blacklisted items are deleted the instant they
    enter your inventory (the server must run Trashventory). Item ids are shown without the `minecraft:`
    prefix, and blocks render in 3D.

## Player commands (everyone — no GUI / mod needed)
Per-player settings are stored **server-side**, so these work even on a vanilla client and stay perfectly in
sync with the client GUI (changing one updates the other live).

| Command | Effect |
| --- | --- |
| `/trash` (alias `/tr`) | Open your trash bin. |
| `/trash timer default` | Follow the server's delay (default). |
| `/trash timer off` | Instant deletion for you (no message). |
| `/trash timer <seconds>` | Your own delay — only **shorter** than the server's (auto-clamped). |
| `/trash message <on\|off>` | Show/hide your "deleted in Ns" message. |
| `/trash blacklist add <item>` | Auto-delete this item the moment it enters your inventory. |
| `/trash blacklist remove <item>` | Remove an item from your blacklist. |
| `/trash blacklist list` | List your blacklist. |
| `/trash blacklist clear` | Clear your blacklist. |
| `/trash settings` | Show your current timer / message / blacklist. |

## Admin commands (OP only — requires op / permission level ≥ 2)
| Command | Effect |
| --- | --- |
| `/trash admin size <single\|double>` | Bin size — single (27) or double (54, default). |
| `/trash admin timer <seconds>` | Server deletion delay (default 10). `0` = instant. |
| `/trash admin timer off` | Turn the countdown off globally (instant deletion). |
| `/trash admin message <on\|off>` | Show/hide the close message for everyone. |
| `/trash admin clientoverride <on\|off>` | Allow/deny players' personal (shorter) timers. |
| `/trash admin clear <player>` | Empty a player's bin immediately. |
| `/trash admin reload` | Reload `config/trashventory.json` from disk. |

The `admin` subtree is hidden from and unusable by non-ops. Server config lives at
`config/trashventory.json`; per-player settings at `<world>/trashventory/player_settings.json`.

## Server safety (anti-dupe / exploit notes)
Trashventory is designed to be safe on public servers, including against modified clients and scripts:

- **Server-authoritative.** The bin is a standard server-side `ChestMenu` + `SimpleContainer`. All item
  movement goes through vanilla's validated container handling, so a modified client cannot inject, forge,
  or duplicate items — desynced clicks are rejected and resynced like any vanilla container.
- **Deletion can't be raced.** The countdown only runs while the bin is **closed**; `clearContent()` never
  fires while a menu is viewing the container, so there's no open-while-deleting dupe window.
- **Per-player isolation.** A bin is keyed to the owner's UUID; `/trash` only ever opens the caller's own
  bin, and the blacklist a client sends only ever affects **that same client's** inventory (the UUID comes
  from the server-side connection, never from the packet).
- **Bounded packets.** The client→server prefs payload is hard-capped (≤1024 ids, ≤200 chars each) so a
  malicious client can't send an oversized packet to exhaust memory. Unknown item ids are ignored.
- **Gone means gone.** Deleted items are removed server-side; the client only ever had a synced view, so
  there is nothing to "restore" client-side.
- **Crash note.** On a graceful stop, bins and player data save together (consistent). A hard crash
  (kill -9 / power loss) carries the same small dupe window as vanilla's own separate-save systems; run
  normal world backups if that matters to you.

## Building
Requires JDK 25. `./gradlew build` → `build/libs/trashventory-<version>.jar`.

## Credits
Made by **dev-limucc** (`dev.limucc`). Licensed under MIT.
