package dev.evvie.waylandcraft.network;

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
 * 交互转发器
 * 处理远程窗口交互的转发逻辑
 */
public class InteractionForwarder {
	
	private static final Logger LOGGER = LoggerFactory.getLogger("waylandcraft-interaction-forwarder");
	
	/**
	 * 转发交互事件给窗口所有者
	 */
	public static void forwardInteraction(SharedWindowInteractionPayload payload, ServerPlayer sender) {
		UUID senderUUID = sender.getUUID();
		SharedWindowManager manager = WaylandCraftCommon.instance.sharedWindowManager;
		
		SharedWindowEntry entry = manager.getWindow(payload.windowHandle());
		if(entry == null) {
			LOGGER.warn("Player {} attempted to interact with non-existent window 0x{}", 
				senderUUID, Long.toHexString(payload.windowHandle()));
			return;
		}
		
		// 检查交互权限
		if(!entry.hasPermission(senderUUID, WindowPermission.INTERACT)) {
			LOGGER.warn("Player {} denied interaction with window 0x{}", 
				senderUUID, Long.toHexString(payload.windowHandle()));
			return;
		}
		
		// 获取窗口所有者
		UUID ownerUUID = entry.getOwnerUUID();
		ServerPlayer owner = sender.server.getPlayerList().getPlayer(ownerUUID);
		if(owner == null) {
			LOGGER.debug("Window owner {} is offline, ignoring interaction", ownerUUID);
			return;
		}
		
		// 转发交互给所有者
		// 注意：这里需要修改payload以包含发送者信息
		// 目前直接转发原始payload
		ServerPlayNetworking.send(owner, payload);
		
		LOGGER.debug("Forwarded interaction from player {} to window owner {} for window 0x{}", 
			senderUUID, ownerUUID, Long.toHexString(payload.windowHandle()));
	}
	
	/**
	 * 处理鼠标移动
	 */
	public static void handleMouseMove(long windowHandle, double x, double y, ServerPlayer sender) {
		SharedWindowInteractionPayload payload = new SharedWindowInteractionPayload(
			windowHandle,
			SharedWindowInteractionPayload.InteractionType.MOUSE_MOVE,
			x, y, 0, 0
		);
		forwardInteraction(payload, sender);
	}
	
	/**
	 * 处理鼠标点击
	 */
	public static void handleMouseClick(long windowHandle, double x, double y, int button, ServerPlayer sender) {
		SharedWindowInteractionPayload payload = new SharedWindowInteractionPayload(
			windowHandle,
			SharedWindowInteractionPayload.InteractionType.MOUSE_CLICK,
			x, y, button, 0
		);
		forwardInteraction(payload, sender);
	}
	
	/**
	 * 处理鼠标释放
	 */
	public static void handleMouseRelease(long windowHandle, double x, double y, int button, ServerPlayer sender) {
		SharedWindowInteractionPayload payload = new SharedWindowInteractionPayload(
			windowHandle,
			SharedWindowInteractionPayload.InteractionType.MOUSE_RELEASE,
			x, y, button, 0
		);
		forwardInteraction(payload, sender);
	}
	
	/**
	 * 处理键盘按下
	 */
	public static void handleKeyPress(long windowHandle, int key, int modifiers, ServerPlayer sender) {
		SharedWindowInteractionPayload payload = new SharedWindowInteractionPayload(
			windowHandle,
			SharedWindowInteractionPayload.InteractionType.KEY_PRESS,
			0, 0, key, modifiers
		);
		forwardInteraction(payload, sender);
	}
	
	/**
	 * 处理键盘释放
	 */
	public static void handleKeyRelease(long windowHandle, int key, int modifiers, ServerPlayer sender) {
		SharedWindowInteractionPayload payload = new SharedWindowInteractionPayload(
			windowHandle,
			SharedWindowInteractionPayload.InteractionType.KEY_RELEASE,
			0, 0, key, modifiers
		);
		forwardInteraction(payload, sender);
	}
	
	/**
	 * 处理滚轮事件
	 */
	public static void handleScroll(long windowHandle, double x, double y, double scrollX, double scrollY, ServerPlayer sender) {
		// 将滚轮数据编码到button和key字段
		int scrollData = (int)(scrollX * 100) | ((int)(scrollY * 100) << 16);
		SharedWindowInteractionPayload payload = new SharedWindowInteractionPayload(
			windowHandle,
			SharedWindowInteractionPayload.InteractionType.SCROLL,
			x, y, scrollData, 0
		);
		forwardInteraction(payload, sender);
	}
	
	/**
	 * 检查玩家是否可以交互指定窗口
	 */
	public static boolean canInteract(UUID playerUUID, long windowHandle) {
		SharedWindowManager manager = WaylandCraftCommon.instance.sharedWindowManager;
		return manager.canInteract(playerUUID, windowHandle);
	}
	
	/**
	 * 获取窗口所有者
	 */
	public static UUID getWindowOwner(long windowHandle) {
		SharedWindowManager manager = WaylandCraftCommon.instance.sharedWindowManager;
		SharedWindowEntry entry = manager.getWindow(windowHandle);
		return entry != null ? entry.getOwnerUUID() : null;
	}
}
