package dev.evvie.waylandcraft.bridge;

public class WaylandCraftBridge {
	
	private long instance;
	
	static {
		System.loadLibrary("waylandcraft");
	}
	
	private WaylandCraftBridge(long handle) {
		this.instance = handle;
	}
	
	public static WaylandCraftBridge start() {
		long handle = init();
		return new WaylandCraftBridge(handle);
	}
	
	public void update() {
		update(this.instance);
	}
	
	public String getSocket() {
		return socket(this.instance);
	}
	
	private static native long init();
	private static native void update(long instance);
	private static native String socket(long instance);
	
}
