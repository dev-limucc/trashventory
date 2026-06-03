package dev.limucc.trashventory.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * S2C: the server pushes the player's authoritative settings to the client — on join, and after any
 * change made by a command — so the GUI always matches what commands set (and vice-versa).
 */
public record TrashSettingsPayload(List<String> blacklist, int personalTimer, boolean showMessage)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TrashSettingsPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("trashventory", "settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrashSettingsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(TrashPrefsPayload.MAX_ID_LEN).apply(ByteBufCodecs.list(TrashPrefsPayload.MAX_ENTRIES)),
            TrashSettingsPayload::blacklist,
            ByteBufCodecs.VAR_INT, TrashSettingsPayload::personalTimer,
            ByteBufCodecs.BOOL, TrashSettingsPayload::showMessage,
            TrashSettingsPayload::new);

    @Override
    public CustomPacketPayload.Type<TrashSettingsPayload> type() {
        return TYPE;
    }
}
