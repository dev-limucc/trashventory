package dev.limucc.trashventory.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C: sent right after the server opens a player's trash bin, carrying that menu's container id so the
 * modded client can tell OUR bin apart from any ordinary chest (e.g. one a player named "Trash Bin").
 * Only then is the settings gear shown.
 */
public record TrashOpenPayload(int containerId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TrashOpenPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("trashventory", "open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrashOpenPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TrashOpenPayload::containerId,
            TrashOpenPayload::new);

    @Override
    public CustomPacketPayload.Type<TrashOpenPayload> type() {
        return TYPE;
    }
}
