package dev.evvie.waylandcraft.shared;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.DynamicTexture;

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
		
		// 解码JPEG数据并更新纹理
		try {
			NativeImage image = decodeJpeg(jpegData, width, height);
			if(image != null) {
				entry.texture.upload();
				entry.lastUpdate = System.currentTimeMillis();
				image.close();
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
			
			TextureEntry entry = new TextureEntry(texture, width, height);
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
			entry.texture.close();
			LOGGER.debug("Destroyed texture for window 0x{}", Long.toHexString(windowHandle));
		}
	}
	
	/**
	 * 获取纹理ID用于渲染
	 */
	public int getTextureId(long windowHandle) {
		TextureEntry entry = textureCache.get(windowHandle);
		if(entry != null) {
			return entry.texture.getId();
		}
		return -1;
	}
	
	/**
	 * 检查纹理是否存在
	 */
	public boolean hasTexture(long windowHandle) {
		return textureCache.containsKey(windowHandle);
	}
	
	/**
	 * 解码JPEG数据
	 */
	@Nullable
	private NativeImage decodeJpeg(byte[] jpegData, int width, int height) {
		try {
			// 创建NativeImage并填充数据
			NativeImage image = new NativeImage(width, height, false);
			
			// 简化实现：直接填充纹理数据
			// 实际实现需要使用JPEG解码器
			for(int y = 0; y < height; y++) {
				for(int x = 0; x < width; x++) {
					int index = (y * width + x) * 3; // RGB格式
					if(index + 2 < jpegData.length) {
						int r = jpegData[index] & 0xFF;
						int g = jpegData[index + 1] & 0xFF;
						int b = jpegData[index + 2] & 0xFF;
						int a = 255;
						image.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
					}
				}
			}
			
			return image;
		} catch(Exception e) {
			LOGGER.error("Failed to decode JPEG data", e);
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
				entry.getValue().texture.close();
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
		textureCache.values().forEach(entry -> entry.texture.close());
		textureCache.clear();
		LOGGER.info("RemoteWindowRenderer cleared");
	}
	
	/**
	 * 纹理条目
	 */
	private static class TextureEntry {
		final DynamicTexture texture;
		final int width;
		final int height;
		long lastUpdate;
		
		TextureEntry(DynamicTexture texture, int width, int height) {
			this.texture = texture;
			this.width = width;
			this.height = height;
			this.lastUpdate = System.currentTimeMillis();
		}
	}
}
