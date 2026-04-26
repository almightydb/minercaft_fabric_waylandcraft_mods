package dev.evvie.waylandcraft.render.model;

import org.joml.Vector2f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.desktop.DesktopEntry;
import dev.evvie.waylandcraft.item.WindowItem;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class WindowSpecialRenderer implements SpecialModelRenderer<ResourceLocation> {
	
	@Override
	public void render(ResourceLocation icon, ItemDisplayContext itemDisplayContext, PoseStack poseStack, MultiBufferSource multiBufferSource, int light, int overlayCoords, boolean foil) {
		poseStack.pushPose();
		poseStack.translate(0, 0, 0.5);
		renderIconItem(poseStack.last(), multiBufferSource, icon, light, overlayCoords);
		poseStack.popPose();
	}
	
	@Override
	public ResourceLocation extractArgument(ItemStack item) {
		WLCToplevel toplevel = WindowItem.getToplevel(item);
		if(toplevel == null) return null;
		
		DesktopEntry entry = WaylandCraft.instance.xdgManager.forAppId(toplevel.appID);
		if(entry == null) return null;
		
		ResourceLocation icon = entry.getIcon();
		return icon;
	}
	
	private void renderIconItem(Pose pose, MultiBufferSource source, ResourceLocation tex, int light, int overlayCoords) {
		VertexConsumer buffer = source.getBuffer(RenderType.itemEntityTranslucentCull(tex));
		Vector3f pos1 = pose.pose().transformPosition(0, 1, 0, new Vector3f());
		Vector3f pos2 = pose.pose().transformPosition(0, 0, 0, new Vector3f());
		Vector3f pos3 = pose.pose().transformPosition(1, 0, 0, new Vector3f());
		Vector3f pos4 = pose.pose().transformPosition(1, 1, 0, new Vector3f());
		
		Vector2f uv1 = new Vector2f(0, 0);
		Vector2f uv2 = new Vector2f(0, 1);
		Vector2f uv3 = new Vector2f(1, 1);
		Vector2f uv4 = new Vector2f(1, 0);
		
		Vector3f normal = pose.transformNormal(0, 0, 1, new Vector3f());
		
		// Front quad
		buffer.addVertex(/* pos */ pos1.x, pos1.y, pos1.z, /* color */ ARGB.white(1.0f), /* uv */ uv1.x, uv1.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
		buffer.addVertex(/* pos */ pos2.x, pos2.y, pos2.z, /* color */ ARGB.white(1.0f), /* uv */ uv2.x, uv2.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
		buffer.addVertex(/* pos */ pos3.x, pos3.y, pos3.z, /* color */ ARGB.white(1.0f), /* uv */ uv3.x, uv3.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
		buffer.addVertex(/* pos */ pos4.x, pos4.y, pos4.z, /* color */ ARGB.white(1.0f), /* uv */ uv4.x, uv4.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
		
		// Back quad
		buffer.addVertex(/* pos */ pos1.x, pos1.y, pos1.z, /* color */ ARGB.white(1.0f), /* uv */ uv1.x, uv1.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
		buffer.addVertex(/* pos */ pos4.x, pos4.y, pos4.z, /* color */ ARGB.white(1.0f), /* uv */ uv4.x, uv4.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
		buffer.addVertex(/* pos */ pos3.x, pos3.y, pos3.z, /* color */ ARGB.white(1.0f), /* uv */ uv3.x, uv3.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
		buffer.addVertex(/* pos */ pos2.x, pos2.y, pos2.z, /* color */ ARGB.white(1.0f), /* uv */ uv2.x, uv2.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
	}
	
	public static record Unbaked() implements SpecialModelRenderer.Unbaked {
		
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());
		
		@Override
		public SpecialModelRenderer<?> bake(EntityModelSet entityModelSet) {
			return new WindowSpecialRenderer();
		}
		
		@Override
		public MapCodec<? extends SpecialModelRenderer.Unbaked> type() {
			return MAP_CODEC;
		}
		
	}
	
}
