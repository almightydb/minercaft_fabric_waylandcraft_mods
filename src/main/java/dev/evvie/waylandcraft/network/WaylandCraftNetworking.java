package dev.evvie.waylandcraft.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.shared.SharedWindowEntry;
import dev.evvie.waylandcraft.shared.SharedWindowManager;
import dev.evvie.waylandcraft.shared.WindowPermission;
import dev.evvie.waylandcraft.utils.IMyServerPlayer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public class WaylandCraftNetworking {
	
	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(ServerboundGiveItemsPayload.TYPE, ServerboundGiveItemsPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ServerboundAliveWindowsPayload.TYPE, ServerboundAliveWindowsPayload.CODEC);
		
		// 注册多人显示功能的数据包
		PayloadTypeRegistry.serverboundPlay().register(SharedWindowRegisterPayload.TYPE, SharedWindowRegisterPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SharedWindowUnregisterPayload.TYPE, SharedWindowUnregisterPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SharedWindowUpdatePayload.TYPE, SharedWindowUpdatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SharedWindowImagePayload.TYPE, SharedWindowImagePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SharedWindowImagePayload.TYPE, SharedWindowImagePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SharedWindowInteractionPayload.TYPE, SharedWindowInteractionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SharedWindowPermissionPayload.TYPE, SharedWindowPermissionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SharedWindowListPayload.TYPE, SharedWindowListPayload.CODEC);
		
		// 权限管理命令
		PayloadTypeRegistry.serverboundPlay().register(PermissionCommandPayload.TYPE, PermissionCommandPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PermissionResponsePayload.TYPE, PermissionResponsePayload.CODEC);
		
		ServerPlayNetworking.registerGlobalReceiver(ServerboundGiveItemsPayload.TYPE, (payload, ctx) -> {
			IMyServerPlayer plr = (IMyServerPlayer) ctx.player();
			if(plr.getItemGiveCooldown() > 0) return;
			plr.setItemGiveCooldown(10);
			
			ArrayList<Long> handles = new ArrayList<Long>();
			for(long handle : payload.handles()) {
				if(handles.contains(handle)) continue;
				handles.add(handle);
			}
			
			if(payload.missingOnly()) WaylandCraftCommon.instance.serverItemManager.giveItemsIfMissing(ctx.player(), handles);
			else WaylandCraftCommon.instance.serverItemManager.giveItems(ctx.player(), handles);
		});
		
		ServerPlayNetworking.registerGlobalReceiver(ServerboundAliveWindowsPayload.TYPE, (payload, ctx) -> {
			IMyServerPlayer plr = (IMyServerPlayer) ctx.player();
			ArrayList<Long> handles = plr.getAliveWindows();
			handles.clear();
			
			for(long handle : payload.handles()) {
				handles.add(handle);
			}
		});
		
		// 处理客户端请求注册共享窗口
		ServerPlayNetworking.registerGlobalReceiver(SharedWindowRegisterPayload.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			UUID playerUUID = player.getUUID();
			
			SharedWindowManager manager = WaylandCraftCommon.instance.sharedWindowManager;
			
			// 注册窗口
			SharedWindowEntry entry = manager.registerWindow(
				payload.windowHandle(),
				playerUUID,
				payload.windowTitle()
			);
			
			// 广播窗口列表给所有订阅者
			broadcastWindowList(manager, player);
		});
		
		// 处理权限管理命令
		ServerPlayNetworking.registerGlobalReceiver(PermissionCommandPayload.TYPE, (payload, ctx) -> {
			ctx.server().execute(() -> {
				SharedWindowServerHandler.handlePermissionCommand(payload, ctx.player());
			});
		});
		
		// 处理客户端上传的窗口图像 - 转发给其他有权限的玩家
		ServerPlayNetworking.registerGlobalReceiver(SharedWindowImagePayload.TYPE, (payload, ctx) -> {
			ServerPlayer sender = ctx.player();
			UUID senderUUID = sender.getUUID();
			
			// 检查窗口是否由该玩家共享
			SharedWindowManager manager = WaylandCraftCommon.instance.sharedWindowManager;
			SharedWindowEntry entry = manager.getWindow(payload.windowHandle());
			if(entry == null || !entry.getOwnerUUID().equals(senderUUID)) {
				return;
			}
			
			// 转发给所有其他有VIEW权限的在线玩家
			for(ServerPlayer player : sender.level().getServer().getPlayerList().getPlayers()) {
				if(player.getUUID().equals(senderUUID)) continue;
				if(entry.hasPermission(player.getUUID(), WindowPermission.VIEW)) {
					ServerPlayNetworking.send(player, payload);
				}
			}
		});
		
		ServerPlayNetworking.registerGlobalReceiver(SharedWindowInteractionPayload.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			UUID playerUUID = player.getUUID();
			
			SharedWindowManager manager = WaylandCraftCommon.instance.sharedWindowManager;
			
			// 检查权限
			if (!manager.canInteract(playerUUID, payload.windowHandle())) {
				return;
			}
			
			// 转发交互给窗口所有者
			// 转发交互给窗口所有者
			InteractionForwarder.forwardInteraction(payload, player);
		});
	}
	
	private static void broadcastWindowList(SharedWindowManager manager, ServerPlayer excludePlayer) {
		// 发送给所有在线玩家（排除发送者）
		for (ServerPlayer player : excludePlayer.level().getServer().getPlayerList().getPlayers()) {
			if (player == excludePlayer) continue;
			
			// 每个接收者用自己的权限构建窗口列表
			List<SharedWindowListPayload.WindowInfo> windowList = new ArrayList<>();
			for (SharedWindowEntry entry : manager.getAllWindows()) {
				windowList.add(new SharedWindowListPayload.WindowInfo(
					entry.getWindowHandle(),
					entry.getOwnerUUID(),
					entry.getWindowTitle(),
					entry.getPermission(player.getUUID())
				));
			}
			
			ServerPlayNetworking.send(player, new SharedWindowListPayload(windowList));
		}
	}
	
}
