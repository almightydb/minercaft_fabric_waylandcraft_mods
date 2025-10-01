package dev.evvie.waylandcraft;

import org.lwjgl.opengl.GL33;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;

public class BufferTexture {
	
	private int id = -1;
	public final int width;
	public final int height;
	public final long ptr;
	
	public BufferTexture(long ptr, int width, int height) {
		this.ptr = ptr;
		this.width = width;
		this.height = height;
		this.id = TextureUtil.generateTextureId();
		this.init();
	}
	
	public int getId() {
		return this.id;
	}
	
	private void init() {
		GlStateManager._bindTexture(this.id);
		GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAX_LEVEL, 0);
		GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_LOD, 0);
		GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAX_LOD, 0);
		GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_LOD_BIAS, 0.0f);
		GlStateManager._texImage2D(GL33.GL_TEXTURE_2D, 0, GL33.GL_RGBA8, width, height, 0, GL33.GL_BGRA, GL33.GL_UNSIGNED_INT_8_8_8_8_REV, null);
		
		this.update();
	}
	
	public void update() {
		GlStateManager._bindTexture(this.id);
		GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_NEAREST);
		GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_NEAREST);
		GlStateManager._pixelStore(GL33.GL_UNPACK_ROW_LENGTH, 0);
		GlStateManager._pixelStore(GL33.GL_UNPACK_SKIP_PIXELS, 0);
		GlStateManager._pixelStore(GL33.GL_UNPACK_SKIP_ROWS, 0);
		GlStateManager._pixelStore(GL33.GL_UNPACK_ALIGNMENT, 4);
		GlStateManager._texSubImage2D(GL33.GL_TEXTURE_2D, 0, 0, 0, width, height, GL33.GL_BGRA, GL33.GL_UNSIGNED_INT_8_8_8_8_REV, this.ptr);
	}
	
	public void release() {
		TextureUtil.releaseTextureId(this.id);
	}
	
}
