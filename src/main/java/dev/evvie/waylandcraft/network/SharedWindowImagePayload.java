package dev.evvie.waylandcraft.network;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SharedWindowImagePayload(long windowHandle, int frameNumber, int x, int y, int width, int height, byte[] imageData, double posX, double posY, double posZ) implements CustomPacketPayload {
	
	public static final Identifier ID = Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "shared_window_image");
	
	public static final CustomPacketPayload.Type<SharedWindowImagePayload> TYPE = new CustomPacketPayload.Type<>(ID);
	
	public static final StreamCodec<RegistryFriendlyByteBuf, SharedWindowImagePayload> CODEC = StreamCodec.of(
		(buf, payload) -> {
			buf.writeLong(payload.windowHandle);
			buf.writeVarInt(payload.frameNumber);
			buf.writeVarInt(payload.x);
			buf.writeVarInt(payload.y);
			buf.writeVarInt(payload.width);
			buf.writeVarInt(payload.height);
			buf.writeVarInt(payload.imageData.length);
			buf.writeBytes(payload.imageData);
			buf.writeDouble(payload.posX);
			buf.writeDouble(payload.posY);
			buf.writeDouble(payload.posZ);
		},
		buf -> {
			long windowHandle = buf.readLong();
			int frameNumber = buf.readVarInt();
			int x = buf.readVarInt();
			int y = buf.readVarInt();
			int width = buf.readVarInt();
			int height = buf.readVarInt();
			int dataLength = buf.readVarInt();
			byte[] imageData = new byte[dataLength];
			buf.readBytes(imageData);
			double posX = buf.readDouble();
			double posY = buf.readDouble();
			double posZ = buf.readDouble();
			return new SharedWindowImagePayload(windowHandle, frameNumber, x, y, width, height, imageData, posX, posY, posZ);
		}
	);
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
	
}
