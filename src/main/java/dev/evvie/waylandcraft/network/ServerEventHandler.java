package dev.evvie.waylandcraft.network;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.shared.SharedWindowManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务器事件处理器
 * 处理玩家连接/断开事件，清理共享窗口数据
 */
public class ServerEventHandler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger("waylandcraft-server-events");
	
	/**
	 * 注册服务器事件处理器
	 */
	public static void register() {
		// 玩家加入服务器
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			handlePlayerJoin(player);
		});
		
		// 玩家离开服务器
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.getPlayer();
			handlePlayerDisconnect(player, server);
		});
		
		LOGGER.info("Server event handlers registered");
	}
	
	/**
	 * 处理玩家加入
	 */
	private static void handlePlayerJoin(ServerPlayer player) {
		UUID playerUUID = player.getUUID();
		LOGGER.info("Player {} joined server", playerUUID);
		
		// 给新玩家授予所有已注册窗口的VIEW权限
		SharedWindowManager manager = WaylandCraftCommon.instance.sharedWindowManager;
		manager.grantViewToNewPlayer(playerUUID);
		
		// 发送当前共享窗口列表给新加入的玩家
		sendWindowListToPlayer(player);
	}
	
	/**
	 * 处理玩家断开连接
	 */
	private static void handlePlayerDisconnect(ServerPlayer player, net.minecraft.server.MinecraftServer server) {
		UUID playerUUID = player.getUUID();
		LOGGER.info("Player {} disconnecting, cleaning up shared windows", playerUUID);
		
		SharedWindowManager manager = WaylandCraftCommon.instance.sharedWindowManager;
		
		// 清理玩家相关的窗口
		manager.handlePlayerDisconnect(playerUUID);
		
		// 广播更新的窗口列表给所有剩余玩家
		broadcastWindowListUpdate(server);
		
		LOGGER.info("Cleaned up shared windows for player {}", playerUUID);
	}
	
	/**
	 * 发送窗口列表给指定玩家
	 */
	private static void sendWindowListToPlayer(ServerPlayer player) {
		SharedWindowManager manager = WaylandCraftCommon.instance.sharedWindowManager;
		
		// 构建窗口列表
		var windowList = new java.util.ArrayList<SharedWindowListPayload.WindowInfo>();
		
		for(var entry : manager.getAllWindows()) {
			windowList.add(new SharedWindowListPayload.WindowInfo(
				entry.getWindowHandle(),
				entry.getOwnerUUID(),
				entry.getWindowTitle(),
				entry.getPermission(player.getUUID())
			));
		}
		
		// 发送给玩家
		SharedWindowListPayload payload = new SharedWindowListPayload(windowList);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
		
		LOGGER.debug("Sent window list to player {}: {} windows", player.getUUID(), windowList.size());
	}
	
	/**
	 * 广播窗口列表更新给所有玩家
	 */
	private static void broadcastWindowListUpdate(net.minecraft.server.MinecraftServer server) {
		SharedWindowManager manager = WaylandCraftCommon.instance.sharedWindowManager;
		
		// 获取所有在线玩家
		for(ServerPlayer player : server.getPlayerList().getPlayers()) {
			sendWindowListToPlayer(player);
		}
		
		LOGGER.debug("Broadcasted window list update to all players");
	}
}
