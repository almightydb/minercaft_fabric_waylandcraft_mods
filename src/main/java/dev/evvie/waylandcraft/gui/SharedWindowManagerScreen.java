package dev.evvie.waylandcraft.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.network.SharedWindowClientHandler;
import dev.evvie.waylandcraft.network.SharedWindowClientHandler.WindowInfo;
import dev.evvie.waylandcraft.shared.WindowPermission;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 共享窗口管理界面
 * 显示可用的共享窗口列表，允许玩家订阅和管理权限
 */
public class SharedWindowManagerScreen extends Screen {
	
	private static final int LIST_WIDTH = 300;
	private static final int LIST_HEIGHT = 200;
	private static final int BUTTON_WIDTH = 100;
	private static final int BUTTON_HEIGHT = 20;
	
	private final WaylandCraft mod;
	
	// 窗口列表
	private List<WindowInfo> windowList = new ArrayList<>();
	private int selectedWindow = -1;
	private int scrollOffset = 0;
	
	// 搜索框
	private EditBox searchBox;
	
	// 按钮
	private Button subscribeButton;
	private Button unsubscribeButton;
	private Button refreshButton;
	private Button closeButton;
	
	public SharedWindowManagerScreen(WaylandCraft mod) {
		super(Component.translatable("waylandcraft.screen.shared_windows"));
		this.mod = mod;
	}
	
