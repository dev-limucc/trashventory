package dev.limucc.trashventory.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.limucc.trashventory.Trashventory;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientConfigManager {

    private ClientConfigManager() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("trashventory_client.json");

    private static ClientConfig instance = new ClientConfig();

    public static ClientConfig get() {
        return instance;
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            ClientConfig loaded = GSON.fromJson(r, ClientConfig.class);
            instance = (loaded != null) ? loaded : new ClientConfig();
        } catch (IOException e) {
            Trashventory.LOGGER.error("Failed to load Trashventory client config.", e);
            instance = new ClientConfig();
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(instance, w);
        } catch (IOException e) {
            Trashventory.LOGGER.error("Failed to save Trashventory client config.", e);
        }
    }
}
