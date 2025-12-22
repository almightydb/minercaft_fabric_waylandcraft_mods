package dev.evvie.waylandcraft;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.evvie.waylandcraft.Window.WindowHitResult;
import dev.evvie.waylandcraft.bridge.WLCPopup;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.bridge.WaylandCraftBridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class WaylandCraft implements ModInitializer, ClientModInitializer {
	public static final String MOD_ID = "waylandcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	public static WaylandCraft instance;
	
	public WaylandCraftBridge bridge = null;
	public ArrayList<Window> windows = new ArrayList<Window>();
	public WindowHitResult hitResult = null;
	
	@Override
	public void onInitialize() {
	}
	
	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing WaylandCraft");
		
		instance = this;
		
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			if(bridge == null) {
				bridge = WaylandCraftBridge.start();
				String socket = bridge.getSocket();
				Minecraft.getInstance().gui.getChat().addMessage(Component.literal("Server started on " + socket));
			}
			bridge.update();
			
			for(WLCToplevel toplevel : bridge.getToplevels()) {
				if(!windows.stream().anyMatch((w) -> w.backing == toplevel)) {
					windows.add(new Window(toplevel));
				}
			}
			for(WLCPopup popup : bridge.getPopups()) {
				if(!windows.stream().anyMatch((w) -> w.backing == popup)) {
					windows.add(new Window(popup));
				}
			}
			windows.removeIf((w) -> !w.isAlive());
			
			for(WLCPopup popup : bridge.getPopups()) {
				anchorToParent(popup);
			}
			
			RenderSystem.enableDepthTest();
			windows.forEach((w) -> w.render(context));
			
			sendMotionEvents();
		});
		
		CoreShaderRegistrationCallback.EVENT.register(context -> {
			RenderUtils.registerShaders(context);
		});
	}
	
	private void anchorToParent(WLCPopup popup) {
		Window window = windows.stream().filter((w) -> w.backing == popup).findAny().get();
		Window parent = windows.stream().filter((w) -> w.backing == popup.getParent()).findAny().get();
		
		// If the parent is also a popup, first make it anchor itself
		if(parent.backing instanceof WLCPopup) {
			anchorToParent((WLCPopup) parent.backing);
		}
		
		window.rotate(parent.normal(), parent.down());
		window.moveOrigin(parent.localToWorld(popup.offsetX, popup.offsetY, 0.05));
	}
	
	private void sendMotionEvents() {
		if(hitResult != null) {
			Vec3 coords = hitResult.surfaceLocal;
			Window w = hitResult.target;
			
			if(!w.isAlive()) {
				hitResult = null;
				bridge.sendMotionOutside();
				return;
			}
			
			if(hitResult.dist < 0) {
				bridge.sendMotionOutside();
				return;
			}
			
			for(WLCSurface surface = w.backing.getSurfaceTreeLast(); surface != null; surface = surface.getPrevChild()) {
				Vec3 rel = coords.subtract(surface.xSubpos, surface.ySubpos, 0);
				
				int width = surface.width();
				int height = surface.height();
				
				if(rel.x < 0 || rel.y < 0 || rel.x > width || rel.y > height) {
					continue;
				}
				
				if(bridge.inputRegionContains(surface, rel.x, rel.y)) {
					bridge.sendMotion(surface, rel.x, rel.y);
					return;
				}
			}
		}
		
		bridge.sendMotionOutside();
	}
}