package dev.evvie.waylandcraft.gui;

import java.awt.Color;
import java.util.List;
import java.util.Random;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.desktop.DesktopEntry;
import dev.evvie.waylandcraft.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

public class AppLauncherScreen extends Screen {
	
	private WaylandCraft wlc;
	private static final ResourceLocation SLOT_THINGY = new ResourceLocation(WaylandCraft.MOD_ID, "textures/gui/slot_thingy.png");
	private static final ResourceLocation SLOT_THINGY_SELECTED = new ResourceLocation(WaylandCraft.MOD_ID, "textures/gui/slot_thingy_selected_overlay.png");
	private int slotWidth = 200;
	private int slotHeight = 32;
	private int slotGap = 4;
	private int elementHeight = slotHeight + slotGap;
	
	public AppLauncherScreen(WaylandCraft wlc) {
		super(Component.literal("App Launcher"));
		
		this.wlc = wlc;
	}
	
	@Override
	protected void init() {
		List<DesktopEntry> entries = wlc.xdgManager.entries();
		Random random = new Random();
		
		int count = 4;
		for(int i = 0; i < count; i++) {
			DesktopEntry entry = null;
			while(!(entry != null && entry.visible)) entry = entries.get(random.nextInt(entries.size()));
			
			this.addRenderableWidget(new AppWidget(entry, width / 2 - slotWidth / 2, height / 2 - (count * elementHeight - slotGap) / 2 + i * elementHeight, slotWidth, slotHeight));
		}
	}
	
	@Override
	public boolean isPauseScreen() {
		return false;
	}
	
	private static class AppWidget extends AbstractWidget {
		
		public final DesktopEntry entry;
		private Font font;
		
		public AppWidget(DesktopEntry entry, int x, int y, int width, int height) {
			super(x, y, width, height, Component.literal(getTitle(entry)));
			this.entry = entry;
			this.font = Minecraft.getInstance().font;
		}
		
		private static String getTitle(DesktopEntry entry) {
			return entry.name != null ? entry.name : entry.appId;
		}
		
		@Override
		protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
			boolean selected = isHoveredOrFocused();
			
			RenderUtils.blit(context, SLOT_THINGY, getX(), getY(), getWidth(), getHeight());
			if(selected) RenderUtils.blit(context, SLOT_THINGY_SELECTED, getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2);
			
			int iconSize = entry.icon != null ? getHeight() - 10 : 0;
			
			context.enableScissor(getX() + 4, getY() + 4, getX() + getWidth() - 4, getY() + getHeight() - 4);
			if(entry.icon != null) RenderUtils.blit(context, entry.icon, getX() + 5, getY() + 5, iconSize, iconSize);
			context.drawString(font, getTitle(entry), getX() + 5 + iconSize + 5, getY() + getHeight() / 2 - font.lineHeight / 2, Color.white.getRGB());
			context.disableScissor();
			
			if(selected) {
				context.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, Color.white.getRGB());
				context.fill(getX() + 4, getY() + 4, getX() + getWidth() - 4, getY() + getHeight() - 4, 1, FastColor.ARGB32.color(128, Color.black.getRGB()));
			}
		}
		
		@Override
		protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
			this.defaultButtonNarrationText(narrationElementOutput);
		}
		
	}
	
}
