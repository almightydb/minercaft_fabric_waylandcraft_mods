package dev.evvie.waylandcraft.command;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.shared.PermissionManager;
import dev.evvie.waylandcraft.shared.WindowPermission;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;

public class PermissionCommand {

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal("waylandcraft")
				.requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
				.then(Commands.literal("permission")
					.then(Commands.literal("default")
						.then(Commands.argument("permission", StringArgumentType.word())
							.suggests(PermissionCommand::suggestPermissions)
							.executes(PermissionCommand::setDefaultPermission)
						)
					)
					.then(Commands.literal("allow")
						.then(Commands.argument("player", GameProfileArgument.gameProfile())
							.then(Commands.argument("permission", StringArgumentType.word())
								.suggests(PermissionCommand::suggestPermissions)
								.executes(PermissionCommand::allowPlayer)
							)
						)
					)
					.then(Commands.literal("deny")
						.then(Commands.argument("player", GameProfileArgument.gameProfile())
							.executes(PermissionCommand::denyPlayer)
						)
					)
					.then(Commands.literal("list")
						.executes(PermissionCommand::listPermissions)
					)
				)
		);
	}

	private static CompletableFuture<Suggestions> suggestPermissions(
			CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
		for (WindowPermission perm : WindowPermission.values()) {
			builder.suggest(perm.name());
		}
		return builder.buildFuture();
	}

	private static @Nullable WindowPermission parsePermission(String permStr, CommandContext<CommandSourceStack> context) {
		try {
			return WindowPermission.valueOf(permStr.toUpperCase());
		} catch (IllegalArgumentException e) {
			context.getSource().sendFailure(
				Component.literal("Invalid permission: " + permStr + ". Valid values: NONE, VIEW, INTERACT, CONTROL")
			);
			return null;
		}
	}

	private static int setDefaultPermission(CommandContext<CommandSourceStack> context) {
		String permStr = StringArgumentType.getString(context, "permission");
		WindowPermission permission = parsePermission(permStr, context);
		if (permission == null) return 0;

		PermissionManager pm = WaylandCraftCommon.instance.permissionManager;
		pm.setDefaultPermission(permission);

		WindowPermission finalPerm = permission;
		context.getSource().sendSuccess(() ->
			Component.literal("Default permission set to " + finalPerm.name()), false);
		return 1;
	}

	private static int allowPlayer(CommandContext<CommandSourceStack> context) {
		String permStr = StringArgumentType.getString(context, "permission");
		WindowPermission permission = parsePermission(permStr, context);
		if (permission == null) return 0;

		PermissionManager pm = WaylandCraftCommon.instance.permissionManager;

		try {
			Collection<NameAndId> profiles = GameProfileArgument.getGameProfiles(context, "player");
			for (NameAndId profile : profiles) {
				UUID playerUUID = profile.id();
				String playerName = profile.name();
				pm.addToWhitelist(playerUUID, permission);

				WindowPermission finalPerm = permission;
				context.getSource().sendSuccess(() ->
					Component.literal("Added " + playerName + " to whitelist with permission " + finalPerm.name()), false);
			}
			return profiles.size();
		} catch (Exception e) {
			context.getSource().sendFailure(Component.literal("Failed to resolve player: " + e.getMessage()));
			return 0;
		}
	}

	private static int denyPlayer(CommandContext<CommandSourceStack> context) {
		PermissionManager pm = WaylandCraftCommon.instance.permissionManager;

		try {
			Collection<NameAndId> profiles = GameProfileArgument.getGameProfiles(context, "player");
			for (NameAndId profile : profiles) {
				UUID playerUUID = profile.id();
				String playerName = profile.name();
				pm.addToBlacklist(playerUUID);

				context.getSource().sendSuccess(() ->
					Component.literal("Added " + playerName + " to blacklist"), false);
			}
			return profiles.size();
		} catch (Exception e) {
			context.getSource().sendFailure(Component.literal("Failed to resolve player: " + e.getMessage()));
			return 0;
		}
	}

	private static int listPermissions(CommandContext<CommandSourceStack> context) {
		PermissionManager pm = WaylandCraftCommon.instance.permissionManager;

		context.getSource().sendSuccess(() ->
			Component.literal("=== Window Permissions ==="), false);
		context.getSource().sendSuccess(() ->
			Component.literal("Default: " + pm.getDefaultPermission().name()), false);

		// Show whitelist
		Map<UUID, WindowPermission> whitelist = pm.getWhitelist();
		if (!whitelist.isEmpty()) {
			context.getSource().sendSuccess(() ->
				Component.literal("--- Whitelist ---"), false);
			for (Map.Entry<UUID, WindowPermission> entry : whitelist.entrySet()) {
				String playerName = resolvePlayerName(context.getSource(), entry.getKey());
				WindowPermission perm = entry.getValue();
				context.getSource().sendSuccess(() ->
					Component.literal("  " + playerName + ": " + perm.name()), false);
			}
		}

		// Show blacklist
		Set<UUID> blacklist = pm.getBlacklist();
		if (!blacklist.isEmpty()) {
			context.getSource().sendSuccess(() ->
				Component.literal("--- Blacklist ---"), false);
			for (UUID uuid : blacklist) {
				String playerName = resolvePlayerName(context.getSource(), uuid);
				context.getSource().sendSuccess(() ->
					Component.literal("  " + playerName), false);
			}
		}

		if (whitelist.isEmpty() && blacklist.isEmpty()) {
			context.getSource().sendSuccess(() ->
				Component.literal("No player-specific permissions set."), false);
		}

		return 1;
	}

	private static String resolvePlayerName(CommandSourceStack source, UUID uuid) {
		ServerPlayer player = source.getServer().getPlayerList().getPlayer(uuid);
		if (player != null) {
			return player.getName().getString();
		}
		return uuid.toString();
	}
}
