package dev.evvie.waylandcraft.shared;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.NativeImage;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

public class RemoteWindowRenderer {
	
	private static final Logger LOGGER = LoggerFactory.getLogger("waylandcraft-remote-renderer");
	
	// 远程窗口纹理缓存: windowHandle -> TextureEntry
	private final Map<Long, TextureEntry> textureCache = new ConcurrentHashMap<>();
	
	// 最大缓存纹理数量
	private static final int MAX_CACHED_TEXTURES = 50;
	
	// 纹理清理间隔 (ticks)
	private static final int CLEANUP_INTERVAL = 600; // 30秒
	private int tickCounter = 0;
	
	public RemoteWindowRenderer() {
		LOGGER.info("RemoteWindowRenderer initialized");
	}
	
	/**
	 * 获取纹理位置标识符
	 */
	private Identifier getTextureLocation(long windowHandle) {
		return Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "remote_" + windowHandle);
	}
	
	/**
	 * 更新远程窗口纹理
	 */
	public void updateTexture(long windowHandle, int x, int y, int width, int height, byte[] jpegData) {
		TextureEntry entry = textureCache.get(windowHandle);
		
		if(entry == null || entry.width != width || entry.height != height) {
			// 创建新纹理
			destroyTexture(windowHandle);
			entry = createTexture(windowHandle, width, height);
			if(entry == null) return;
		}
		
		// 解码数据并更新纹理
		try {
			NativeImage image = decodeImageData(jpegData, width, height);
			if(image != null) {
				// 用新图像数据替换DynamicTexture的NativeImage
				entry.texture.close();
				DynamicTexture newTexture = new DynamicTexture(() -> "remote_window_" + Long.toHexString(windowHandle), image);
				entry.texture = newTexture;
				
				// 重新注册到TextureManager
				TextureManager textureManager = Minecraft.getInstance().getTextureManager();
				textureManager.register(entry.location, newTexture);
				
				entry.lastUpdate = System.currentTimeMillis();
			}
		} catch(Exception e) {
			LOGGER.error("Failed to update texture for window 0x{}", Long.toHexString(windowHandle), e);
		}
	}
	
	/**
	 * 创建新纹理
	 */
	@Nullable
	private TextureEntry createTexture(long windowHandle, int width, int height) {
		try {
			// 检查缓存限制
			if(textureCache.size() >= MAX_CACHED_TEXTURES) {
				cleanupOldTextures();
			}
			
			NativeImage image = new NativeImage(width, height, false);
			DynamicTexture texture = new DynamicTexture(() -> "remote_window_" + Long.toHexString(windowHandle), image);
			
			Identifier location = getTextureLocation(windowHandle);
			TextureManager textureManager = Minecraft.getInstance().getTextureManager();
			textureManager.register(location, texture);
			
			TextureEntry entry = new TextureEntry(texture, location, width, height);
			textureCache.put(windowHandle, entry);
			
			LOGGER.debug("Created texture for window 0x{} ({}x{})", Long.toHexString(windowHandle), width, height);
			return entry;
		} catch(Exception e) {
			LOGGER.error("Failed to create texture for window 0x{}", Long.toHexString(windowHandle), e);
			return null;
		}
	}
	
	/**
	 * 销毁纹理
	 */
	public void destroyTexture(long windowHandle) {
		TextureEntry entry = textureCache.remove(windowHandle);
		if(entry != null) {
			TextureManager textureManager = Minecraft.getInstance().getTextureManager();
			textureManager.release(entry.location);
			LOGGER.debug("Destroyed texture for window 0x{}", Long.toHexString(windowHandle));
		}
	}
	
	/**
	 * 获取纹理位置用于渲染
	 */
	@Nullable
	public Identifier getTextureLocation_obj(long windowHandle) {
		TextureEntry entry = textureCache.get(windowHandle);
		return entry != null ? entry.location : null;
	}
	
	/**
	 * 检查纹理是否存在
	 */
	public boolean hasTexture(long windowHandle) {
		return textureCache.containsKey(windowHandle);
	}
	
	/**
	 * 解码图像数据并填充到NativeImage
	 */
	@Nullable
	private NativeImage decodeImageData(byte[] data, int width, int height) {
		try {
			NativeImage image = new NativeImage(width, height, false);
			
			// 填充像素数据
			for(int y = 0; y < height; y++) {
				for(int x = 0; x < width; x++) {
					int index = (y * width + x) * 3; // RGB格式
					if(index + 2 < data.length) {
						int r = data[index] & 0xFF;
						int g = data[index + 1] & 0xFF;
						int b = data[index + 2] & 0xFF;
						int a = 255;
						// ABGR格式: Alpha in highest byte, then B, G, R
						image.setPixelABGR(x, y, (a << 24) | (b << 16) | (g << 8) | r);
					}
				}
			}
			
			return image;
		} catch(Exception e) {
			LOGGER.error("Failed to decode image data", e);
			return null;
		}
	}
	
	/**
	 * 清理旧纹理
	 */
	private void cleanupOldTextures() {
		long now = System.currentTimeMillis();
		long threshold = 30000; // 30秒
		
		textureCache.entrySet().removeIf(entry -> {
			if(now - entry.getValue().lastUpdate > threshold) {
				TextureManager textureManager = Minecraft.getInstance().getTextureManager();
				textureManager.release(entry.getValue().location);
				LOGGER.debug("Cleaned up old texture for window 0x{}", Long.toHexString(entry.getKey()));
				return true;
			}
			return false;
		});
	}
	
	/**
	 * 定期清理
	 */
	public void tick() {
		tickCounter++;
		if(tickCounter >= CLEANUP_INTERVAL) {
			tickCounter = 0;
			cleanupOldTextures();
		}
	}
	
	/**
	 * 清理所有纹理
	 */
	public void clear() {
		TextureManager textureManager = Minecraft.getInstance().getTextureManager();
		textureCache.values().forEach(entry -> textureManager.release(entry.location));
		textureCache.clear();
		LOGGER.info("RemoteWindowRenderer cleared");
	}
	
	/**
	 * 纹理条目
	 */
	private static class TextureEntry {
		DynamicTexture texture;
		final Identifier location;
		final int width;
		final int height;
		long lastUpdate;
		
		TextureEntry(DynamicTexture texture, Identifier location, int width, int height) {
			this.texture = texture;
			this.location = location;
			this.width = width;
			this.height = height;
			this.lastUpdate = System.currentTimeMillis();
		}
	}
}
