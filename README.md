# WaylandCraft 🎮🪟

**在 Minecraft 里运行 Linux 桌面应用 / Run Linux desktop apps inside Minecraft**

一个 Fabric mod，将 Wayland compositor 功能集成到 Minecraft 中，让玩家可以在游戏世界中查看和交互 Linux 桌面窗口。支持多人窗口共享。

A Fabric mod that integrates a Wayland compositor into Minecraft, allowing players to view and interact with Linux desktop windows in-game. Supports multi-player window sharing.

![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-green)
![Fabric](https://img.shields.io/badge/Fabric-0.19.3-blue)
![Java](https://img.shields.io/badge/Java-25-orange)
![Rust](https://img.shields.io/badge/Rust-Native%20Bridge-red)
![License](https://img.shields.io/badge/License-MIT-yellow)

---

## ✨ 功能特性 / Features

### 🖥️ 窗口管理 / Window Management

| | 功能 Feature |
|---|---|
| **Wayland Compositor** | 创建独立的 Wayland compositor，应用在游戏内运行 / Creates an isolated compositor, apps run in-game |
| **App Launcher** | 按 `V` 键从 .desktop 列表启动应用 / Press `V` to launch apps from .desktop list |
| **Desktop Capture** | 通过 XDG Desktop Portal + PipeWire 捕获已有桌面窗口 / Capture existing desktop windows via Portal + PipeWire |
| **窗口控制 Controls** | 滚轮缩放、Ctrl+滚轮旋转、Ctrl+Alt+滚轮调整显示比例 / Scroll to zoom, Ctrl+scroll to rotate, Ctrl+Alt+scroll to resize |

### 👥 多人窗口共享 / Multi-Player Window Sharing

| | 功能 Feature |
|---|---|
| **实时共享 Real-time** | 将窗口共享给其他玩家，在对方游戏世界中渲染 / Share windows to other players, rendered in their world |
| **权限系统 Permissions** | 四级权限：NONE / VIEW / INTERACT / CONTROL / 4-level: NONE / VIEW / INTERACT / CONTROL |
| **自适应质量 Adaptive** | 根据带宽自动调整分辨率和质量 / Auto-adjust resolution and quality based on bandwidth |
| **码率限速 Bitrate** | Token Bucket 算法，可配置最大码率 / Token Bucket algorithm with configurable max bitrate |
| **差异检测 Diff** | 静态画面跳帧，节省带宽 / Skip frames when static, saves bandwidth |

### ⚡ 性能优化 / Performance

| | 说明 Description |
|---|---|
| **PBO 异步回读** | 双缓冲 PBO 消除 GPU→CPU 同步阻塞 / Double-buffered PBO eliminates GPU→CPU sync stalls |
| **GPU 侧缩放** | `glBlitFramebuffer` 在 GPU 上完成缩放 / GPU-side scaling via `glBlitFramebuffer` |
| **直接内存写入** | 接收端通过 `NativeImage.getPointer()` + `MemoryUtil` 零 JNI 开销更新纹理 / Zero-JNI-overhead texture update via direct memory access |
| **批量像素解码** | `BufferedImage.getRGB()` 一次获取，减少 JNI 调用 / Batch `getRGB()` decoding, fewer JNI calls |

---

## 🏗️ 架构 / Architecture

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

---

## 🛠️ 技术栈 / Tech Stack

| 层级 Layer | 技术 Technology |
|------|------|
| **游戏端 Game** | Java 25, Fabric Loader 0.19.3, Fabric API 0.151.0 |
| **原生桥接 Bridge** | Rust, JNI (bind_java_type! macro) |
| **Wayland** | Smithay (compositor framework), wayland-client, wlr-foreign-toplevel-management |
| **桌面捕获 Capture** | XDG Desktop Portal (D-Bus), PipeWire |
| **窗口列表 Window List** | GNOME Shell Extension (D-Bus), wmctrl, wlr-foreign-toplevel, /proc |
| **图像处理 Image** | PBO double-buffer, glBlitFramebuffer, JPEG, MemoryUtil direct memory |
| **网络 Network** | Fabric Networking API, custom Payload protocol |

---

## 📦 构建 / Build

### 前置要求 / Prerequisites

- Java 25 (openjdk-25)
- Rust toolchain (rustup stable)
- Wayland dev libs: `apt install libwayland-dev libxkbcommon-dev pkg-config libclang-dev`

### 编译步骤 / Build Steps

```bash
# 1. 编译 Rust 原生库 / Build Rust native library
source ~/.cargo/env
cd native && cargo build --release
cp target/release/libwaylandcraft.so target/debug/libwaylandcraft.so

# 2. 编译 Java mod / Build Java mod
cd /workspace/waylandcraft
env -u LD_PRELOAD -u PROXYCHAINS_CONF_FILE ./gradlew clean build

# 3. 输出 / Output
# build/libs/waylandcraft.jar (~2.0MB)
```

### Rust Release 优化 / Rust Release Optimization

```toml
# native/Cargo.toml
[profile.release]
opt-level = "z"      # 优化体积 / Optimize for size
lto = true           # 链接时优化 / Link-time optimization
codegen-units = 1    # 单编译单元 / Single codegen unit
strip = true         # 去除符号 / Strip debug symbols
panic = "abort"      # 更小的 panic 处理 / Smaller panic handling
```

---

## 🎮 使用方法 / Usage

### 基本操作 / Basic Controls

| 按键 Key | 功能 Function |
|------|------|
| `B` | 窗口管理器（选择/聚焦窗口） / Window manager (select/focus) |
| `V` | 应用启动器（启动应用到 compositor） / App launcher (launch to compositor) |
| `N` | 共享窗口管理器（多人共享） / Shared window manager (multi-player) |
| `G` | 捕获/释放键盘（向窗口发送按键） / Capture/release keyboard input |
| `右键长按` + WindowItem | 在世界中显示窗口 / Display window in world |

### 窗口控制（悬停时） / Window Controls (while hovering)

| 输入 Input | 功能 Function |
|------|------|
| 滚轮 Scroll | 向应用转发滚动（页面滚动等） / Forward scroll to app |
| Ctrl+滚轮 Ctrl+Scroll | 绕法线旋转窗口 / Rotate window around normal |
| Ctrl+Alt+滚轮 Ctrl+Alt+Scroll | 调整显示比例 (0.2x - 5.0x) / Adjust display scale (0.2x - 5.0x) |

### 命令系统 / Command System (`/wl`)

#### 窗口管理 / Window Management

| 命令 Command | 功能 Function |
|------|------|
| `/wl list windows` | 列出 compositor 中的窗口 / List windows in compositor |
| `/wl list apps` | 列出可启动的应用 / List launchable apps |
| `/wl list desktop` | 列出桌面窗口（用于捕获） / List desktop windows (for capture) |
| `/wl give create <name>` | 启动应用到 compositor / Launch app to compositor |
| `/wl give capture` | 通过 Portal 捕获桌面窗口 / Capture desktop window via Portal |
| `/wl remove <handle>` | 移除窗口物品 / Remove window item |
| `/wl close <handle>` | 关闭窗口 / Close window |
| `/wl resize <handle> <w> <h>` | 调整窗口大小 / Resize window |

#### 共享管理 / Share Management

| 命令 Command | 功能 Function |
|------|------|
| `/wl share start <handle>` | 开始共享窗口 / Start sharing window |
| `/wl unshare <handle>` | 停止共享 / Stop sharing |
| `/wl share quality <handle> <s> <q> <fps>` | 设置画质 / Set quality (scale, quality, fps) |
| `/wl share quality-reset <handle>` | 重置画质 / Reset quality to default |
| `/wl share config <handle> <param> <value>` | 设置单个参数 / Set single parameter |
| `/wl share preset <handle> <preset>` | 应用预设 / Apply preset |
| `/wl share info <handle>` | 查看当前配置 / Show current config |
| `/wl share resolution <handle> <w> <h>` | 设置目标分辨率 / Set target resolution |
| `/wl share stats <handle>` | 查看共享统计 / Show sharing statistics |

#### 权限管理 / Permission Management

| 命令 Command | 功能 Function |
|------|------|
| `/wl perm list` | 查看所有权限 / List all permissions |
| `/wl perm default <PERM>` | 设置默认权限 / Set default permission |
| `/wl perm allow <player> <PERM>` | 添加白名单 / Add to whitelist |
| `/wl perm deny <player>` | 添加黑名单 / Add to blacklist |
| `/wl perm remove <player>` | 移除玩家 / Remove player |

### 配置参数 / Configuration Parameters

| 参数 Param | 说明 Description | 范围 Range |
|------|------|------|
| `scale` | 分辨率缩放 / Resolution scale | 0.1 - 1.0 |
| `quality` | JPEG 质量 / JPEG quality | 0.1 - 1.0 |
| `fps` | 最大帧率 / Max framerate | 5 - 120 |
| `bitrate` | 最大码率 / Max bitrate (kbps) | 0 = 无限制 unlimited |
| `diffThreshold` | 像素变化阈值 / Pixel change threshold | 0.001 - 1.0 |
| `diff` | 差异更新 / Diff update | true/false |
| `compression` | 压缩方式 / Compression | jpeg/webp/none |

### 预设配置 / Presets

| 预设 Preset | Scale | Quality | FPS | Bitrate |
|------|-------|---------|-----|---------|
| `performance` | 0.25 | 0.5 | 60 | 1000kbps |
| `balanced` | 0.5 | 0.7 | 30 | 2000kbps |
| `quality` | 1.0 | 1.0 | 30 | 无限制 unlimited |
| `lowlatency` | 0.35 | 0.6 | 60 | 1500kbps |

---

## 🔧 桌面窗口捕获 / Desktop Window Capture

通过 XDG Desktop Portal 捕获已有桌面窗口：/ Capture existing desktop windows via Portal:

```bash
# 在游戏中运行 / Run in-game
/wl give capture
```

会弹出系统窗口选择对话框，选择后通过 PipeWire 自动读取帧并渲染。/ System dialog pops up, selected window is captured via PipeWire.

**首次使用需要用户点击确认**（GNOME 安全机制），后续可用 restore_token 跳过。/ **First use requires user confirmation** (GNOME security), subsequent uses can skip with restore_token.

### 窗口列表（自动检测） / Window List (auto-detect)

| 优先级 Priority | 方法 Method | 适用环境 Environment |
|--------|------|---------|
| 1 | GNOME Shell Extension D-Bus | GNOME 48+ |
| 2 | wmctrl + X authority | XWayland windows |
| 3 | wlr-foreign-toplevel-management | wlroots (Sway, Hyprland) |
| 4 | /proc 进程列表 / process list | All Linux |

---

## 🐛 已知问题 / Known Issues

- **Flatpak 应用 / Flatpak Apps** — Flatpak 沙箱会覆盖 `WAYLAND_DISPLAY` 环境变量，应用可能启动到桌面而非游戏内。解决：`flatpak override --user --env=WAYLAND_DISPLAY=wayland-1 <app-id>` / Flatpak sandbox overrides `WAYLAND_DISPLAY`, apps may launch to desktop instead of in-game. Fix: `flatpak override --user --env=WAYLAND_DISPLAY=wayland-1 <app-id>`
- **xkbcli** — 键盘按键映射需要 `xkbcli`，缺失时日志报错但不影响运行。安装：`apt install xkbcli` / Keyboard mapping requires `xkbcli`, logs error if missing but doesn't affect operation. Install: `apt install xkbcli`

---

## 📁 项目结构 / Project Structure

```
waylandcraft/
├── src/main/java/dev/evvie/waylandcraft/
│   ├── WaylandCraft.java              # 客户端入口 / Client entry
│   ├── WaylandCraftCommon.java        # 通用入口 / Common entry (client+server)
│   ├── bridge/                        # Rust 原生桥接 / Rust native bridge
│   │   ├── WaylandCraftBridge.java    # JNI 声明 / JNI declarations
│   │   ├── WLCToplevel.java           # 窗口抽象 / Window abstraction
│   │   └── WLC*.java                  # 其他桥接类 / Other bridge classes
│   ├── capture/                       # 桌面窗口捕获 / Desktop capture
│   │   ├── PipeWireCaptureManager.java
│   │   └── CaptureSession.java
│   ├── command/                       # 命令系统 / Command system
│   │   └── WaylandCraftCommand.java   # /wl 命令 / /wl commands
│   ├── gui/                           # GUI 界面 / GUI screens
│   │   ├── WindowManagerScreen.java
│   │   └── SharedWindowManagerScreen.java
│   ├── network/                       # 网络协议 / Network protocol
│   │   ├── SharedWindowImagePayload.java
│   │   ├── SharedWindowClientHandler.java
│   │   └── SharedWindowServerHandler.java
│   ├── render/                        # 渲染系统 / Rendering
│   │   ├── WindowDisplay.java
│   │   ├── WindowFramebuffer.java
│   │   ├── SharedWindowDisplay.java
│   │   └── RenderUtils.java
│   └── shared/                        # 共享系统 / Sharing system
│       ├── WindowShareManager.java    # 共享管理器 / Share manager
│       ├── ImageCapture.java          # 图像捕获 / Image capture (PBO+GPU)
│       ├── RemoteWindowRenderer.java  # 远程渲染 / Remote renderer (direct mem)
│       ├── FrameRateController.java   # 帧率控制 / Frame rate control
│       ├── DiffUpdateManager.java     # 差分更新 / Diff update
│       └── PermissionManager.java     # 权限管理 / Permission management
├── native/src/                        # Rust 原生库 / Rust native library
│   ├── bridge.rs                      # JNI 实现 / JNI implementation
│   ├── lib.rs                         # 库入口 / Library entry
│   ├── process.rs                     # 进程管理 / Process management
│   ├── xdg_spec.rs                    # XDG 规范实现 / XDG spec
│   ├── desktop_windows.rs             # 桌面窗口列表 / Desktop window list
│   └── portal_capture.rs              # Portal 捕获 / Portal capture
├── build.gradle                       # 构建配置 / Build config
└── native/Cargo.toml                  # Rust 依赖 / Rust dependencies
```

---

## 📄 License

MIT License

## 🙏 致谢 / Acknowledgments

- [Smithay](https://github.com/Smithay/smithay) — Wayland compositor 框架 / Wayland compositor framework
- [Fabric](https://fabricmc.net/) — Minecraft mod 加载器 / Minecraft mod loader
- [Fabric API](https://github.com/FabricMC/fabric) — Minecraft mod API
