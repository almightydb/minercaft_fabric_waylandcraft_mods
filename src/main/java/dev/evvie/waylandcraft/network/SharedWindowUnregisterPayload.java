package dev.evvie.waylandcraft.network;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SharedWindowUnregisterPayload(long windowHandle) implements CustomPacketPayload {
	
	public static final Identifier ID = Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "shared_window_unregister");
	
	public static final CustomPacketPayload.Type<SharedWindowUnregisterPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	
	public static final StreamCodec<RegistryFriendlyByteBuf, SharedWindowUnregisterPayload> CODEC = StreamCodec.of(
		(buf, payload) -> {
			buf.writeLong(payload.windowHandle);
		},
		buf -> new SharedWindowUnregisterPayload(buf.readLong())
	);
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
	
}
