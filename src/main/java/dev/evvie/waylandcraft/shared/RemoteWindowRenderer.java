package dev.evvie.waylandcraft.shared;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
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
		// 解码JPEG获取实际尺寸
		NativeImage image;
		try {
			image = decodeImageData(jpegData, width, height);
		} catch(Exception e) {
			LOGGER.error("Failed to decode image for window 0x{}", Long.toHexString(windowHandle), e);
			return;
		}
		if(image == null) return;
		
		int actualWidth = image.getWidth();
		int actualHeight = image.getHeight();
		
		TextureEntry entry = textureCache.get(windowHandle);
		
		// 尺寸变化或首次创建时重建纹理
		if(entry == null || entry.width != actualWidth || entry.height != actualHeight) {
			destroyTexture(windowHandle);
			entry = createTexture(windowHandle, actualWidth, actualHeight);
			if(entry == null) {
				image.close();
				return;
			}
		}
		
		// 更新纹理
		try {
			// 创建新纹理并注册（DynamicTexture内部复用NativeImage生命周期）
			entry.texture.close();
			DynamicTexture newTexture = new DynamicTexture(() -> "remote_window_" + Long.toHexString(windowHandle), image);
			entry.texture = newTexture;
			
			TextureManager textureManager = Minecraft.getInstance().getTextureManager();
			textureManager.register(entry.location, newTexture);
			
			entry.lastUpdate = System.currentTimeMillis();
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
	 * 获取纹理尺寸
	 */
	public int[] getTextureDimensions(long windowHandle) {
		TextureEntry entry = textureCache.get(windowHandle);
		if(entry == null) return null;
		return new int[]{entry.width, entry.height};
	}
	
	/**
	 * 解码JPEG图像数据为NativeImage
	 * 
	 * 关键：MC 26.1.2 的 NativeImage 内部用 ByteBuffer（默认 BIG_ENDIAN）存储像素。
	 * setPixelABGR 用 ByteBuffer.putInt 写入，big-endian 下 int 0xAABBGGRR 写为 [AA,BB,GG,RR]，
	 * 而 GPU 按 RGBA 读 → R=AA, G=BB, B=GG, A=RR → alpha 实际是原始 R 通道！
	 * → 低 R 值颜色（蓝/绿/黑）alpha≈0 → ALPHA_CUTOUT discard → "部分颜色变透明"
	 * 
	 * 修复：绕过 setPixelABGR，用 MemoryUtil.memPutInt（platform native LE）直接写，
	 * LE 下 int 0xAABBGGRR 写为 [RR,GG,BB,AA] = RGBA，GPU 正确读取。
	 */
	@Nullable
	private NativeImage decodeImageData(byte[] data, int width, int height) {
		try {
			// 用ImageIO解码JPEG数据
			BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(data));
			if(bufferedImage == null) {
				LOGGER.warn("ImageIO.read returned null (invalid JPEG data, {} bytes)", data.length);
				return null;
			}
			
			int w = bufferedImage.getWidth();
			int h = bufferedImage.getHeight();
			NativeImage image = new NativeImage(w, h, false);
			
			long ptr = image.getPointer();
			if(ptr == 0L) {
				LOGGER.error("NativeImage pointer is null after construction");
				image.close();
				return null;
			}
			
			for(int y = 0; y < h; y++) {
				for(int x = 0; x < w; x++) {
					int argb = bufferedImage.getRGB(x, y);
					int a = (argb >>> 24) & 0xFF;
					int r = (argb >>> 16) & 0xFF;
					int g = (argb >>> 8)  & 0xFF;
					int b = argb & 0xFF;
					// ABGR packed int: A in bits[31..24], B[23..16], G[15..8], R[7..0]
					// MemoryUtil.memPutInt 在 LE JVM 上写为 [R,G,B,A] = GL_RGBA ✓
					int abgrPacked = (a << 24) | (b << 16) | (g << 8) | r;
					MemoryUtil.memPutInt(ptr + (long)(y * w + x) * 4L, abgrPacked);
				}
			}
			
			// 日志：首像素验证
			if(LOGGER.isDebugEnabled()) {
				int firstArgb = bufferedImage.getRGB(0, 0);
				int fa = (firstArgb >>> 24) & 0xFF;
				int fr = (firstArgb >>> 16) & 0xFF;
				int fg = (firstArgb >>> 8)  & 0xFF;
				int fb = firstArgb & 0xFF;
				int fabgr = (fa << 24) | (fb << 16) | (fg << 8) | fr;
				long written = MemoryUtil.memGetInt(ptr);
				LOGGER.debug("decodeImageData: first pixel ARGB=0x{} a={} r={} g={} b={} -> ABGR_packed=0x{} written=0x{}",
					Integer.toHexString(firstArgb), fa, fr, fg, fb,
					Integer.toHexString(fabgr), Long.toHexString(written & 0xFFFFFFFFL));
			}
			
			return image;
		} catch(Exception e) {
			LOGGER.error("Failed to decode JPEG image data ({} bytes)", data.length, e);
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
