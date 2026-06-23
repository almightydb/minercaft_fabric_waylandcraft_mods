# WaylandCraft 🎮🪟

**在 Minecraft 里运行 Linux 桌面应用** — 一个 Fabric mod，将 Wayland compositor 功能集成到 Minecraft 中，让玩家可以在游戏世界中查看和交互 Linux 桌面窗口。支持多人窗口共享。

![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-green)
![Fabric](https://img.shields.io/badge/Fabric-0.19.3-blue)
![Java](https://img.shields.io/badge/Java-25-orange)
![Rust](https://img.shields.io/badge/Rust-Native%20Bridge-red)
![License](https://img.shields.io/badge/License-MIT-yellow)

## ✨ 功能特性

### 🖥️ 窗口管理
- **Wayland Compositor** — 创建独立的 Wayland compositor，应用在游戏内运行
- **App Launcher** — 按 `V` 键从 .desktop 列表启动应用
- **Desktop Capture** — 通过 XDG Desktop Portal + PipeWire 捕获已有桌面窗口
- **窗口控制** — 滚轮缩放、Ctrl+滚轮旋转、Ctrl+Alt+滚轮调整显示比例

### 👥 多人窗口共享
- **实时共享** — 将窗口共享给其他玩家，在对方游戏世界中渲染
- **权限系统** — 四级权限：NONE / VIEW / INTERACT / CONTROL
- **自适应质量** — 根据带宽自动调整分辨率和质量
- **码率限速** — Token Bucket 算法，可配置最大码率
- **像素差异检测** — 静态画面跳帧，节省带宽

### ⚡ 性能优化
- **PBO 异步回读** — 双缓冲 PBO 消除 GPU→CPU 同步阻塞
- **GPU 侧缩放** — `glBlitFramebuffer` 在 GPU 上完成缩放
- **直接内存写入** — 接收端通过 `NativeImage.getPointer()` + `MemoryUtil` 零 JNI 开销更新纹理
- **批量像素解码** — `BufferedImage.getRGB()` 一次获取，减少 JNI 调用

## 🏗️ 架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     Minecraft Client                            │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐ │
│  │ WaylandCraft │  │  WindowShare │  │   SharedWindowDisplay  │ │
│  │  (Compositor)│  │   Manager    │  │   (Remote Rendering)   │ │
│  └──────┬───────┘  └──────┬───────┘  └───────────┬───────────┘ │
│         │                 │                       │             │
│  ┌──────┴───────┐  ┌──────┴───────┐  ┌───────────┴───────────┐ │
│  │ Rust Native  │  │ ImageCapture │  │  RemoteWindowRenderer  │ │
│  │ Bridge (JNI) │  │  (PBO+GPU)   │  │  (MemoryUtil Direct)   │ │
│  └──────┬───────┘  └──────┬───────┘  └───────────┬───────────┘ │
│         │                 │                       │             │
└─────────┼─────────────────┼───────────────────────┼─────────────┘
          │                 │                       │
   ┌──────┴───────┐  ┌──────┴───────┐               │
   │ Smithay      │  │ JPEG Encode  │               │
   │ Compositor   │  │ (Direct RGBA)│               │
   └──────────────┘  └──────────────┘               │
                                                     │
┌────────────────────────────────────────────────────┼─────────────┐
│                  Minecraft Server                   │             │
│  ┌──────────────────────┐  ┌───────────────────────────────────┐ │
│  │ SharedWindowManager  │  │  Server Image Forwarding          │ │
│  │ PermissionManager    │  │  (C2S → S2C broadcast)            │ │
│  └──────────────────────┘  └───────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

## 🛠️ 技术栈

| 层级 | 技术 |
|------|------|
| **游戏端** | Java 25, Fabric Loader 0.19.3, Fabric API 0.151.0 |
| **原生桥接** | Rust, JNI (bind_java_type! macro) |
| **Wayland** | Smithay (compositor framework), wayland-client, wlr-foreign-toplevel-management |
| **桌面捕获** | XDG Desktop Portal (D-Bus), PipeWire |
| **窗口列表** | GNOME Shell Extension (D-Bus), wmctrl, wlr-foreign-toplevel, /proc |
| **图像处理** | PBO 双缓冲, glBlitFramebuffer, JPEG, MemoryUtil 直接内存访问 |
| **网络** | Fabric Networking API, 自定义 Payload 协议 |

## 📦 构建

### 前置要求
- Java 25 (openjdk-25)
- Rust toolchain (rustup stable)
- Wayland dev libs: `apt install libwayland-dev libxkbcommon-dev pkg-config libclang-dev`

### 编译步骤

```bash
# 1. 编译 Rust 原生库
source ~/.cargo/env
cd native && cargo build --release
cp target/release/libwaylandcraft.so target/debug/libwaylandcraft.so

# 2. 编译 Java mod
cd /workspace/waylandcraft
env -u LD_PRELOAD -u PROXYCHAINS_CONF_FILE ./gradlew clean build

# 3. 输出
# build/libs/waylandcraft.jar (~2.0MB)
```

### Rust Release 优化
```toml
# native/Cargo.toml
[profile.release]
opt-level = "z"      # 优化体积
lto = true           # 链接时优化
codegen-units = 1    # 单编译单元
strip = true         # 去除符号
panic = "abort"      # 更小的 panic 处理
```

## 🎮 使用方法

### 基本操作

| 按键 | 功能 |
|------|------|
| `B` | 窗口管理器（选择/聚焦窗口） |
| `V` | 应用启动器（启动应用到 compositor） |
| `N` | 共享窗口管理器（多人共享） |
| `G` | 捕获/释放键盘（向窗口发送按键） |
| `右键长按` + WindowItem | 在世界中显示窗口 |

### 窗口控制（悬停时）

| 输入 | 功能 |
|------|------|
| 滚轮 | 向应用转发滚动（页面滚动等） |
| Ctrl+滚轮 | 绕法线旋转窗口 |
| Ctrl+Alt+滚轮 | 调整显示比例 (0.2x - 5.0x) |

### 命令系统 (`/wl`)

#### 窗口管理
| 命令 | 功能 |
|------|------|
| `/wl list windows` | 列出 compositor 中的窗口 |
| `/wl list apps` | 列出可启动的应用 |
| `/wl list desktop` | 列出桌面窗口（用于捕获） |
| `/wl give create <name>` | 启动应用到 compositor |
| `/wl give capture` | 通过 Portal 捕获桌面窗口 |
| `/wl remove <handle>` | 移除窗口物品 |
| `/wl close <handle>` | 关闭窗口 |
| `/wl resize <handle> <w> <h>` | 调整窗口大小 |

#### 共享管理
| 命令 | 功能 |
|------|------|
| `/wl share start <handle>` | 开始共享窗口 |
| `/wl unshare <handle>` | 停止共享 |
| `/wl share quality <handle> <scale> <quality> <fps>` | 设置画质 |
| `/wl share quality-reset <handle>` | 重置画质 |
| `/wl share config <handle> <param> <value>` | 设置单个参数 |
| `/wl share preset <handle> <preset>` | 应用预设 |
| `/wl share info <handle>` | 查看当前配置 |
| `/wl share resolution <handle> <w> <h>` | 设置目标分辨率 |
| `/wl share stats <handle>` | 查看共享统计 |

#### 权限管理
| 命令 | 功能 |
|------|------|
| `/wl perm list` | 查看所有权限 |
| `/wl perm default <PERM>` | 设置默认权限 |
| `/wl perm allow <player> <PERM>` | 添加白名单 |
| `/wl perm deny <player>` | 添加黑名单 |
| `/wl perm remove <player>` | 移除玩家 |

### 配置参数

| 参数 | 说明 | 范围 |
|------|------|------|
| `scale` | 分辨率缩放 | 0.1 - 1.0 |
| `quality` | JPEG 质量 | 0.1 - 1.0 |
| `fps` | 最大帧率 | 5 - 120 |
| `bitrate` | 最大码率 (kbps) | 0 = 无限制 |
| `diffThreshold` | 像素变化阈值 | 0.001 - 1.0 |
| `diff` | 差异更新 | true/false |
| `compression` | 压缩方式 | jpeg/webp/none |

### 预设配置

| 预设 | Scale | Quality | FPS | Bitrate |
|------|-------|---------|-----|---------|
| `performance` | 0.25 | 0.5 | 60 | 1000kbps |
| `balanced` | 0.5 | 0.7 | 30 | 2000kbps |
| `quality` | 1.0 | 1.0 | 30 | 无限制 |
| `lowlatency` | 0.35 | 0.6 | 60 | 1500kbps |

## 🔧 桌面窗口捕获

WaylandCraft 支持通过 XDG Desktop Portal 捕获已有桌面窗口：

```bash
# 在游戏中运行
/wl give capture
```

会弹出系统窗口选择对话框，选择后通过 PipeWire 自动读取帧并渲染。

**首次使用需要用户点击确认**（GNOME 安全机制），后续可用 restore_token 跳过。

### 窗口列表（自动检测）

| 优先级 | 方法 | 适用环境 |
|--------|------|---------|
| 1 | GNOME Shell Extension D-Bus | GNOME 48+ |
| 2 | wmctrl + X authority | XWayland 窗口 |
| 3 | wlr-foreign-toplevel-management | wlroots (Sway, Hyprland) |
| 4 | /proc 进程列表 | 所有 Linux |

## 🐛 已知问题

- **Flatpak 应用** — Flatpak 沙箱会覆盖 `WAYLAND_DISPLAY` 环境变量，应用可能启动到桌面而非游戏内。解决：`flatpak override --user --env=WAYLAND_DISPLAY=wayland-1 <app-id>`
- **xkbcli** — 键盘按键映射需要 `xkbcli`，缺失时日志报错但不影响运行。安装：`apt install xkbcli`

## 📁 项目结构

```
waylandcraft/
├── src/main/java/dev/evvie/waylandcraft/
│   ├── WaylandCraft.java              # 客户端入口
│   ├── WaylandCraftCommon.java        # 通用入口（客户端+服务端）
│   ├── bridge/                        # Rust 原生桥接
│   │   ├── WaylandCraftBridge.java    # JNI 声明
│   │   ├── WLCToplevel.java           # 窗口抽象
│   │   └── WLC*.java                  # 其他桥接类
│   ├── capture/                       # 桌面窗口捕获
│   │   ├── PipeWireCaptureManager.java
│   │   └── CaptureSession.java
│   ├── command/                       # 命令系统
│   │   └── WaylandCraftCommand.java   # /wl 命令
│   ├── gui/                           # GUI 界面
│   │   ├── WindowManagerScreen.java
│   │   └── SharedWindowManagerScreen.java
│   ├── network/                       # 网络协议
│   │   ├── SharedWindowImagePayload.java
│   │   ├── SharedWindowClientHandler.java
│   │   └── SharedWindowServerHandler.java
│   ├── render/                        # 渲染系统
│   │   ├── WindowDisplay.java
│   │   ├── WindowFramebuffer.java
│   │   ├── SharedWindowDisplay.java
│   │   └── RenderUtils.java
│   └── shared/                        # 共享系统
│       ├── WindowShareManager.java    # 共享管理器
│       ├── ImageCapture.java          # 图像捕获（PBO+GPU）
│       ├── RemoteWindowRenderer.java  # 远程渲染（直接内存）
│       ├── FrameRateController.java   # 帧率控制
│       ├── DiffUpdateManager.java     # 差分更新
│       └── PermissionManager.java     # 权限管理
├── native/src/                        # Rust 原生库
│   ├── bridge.rs                      # JNI 实现
│   ├── lib.rs                         # 库入口
│   ├── process.rs                     # 进程管理
│   ├── xdg_spec.rs                    # XDG 规范实现
│   ├── desktop_windows.rs             # 桌面窗口列表
│   └── portal_capture.rs              # Portal 捕获
├── build.gradle                       # 构建配置
└── native/Cargo.toml                  # Rust 依赖
```

## 📄 License

MIT License

## 🙏 致谢

- [Smithay](https://github.com/Smithay/smithay) — Wayland compositor 框架
- [Fabric](https://fabricmc.net/) — Minecraft mod 加载器
- [Fabric API](https://github.com/FabricMC/fabric) — Minecraft mod API
