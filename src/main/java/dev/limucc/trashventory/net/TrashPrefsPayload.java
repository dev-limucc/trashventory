package dev.limucc.trashventory.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * C2S: the client GUI pushes the player's full settings to the server (which is authoritative).
 *
 * @param blacklist    item ids (with or without "minecraft:") to auto-delete on pickup
 * @param personalTimer -1 = follow server default, 0 = off (instant), &gt;0 = custom seconds (clamped ≤ server)
 * @param showMessage  whether the player wants the "deleted in Ns" message
 */
public record TrashPrefsPayload(List<String> blacklist, int personalTimer, boolean showMessage)
        implements CustomPacketPayload {

    /** Hard caps so a malicious client can't send an oversized packet to exhaust server memory. */
    public static final int MAX_ENTRIES = 1024;
    public static final int MAX_ID_LEN = 200;

    public static final CustomPacketPayload.Type<TrashPrefsPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("trashventory", "prefs"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrashPrefsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_ID_LEN).apply(ByteBufCodecs.list(MAX_ENTRIES)), TrashPrefsPayload::blacklist,
            ByteBufCodecs.VAR_INT, TrashPrefsPayload::personalTimer,
            ByteBufCodecs.BOOL, TrashPrefsPayload::showMessage,
            TrashPrefsPayload::new);

    @Override
    public CustomPacketPayload.Type<TrashPrefsPayload> type() {
        return TYPE;
    }
}
