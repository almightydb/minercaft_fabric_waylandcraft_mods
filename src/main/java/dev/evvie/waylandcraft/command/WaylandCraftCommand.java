package dev.evvie.waylandcraft.command;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.desktop.DesktopEntry;
import dev.evvie.waylandcraft.network.SharedWindowClientHandler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

public class WaylandCraftCommand {

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
		);
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

	private static WLCToplevel findToplevelByHandle(FabricClientCommandSource source, String handleStr) {
		long handle = parseWindowHandle(handleStr);
		if(handle < 0) {
			source.sendError(Component.literal("Invalid handle: " + handleStr));
			return null;
		}

		WaylandCraft wlc = WaylandCraft.instance;
		if(wlc == null || wlc.bridge == null) {
			source.sendError(Component.literal("WaylandCraft not initialized"));
			return null;
		}

		WLCToplevel toplevel = wlc.bridge.getToplevel(handle);
		if(toplevel == null) {
			source.sendError(Component.literal("Window not found: 0x" + Long.toHexString(handle)));
			return null;
		}

		return toplevel;
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

		source.sendFeedback(Component.literal("=== Wayland Windows (" + toplevels.length + ") ==="));

		for(WLCToplevel toplevel : toplevels) {
			String hexHandle = "0x" + Long.toHexString(toplevel.getHandle());
			String displayName = getWindowDisplayName(toplevel);
			boolean shared = isWindowShared(toplevel.getHandle());

			String line = "[" + hexHandle + "] " + displayName;
			if(toplevel.appID != null && !toplevel.appID.equals(displayName)) {
				line += " (" + toplevel.appID + ")";
			}
			if(shared) {
				line += " (shared)";
			}

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

		// Check if a window with matching name/appId is already running
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

		// Search desktop entries and launch
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
			source.sendFeedback(Component.literal("Multiple matches found, please be more specific:"));
			for(DesktopEntry entry : matches) {
				String entryName = entry.name != null ? entry.name : entry.appId;
				source.sendFeedback(Component.literal("  - " + entryName + " (" + entry.appId + ")"));
			}
			return 0;
		}

		DesktopEntry entry = matches.get(0);
		boolean launched = wlc.bridge.execApp(entry.appId);
		if(launched) {
			String entryName = entry.name != null ? entry.name : entry.appId;
			source.sendFeedback(Component.literal("Launched: " + entryName + ". Window item will be given when ready."));
		} else {
			source.sendError(Component.literal("Failed to launch: " + entry.appId));
		}

		return launched ? 1 : 0;
	}

	private static int removeWindowItem(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		String handleStr = StringArgumentType.getString(context, "handle");
		long handle = parseWindowHandle(handleStr);

		if(handle < 0) {
			source.sendError(Component.literal("Invalid handle: " + handleStr));
			return 0;
		}

		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null) {
			source.sendError(Component.literal("No player available"));
			return 0;
		}

		// Search inventory for window item with matching handle
		var inventory = mc.player.getInventory();
		boolean found = false;
		for(int i = 0; i < inventory.getContainerSize(); i++) {
			var stack = inventory.getItem(i);
			Long itemHandle = stack.get(dev.evvie.waylandcraft.item.WindowItem.WINDOW_HANDLE);
			if(itemHandle != null && itemHandle == handle) {
				inventory.removeItem(i, 1);
				found = true;
				break;
			}
		}

		if(found) {
			source.sendFeedback(Component.literal("Removed window item [0x" + Long.toHexString(handle) + "] from inventory"));
		} else {
			source.sendError(Component.literal("No window item with handle 0x" + Long.toHexString(handle) + " found in inventory"));
		}

		return found ? 1 : 0;
	}

	private static int closeWindow(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		String handleStr = StringArgumentType.getString(context, "handle");

		WLCToplevel toplevel = findToplevelByHandle(source, handleStr);
		if(toplevel == null) return 0;

		WaylandCraft wlc = WaylandCraft.instance;
		String displayName = getWindowDisplayName(toplevel);

		// Try to kill the process via appID
		if(toplevel.appID != null && !toplevel.appID.isEmpty()) {
			try {
				ProcessBuilder pb = new ProcessBuilder("pkill", "-f", toplevel.appID);
				pb.start();
				source.sendFeedback(Component.literal("Sent close signal to: " + displayName + " (" + toplevel.appID + ")"));
				return 1;
			} catch(Exception e) {
				source.sendError(Component.literal("Failed to close window: " + e.getMessage()));
				return 0;
			}
		}

		source.sendError(Component.literal("Cannot close window: no app ID available"));
		return 0;
	}

	private static int shareWindow(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		String handleStr = StringArgumentType.getString(context, "handle");

		WLCToplevel toplevel = findToplevelByHandle(source, handleStr);
		if(toplevel == null) return 0;

		String displayName = getWindowDisplayName(toplevel);

		SharedWindowClientHandler.requestWindowRegister(toplevel.getHandle(), displayName);
		source.sendFeedback(Component.literal("Shared window: " + displayName + " [0x" + Long.toHexString(toplevel.getHandle()) + "]"));

		return 1;
	}

	private static int unshareWindow(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		String handleStr = StringArgumentType.getString(context, "handle");

		long handle = parseWindowHandle(handleStr);
		if(handle < 0) {
			source.sendError(Component.literal("Invalid handle: " + handleStr));
			return 0;
		}

		SharedWindowClientHandler.requestWindowUnregister(handle);
		source.sendFeedback(Component.literal("Stopped sharing window [0x" + Long.toHexString(handle) + "]"));

		return 1;
	}

}
