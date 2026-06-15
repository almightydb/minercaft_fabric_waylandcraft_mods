package dev.evvie.waylandcraft.shared;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;

import dev.evvie.waylandcraft.render.WindowFramebuffer;

/**
 * 图像捕获和压缩模块
 * 负责从OpenGL framebuffer捕获图像并压缩为JPEG格式
 */
public class ImageCapture {
	
	private static final Logger LOGGER = LoggerFactory.getLogger("waylandcraft-image-capture");
	
	// 默认JPEG质量
	private static final float DEFAULT_JPEG_QUALITY = 0.7f;
	
	// 默认缩放比例
	private static final float DEFAULT_SCALE = 0.5f;
	
	// 最大图像尺寸
	private static final int MAX_WIDTH = 1920;
	private static final int MAX_HEIGHT = 1080;
	
	// 可复用的ByteBuffer，避免每帧分配
	private static ByteBuffer reusableBuffer = null;
	private static int lastBufferWidth = 0;
	private static int lastBufferHeight = 0;
	
	/**
	 * 从当前OpenGL framebuffer捕获图像
	 * @param x 起始X坐标
	 * @param y 起始Y坐标
	 * @param width 宽度
	 * @param height 高度
	 * @return JPEG压缩的图像数据，失败返回null
	 */
	@Nullable
	public static byte[] captureFramebuffer(int x, int y, int width, int height) {
		return captureFramebuffer(x, y, width, height, DEFAULT_SCALE, DEFAULT_JPEG_QUALITY);
	}
	
	/**
	 * 从当前OpenGL framebuffer捕获图像
	 */
	@Nullable
	public static byte[] captureFramebuffer(int x, int y, int width, int height, float scale, float quality) {
		// 限制尺寸
		width = Math.min(width, MAX_WIDTH);
		height = Math.min(height, MAX_HEIGHT);
		
		// 计算缩放后的尺寸
		int scaledWidth = (int)(width * scale);
		int scaledHeight = (int)(height * scale);
		
		if(scaledWidth <= 0 || scaledHeight <= 0) {
			LOGGER.warn("Invalid capture dimensions: {}x{}", scaledWidth, scaledHeight);
			return null;
		}
		
		try {
			// 从OpenGL读取像素数据
			ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4);
			GL11.glReadPixels(x, y, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
			
			// 转换为BufferedImage
			BufferedImage image = pixelBufferToImage(buffer, width, height);
			
			// 缩放图像
			if(scale != 1.0f) {
				image = scaleImage(image, scaledWidth, scaledHeight);
			}
			
			// 压缩为JPEG
			return compressToJpeg(image, quality);
			
		} catch(Exception e) {
			LOGGER.error("Failed to capture framebuffer", e);
			return null;
		}
	}
	
	/**
	 * 从WindowFramebuffer捕获图像
	 */
	@Nullable
	public static byte[] captureFromFramebuffer(WindowFramebuffer framebuffer) {
		return captureFromFramebuffer(framebuffer, DEFAULT_SCALE, DEFAULT_JPEG_QUALITY);
	}
	
	/**
	 * 从WindowFramebuffer捕获图像
	 */
	@Nullable
	public static byte[] captureFromFramebuffer(WindowFramebuffer framebuffer, float scale, float quality) {
		if(!framebuffer.isValid()) {
			return null;
		}
		
		var target = framebuffer.getRenderTarget();
		if(target == null) {
			return null;
		}
		
		int width = framebuffer.getWidth();
		int height = framebuffer.getHeight();
		
		if(width <= 0 || height <= 0) {
			return null;
		}
		
		var colorTex = target.getColorTexture();
		if(colorTex == null || colorTex.isClosed()) {
			return null;
		}
		
		try {
			// 通过 GlTexture 获取 GL 纹理 ID，创建临时 FBO 读取像素
			int glTexId = ((com.mojang.blaze3d.opengl.GlTexture) colorTex).glId();
			
			int tempFbo = org.lwjgl.opengl.GL30.glGenFramebuffers();
			org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, tempFbo);
			org.lwjgl.opengl.GL30.glFramebufferTexture2D(
				org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,
				org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0,
				GL11.GL_TEXTURE_2D, glTexId, 0
			);
			
			// FBO完整性检查
			int status = org.lwjgl.opengl.GL30.glCheckFramebufferStatus(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER);
			if(status != org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE) {
				LOGGER.error("FBO incomplete: 0x{}", Integer.toHexString(status));
				org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, 0);
				org.lwjgl.opengl.GL30.glDeleteFramebuffers(tempFbo);
				return null;
			}
			
			// 复用ByteBuffer
			int needed = width * height * 4;
			if(reusableBuffer == null || lastBufferWidth != width || lastBufferHeight != height) {
				reusableBuffer = ByteBuffer.allocateDirect(needed);
				lastBufferWidth = width;
				lastBufferHeight = height;
			} else {
				reusableBuffer.clear();
			}
			
			GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, reusableBuffer);
			
			// GL错误检查
			int glError = GL11.glGetError();
			if(glError != GL11.GL_NO_ERROR) {
				LOGGER.error("glReadPixels error: 0x{}", Integer.toHexString(glError));
				org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, 0);
				org.lwjgl.opengl.GL30.glDeleteFramebuffers(tempFbo);
				return null;
			}
			
			// 清理临时 FBO
			org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, 0);
			org.lwjgl.opengl.GL30.glDeleteFramebuffers(tempFbo);
			
			// 转换为 BufferedImage
			BufferedImage image = pixelBufferToImage(reusableBuffer, width, height);
			
			// 缩放
			if(scale != 1.0f) {
				int scaledW = (int)(width * scale);
				int scaledH = (int)(height * scale);
				if(scaledW > 0 && scaledH > 0) {
					image = scaleImage(image, scaledW, scaledH);
				}
			}
			
			return compressToJpeg(image, quality);
		} catch(Exception e) {
			LOGGER.error("Failed to capture from framebuffer", e);
			return null;
		}
	}
	
	/**
	 * 将OpenGL像素缓冲区转换为BufferedImage
	 */
	private static BufferedImage pixelBufferToImage(ByteBuffer buffer, int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		
		// OpenGL的像素是从左下角开始的，需要翻转
		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) {
				int index = ((height - y - 1) * width + x) * 4;
				
				int r = buffer.get(index) & 0xFF;
				int g = buffer.get(index + 1) & 0xFF;
				int b = buffer.get(index + 2) & 0xFF;
				int a = buffer.get(index + 3) & 0xFF;
				
				int argb = (a << 24) | (r << 16) | (g << 8) | b;
				image.setRGB(x, y, argb);
			}
		}
		
		return image;
	}
	
	/**
	 * 缩放图像
	 */
	private static BufferedImage scaleImage(BufferedImage source, int targetWidth, int targetHeight) {
		BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g2d = scaled.createGraphics();
		
		// 设置高质量缩放
		g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, 
			java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, 
			java.awt.RenderingHints.VALUE_RENDER_QUALITY);
		g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, 
			java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		
		g2d.drawImage(source, 0, 0, targetWidth, targetHeight, null);
		g2d.dispose();
		
		return scaled;
	}
	
	/**
	 * 压缩图像为JPEG格式（ARGB→RGB转换）
	 */
	@Nullable
	public static byte[] compressToJpeg(BufferedImage image, float quality) {
		try {
			// JPEG不支持alpha通道，需要转为RGB
			BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
			java.awt.Graphics2D g2d = rgbImage.createGraphics();
			g2d.drawImage(image, 0, 0, null);
			g2d.dispose();
			
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			
			ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
			ImageWriteParam param = writer.getDefaultWriteParam();
			
			// 设置压缩质量
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(quality);
			
			// 写入JPEG数据
			ImageOutputStream imageOutputStream = new MemoryCacheImageOutputStream(outputStream);
			writer.setOutput(imageOutputStream);
			writer.write(null, new IIOImage(rgbImage, null, null), param);
			writer.dispose();
			imageOutputStream.close();
			
			return outputStream.toByteArray();
			
		} catch(IOException e) {
			LOGGER.error("Failed to compress image to JPEG", e);
			return null;
		}
	}
	
	/**
	 * 使用Deflater压缩数据（替代方案）
	 */
	@Nullable
	public static byte[] compressWithDeflater(byte[] data) {
		Deflater deflater = new Deflater(Deflater.BEST_SPEED);
		deflater.setInput(data);
		deflater.finish();
		
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
		byte[] buffer = new byte[1024];
		
		while(!deflater.finished()) {
			int count = deflater.deflate(buffer);
			outputStream.write(buffer, 0, count);
		}
		
		deflater.end();
		return outputStream.toByteArray();
	}
	
	/**
	 * 计算两帧之间的差异
	 * @return 差异数据，如果没有差异返回null
	 */
	@Nullable
	public static byte[] calculateDiff(byte[] previousFrame, byte[] currentFrame) {
		if(previousFrame == null || currentFrame == null) {
			return currentFrame;
		}
		
		if(previousFrame.length != currentFrame.length) {
			return currentFrame;
		}
		
		// 简单的差异计算：只传输变化的字节
		ByteArrayOutputStream diffStream = new ByteArrayOutputStream();
		int changedPixels = 0;
		
		for(int i = 0; i < currentFrame.length; i++) {
			if(previousFrame[i] != currentFrame[i]) {
				diffStream.write(i & 0xFF);
				diffStream.write((i >> 8) & 0xFF);
				diffStream.write(currentFrame[i]);
				changedPixels++;
			}
		}
		
		// 如果变化超过50%，直接传输完整帧
		if(changedPixels > currentFrame.length / 2) {
			return currentFrame;
		}
		
		return diffStream.toByteArray();
	}
	
	/**
	 * 获取推荐的捕获配置
	 */
	public static CaptureConfig getRecommendedConfig(int width, int height) {
		// 根据窗口大小推荐配置
		if(width * height > 1920 * 1080) {
			return new CaptureConfig(0.25f, 0.5f, 10); // 大窗口：低质量、低帧率
		} else if(width * height > 1280 * 720) {
			return new CaptureConfig(0.5f, 0.6f, 15); // 中等窗口
		} else {
			return new CaptureConfig(0.75f, 0.7f, 20); // 小窗口：高质量
		}
	}
	
	/**
	 * 捕获配置
	 */
	public static class CaptureConfig {
		public final float scale;
		public final float quality;
		public final int maxFps;
		
		public CaptureConfig(float scale, float quality, int maxFps) {
			this.scale = scale;
			this.quality = quality;
			this.maxFps = maxFps;
		}
	}
}
