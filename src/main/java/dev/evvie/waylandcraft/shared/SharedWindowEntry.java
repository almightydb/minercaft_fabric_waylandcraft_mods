package dev.evvie.waylandcraft.shared;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import dev.evvie.waylandcraft.bridge.WLCToplevel;
import net.minecraft.server.level.ServerPlayer;

public class SharedWindowEntry {

	private final long windowHandle;
	private final UUID ownerUUID;
	private final String windowTitle;
	private final long createdAt;

	private final Map<UUID, WindowPermission> permissions = new HashMap<>();

	private int x, y;
	private int width, height;
	private boolean visible = true;

	public SharedWindowEntry(long windowHandle, UUID ownerUUID, String windowTitle) {
		this.windowHandle = windowHandle;
		this.ownerUUID = ownerUUID;
		this.windowTitle = windowTitle;
		this.createdAt = System.currentTimeMillis();

		this.permissions.put(ownerUUID, WindowPermission.CONTROL);
	}

	public long getWindowHandle() {
		return windowHandle;
	}

	public UUID getOwnerUUID() {
		return ownerUUID;
	}

	public String getWindowTitle() {
		return windowTitle;
	}

	public long getCreatedAt() {
		return createdAt;
	}

	public void setPermission(UUID playerUUID, WindowPermission permission) {
		if (permission == WindowPermission.NONE) {
			permissions.remove(playerUUID);
		} else {
			permissions.put(playerUUID, permission);
		}
	}

	public WindowPermission getPermission(UUID playerUUID) {
		return permissions.getOrDefault(playerUUID, WindowPermission.NONE);
	}

	public boolean hasPermission(UUID playerUUID, WindowPermission required) {
		return getPermission(playerUUID).hasPermission(required);
	}

	public Map<UUID, WindowPermission> getAllPermissions() {
		return new HashMap<>(permissions);
	}

	public void updatePosition(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public void updateSize(int width, int height) {
		this.width = width;
		this.height = height;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public int getX() { return x; }
	public int getY() { return y; }
	public int getWidth() { return width; }
	public int getHeight() { return height; }
	public boolean isVisible() { return visible; }

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof SharedWindowEntry)) return false;
		SharedWindowEntry other = (SharedWindowEntry) obj;
		return windowHandle == other.windowHandle;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(windowHandle);
	}
}
