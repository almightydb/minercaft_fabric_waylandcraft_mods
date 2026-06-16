package dev.evvie.waylandcraft.render;

import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.evvie.waylandcraft.WindowDisplay;
import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.WLCAbstractWindow;
import dev.evvie.waylandcraft.math.WorldPlane;
import dev.evvie.waylandcraft.shared.RemoteWindowRenderer;
import dev.evvie.waylandcraft.shared.WindowPermission;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * 共享窗口显示类
 * 用于显示远程玩家共享的窗口
 */
public class SharedWindowDisplay {
	
	private final long windowHandle;
	private final String windowTitle;
	private final String ownerName;
	
	// 窗口位置和方向
	private Vec3 pivot = new Vec3(0, 0, 0);
	private Vec3 normal = new Vec3(0, 0, 1);
	private Vec3 down = new Vec3(0, -1, 0);
	
	// 窗口尺寸
	private int width;
	private int height;
	
	// 权限
	private WindowPermission permission = WindowPermission.VIEW;
	
	// 渲染器
	private final RemoteWindowRenderer renderer;
	
	// 是否可见
	private boolean visible = true;
	
	// 锚定距离
	public double anchorDistance = 2.0;
	
	public SharedWindowDisplay(long windowHandle, String windowTitle, String ownerName, RemoteWindowRenderer renderer) {
		this.windowHandle = windowHandle;
		this.windowTitle = windowTitle;
		this.ownerName = ownerName;
		this.renderer = renderer;
	}
	
	/**
	 * 获取窗口句柄
	 */
	public long getWindowHandle() {
		return windowHandle;
	}
	
	/**
	 * 获取窗口标题
	 */
	public String getWindowTitle() {
		return windowTitle;
	}
	
	/**
	 * 获取所有者名称
	 */
	public String getOwnerName() {
		return ownerName;
	}
	
	/**
	 * 设置权限
	 */
	public void setPermission(WindowPermission permission) {
		this.permission = permission;
	}
	
	/**
	 * 获取权限
	 */
	public WindowPermission getPermission() {
		return permission;
	}
	
	/**
	 * 设置可见性
	 */
	public void setVisible(boolean visible) {
		this.visible = visible;
	}
	
	/**
	 * 是否可见
	 */
	public boolean isVisible() {
		return visible;
	}
	
	/**
	 * 更新窗口位置
	 */
	public void updatePosition(int x, int y) {
		// 将屏幕坐标转换为世界坐标
		// 这里简化处理，实际需要根据窗口朝向计算
	}
	
	/**
	 * 设置窗口变换（来自发送者的原始WindowDisplay的pivot/normal/down）
	 */
	public void setTransform(Vec3 pivot, Vec3 normal, Vec3 down) {
		this.pivot = pivot;
		this.normal = normal;
		this.down = down;
	}
	
	/**
	 * 设置所有者世界坐标（窗口显示在该位置）— 兼容旧接口
	 */
	public void setWorldPosition(double x, double y, double z) {
		this.pivot = new Vec3(x, y, z);
	}
	
	/**
	 * 更新窗口大小
	 */
	public void updateSize(int width, int height) {
		this.width = width;
		this.height = height;
	}
	
	/**
	 * 获取像素缩放比例 — 与WindowDisplay一致，读取用户设置
	 */
	public float pixelScale() {
		return 1.0f / WaylandCraft.instance.settings.getPixelsPerBlock();
	}
	
	/**
	 * 获取局部X轴方向
	 */
	public Vec3 localX() {
		return normal.cross(down).scale(pixelScale());
	}
	
	/**
	 * 获取局部Y轴方向
	 */
	public Vec3 localY() {
		return down.scale(pixelScale());
	}
	
	/**
	 * 获取原点位置
	 */
	public Vec3 origin() {
		return pivot.add(localX().scale(-width/2)).add(localY().scale(-height/2));
	}
	
	/**
	 * 获取世界平面
	 */
	public WorldPlane getPlane() {
		return new WorldPlane(origin(), localX(), localY(), normal);
	}
	
	/**
	 * 旋转窗口
	 */
	public void rotate(Vec3 normal, Vec3 down) {
		this.normal = normal;
		this.down = down;
	}
	
	/**
	 * 移动原点
	 */
	public void moveOrigin(Vec3 pos) {
		pivot = pos.add(localX().scale(width/2)).add(localY().scale(height/2));
	}
	
	/**
	 * 锚定到位置和视角
	 */
	public void anchorToPosView(Vec3 pos, Vec3 look, Vec3 up) {
		this.pivot = pos.add(look.scale(this.anchorDistance));
		this.rotate(look.reverse(), up.reverse());
	}
	