	@Override
	protected void init() {
		super.init();
		
		int centerX = this.width / 2;
		int centerY = this.height / 2;
		int listX = centerX - LIST_WIDTH / 2;
		int listY = centerY - LIST_HEIGHT / 2 - 20;
		
		// 搜索框
		this.searchBox = new EditBox(this.font, listX, listY - 25, LIST_WIDTH, 20, 
			Component.translatable("waylandcraft.screen.search"));
		this.searchBox.setResponder(this::onSearchChanged);
		this.addWidget(this.searchBox);
		
		// 按钮
		int buttonY = listY + LIST_HEIGHT + 10;
		
		this.subscribeButton = Button.builder(
			Component.translatable("waylandcraft.screen.subscribe"),
			button -> this.subscribeSelected()
		).bounds(listX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
		
		this.unsubscribeButton = Button.builder(
			Component.translatable("waylandcraft.screen.unsubscribe"),
			button -> this.unsubscribeSelected()
		).bounds(listX + BUTTON_WIDTH + 10, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
		
		this.refreshButton = Button.builder(
			Component.translatable("waylandcraft.screen.refresh"),
			button -> this.refreshWindowList()
		).bounds(listX + (BUTTON_WIDTH + 10) * 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
		
		this.closeButton = Button.builder(
			Component.translatable("waylandcraft.screen.close"),
			button -> this.onClose()
		).bounds(centerX - 50, buttonY + 30, 100, BUTTON_HEIGHT).build();
		
		this.addRenderableWidget(this.subscribeButton);
		this.addRenderableWidget(this.unsubscribeButton);
		this.addRenderableWidget(this.refreshButton);
		this.addRenderableWidget(this.closeButton);
		
		// 刷新窗口列表
		this.refreshWindowList();
	}
	
	/**
	 * 刷新窗口列表
	 */
	private void refreshWindowList() {
		this.windowList = SharedWindowClientHandler.getAllRemoteWindows();
		this.selectedWindow = -1;
		this.scrollOffset = 0;
		this.updateButtonStates();
	}
	
	/**
	 * 搜索框内容变化
	 */
	private void onSearchChanged(String searchText) {
		// 过滤窗口列表
		if(searchText.isEmpty()) {
			this.windowList = SharedWindowClientHandler.getAllRemoteWindows();
		} else {
			this.windowList = SharedWindowClientHandler.getAllRemoteWindows().stream()
				.filter(info -> info.title().toLowerCase().contains(searchText.toLowerCase()) ||
					info.ownerName().toLowerCase().contains(searchText.toLowerCase()))
				.toList();
		}
		this.selectedWindow = -1;
		this.updateButtonStates();
	}
	
	/**
	 * 订阅选中的窗口
	 */
	private void subscribeSelected() {
		if(selectedWindow >= 0 && selectedWindow < windowList.size()) {
			WindowInfo info = windowList.get(selectedWindow);
			SharedWindowClientHandler.requestWindowRegister(info.windowHandle(), info.title());
		}
	}
	
	/**
	 * 取消订阅选中的窗口
	 */
	private void unsubscribeSelected() {
		if(selectedWindow >= 0 && selectedWindow < windowList.size()) {
			WindowInfo info = windowList.get(selectedWindow);
			// TODO: 发送取消订阅请求
		}
	}
	
	/**
	 * 更新按钮状态
	 */
	private void updateButtonStates() {
		boolean hasSelection = selectedWindow >= 0 && selectedWindow < windowList.size();
		this.subscribeButton.active = hasSelection;
		this.unsubscribeButton.active = hasSelection;
	}
	
	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		// 渲染背景
		super.extractRenderState(context, mouseX, mouseY, delta);
		
		int centerX = this.width / 2;
		int centerY = this.height / 2;
		int listX = centerX - LIST_WIDTH / 2;
		int listY = centerY - LIST_HEIGHT / 2 - 20;
		
		// 渲染标题
		context.text(this.font, this.title, centerX - this.font.width(this.title) / 2, listY - 40, 0xFFFFFF);
		
		// 渲染搜索框
		this.searchBox.extractRenderState(context, mouseX, mouseY, delta);
		
		// 渲染窗口列表背景
		context.fill(listX, listY, listX + LIST_WIDTH, listY + LIST_HEIGHT, 0x80000000);
		
		// 渲染窗口列表
		renderWindowList(context, listX, listY, mouseX, mouseY);
		
		// 渲染选中窗口的详细信息
		if(selectedWindow >= 0 && selectedWindow < windowList.size()) {
			renderWindowDetails(context, listX + LIST_WIDTH + 10, listY);
		}
	}
	
	/**
	 * 渲染窗口列表
	 */
	private void renderWindowList(GuiGraphicsExtractor context, int x, int y, int mouseX, int mouseY) {
		int itemHeight = 20;
		int visibleItems = LIST_HEIGHT / itemHeight;
		
		for(int i = 0; i < visibleItems && i + scrollOffset < windowList.size(); i++) {
			int index = i + scrollOffset;
			WindowInfo info = windowList.get(index);
			
			int itemY = y + i * itemHeight;
			boolean isSelected = index == selectedWindow;
			boolean isHovered = mouseX >= x && mouseX < x + LIST_WIDTH && 
				mouseY >= itemY && mouseY < itemY + itemHeight;
			
			// 渲染选中/悬停背景
			if(isSelected) {
				context.fill(x, itemY, x + LIST_WIDTH, itemY + itemHeight, 0xFF0000FF);
			} else if(isHovered) {
				context.fill(x, itemY, x + LIST_WIDTH, itemY + itemHeight, 0x40FFFFFF);
			}
			
			// 渲染窗口信息
			String title = info.title();
			if(title.length() > 25) {
				title = title.substring(0, 22) + "...";
			}
			
			context.text(this.font, title, x + 5, itemY + 5, 0xFFFFFF);
			
			// 渲染权限图标
			WindowPermission permission = info.permission();
			int permissionColor = getPermissionColor(permission);
			context.fill(x + LIST_WIDTH - 15, itemY + 5, x + LIST_WIDTH - 5, itemY + 15, permissionColor);
		}
		
		// 渲染滚动条
		if(windowList.size() > visibleItems) {
			int scrollbarHeight = (int)((float)visibleItems / windowList.size() * LIST_HEIGHT);
			int scrollbarY = y + (int)((float)scrollOffset / windowList.size() * LIST_HEIGHT);
			context.fill(x + LIST_WIDTH - 5, scrollbarY, x + LIST_WIDTH, scrollbarY + scrollbarHeight, 0xFFFFFFFF);
		}
	}
	
	/**
	 * 渲染窗口详细信息
	 */
	private void renderWindowDetails(GuiGraphicsExtractor context, int x, int y) {
		WindowInfo info = windowList.get(selectedWindow);
		
		// 背景
		context.fill(x, y, x + 200, y + 120, 0x80000000);
		
		// 详细信息
		context.text(this.font, "§l窗口详情", x + 5, y + 5, 0xFFFFFF);
		context.text(this.font, "名称: " + info.title(), x + 5, y + 25, 0xCCCCCC);
		context.text(this.font, "所有者: " + info.ownerName(), x + 5, y + 40, 0xCCCCCC);
		context.text(this.font, "权限: " + info.permission().name(), x + 5, y + 55, 0xCCCCCC);
		context.text(this.font, "尺寸: " + info.width() + "x" + info.height(), x + 5, y + 70, 0xCCCCCC);
		context.text(this.font, "状态: " + (info.visible() ? "可见" : "隐藏"), x + 5, y + 85, 0xCCCCCC);
		
		// 权限说明
		context.text(this.font, "§l权限说明", x + 5, y + 100, 0xFFFF00);
	}
	
	/**
	 * 获取权限颜色
	 */
	private int getPermissionColor(WindowPermission permission) {
		return switch(permission) {
			case NONE -> 0xFFFF0000; // 红色
			case VIEW -> 0xFFFFFF00; // 黄色
			case INTERACT -> 0xFF00FF00; // 绿色
			case CONTROL -> 0xFF00FFFF; // 青色
		};
	}
	
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
		if(event.button() == 0) {
			double mouseX = event.x();
			double mouseY = event.y();
			int centerX = this.width / 2;
			int centerY = this.height / 2;
			int listX = centerX - LIST_WIDTH / 2;
			int listY = centerY - LIST_HEIGHT / 2 - 20;
			
			// 检查是否点击了列表项
			if(mouseX >= listX && mouseX < listX + LIST_WIDTH && 
				mouseY >= listY && mouseY < listY + LIST_HEIGHT) {
				int itemHeight = 20;
				int clickedIndex = (int)((mouseY - listY) / itemHeight) + scrollOffset;
				
				if(clickedIndex >= 0 && clickedIndex < windowList.size()) {
					this.selectedWindow = clickedIndex;
					this.updateButtonStates();
				}
			}
		}
		
		return super.mouseClicked(event, isDoubleClick);
	}
	
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int maxScroll = Math.max(0, windowList.size() - LIST_HEIGHT / 20);
		this.scrollOffset = (int)Math.max(0, Math.min(maxScroll, this.scrollOffset - verticalAmount));
		return true;
	}
	
	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
