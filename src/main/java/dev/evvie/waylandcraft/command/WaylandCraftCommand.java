package dev.evvie.waylandcraft.command;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.desktop.DesktopEntry;
import dev.evvie.waylandcraft.network.PermissionCommandPayload;
import dev.evvie.waylandcraft.network.SharedWindowClientHandler;
import dev.evvie.waylandcraft.shared.WindowPermission;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

public class WaylandCraftCommand {

	private static final String SHORT_PREFIX = "0x";

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register(WaylandCraftCommand::registerCommands);
	}

	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
		dispatcher.register(
			ClientCommands.literal("wl")
				.then(ClientCommands.literal("list")
					.executes(WaylandCraftCommand::listWindows)
				)
				.then(ClientCommands.literal("give")
					.then(ClientCommands.argument("app_name", StringArgumentType.greedyString())
						.executes(WaylandCraftCommand::giveWindowItem)
					)
				)
				.then(ClientCommands.literal("remove")
					.then(ClientCommands.argument("handle", StringArgumentType.word())
						.executes(WaylandCraftCommand::removeWindowItem)
					)
				)
				.then(ClientCommands.literal("close")
					.then(ClientCommands.argument("handle", StringArgumentType.word())
						.executes(WaylandCraftCommand::closeWindow)
					)
				)
				.then(ClientCommands.literal("share")
					.then(ClientCommands.argument("handle", StringArgumentType.word())
						.executes(WaylandCraftCommand::shareWindow)
					)
				)
				.then(ClientCommands.literal("unshare")
					.then(ClientCommands.argument("handle", StringArgumentType.word())
						.executes(WaylandCraftCommand::unshareWindow)
					)
				)
				.then(ClientCommands.literal("test")
					.executes(WaylandCraftCommand::runTest)
				)
				// 权限管理 - 任意玩家可用
				.then(ClientCommands.literal("perm")
					.then(ClientCommands.literal("list")
						.executes(WaylandCraftCommand::permList)
					)
					.then(ClientCommands.literal("default")
						.then(ClientCommands.argument("permission", StringArgumentType.word())
							.suggests((ctx, builder) -> {
								for (WindowPermission p : WindowPermission.values()) builder.suggest(p.name());
								return builder.buildFuture();
							})
							.executes(WaylandCraftCommand::permDefault)
						)
					)
					.then(ClientCommands.literal("allow")
						.then(ClientCommands.argument("player", StringArgumentType.word())
							.then(ClientCommands.argument("permission", StringArgumentType.word())
								.suggests((ctx, builder) -> {
									for (WindowPermission p : WindowPermission.values()) builder.suggest(p.name());
									return builder.buildFuture();
								})
								.executes(WaylandCraftCommand::permAllow)
							)
						)
					)
					.then(ClientCommands.literal("deny")
						.then(ClientCommands.argument("player", StringArgumentType.word())
							.executes(WaylandCraftCommand::permDeny)
						)
					)
					.then(ClientCommands.literal("remove")
						.then(ClientCommands.argument("player", StringArgumentType.word())
							.executes(WaylandCraftCommand::permRemove)
						)
					)
				)
		);
	}

	// ===== Handle 缩短显示 =====

	private static String shortHex(long handle) {
		return SHORT_PREFIX + Long.toHexString(handle & 0xFFFF);
	}

	private static long parseWindowHandle(String handleStr) {
		handleStr = handleStr.trim();
		try {
			if(handleStr.toLowerCase().startsWith("0x")) {
				return Long.parseLong(handleStr.substring(2), 16);
			}
			return Long.parseLong(handleStr);
		} catch(NumberFormatException e) {
			return -1;
		}
	}

	/**
	 * 按短handle查找窗口 - 先精确匹配，再后缀匹配
	 */
	private static WLCToplevel findToplevelByHandle(FabricClientCommandSource source, String handleStr) {
		WaylandCraft wlc = WaylandCraft.instance;
		if(wlc == null || wlc.bridge == null) {
			source.sendError(Component.literal("WaylandCraft not initialized"));
			return null;
		}

		long handle = parseWindowHandle(handleStr);

		// 先精确匹配
		if(handle >= 0) {
			WLCToplevel t = wlc.bridge.getToplevel(handle);
			if(t != null) return t;
		}

		// 后缀匹配（支持短handle如 0xABCD）
		WLCToplevel[] toplevels = wlc.bridge.getToplevels();
		String hex = handleStr.toLowerCase().replace("0x", "");
		for(WLCToplevel t : toplevels) {
			String fullHex = Long.toHexString(t.getHandle());
			if(fullHex.endsWith(hex)) {
				return t;
			}
		}

		source.sendError(Component.literal("Window not found: " + handleStr));
		return null;
	}

	private static String getWindowDisplayName(WLCToplevel toplevel) {
		WaylandCraft wlc = WaylandCraft.instance;
		if(wlc == null || wlc.xdgManager == null) {
			return toplevel.title != null ? toplevel.title : "Unknown";
		}

		DesktopEntry entry = wlc.xdgManager.forAppId(toplevel.appID);
		if(entry != null && entry.name != null) {
			return entry.name;
		}

		return toplevel.title != null ? toplevel.title : "Unknown";
	}

	private static boolean isWindowShared(long handle) {
		return SharedWindowClientHandler.getRemoteWindow(handle) != null;
	}

	// ===== 窗口命令 =====

	private static int listWindows(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		WaylandCraft wlc = WaylandCraft.instance;

		if(wlc == null || wlc.bridge == null) {
			source.sendError(Component.literal("WaylandCraft not initialized"));
			return 0;
		}

		WLCToplevel[] toplevels = wlc.bridge.getToplevels();
		if(toplevels.length == 0) {
			source.sendFeedback(Component.literal("No windows detected"));
			return 1;
		}

		source.sendFeedback(Component.literal("=== Windows (" + toplevels.length + ") ==="));

		for(WLCToplevel toplevel : toplevels) {
			String hex = shortHex(toplevel.getHandle());
			String displayName = getWindowDisplayName(toplevel);
			boolean shared = isWindowShared(toplevel.getHandle());

			String line = "[" + hex + "] " + displayName;
			if(shared) line += " (shared)";

			source.sendFeedback(Component.literal(line));
		}

		return toplevels.length;
	}

	private static int giveWindowItem(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		String appName = StringArgumentType.getString(context, "app_name").trim();
		WaylandCraft wlc = WaylandCraft.instance;

		if(wlc == null || wlc.bridge == null) {
			source.sendError(Component.literal("WaylandCraft not initialized"));
			return 0;
		}

		WLCToplevel[] toplevels = wlc.bridge.getMappedToplevels();
		WLCToplevel matchedToplevel = null;

		for(WLCToplevel toplevel : toplevels) {
			String displayName = getWindowDisplayName(toplevel);
			if(displayName.toLowerCase().contains(appName.toLowerCase())) {
				matchedToplevel = toplevel;
				break;
			}
			if(toplevel.appID != null && toplevel.appID.toLowerCase().contains(appName.toLowerCase())) {
				matchedToplevel = toplevel;
				break;
			}
		}

		if(matchedToplevel != null) {
			wlc.itemManager.giveItem(matchedToplevel);
			source.sendFeedback(Component.literal("Given window item for: " + getWindowDisplayName(matchedToplevel)));
			return 1;
		}

		if(wlc.xdgManager == null) {
			source.sendError(Component.literal("Desktop entries not loaded yet"));
			return 0;
		}

		List<DesktopEntry> entries = wlc.xdgManager.entries();
		List<DesktopEntry> matches = new ArrayList<>();

		for(DesktopEntry entry : entries) {
			if(entry.name != null && entry.name.toLowerCase().contains(appName.toLowerCase())) {
				matches.add(entry);
			} else if(entry.genericName != null && entry.genericName.toLowerCase().contains(appName.toLowerCase())) {
				matches.add(entry);
			} else if(entry.appId.toLowerCase().contains(appName.toLowerCase())) {
				matches.add(entry);
			}
		}

		if(matches.isEmpty()) {
			source.sendError(Component.literal("No application found matching: " + appName));
			return 0;
		}

		if(matches.size() > 1) {
			source.sendFeedback(Component.literal("Multiple matches found:"));
			for(DesktopEntry entry : matches) {
				source.sendFeedback(Component.literal("  - " + (entry.name != null ? entry.name : entry.appId)));
			}
			return 0;
		}

		DesktopEntry entry = matches.get(0);
		boolean launched = wlc.bridge.execApp(entry.appId);
		if(launched) {
			source.sendFeedback(Component.literal("Launched: " + (entry.name != null ? entry.name : entry.appId)));
		} else {
			source.sendError(Component.literal("Failed to launch: " + entry.appId));
		}
		return launched ? 1 : 0;
	}

	private static int removeWindowItem(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		String handleStr = StringArgumentType.getString(context, "handle");

		// 匹配handle
		WaylandCraft wlc = WaylandCraft.instance;
		if(wlc == null || wlc.bridge == null) {
			source.sendError(Component.literal("WaylandCraft not initialized"));
			return 0;
		}

		long handle = -1;
		// 先尝试精确解析
		long parsed = parseWindowHandle(handleStr);
		if(parsed >= 0) handle = parsed;

		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null) {
			source.sendError(Component.literal("No player available"));
			return 0;
		}

		var inventory = mc.player.getInventory();
		boolean found = false;
		for(int i = 0; i < inventory.getContainerSize(); i++) {
			var stack = inventory.getItem(i);
			Long itemHandle = stack.get(dev.evvie.waylandcraft.item.WindowItem.WINDOW_HANDLE);
			if(itemHandle != null) {
				// 精确匹配或后缀匹配
				if(itemHandle == handle || Long.toHexString(itemHandle).endsWith(handleStr.toLowerCase().replace("0x", ""))) {
					inventory.removeItem(i, 1);
					found = true;
					break;
				}
			}
		}

		if(found) {
			source.sendFeedback(Component.literal("Removed window item [" + handleStr + "]"));
		} else {
			source.sendError(Component.literal("No window item matching " + handleStr));
		}
		return found ? 1 : 0;
	}

	private static int closeWindow(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		String handleStr = StringArgumentType.getString(context, "handle");

		WLCToplevel toplevel = findToplevelByHandle(source, handleStr);
		if(toplevel == null) return 0;

		String displayName = getWindowDisplayName(toplevel);
		if(toplevel.appID != null && !toplevel.appID.isEmpty()) {
			try {
				ProcessBuilder pb = new ProcessBuilder("pkill", "-f", toplevel.appID);
				pb.start();
				source.sendFeedback(Component.literal("Closed: " + displayName));
				return 1;
			} catch(Exception e) {
				source.sendError(Component.literal("Failed: " + e.getMessage()));
				return 0;
			}
		}
		source.sendError(Component.literal("No app ID available"));
		return 0;
	}

	private static int shareWindow(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		String handleStr = StringArgumentType.getString(context, "handle");

		WLCToplevel toplevel = findToplevelByHandle(source, handleStr);
		if(toplevel == null) return 0;

		String displayName = getWindowDisplayName(toplevel);
		WaylandCraft wlc = WaylandCraft.instance;
		if(wlc != null && wlc.windowShareManager != null) {
			wlc.windowShareManager.startSharing(toplevel.getHandle(), displayName);
		} else {
			SharedWindowClientHandler.requestWindowRegister(toplevel.getHandle(), displayName);
		}
		source.sendFeedback(Component.literal("Shared: " + displayName + " [" + shortHex(toplevel.getHandle()) + "]"));
		return 1;
	}

	private static int unshareWindow(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		String handleStr = StringArgumentType.getString(context, "handle");

		WLCToplevel toplevel = findToplevelByHandle(source, handleStr);
		if(toplevel == null) return 0;

		WaylandCraft wlc = WaylandCraft.instance;
		if(wlc != null && wlc.windowShareManager != null) {
			wlc.windowShareManager.stopSharing(toplevel.getHandle());
		} else {
			SharedWindowClientHandler.requestWindowUnregister(toplevel.getHandle());
		}
		source.sendFeedback(Component.literal("Unshared: " + getWindowDisplayName(toplevel)));
		return 1;
	}

	// ===== 权限命令 =====

	// ===== 测试命令 =====
	
	private static int runTest(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		WaylandCraft wlc = WaylandCraft.instance;
		
		if(wlc == null || wlc.bridge == null) {
			source.sendError(Component.literal("WaylandCraft not initialized"));
			return 0;
		}
		
		source.sendFeedback(Component.literal("[TEST] Starting test..."));
		
		// 1. 设置默认权限为VIEW（所有人可看）
		ClientPlayNetworking.send(new PermissionCommandPayload(
			PermissionCommandPayload.ACTION_SET_DEFAULT, "", (byte) WindowPermission.VIEW.ordinal()));
		source.sendFeedback(Component.literal("[TEST] Default permission set to VIEW"));
		
		// 2. 在后台启动 firefox
		new Thread(() -> {
			try {
				// 获取 wayland socket 路径
				String socketPath = wlc.bridge.getSocket();
				if(socketPath == null || socketPath.isEmpty()) {
					sendChat(source, "[TEST] ERROR: No wayland socket");
					return;
				}
				
				sendChat(source, "[TEST] Launching firefox on " + socketPath + "...");
				
				ProcessBuilder pb = new ProcessBuilder("firefox");
				pb.environment().put("WAYLAND_DISPLAY", socketPath);
				pb.environment().put("GDK_BACKEND", "wayland");
				pb.redirectErrorStream(true);
				Process proc = pb.start();
				
				sendChat(source, "[TEST] Firefox launched, waiting for window...");
				
				// 3. 等窗口出现（最多10秒）
				for(int i = 0; i < 20; i++) {
					Thread.sleep(500);
					WLCToplevel[] toplevels = wlc.bridge.getToplevels();
					for(WLCToplevel t : toplevels) {
						if(t.appID != null && (t.appID.toLowerCase().contains("firefox")
								|| t.title != null && t.title.toLowerCase().contains("firefox"))) {
							// 找到了！分享它
							String displayName = getWindowDisplayName(t);
							long handle = t.getHandle();
							
							if(wlc.windowShareManager != null) {
								wlc.windowShareManager.startSharing(handle, displayName);
							} else {
								SharedWindowClientHandler.requestWindowRegister(handle, displayName);
							}
							
							sendChat(source, "[TEST] Shared: " + displayName + " [" + shortHex(handle) + "]");
							sendChat(source, "[TEST] Test ready! Other players should see the window.");
							return;
						}
					}
				}
				
				sendChat(source, "[TEST] ERROR: Firefox window not found after 10s");
			} catch(Exception e) {
				sendChat(source, "[TEST] ERROR: " + e.getMessage());
			}
		}, "wl-test").start();
		
		return 1;
	}
	
	private static void sendChat(FabricClientCommandSource source, String msg) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if(mc.player != null) {
			mc.execute(() -> mc.player.sendSystemMessage(Component.literal(msg)));
		}
	}
	
	private static int permList(CommandContext<FabricClientCommandSource> context) {
		ClientPlayNetworking.send(new PermissionCommandPayload(
			PermissionCommandPayload.ACTION_LIST, "", (byte) 0));
		return 1;
	}

	private static int permDefault(CommandContext<FabricClientCommandSource> context) {
		String permStr = StringArgumentType.getString(context, "permission").toUpperCase();
		WindowPermission perm = parsePerm(permStr, context.getSource());
		if(perm == null) return 0;
		ClientPlayNetworking.send(new PermissionCommandPayload(
			PermissionCommandPayload.ACTION_SET_DEFAULT, "", (byte) perm.ordinal()));
		return 1;
	}

	private static int permAllow(CommandContext<FabricClientCommandSource> context) {
		String playerName = StringArgumentType.getString(context, "player");
		String permStr = StringArgumentType.getString(context, "permission").toUpperCase();
		WindowPermission perm = parsePerm(permStr, context.getSource());
		if(perm == null) return 0;
		ClientPlayNetworking.send(new PermissionCommandPayload(
			PermissionCommandPayload.ACTION_ALLOW, playerName, (byte) perm.ordinal()));
		return 1;
	}

	private static int permDeny(CommandContext<FabricClientCommandSource> context) {
		String playerName = StringArgumentType.getString(context, "player");
		ClientPlayNetworking.send(new PermissionCommandPayload(
			PermissionCommandPayload.ACTION_DENY, playerName, (byte) 0));
		return 1;
	}

	private static int permRemove(CommandContext<FabricClientCommandSource> context) {
		String playerName = StringArgumentType.getString(context, "player");
		ClientPlayNetworking.send(new PermissionCommandPayload(
			PermissionCommandPayload.ACTION_REMOVE, playerName, (byte) 0));
		return 1;
	}

	private static WindowPermission parsePerm(String permStr, FabricClientCommandSource source) {
		try {
			return WindowPermission.valueOf(permStr);
		} catch(IllegalArgumentException e) {
			source.sendError(Component.literal("Invalid permission: " + permStr + " (NONE/VIEW/INTERACT/CONTROL)"));
			return null;
		}
	}
}