	/**
	 * 锚定到相机
	 */
	public void anchorToCamera(Camera camera) {
		anchorToPosView(camera.position(), new Vec3(camera.forwardVector()), new Vec3(camera.upVector()));
	}
	
	/**
	 * 调整锚定距离
	 */
	public void adjustAnchorDistance(double delta) {
		this.anchorDistance = Math.clamp(this.anchorDistance + delta * 0.1d, 0.5d, 20d);
	}
	
	/**
	 * 渲染共享窗口 — 使用与WindowDisplay.render相同的WINDOW_CUTOUT管线
	 * 始终面向相机（翻转normal/down避免看到镜像背面）
	 */
	public void render(LevelRenderContext ctx) {
		if(!visible) return;
		if(!renderer.hasTexture(windowHandle)) return;
		
		Identifier textureLocation = renderer.getTextureLocation_obj(windowHandle);
		if(textureLocation == null) return;
		
		// 从renderer获取实际纹理尺寸（首次渲染时本地width/height可能为0）
		int renderWidth = this.width;
		int renderHeight = this.height;
		if(renderWidth <= 0 || renderHeight <= 0) {
			int[] dims = renderer.getTextureDimensions(windowHandle);
			if(dims != null && dims[0] > 0 && dims[1] > 0) {
				renderWidth = dims[0];
				renderHeight = dims[1];
			} else {
				return;
			}
		}
		
		// 获取渲染所需的各种向量 — 与WindowDisplay.render一致
		Vec3 localX = localX();
		Vec3 localY = localY();
		
		Vec3 cameraPos = ctx.levelState().cameraRenderState.pos;
		Vec3 originRel = origin().subtract(cameraPos);
		
		// 检查相机是否在窗口背面 — 如果是，翻转normal/down让窗口面向相机
		Vec3 cameraDir = cameraPos.subtract(pivot).normalize();
		boolean flipped = cameraDir.dot(normal) > 0;
		
		if(flipped) {
			// 翻转法线和向下向量，让窗口面向相机
			// right = normal×down → (-normal)×(-down) = normal×down，不变
			// 但localX和localY方向反转，需要交换tl↔tr来纠正UV镜像
			localX = normal.reverse().cross(down.reverse()).scale(pixelScale());
			localY = down.reverse().scale(pixelScale());
		}
		
		// 计算四个角的位置
		Vec3 tl = new Vec3(0, 0, 0);
		Vec3 bl = localY.scale(renderHeight);
		Vec3 br = bl.add(localX.scale(renderWidth));
		Vec3 tr = localX.scale(renderWidth);
		
		// 渲染纹理 — 使用WINDOW_CUTOUT管线（与renderFramebuffer相同的着色器和管线）
		PoseStack poseStack = ctx.poseStack();
		SubmitNodeCollector collector = ctx.submitNodeCollector();
		poseStack.pushPose();
		poseStack.translate(originRel.x, originRel.y, originRel.z);
		
		// 前面 — 使用WINDOW_CUTOUT渲染类型（双面渲染，与renderFramebuffer一致）
		RenderType cutoutType = RenderUtils.WINDOW_CUTOUT.apply(textureLocation);
		
		if(!flipped) {
			// 正常朝向：标准UV映射
			collector.submitCustomGeometry(poseStack, cutoutType,
				new RenderUtils.FramebufferRenderInstance(tl, bl, br, tr, false));
		} else {
			// 翻转朝向：交换左右顶点纠正水平镜像
			collector.submitCustomGeometry(poseStack, cutoutType,
				new RenderUtils.FramebufferRenderInstance(tr, br, bl, tl, false));
		}
		
		// 背面 — 使用WINDOW_BACKGROUND_CUTOUT渲染类型
		RenderType bgType = RenderUtils.WINDOW_BACKGROUND_CUTOUT.apply(textureLocation);
		collector.submitCustomGeometry(poseStack, bgType,
			new RenderUtils.FramebufferRenderInstance(tl, bl, br, tr, true));
		
		poseStack.popPose();
	}
	
	/**
	 * 窗口是否有效
	 */
	public boolean isValid() {
		return visible && renderer.hasTexture(windowHandle);
	}
	
	@Override
	public boolean equals(Object obj) {
		if(this == obj) return true;
		if(!(obj instanceof SharedWindowDisplay)) return false;
		SharedWindowDisplay other = (SharedWindowDisplay) obj;
		return windowHandle == other.windowHandle;
	}
	
	@Override
	public int hashCode() {
		return Long.hashCode(windowHandle);
	}
}
