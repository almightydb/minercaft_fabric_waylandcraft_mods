package dev.evvie.waylandcraft.shared;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.server.level.ServerPlayer;

public class SharedWindowManager {

	private static final Logger LOGGER = LoggerFactory.getLogger("waylandcraft-shared");

	private final Map<Long, SharedWindowEntry> windowRegistry = new ConcurrentHashMap<>();
	private final Map<UUID, ConcurrentHashMap.KeySetView<Long, Boolean>> playerSubscriptions = new ConcurrentHashMap<>();

	public SharedWindowManager() {
		LOGGER.info("SharedWindowManager initialized");
	}

	public SharedWindowEntry registerWindow(long windowHandle, UUID ownerUUID, String windowTitle) {
		SharedWindowEntry entry = new SharedWindowEntry(windowHandle, ownerUUID, windowTitle);
		windowRegistry.put(windowHandle, entry);

		subscribePlayer(ownerUUID, windowHandle);

		LOGGER.info("Window registered: 0x{} by {}", Long.toHexString(windowHandle), ownerUUID);
		return entry;
	}

	public void unregisterWindow(long windowHandle) {
		SharedWindowEntry entry = windowRegistry.remove(windowHandle);
		if (entry != null) {
			playerSubscriptions.values().forEach(set -> set.remove(windowHandle));
			LOGGER.info("Window unregistered: 0x{}", Long.toHexString(windowHandle));
		}
	}

	@Nullable
	public SharedWindowEntry getWindow(long windowHandle) {
		return windowRegistry.get(windowHandle);
	}

	public Collection<SharedWindowEntry> getAllWindows() {
		return Collections.unmodifiableCollection(windowRegistry.values());
	}

	public Collection<Long> getPlayerSubscriptions(UUID playerUUID) {
		return playerSubscriptions.getOrDefault(playerUUID, Collections.emptySet());
	}

	public boolean subscribePlayer(UUID playerUUID, long windowHandle) {
		SharedWindowEntry entry = windowRegistry.get(windowHandle);
		if (entry == null) return false;

		if (!entry.hasPermission(playerUUID, WindowPermission.VIEW)) {
			LOGGER.warn("Player {} denied subscription to window 0x{}", playerUUID, Long.toHexString(windowHandle));
			return false;
		}

		playerSubscriptions.computeIfAbsent(playerUUID, k -> ConcurrentHashMap.newKeySet()).add(windowHandle);
		LOGGER.info("Player {} subscribed to window 0x{}", playerUUID, Long.toHexString(windowHandle));
		return true;
	}

	public void unsubscribePlayer(UUID playerUUID, long windowHandle) {
		ConcurrentHashMap.KeySetView<Long, Boolean> set = playerSubscriptions.get(playerUUID);
		if (set != null) {
			set.remove(windowHandle);
			if (set.isEmpty()) {
				playerSubscriptions.remove(playerUUID);
			}
		}
	}

	public void handlePlayerDisconnect(UUID playerUUID) {
		playerSubscriptions.remove(playerUUID);

		windowRegistry.entrySet().removeIf(entry -> {
			if (entry.getValue().getOwnerUUID().equals(playerUUID)) {
				LOGGER.info("Owner disconnected, unregistering window 0x{}", Long.toHexString(entry.getKey()));
				return true;
			}
			return false;
		});
	}

	public boolean updatePermission(long windowHandle, UUID targetUUID, WindowPermission permission, UUID requesterUUID) {
		SharedWindowEntry entry = windowRegistry.get(windowHandle);
		if (entry == null) return false;

		if (!entry.hasPermission(requesterUUID, WindowPermission.CONTROL)) {
			LOGGER.warn("Player {} denied permission update for window 0x{}", requesterUUID, Long.toHexString(windowHandle));
			return false;
		}

		entry.setPermission(targetUUID, permission);
		LOGGER.info("Permission updated for player {} on window 0x{}: {}", targetUUID, Long.toHexString(windowHandle), permission);

		if (permission == WindowPermission.NONE) {
			unsubscribePlayer(targetUUID, windowHandle);
		}

		return true;
	}

	public boolean updateWindowState(long windowHandle, int x, int y, int width, int height, boolean visible, UUID requesterUUID) {
		SharedWindowEntry entry = windowRegistry.get(windowHandle);
		if (entry == null) return false;

		if (!entry.hasPermission(requesterUUID, WindowPermission.CONTROL)) {
			return false;
		}

		entry.updatePosition(x, y);
		entry.updateSize(width, height);
		entry.setVisible(visible);

		return true;
	}

	public boolean canInteract(UUID playerUUID, long windowHandle) {
		SharedWindowEntry entry = windowRegistry.get(windowHandle);
		if (entry == null) return false;
		return entry.hasPermission(playerUUID, WindowPermission.INTERACT);
	}

	public int getWindowCount() {
		return windowRegistry.size();
	}

	public void clear() {
		windowRegistry.clear();
		playerSubscriptions.clear();
		LOGGER.info("SharedWindowManager cleared");
	}
}
