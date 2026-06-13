package dev.evvie.waylandcraft.shared;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

public class PermissionManager {
	
	private static final Logger LOGGER = LoggerFactory.getLogger("waylandcraft-permissions");
	
	// 默认权限设置
	private WindowPermission defaultPermission = WindowPermission.VIEW;
	
	// 玩家全局权限覆盖: playerUUID -> WindowPermission
	private final Map<UUID, WindowPermission> globalOverrides = new ConcurrentHashMap<>();
	
	// 白名单: playerUUID -> WindowPermission
	private final Map<UUID, WindowPermission> whitelist = new ConcurrentHashMap<>();
	
	// 黑名单: playerUUID
	private final ConcurrentHashMap.KeySetView<UUID, Boolean> blacklist = ConcurrentHashMap.newKeySet();
	
	public PermissionManager() {
		LOGGER.info("PermissionManager initialized with default permission: {}", defaultPermission);
	}
	
	/**
	 * 设置默认权限
	 */
	public void setDefaultPermission(WindowPermission permission) {
		this.defaultPermission = permission;
		LOGGER.info("Default permission set to: {}", permission);
	}
	
	/**
	 * 获取默认权限
	 */
	public WindowPermission getDefaultPermission() {
		return defaultPermission;
	}
	
	/**
	 * 添加玩家到白名单
	 */
	public void addToWhitelist(UUID playerUUID, WindowPermission permission) {
		whitelist.put(playerUUID, permission);
		blacklist.remove(playerUUID);
		LOGGER.info("Player {} added to whitelist with permission: {}", playerUUID, permission);
	}
	
	/**
	 * 从白名单移除玩家
	 */
	public void removeFromWhitelist(UUID playerUUID) {
		whitelist.remove(playerUUID);
		LOGGER.info("Player {} removed from whitelist", playerUUID);
	}
	
	/**
	 * 添加玩家到黑名单
	 */
	public void addToBlacklist(UUID playerUUID) {
		blacklist.add(playerUUID);
		whitelist.remove(playerUUID);
		globalOverrides.remove(playerUUID);
		LOGGER.info("Player {} added to blacklist", playerUUID);
	}
	
	/**
	 * 从黑名单移除玩家
	 */
	public void removeFromBlacklist(UUID playerUUID) {
		blacklist.remove(playerUUID);
		LOGGER.info("Player {} removed from blacklist", playerUUID);
	}
	
	/**
	 * 设置玩家全局权限覆盖
	 */
	public void setGlobalOverride(UUID playerUUID, WindowPermission permission) {
		if (permission == WindowPermission.NONE) {
			globalOverrides.remove(playerUUID);
		} else {
			globalOverrides.put(playerUUID, permission);
		}
		LOGGER.info("Global override set for player {}: {}", playerUUID, permission);
	}
	
	/**
	 * 获取玩家的有效权限
	 * 优先级：黑名单 > 白名单 > 全局覆盖 > 默认权限
	 */
	public WindowPermission getEffectivePermission(UUID playerUUID) {
		// 黑名单检查
		if (blacklist.contains(playerUUID)) {
			return WindowPermission.NONE;
		}
		
		// 白名单检查
		WindowPermission whitelistPermission = whitelist.get(playerUUID);
		if (whitelistPermission != null) {
			return whitelistPermission;
		}
		
		// 全局覆盖检查
		WindowPermission globalOverride = globalOverrides.get(playerUUID);
		if (globalOverride != null) {
			return globalOverride;
		}
		
		// 返回默认权限
		return defaultPermission;
	}
	
	/**
	 * 检查玩家是否有指定权限
	 */
	public boolean hasPermission(UUID playerUUID, WindowPermission required) {
		return getEffectivePermission(playerUUID).hasPermission(required);
	}
	
	/**
	 * 检查玩家是否被禁止
	 */
	public boolean isBlacklisted(UUID playerUUID) {
		return blacklist.contains(playerUUID);
	}
	
	/**
	 * 检查玩家是否在白名单中
	 */
	public boolean isWhitelisted(UUID playerUUID) {
		return whitelist.containsKey(playerUUID);
	}
	
	/**
	 * 获取所有白名单玩家
	 */
	public Map<UUID, WindowPermission> getWhitelist() {
		return Map.copyOf(whitelist);
	}
	
	/**
	 * 获取所有黑名单玩家
	 */
	public java.util.Set<UUID> getBlacklist() {
		return java.util.Set.copyOf(blacklist);
	}
	
	/**
	 * 清除所有权限数据
	 */
	public void clear() {
		globalOverrides.clear();
		whitelist.clear();
		blacklist.clear();
		LOGGER.info("PermissionManager cleared");
	}
}
