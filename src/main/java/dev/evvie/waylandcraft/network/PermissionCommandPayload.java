package dev.evvie.waylandcraft.network;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S: 客户端发送权限管理命令到服务端
 * action: 0=SET_DEFAULT, 1=ALLOW, 2=DENY, 3=LIST
 */
public record PermissionCommandPayload(byte action, String targetName, byte permissionLevel) implements CustomPacketPayload {

	public static final Identifier ID = Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "permission_command");

	public static final CustomPacketPayload.Type<PermissionCommandPayload> TYPE = new CustomPacketPayload.Type<>(ID);

	public static final StreamCodec<RegistryFriendlyByteBuf, PermissionCommandPayload> CODEC = StreamCodec.of(
		(buf, p) -> {
			buf.writeByte(p.action);
			buf.writeUtf(p.targetName, 64);
			buf.writeByte(p.permissionLevel);
		},
		buf -> new PermissionCommandPayload(
			buf.readByte(),
			buf.readUtf(64),
			buf.readByte()
		)
	);

	public static final byte ACTION_SET_DEFAULT = 0;
	public static final byte ACTION_ALLOW = 1;
	public static final byte ACTION_DENY = 2;
	public static final byte ACTION_LIST = 3;
	public static final byte ACTION_REMOVE = 4;

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
