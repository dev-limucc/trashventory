package dev.limucc.trashventory.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C: sent to modded clients on join so their settings GUI knows the server's limits — the maximum
 * timer they may pick, whether the countdown is even enabled, and whether overrides are allowed.
 */
public record TrashConfigPayload(int serverTimerSeconds, boolean countdownEnabled, boolean doubleChest,
                                 boolean serverShowMessage, boolean allowClientOverride)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TrashConfigPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("trashventory", "config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrashConfigPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TrashConfigPayload::serverTimerSeconds,
            ByteBufCodecs.BOOL, TrashConfigPayload::countdownEnabled,
            ByteBufCodecs.BOOL, TrashConfigPayload::doubleChest,
            ByteBufCodecs.BOOL, TrashConfigPayload::serverShowMessage,
            ByteBufCodecs.BOOL, TrashConfigPayload::allowClientOverride,
            TrashConfigPayload::new);

    @Override
    public CustomPacketPayload.Type<TrashConfigPayload> type() {
        return TYPE;
    }
}
