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
					.then(ClientCommands.literal("windows")
						.executes(WaylandCraftCommand::listWindows)
					)
					.then(ClientCommands.literal("apps")
						.executes(WaylandCraftCommand::listApps)
					)
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
			source.sendError(Component.literal("§c✘ WaylandCraft not initialized§r"));
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

		source.sendError(Component.literal("§c✘ Window not found: " + handleStr + "§r"));
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
			source.sendError(Component.literal("§c✘ WaylandCraft not initialized§r"));
			return 0;
		}

		WLCToplevel[] toplevels = wlc.bridge.getToplevels();
		source.sendFeedback(Component.literal("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
		source.sendFeedback(Component.literal("§6 §lWaylandCraft §r§7 Windows §7(" + toplevels.length + " total)§r"));
		source.sendFeedback(Component.literal("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));

		if(toplevels.length == 0) {
			source.sendFeedback(Component.literal(" §7No windows detected§r"));
		} else {
			for(WLCToplevel toplevel : toplevels) {
				String hex = shortHex(toplevel.getHandle());
				String displayName = getWindowDisplayName(toplevel);
				boolean shared = isWindowShared(toplevel.getHandle());

				String line = " §e" + hex + "§r §f" + displayName + "§r";
				if(toplevel.appID != null && !toplevel.appID.isEmpty()) {
					line += " §7- §8" + toplevel.appID + "§r";
				}
				if(shared) line += " §a✔ shared§r";

				source.sendFeedback(Component.literal(line));
			}
		}

		source.sendFeedback(Component.literal("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
		return toplevels.length;
	}

	private static int listApps(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		WaylandCraft wlc = WaylandCraft.instance;

		if(wlc == null || wlc.xdgManager == null) {
			source.sendError(Component.literal("§c✘ Desktop entries not loaded§r"));
			return 0;
		}

		List<DesktopEntry> entries = wlc.xdgManager.entries();
		List<DesktopEntry> visible = new ArrayList<>();
		for(DesktopEntry e : entries) {
			if(e.visible && e.name != null) visible.add(e);
		}

		source.sendFeedback(Component.literal("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
		source.sendFeedback(Component.literal("§6 §lWaylandCraft §r§7 Apps §7(" + visible.size() + " total)§r"));
		source.sendFeedback(Component.literal("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));

		for(DesktopEntry entry : visible) {
			String name = entry.name;
			String desc = entry.genericName != null ? entry.genericName : "";
			String line = " §b" + name + "§r";
			if(!desc.isEmpty()) line += " §7- §8" + desc + "§r";
			source.sendFeedback(Component.literal(line));
		}

		source.sendFeedback(Component.literal("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
		source.sendFeedback(Component.literal(" §7Use §e/wl give <name>§7 to launch§r"));
		return 1;
	}

	private static int giveWindowItem(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		String appName = StringArgumentType.getString(context, "app_name").trim();
		WaylandCraft wlc = WaylandCraft.instance;

		if(wlc == null || wlc.bridge == null) {
			source.sendError(Component.literal("§c✘ WaylandCraft not initialized§r"));
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
			source.sendFeedback(Component.literal("§a✔ Given window: §f" + getWindowDisplayName(matchedToplevel) + "§r"));
			return 1;
		}

		if(wlc.xdgManager == null) {
			source.sendError(Component.literal("§c✘ Desktop entries not loaded§r"));
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
			source.sendError(Component.literal("§c✘ No application found: " + appName + "§r"));
			return 0;
		}

		if(matches.size() > 1) {
			source.sendFeedback(Component.literal("§eMultiple matches:§r"));
			for(DesktopEntry entry : matches) {
				source.sendFeedback(Component.literal("  §b- " + (entry.name != null ? entry.name : entry.appId) + "§r"));
			}
			return 0;
		}

		DesktopEntry entry = matches.get(0);
		boolean launched = launchApp(wlc, entry);
		if(launched) {
			source.sendFeedback(Component.literal("§a✔ Launched: §f" + (entry.name != null ? entry.name : entry.appId) + "§r"));
		} else {
			source.sendError(Component.literal("§c✘ Failed to launch: " + entry.appId + "§r"));
		}
		return launched ? 1 : 0;
	}

	/**
	 * 通过ProcessBuilder启动应用，设置Wayland环境变量
	 * 比原生execApp更可靠，支持GTK/Qt/Firefox等需要特定环境变量的应用
	 */
	private static boolean launchApp(WaylandCraft wlc, DesktopEntry entry) {
		if(entry.exec == null || entry.exec.isEmpty()) {
			return wlc.bridge.execApp(entry.appId);
		}
		try {
			// 清理exec命令 - 移除 .desktop 字段代码 (%f %F %u %U 等)
			String exec = entry.exec.replaceAll("%[fFuUdDnNickvm]", "").trim();
			String[] parts = exec.split("\\s+");

			String socketPath = wlc.bridge.getSocket();
			if(socketPath == null || socketPath.isEmpty()) {
				return wlc.bridge.execApp(entry.appId);
			}

			ProcessBuilder pb = new ProcessBuilder(parts);
			pb.environment().put("WAYLAND_DISPLAY", socketPath);
			pb.environment().put("GDK_BACKEND", "wayland");
			pb.environment().put("QT_QPA_PLATFORM", "wayland");
			pb.environment().put("MOZ_ENABLE_WAYLAND", "1");
			pb.environment().put("DISPLAY", ""); // 禁止X11回退
			pb.redirectErrorStream(true);
			pb.start();
			return true;
		} catch(Exception e) {
			// 回退到原生exec
			return wlc.bridge.execApp(entry.appId);
		}
	}

	private static int removeWindowItem(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		String handleStr = StringArgumentType.getString(context, "handle");

		WaylandCraft wlc = WaylandCraft.instance;
		if(wlc == null || wlc.bridge == null) {
			source.sendError(Component.literal("§c✘ WaylandCraft not initialized§r"));
			return 0;
		}

		long handle = parseWindowHandle(handleStr);

		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null) {
			source.sendError(Component.literal("§c✘ No player available§r"));
			return 0;
		}

		var inventory = mc.player.getInventory();
		boolean found = false;
		for(int i = 0; i < inventory.getContainerSize(); i++) {
			var stack = inventory.getItem(i);
			Long itemHandle = stack.get(dev.evvie.waylandcraft.item.WindowItem.WINDOW_HANDLE);
			if(itemHandle != null) {
				if(itemHandle == handle || Long.toHexString(itemHandle).endsWith(handleStr.toLowerCase().replace("0x", ""))) {
					inventory.removeItem(i, 1);
					found = true;
					break;
				}
			}
		}

		if(found) {
			source.sendFeedback(Component.literal("§a✔ Removed window §e" + handleStr + "§r"));
		} else {
			source.sendError(Component.literal("§c✘ No window item: " + handleStr + "§r"));
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
				source.sendFeedback(Component.literal("§a✔ Closed: §f" + displayName + "§r"));
				return 1;
			} catch(Exception e) {
				source.sendError(Component.literal("§c✘ Failed: " + e.getMessage() + "§r"));
				return 0;
			}
		}
		source.sendError(Component.literal("§c✘ No app ID available§r"));
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
		source.sendFeedback(Component.literal("§a✔ Shared: §f" + displayName + "§r §e" + shortHex(toplevel.getHandle()) + "§r"));
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
		source.sendFeedback(Component.literal("§a✔ Unshared: §f" + getWindowDisplayName(toplevel) + "§r"));
		return 1;
	}

	// ===== 权限命令 =====

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
			source.sendError(Component.literal("§c✘ Invalid permission: " + permStr + " (NONE/VIEW/INTERACT/CONTROL)§r"));
			return null;
		}
	}
}
