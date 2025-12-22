package dev.evvie.waylandcraft.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.Window.WindowHitResult;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import net.minecraft.client.KeyboardHandler;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	
	@Inject(method = "keyPress", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;getKey(II)Lcom/mojang/blaze3d/platform/InputConstants$Key;"), cancellable = true)
	public void onPress(long windowHandle, int key, int scancode, int action, int modifiers, CallbackInfo info) {
		if(key == GLFW.GLFW_KEY_F7) {
			handleCaptureKey(action);
			info.cancel();
			return;
		}
		
		if(!WaylandCraft.instance.keyboardCaptured) return;
		
		info.cancel();
		
		/* This code just completely naively assumes that the scancode received by GLFW
		 * is also the correct matching Wayland scancode for the default XKBConfig.
		 * For X11 and Wayland hosts, this is a huge hack but should mostly work for now
		 */
		if(action == GLFW.GLFW_PRESS) {
			WaylandCraft.LOGGER.info("PRESSED KEY: " + scancode);
			WaylandCraft.instance.bridge.pressKey(scancode);
		}
		else if(action == GLFW.GLFW_RELEASE) {
			WaylandCraft.LOGGER.info("RELEASED KEY: " + scancode);
			WaylandCraft.instance.bridge.releaseKey(scancode);
		}
	}
	
	private void handleCaptureKey(int action) {
		if(action != GLFW.GLFW_PRESS) {
			return;
		}
		
		WindowHitResult result = WaylandCraft.instance.hitResult;
		if(result == null || result.dist < 0) {
			WaylandCraft.instance.bridge.focusSurface(null);
			WaylandCraft.instance.keyboardCaptured = false;
			return;
		}
		
		WLCSurface surface = result.target.backing.getSurfaceTree();
		if(!surface.isAlive()) return;
		
		WaylandCraft.instance.bridge.focusSurface(surface);
		WaylandCraft.instance.keyboardCaptured = !WaylandCraft.instance.keyboardCaptured;
	}
	
}
