package dev.evvie.waylandcraft.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.shared.SharedWindowEntry;
import dev.evvie.waylandcraft.shared.SharedWindowManager;
import dev.evvie.waylandcraft.shared.WindowPermission;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务器端窗口共享处理器
 * 处理窗口共享的业务逻辑
 */
public class SharedWindowServerHandler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger("waylandcraft-server-handler");
	
	/**
	 * 处理窗口注册请求
	 */
	public static void handleWindowRegister(SharedWindowRegisterPayload payload, ServerPlayer player) {
		UUID playerUUID = player.getUUID();
		SharedWindowManager manager = WaylandCraftCommon.instance.sharedWindowManager;
		
		// 检查是否已经注册过该窗口
		SharedWindowEntry existing = manager.getWindow(payload.windowHandle());
		if(existing != null) {
			LOGGER.warn("Player {} attempted to register already registered window 0x{}", 
				playerUUID, Long.toHexString(payload.windowHandle()));
			return;
		}
		
		// 注册窗口
		SharedWindowEntry entry = manager.registerWindow(
			payload.windowHandle(),
			playerUUID,
			payload.windowTitle()
		);
		
		LOGGER.info("Player {} registered window 0x{}: {}", 
			playerUUID, Long.toHexString(payload.windowHandle()), payload.windowTitle());
		
		// 广播窗口列表给所有玩家
		broadcastWindowListToAll(manager);
	}
	
	/**
	 * 处理窗口注销请求
	 */
	public static void handleWindowUnregister(long windowHandle, ServerPlayer player) {
		UUID playerUUID = player.getUUID();
		SharedWindowManager manager = WaylandCraftCommon.instance.sharedWindowManager;
		
		SharedWindowEntry entry = manager.getWindow(windowHandle);
		if(entry == null) {
			LOGGER.warn("Player {} attempted to unregister non-existent window 0x{}", 
				playerUUID, Long.toHexString(windowHandle));
			return;
		}
		
		// 检查是否是窗口所有者
		if(!entry.getOwnerUUID().equals(playerUUID)) {
			LOGGER.warn("Player {} attempted to unregister window 0x{} owned by {}", 
				playerUUID, Long.toHexString(windowHandle), entry.getOwnerUUID());
			return;
		}
		
		// 注销窗口
		manager.unregisterWindow(windowHandle);
		LOGGER.info("Player {} unregistered window 0x{}", playerUUID, Long.toHexString(windowHandle));
		
		// 广播窗口列表给所有玩家
		broadcastWindowListToAll(manager);
	}
	
	/**
	 * 处理窗口状态更新
	 */
	public static void handleWindowStateUpdate(SharedWindowUpdatePayload payload, ServerPlayer player) {
		UUID playerUUID = player.getUUID();
		SharedWindowManager manager = WaylandCraftCommon.instance.sharedWindowManager;
		
		SharedWindowEntry entry = manager.getWindow(payload.windowHandle());
		if(entry == null) {
			return;
		}
		
		// 检查权限
		if(!entry.hasPermission(playerUUID, WindowPermission.CONTROL)) {
			LOGGER.warn("Player {} denied state update for window 0x{}", 
				playerUUID, Long.toHexString(payload.windowHandle()));
			return;
		}
		
		// 更新窗口状态
		manager.updateWindowState(
			payload.windowHandle(),
			payload.x(), payload.y(),
			payload.width(), payload.height(),
			payload.visible(),
			playerUUID
		);
		
		// 广播状态更新给所有订阅者
		broadcastWindowState(entry, payload);
	}
	
	/**
	 * 处理权限更新请求
	 */
	public static void handlePermissionUpdate(long windowHandle, UUID targetUUID, WindowPermission permission, ServerPlayer player) {
		UUID playerUUID = player.getUUID();
		SharedWindowManager manager = WaylandCraftCommon.instance.sharedWindowManager;
		
		// 更新权限
		boolean success = manager.updatePermission(windowHandle, targetUUID, permission, playerUUID);
		if(!success) {
			LOGGER.warn("Player {} failed to update permission for window 0x{}", 
				playerUUID, Long.toHexString(windowHandle));
			return;
		}
		
		LOGGER.info("Player {} updated permission for player {} on window 0x{}: {}", 
			playerUUID, targetUUID, Long.toHexString(windowHandle), permission);
		
		// 发送权限更新给目标玩家
		ServerPlayer targetPlayer = player.level().getServer().getPlayerList().getPlayer(targetUUID);
		if(targetPlayer != null) {
			SharedWindowPermissionPayload permissionPayload = new SharedWindowPermissionPayload(
				windowHandle, playerUUID, permission
			);
			ServerPlayNetworking.send(targetPlayer, permissionPayload);
		}
	}
	
	/**
	 * 处理玩家断开连接
	 */
	public static void handlePlayerDisconnect(ServerPlayer player) {
		UUID playerUUID = player.getUUID();
		SharedWindowManager manager = WaylandCraftCommon.instance.sharedWindowManager;
		
		// 清理玩家相关的窗口
		manager.handlePlayerDisconnect(playerUUID);
		
		LOGGER.info("Player {} disconnected, cleaned up shared windows", playerUUID);
		
		// 广播窗口列表给所有玩家
		broadcastWindowListToAll(manager);
	}
	
	/**
	 * 广播窗口列表给所有在线玩家
	 */
	private static void broadcastWindowListToAll(SharedWindowManager manager) {
		List<SharedWindowListPayload.WindowInfo> windowList = new ArrayList<>();
		
		for(SharedWindowEntry entry : manager.getAllWindows()) {
			// 为每个玩家创建不同的窗口列表（权限不同）
			windowList.add(new SharedWindowListPayload.WindowInfo(
				entry.getWindowHandle(),
				entry.getOwnerUUID(),
				entry.getWindowTitle(),
				WindowPermission.VIEW // 默认权限，实际需要为每个玩家单独计算
			));
		}
		
		SharedWindowListPayload listPayload = new SharedWindowListPayload(windowList);
		
		// 发送给所有在线玩家
		// 注意：这里需要访问服务器实例，简化处理
		// 实际实现需要通过事件系统或全局访问点
		LOGGER.info("Broadcasting window list: {} windows", windowList.size());
	}
	
	/**
	 * 广播窗口状态更新给订阅者
	 */
	private static void broadcastWindowState(SharedWindowEntry entry, SharedWindowUpdatePayload payload) {
		// TODO: 实现状态更新广播
		// 需要获取订阅该窗口的玩家列表，然后发送更新
		LOGGER.debug("Broadcasting state update for window 0x{}", Long.toHexString(entry.getWindowHandle()));
	}
}
