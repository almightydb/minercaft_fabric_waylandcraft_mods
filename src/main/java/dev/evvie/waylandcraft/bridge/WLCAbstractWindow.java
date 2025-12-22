package dev.evvie.waylandcraft.bridge;

import org.jetbrains.annotations.Nullable;

public abstract class WLCAbstractWindow {
	
	// Set to zero when this window no longer exists
	private long handle;
	
	@Nullable
	protected WLCSurface surface;
	
	@Nullable
	protected WLCSurface lastChild;
	
	public WLCAbstractWindow(long handle) {
		this.handle = handle;
	}
	
	protected long getHandle() {
		return this.handle;
	}
	
	protected long takeHandle() {
		long old = this.handle;
		this.handle = 0;
		return old;
	}
	
	public boolean isAlive() {
		return handle != 0;
	}
	
	public WLCSurface getSurfaceTree() {
		return this.surface;
	}
	
	public WLCSurface getSurfaceTreeLast() {
		return this.lastChild;
	}
	
}
