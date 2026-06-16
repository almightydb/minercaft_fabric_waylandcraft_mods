package dev.evvie.waylandcraft.shared;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.network.SharedWindowClientHandler;
import dev.evvie.waylandcraft.network.SharedWindowImagePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import dev.evvie.waylandcraft.render.SharedWindowDisplay;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * 窗口共享管理器
 * 协调窗口共享的完整流程
 */
public class WindowShareManager {
	
	private static final Logger LOGGER = LoggerFactory.getLogger("waylandcraft-share-manager");
	
	// 客户端实例
	private final WaylandCraft clientMod;
	
	// 服务器端实例
	private final WaylandCraftCommon serverMod;
	
	// 图像捕获配置
	private ImageCapture.CaptureConfig captureConfig;
	
	// 帧率控制器
	private final FrameRateController frameRateController;
	
	// 差分更新管理器
	private final DiffUpdateManager diffUpdateManager;
	
	// 共享状态: windowHandle -> ShareState
	private final Map<Long, ShareState> shareStates = new ConcurrentHashMap<>();
	
	// 是否启用共享
	private boolean sharingEnabled = true;
	
	/**
	 * 构造函数（客户端）
	 */
	public WindowShareManager(WaylandCraft clientMod) {
		this.clientMod = clientMod;
		this.serverMod = null;
		this.captureConfig = new ImageCapture.CaptureConfig(0.5f, 0.7f, 20);
		this.frameRateController = new FrameRateController();
		this.diffUpdateManager = new DiffUpdateManager();
		
		// 注册客户端事件
		registerClientEvents();
		
		LOGGER.info("WindowShareManager initialized (client)");
	}
	
	/**
	 * 构造函数（服务器端）
	 */
	public WindowShareManager(WaylandCraftCommon serverMod) {
		this.clientMod = null;
		this.serverMod = serverMod;
		this.captureConfig = null;
		this.frameRateController = new FrameRateController();
		this.diffUpdateManager = new DiffUpdateManager();
		
		LOGGER.info("WindowShareManager initialized (server)");
	}
	
