package dev.evvie.waylandcraft.network;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C: 服务端返回权限操作结果
 */
public record PermissionResponsePayload(String message) implements CustomPacketPayload {

	public static final Identifier ID = Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "permission_response");

	public static final CustomPacketPayload.Type<PermissionResponsePayload> TYPE = new CustomPacketPayload.Type<>(ID);

	public static final StreamCodec<RegistryFriendlyByteBuf, PermissionResponsePayload> CODEC = StreamCodec.of(
		(buf, p) -> buf.writeUtf(p.message, 1024),
		buf -> new PermissionResponsePayload(buf.readUtf(1024))
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
