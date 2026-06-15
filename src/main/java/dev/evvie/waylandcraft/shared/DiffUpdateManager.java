package dev.evvie.waylandcraft.shared;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 差分更新管理器
 * 管理窗口图像的差分更新，减少网络传输数据量
 */
public class DiffUpdateManager {
	
	private static final Logger LOGGER = LoggerFactory.getLogger("waylandcraft-diff-update");
	
	// 窗口帧缓存: windowHandle -> FrameCache
	private final Map<Long, FrameCache> frameCacheMap = new ConcurrentHashMap<>();
	
	// 最大缓存大小
	private static final int MAX_CACHE_SIZE = 100;
	
	/**
	 * 处理帧更新
	 * 对于JPEG压缩数据，差分无意义（JPEG编码不确定性），直接透传
	 */
	@Nullable
	public byte[] processFrame(long windowHandle, byte[] newFrame) {
		// JPEG已压缩，diffing compressed data is meaningless
		// 直接返回完整帧
		return newFrame;
	}
	
	/**
	 * 计算两帧之间的差异百分比
	 */
	private float calculateDiffPercent(byte[] frame1, byte[] frame2) {
		if(frame1.length != frame2.length) {
			return 1.0f;
		}
		
		// 采样计算差异
		int sampleSize = Math.min(500, frame1.length);
		int step = frame1.length / sampleSize;
		
		int diffCount = 0;
		for(int i = 0; i < frame1.length; i += step) {
			if(frame1[i] != frame2[i]) {
				diffCount++;
			}
		}
		
		return (float)diffCount / sampleSize;
	}
	
	/**
	 * 生成差分数据
	 * 格式: [offset_high, offset_low, length_high, length_low, data...]
	 */
	private byte[] generateDiff(byte[] oldFrame, byte[] newFrame) {
		java.io.ByteArrayOutputStream diffStream = new java.io.ByteArrayOutputStream();
		
		int i = 0;
		while(i < newFrame.length) {
			// 找到差异开始位置
			if(oldFrame[i] == newFrame[i]) {
				i++;
				continue;
			}
			
			// 找到差异结束位置
			int diffStart = i;
			while(i < newFrame.length && (i - diffStart < 255) && oldFrame[i] != newFrame[i]) {
				i++;
			}
			int diffEnd = i;
			int diffLength = diffEnd - diffStart;
			
			// 写入差分数据
			diffStream.write((diffStart >> 8) & 0xFF); // offset high
			diffStream.write(diffStart & 0xFF);         // offset low
			diffStream.write(diffLength);               // length
			diffStream.write(newFrame, diffStart, diffLength); // data
		}
		
		return diffStream.toByteArray();
	}
	
	/**
	 * 应用差分数据
	 * @param baseFrame 基础帧
	 * @param diffData 差分数据
	 * @return 更新后的帧
	 */
	@Nullable
	public byte[] applyDiff(byte[] baseFrame, byte[] diffData) {
		byte[] result = baseFrame.clone();
		
		int i = 0;
		while(i < diffData.length - 3) {
			int offset = ((diffData[i] & 0xFF) << 8) | (diffData[i + 1] & 0xFF);
			int length = diffData[i + 2] & 0xFF;
			i += 3;
			
			if(offset + length > result.length) {
				LOGGER.warn("Invalid diff data: offset {} + length {} > frame size {}", 
					offset, length, result.length);
				return null;
			}
			
			System.arraycopy(diffData, i, result, offset, length);
			i += length;
		}
		
		return result;
	}
	
	/**
	 * 获取缓存统计信息
	 */
	public CacheStats getStats(long windowHandle) {
		FrameCache cache = frameCacheMap.get(windowHandle);
		if(cache == null) {
			return new CacheStats(0, 0, 0);
		}
		return new CacheStats(cache.frameCount, cache.keyFrameCount, 
			cache.frameCount > 0 ? (float)cache.keyFrameCount / cache.frameCount : 0);
	}
	
	/**
	 * 清理窗口缓存
	 */
	public void clearWindow(long windowHandle) {
		frameCacheMap.remove(windowHandle);
	}
	
	/**
	 * 清理所有缓存
	 */
	public void clear() {
		frameCacheMap.clear();
	}
	
	/**
	 * 帧缓存内部类
	 */
	private static class FrameCache {
		byte[] lastFrame;
		long frameCount = 0;
		long keyFrameCount = 0;
	}
	
	/**
	 * 缓存统计信息
	 */
	public static class CacheStats {
		public final long totalFrames;
		public final long keyFrames;
		public final float keyFrameRate;
		
		public CacheStats(long totalFrames, long keyFrames, float keyFrameRate) {
			this.totalFrames = totalFrames;
			this.keyFrames = keyFrames;
			this.keyFrameRate = keyFrameRate;
		}
	}
}