	/**
	 * 注册客户端事件
	 */
	private void registerClientEvents() {
		// 玩家断开连接时清理
		ClientPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			handleDisconnect();
		});
	}
	
	/**
	 * 开始共享窗口
	 */
	public boolean startSharing(long windowHandle, String windowTitle) {
		if(clientMod == null) {
			LOGGER.warn("Cannot start sharing on server side");
			return false;
		}
		
		// 检查是否已经共享
		if(shareStates.containsKey(windowHandle)) {
			LOGGER.warn("Window 0x{} is already being shared", Long.toHexString(windowHandle));
			return false;
		}
		
		// 创建共享状态
		ShareState state = new ShareState(windowHandle, windowTitle);
		shareStates.put(windowHandle, state);
		
		// 发送注册请求到服务器
		SharedWindowClientHandler.requestWindowRegister(windowHandle, windowTitle);
		
		LOGGER.info("Started sharing window 0x{}: {}", Long.toHexString(windowHandle), windowTitle);
		return true;
	}
	
	/**
	 * 停止共享窗口
	 */
	public boolean stopSharing(long windowHandle) {
		ShareState state = shareStates.remove(windowHandle);
		if(state == null) {
			LOGGER.warn("Window 0x{} is not being shared", Long.toHexString(windowHandle));
			return false;
		}
		
		// 发送注销请求到服务器
		SharedWindowClientHandler.requestWindowUnregister(windowHandle);
		
		// 清理差分更新缓存
		diffUpdateManager.clearWindow(windowHandle);
		frameRateController.reset(windowHandle);
		
		LOGGER.info("Stopped sharing window 0x{}", Long.toHexString(windowHandle));
		return true;
	}
	
	/**
	 * 更新共享窗口
	 * 应该在每个客户端tick中调用
	 */
	public void update() {
		if(clientMod == null || !sharingEnabled) return;
		
		// 遍历所有共享窗口
		for(ShareState state : shareStates.values()) {
			updateSharedWindow(state);
		}
	}
	
	/**
	 * 更新单个共享窗口
	 */
	private void updateSharedWindow(ShareState state) {
		// 检查帧率限制
		if(!frameRateController.shouldUpdate(state.windowHandle, captureConfig.maxFps)) {
			return;
		}
		
		// 获取本地窗口
		WLCToplevel toplevel = getLocalWindow(state.windowHandle);
		if(toplevel == null) {
			LOGGER.warn("[SHARE] toplevel is null for handle 0x{}", Long.toHexString(state.windowHandle));
			return;
		}
		if(!toplevel.isMapped()) {
			LOGGER.warn("[SHARE] toplevel not mapped for handle 0x{}", Long.toHexString(state.windowHandle));
			return;
		}
		
		// 捕获窗口图像 - 从窗口的framebuffer读取
		if(toplevel.framebuffer == null) {
			LOGGER.warn("[SHARE] framebuffer is null for handle 0x{}", Long.toHexString(state.windowHandle));
			return;
		}
		
		LOGGER.info("[SHARE] capturing window 0x{} ({}x{})", 
			Long.toHexString(state.windowHandle),
			toplevel.framebuffer.getWidth(), toplevel.framebuffer.getHeight());
		
		byte[] imageData = ImageCapture.captureFromFramebuffer(
			toplevel.framebuffer,
			captureConfig.scale,
			captureConfig.quality
		);
		
		if(imageData == null) {
			LOGGER.error("[SHARE] ImageCapture returned null!");
			return;
		}
		
		LOGGER.info("[SHARE] captured {} bytes JPEG", imageData.length);
		
		// 处理差分更新
		byte[] processedData = diffUpdateManager.processFrame(state.windowHandle, imageData);
		if(processedData == null) {
			LOGGER.warn("[SHARE] DiffUpdateManager returned null");
			return;
		}
		
		// 使用原始窗口尺寸（非缩放），接收端根据这个尺寸计算世界大小
		int originalW = toplevel.geometry.width();
		int originalH = toplevel.geometry.height();
		
		// 从本地WindowDisplay获取窗口变换（pivot/normal/down）
		double pivotX = 0, pivotY = 0, pivotZ = 0;
		double normalX = 0, normalY = 0, normalZ = 1;
		double downX = 0, downY = -1, downZ = 0;
		if(clientMod != null) {
			for(var display : clientMod.displays) {
				if(display.window.getHandle() == state.windowHandle) {
					Vec3 pivot = display.pivot;
					Vec3 normal = display.normal();
					Vec3 d = display.down();
					pivotX = pivot.x; pivotY = pivot.y; pivotZ = pivot.z;
					normalX = normal.x; normalY = normal.y; normalZ = normal.z;
					downX = d.x; downY = d.y; downZ = d.z;
					break;
				}
			}
		}
		
		SharedWindowImagePayload imagePayload = new SharedWindowImagePayload(
			state.windowHandle, 0, 0, 0,
			originalW, originalH,
			processedData,
			pivotX, pivotY, pivotZ,
			normalX, normalY, normalZ,
			downX, downY, downZ
		);
		ClientPlayNetworking.send(imagePayload);
		
		LOGGER.info("[SHARE] sent image payload: {} bytes, {}x{}", processedData.length, originalW, originalH);
		
		// 更新统计信息
		state.lastUpdateTime = System.currentTimeMillis();
		state.frameCount++;
		state.totalBytes += processedData.length;
	}
	
	/**
	 * 获取本地窗口
	 */
	@Nullable
	private WLCToplevel getLocalWindow(long windowHandle) {
		if(clientMod == null || clientMod.bridge == null) {
			return null;
		}
		return clientMod.bridge.getToplevel(windowHandle);
	}
	
	/**
	 * 处理断开连接
	 */
	private void handleDisconnect() {
		shareStates.clear();
		diffUpdateManager.clear();
		frameRateController.clear();
		LOGGER.info("Cleared all share states due to disconnect");
	}
	
	/**
	 * 获取共享状态
	 */
	@Nullable
	public ShareState getShareState(long windowHandle) {
		return shareStates.get(windowHandle);
	}
	
	/**
	 * 获取所有共享状态
	 */
	public Map<Long, ShareState> getAllShareStates() {
		return Map.copyOf(shareStates);
	}
	
	/**
	 * 设置捕获配置
	 */
	public void setCaptureConfig(ImageCapture.CaptureConfig config) {
		this.captureConfig = config;
		LOGGER.info("Updated capture config: scale={}, quality={}, maxFps={}", 
			config.scale, config.quality, config.maxFps);
	}
	
	/**
	 * 启用/禁用共享
	 */
	public void setSharingEnabled(boolean enabled) {
		this.sharingEnabled = enabled;
		LOGGER.info("Sharing {}", enabled ? "enabled" : "disabled");
	}
	
	/**
	 * 检查是否启用共享
	 */
	public boolean isSharingEnabled() {
		return sharingEnabled;
	}
	
	/**
	 * 获取统计信息
	 */
	public String getStats() {
		long totalFrames = shareStates.values().stream().mapToLong(s -> s.frameCount).sum();
		long totalBytes = shareStates.values().stream().mapToLong(s -> s.totalBytes).sum();
		
		return String.format("Windows: %d, Frames: %d, Bytes: %d", 
			shareStates.size(), totalFrames, totalBytes);
	}
	
	/**
	 * 共享状态内部类
	 */
	public static class ShareState {
		public final long windowHandle;
		public final String windowTitle;
		public final long startTime;
		
		public long lastUpdateTime = 0;
		public long frameCount = 0;
		public long totalBytes = 0;
		
		public ShareState(long windowHandle, String windowTitle) {
			this.windowHandle = windowHandle;
			this.windowTitle = windowTitle;
			this.startTime = System.currentTimeMillis();
		}
	}
}
