package dev.limucc.trashventory.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.limucc.trashventory.Trashventory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative, persistent per-player settings. This is the single source of truth: both the
 * client GUI (via {@code TrashPrefsPayload}) and the player commands write here, so they always match.
 * Blacklist ids are resolved to {@link Item} once per change, so the per-tick enforcement scan never
 * parses strings; only players with a non-empty blacklist appear in {@link #resolvedBlacklists()}.
 */
public final class PlayerSettingsStore {

    private PlayerSettingsStore() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<HashMap<String, PlayerSettings>>() {}.getType();

    private static Path file;
    private static Map<UUID, PlayerSettings> map = new HashMap<>();
    private static final Map<UUID, Set<Item>> resolved = new HashMap<>();   // only non-empty entries

    public static void init(Path dataDir) {
        file = dataDir.resolve("player_settings.json");
        map = new HashMap<>();
        resolved.clear();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                Map<String, PlayerSettings> raw = GSON.fromJson(r, MAP_TYPE);
                if (raw != null) {
                    for (Map.Entry<String, PlayerSettings> e : raw.entrySet()) {
                        try {
                            UUID id = UUID.fromString(e.getKey());
                            map.put(id, e.getValue() != null ? e.getValue() : new PlayerSettings());
                            recompute(id);
                        } catch (IllegalArgumentException ignored) {
                            // skip malformed uuid key
                        }
                    }
                }
            } catch (IOException e) {
                Trashventory.LOGGER.error("Failed to load player settings.", e);
            }
        }
    }

    private static void save() {
        if (file == null) return;
        Map<String, PlayerSettings> raw = new HashMap<>();
        for (Map.Entry<UUID, PlayerSettings> e : map.entrySet()) raw.put(e.getKey().toString(), e.getValue());
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(raw, w);
        } catch (IOException e) {
            Trashventory.LOGGER.error("Failed to save player settings.", e);
        }
    }

    public static PlayerSettings get(UUID id) {
        return map.computeIfAbsent(id, k -> new PlayerSettings());
    }

    public static Map<UUID, Set<Item>> resolvedBlacklists() {
        return resolved;
    }

    private static void recompute(UUID id) {
        PlayerSettings s = map.get(id);
        if (s == null) {
            resolved.remove(id);
            return;
        }
        Set<Item> set = new HashSet<>();
        for (String raw : s.blacklist) resolveItem(raw).ifPresent(set::add);
        if (set.isEmpty()) resolved.remove(id);
        else resolved.put(id, set);
    }

    public static Optional<Item> resolveItem(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String s = raw.trim();
        Identifier id = Identifier.tryParse(s.indexOf(':') >= 0 ? s : "minecraft:" + s);
        if (id == null) return Optional.empty();
        return BuiltInRegistries.ITEM.getOptional(id);
    }

    /** Canonical stored form: drop the namespace for vanilla items, keep it for modded. */
    public static String canonical(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return "";
        return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
    }

    // ── mutators (commands) ─────────────────────────────────────────────────────
    public static void setTimer(UUID id, int personalTimer) {
        get(id).personalTimer = personalTimer;
        save();
    }

    public static void setMessage(UUID id, boolean show) {
        get(id).showMessage = show;
        save();
    }

    public static boolean blacklistAdd(UUID id, String itemKey) {
        PlayerSettings s = get(id);
        if (s.blacklist.contains(itemKey)) return false;
        s.blacklist.add(itemKey);
        recompute(id);
        save();
        return true;
    }

    public static boolean blacklistRemove(UUID id, String itemKey) {
        PlayerSettings s = get(id);
        boolean removed = s.blacklist.remove(itemKey);
        if (removed) {
            recompute(id);
            save();
        }
        return removed;
    }

    public static void blacklistClear(UUID id) {
        get(id).blacklist.clear();
        recompute(id);
        save();
    }

    // ── full replace (client GUI) ────────────────────────────────────────────────
    public static void applyFromClient(UUID id, List<String> blacklist, int personalTimer, boolean showMessage) {
        PlayerSettings s = get(id);
        s.blacklist = new ArrayList<>(blacklist);
        s.personalTimer = personalTimer;
        s.showMessage = showMessage;
        recompute(id);
        save();
    }
}
