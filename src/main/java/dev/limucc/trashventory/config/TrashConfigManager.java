package dev.limucc.trashventory.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.limucc.trashventory.Trashventory;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads/saves {@link TrashConfig} as pretty JSON in the platform config directory. */
public final class TrashConfigManager {

    private TrashConfigManager() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("trashventory.json");

    private static TrashConfig instance = new TrashConfig();

    public static TrashConfig get() {
        return instance;
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            TrashConfig loaded = GSON.fromJson(r, TrashConfig.class);
            instance = (loaded != null) ? loaded : new TrashConfig();
        } catch (IOException e) {
            Trashventory.LOGGER.error("Failed to load Trashventory config.", e);
            instance = new TrashConfig();
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(instance, w);
        } catch (IOException e) {
            Trashventory.LOGGER.error("Failed to save Trashventory config.", e);
        }
    }
}
