package dev.limucc.trashventory.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.limucc.trashventory.client.gui.TrashventoryScreen;

/** Opens the Trashventory settings GUI from the ModMenu mod list. */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return TrashventoryScreen::new;
    }
}
