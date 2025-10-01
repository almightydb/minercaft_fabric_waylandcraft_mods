package dev.evvie.waylandcraft.bridge;

import java.util.ArrayList;

import org.apache.commons.lang3.ArrayUtils;

import dev.evvie.waylandcraft.BufferTexture;

public class WaylandCraftBridge {
	
	private long instance;
	private ArrayList<WLCToplevel> toplevels = new ArrayList<WLCToplevel>();
	
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
	
	private WLCToplevel getOrCreate(long handle) {
		for(WLCToplevel toplevel : toplevels) {
			if(toplevel.getHandle() == handle) return toplevel;
		}
		WLCToplevel toplevel = new WLCToplevel(handle);
		toplevels.add(toplevel);
		return toplevel;
	}
	
	private void deleteNonExisting(long[] remainingHandles) {
		toplevels.removeIf((toplevel) -> !ArrayUtils.contains(remainingHandles, toplevel.takeHandle()));
	}
	
	public void update() {
		update(this.instance);
		
		long[] toplevels = toplevels(instance);
		deleteNonExisting(toplevels);
		
		for(long handle : toplevels) {
			WLCToplevel toplevel = getOrCreate(handle);
			
			WLCSurface root = toplevel.getSurfaceTree();
			BufferTexture buf;
			if(root != null && (buf = root.getBuffer()) != null) {
				buf.release();
			}
			
			root = surfaceTree(this.instance, handle);
			toplevel.setSurface(root);
		}
	}
	
	public WLCToplevel[] getToplevels() {
		return toplevels.toArray(new WLCToplevel[toplevels.size()]);
	}
	
	public String getSocket() {
		return socket(this.instance);
	}
	
	private static native long init();
	private static native void update(long instance);
	private static native String socket(long instance);
	
	private static native long[] toplevels(long instance);
	private static native WLCSurface surfaceTree(long instance, long handle);
	
}
