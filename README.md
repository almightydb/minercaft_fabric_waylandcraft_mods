# WaylandCraft 🎮🪟

**Run Linux desktop apps inside Minecraft** — A Fabric mod that integrates a Wayland compositor into Minecraft, allowing players to view and interact with Linux desktop windows in-game. Supports multi-player window sharing.

**在 Minecraft 里运行 Linux 桌面应用** — 一个 Fabric mod，将 Wayland compositor 功能集成到 Minecraft 中，让玩家可以在游戏世界中查看和交互 Linux 桌面窗口。支持多人窗口共享。

![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-green)
![Fabric](https://img.shields.io/badge/Fabric-0.19.3-blue)
![Java](https://img.shields.io/badge/Java-25-orange)
![Rust](https://img.shields.io/badge/Rust-Native%20Bridge-red)
![License](https://img.shields.io/badge/License-MIT-yellow)

---

## ✨ Features / 功能特性

### 🖥️ Window Management / 窗口管理

| | Feature |
|---|---|
| **Wayland Compositor** | Creates an isolated compositor, apps run in-game / 创建独立的 Wayland compositor，应用在游戏内运行 |
| **App Launcher** | Press `V` to launch apps from .desktop list / 按 `V` 键从 .desktop 列表启动应用 |
| **Desktop Capture** | Capture existing desktop windows via XDG Desktop Portal + PipeWire / 通过 Portal + PipeWire 捕获已有桌面窗口 |
| **Controls** | Scroll to zoom, Ctrl+scroll to rotate, Ctrl+Alt+scroll to resize / 滚轮缩放、Ctrl+滚轮旋转、Ctrl+Alt+滚轮调整比例 |

### 👥 Multi-Player Window Sharing / 多人窗口共享

| | Feature |
|---|---|
| **Real-time Sharing** | Share windows to other players, rendered in their world / 将窗口共享给其他玩家，在对方游戏世界中渲染 |
| **Permission System** | 4-level: NONE / VIEW / INTERACT / CONTROL / 四级权限系统 |
| **Adaptive Quality** | Auto-adjust resolution and quality based on bandwidth / 根据带宽自动调整分辨率和质量 |
| **Bitrate Limiting** | Token Bucket algorithm with configurable max bitrate / Token Bucket 算法，可配置最大码率 |
| **Diff Detection** | Skip frames when static, saves bandwidth / 静态画面跳帧，节省带宽 |

### ⚡ Performance / 性能优化

| | Description |
|---|---|
| **PBO Async Readback** | Double-buffered PBO eliminates GPU→CPU sync stalls / 双缓冲 PBO 消除同步阻塞 |
| **GPU-side Scaling** | `glBlitFramebuffer` scales on GPU / GPU 上完成缩放 |
| **Direct Memory Write** | Zero-JNI-overhead texture update via `NativeImage.getPointer()` + `MemoryUtil` / 零 JNI 开销更新纹理 |
| **Batch Pixel Decode** | Batch `getRGB()` decoding, fewer JNI calls / 批量解码，减少 JNI 调用 |

---

## 🏗️ Architecture / 架构

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

## 🛠️ Tech Stack / 技术栈

| Layer | Technology |
|------|------|
| **Game** | Java 25, Fabric Loader 0.19.3, Fabric API 0.151.0 |
| **Native Bridge** | Rust, JNI (bind_java_type! macro) |
| **Wayland** | Smithay, wayland-client, wlr-foreign-toplevel-management |
| **Capture** | XDG Desktop Portal (D-Bus), PipeWire |
| **Window List** | GNOME Shell Extension (D-Bus), wmctrl, wlr-foreign-toplevel, /proc |
| **Image** | PBO double-buffer, glBlitFramebuffer, JPEG, MemoryUtil |
| **Network** | Fabric Networking API, custom Payload protocol |

---

## 📦 Build / 构建

### Prerequisites / 前置要求

- Java 25 (openjdk-25)
- Rust toolchain (rustup stable)
- Wayland dev libs: `apt install libwayland-dev libxkbcommon-dev pkg-config libclang-dev`

### Build Steps / 编译步骤

```bash
# 1. Build Rust native library / 编译 Rust 原生库
source ~/.cargo/env
cd native && cargo build --release
cp target/release/libwaylandcraft.so target/debug/libwaylandcraft.so

# 2. Build Java mod / 编译 Java mod
cd /workspace/waylandcraft
env -u LD_PRELOAD -u PROXYCHAINS_CONF_FILE ./gradlew clean build

# 3. Output / 输出
# build/libs/waylandcraft.jar (~2.0MB)
```

### Rust Release Optimization / Rust 优化配置

```toml
# native/Cargo.toml
[profile.release]
opt-level = "z"      # Optimize for size
lto = true           # Link-time optimization
codegen-units = 1    # Single codegen unit
strip = true         # Strip debug symbols
panic = "abort"      # Smaller panic handling
```

---

## 🎮 Usage / 使用方法

### Basic Controls / 基本操作

| Key | Function |
|------|------|
| `B` | Window manager (select/focus) / 窗口管理器 |
| `V` | App launcher (launch to compositor) / 应用启动器 |
| `N` | Shared window manager (multi-player) / 共享窗口管理器 |
| `G` | Capture/release keyboard input / 捕获/释放键盘 |
| `Right-click hold` + WindowItem | Display window in world / 在世界中显示窗口 |

### Window Controls (while hovering) / 窗口控制（悬停时）

| Input | Function |
|------|------|
| Scroll / 滚轮 | Forward scroll to app / 向应用转发滚动 |
| Ctrl+Scroll | Rotate window around normal / 绕法线旋转窗口 |
| Ctrl+Alt+Scroll | Adjust display scale (0.2x–5.0x) / 调整显示比例 |

### Command System / 命令系统 (`/wl`)

#### Window Management / 窗口管理

| Command | Function |
|------|------|
| `/wl list windows` | List windows in compositor |
| `/wl list apps` | List launchable apps |
| `/wl list desktop` | List desktop windows (for capture) |
| `/wl give create <name>` | Launch app to compositor |
| `/wl give capture` | Capture desktop window via Portal |
| `/wl remove <handle>` | Remove window item |
| `/wl close <handle>` | Close window |
| `/wl resize <handle> <w> <h>` | Resize window |

#### Share Management / 共享管理

| Command | Function |
|------|------|
| `/wl share start <handle>` | Start sharing window |
| `/wl unshare <handle>` | Stop sharing |
| `/wl share quality <handle> <s> <q> <fps>` | Set quality (scale, quality, fps) |
| `/wl share quality-reset <handle>` | Reset quality to default |
| `/wl share config <handle> <param> <value>` | Set single parameter |
| `/wl share preset <handle> <preset>` | Apply preset |
| `/wl share info <handle>` | Show current config |
| `/wl share resolution <handle> <w> <h>` | Set target resolution |
| `/wl share stats <handle>` | Show sharing statistics |

#### Permission Management / 权限管理

| Command | Function |
|------|------|
| `/wl perm list` | List all permissions |
| `/wl perm default <PERM>` | Set default permission |
| `/wl perm allow <player> <PERM>` | Add to whitelist |
| `/wl perm deny <player>` | Add to blacklist |
| `/wl perm remove <player>` | Remove player |

### Configuration Parameters / 配置参数

| Param | Description | Range |
|------|------|------|
| `scale` | Resolution scale / 分辨率缩放 | 0.1 – 1.0 |
| `quality` | JPEG quality / JPEG 质量 | 0.1 – 1.0 |
| `fps` | Max framerate / 最大帧率 | 5 – 120 |
| `bitrate` | Max bitrate (kbps) / 最大码率 | 0 = unlimited |
| `diffThreshold` | Pixel change threshold / 像素变化阈值 | 0.001 – 1.0 |
| `diff` | Diff update / 差异更新 | true/false |
| `compression` | Compression / 压缩方式 | jpeg/webp/none |

### Presets / 预设配置

| Preset | Scale | Quality | FPS | Bitrate |
|------|-------|---------|-----|---------|
| `performance` | 0.25 | 0.5 | 60 | 1000kbps |
| `balanced` | 0.5 | 0.7 | 30 | 2000kbps |
| `quality` | 1.0 | 1.0 | 30 | unlimited |
| `lowlatency` | 0.35 | 0.6 | 60 | 1500kbps |

---

## 🔧 Desktop Window Capture / 桌面窗口捕获

Capture existing desktop windows via XDG Desktop Portal:

```bash
# Run in-game / 在游戏中运行
/wl give capture
```

System dialog pops up, selected window is captured via PipeWire automatically.

**First use requires user confirmation** (GNOME security). Subsequent uses can skip with restore_token.

会弹出系统窗口选择对话框，选择后通过 PipeWire 自动读取帧并渲染。首次使用需用户确认，后续可用 restore_token 跳过。

### Window List (auto-detect) / 窗口列表（自动检测）

| Priority | Method | Environment |
|--------|------|---------|
| 1 | GNOME Shell Extension D-Bus | GNOME 48+ |
| 2 | wmctrl + X authority | XWayland windows |
| 3 | wlr-foreign-toplevel-management | wlroots (Sway, Hyprland) |
| 4 | /proc process list | All Linux |

---

## 🐛 Known Issues / 已知问题

- **Flatpak Apps** — Flatpak sandbox overrides `WAYLAND_DISPLAY`, apps may launch to desktop instead of in-game. Fix: `flatpak override --user --env=WAYLAND_DISPLAY=wayland-1 <app-id>`
- **xkbcli** — Keyboard mapping requires `xkbcli`, logs error if missing but doesn't affect operation. Install: `apt install xkbcli`

---

## 📁 Project Structure / 项目结构

```
waylandcraft/
├── src/main/java/dev/evvie/waylandcraft/
│   ├── WaylandCraft.java              # Client entry
│   ├── WaylandCraftCommon.java        # Common entry (client+server)
│   ├── bridge/                        # Rust native bridge
│   │   ├── WaylandCraftBridge.java    # JNI declarations
│   │   ├── WLCToplevel.java           # Window abstraction
│   │   └── WLC*.java                  # Other bridge classes
│   ├── capture/                       # Desktop capture
│   │   ├── PipeWireCaptureManager.java
│   │   └── CaptureSession.java
│   ├── command/                       # Command system
│   │   └── WaylandCraftCommand.java   # /wl commands
│   ├── gui/                           # GUI screens
│   │   ├── WindowManagerScreen.java
│   │   └── SharedWindowManagerScreen.java
│   ├── network/                       # Network protocol
│   │   ├── SharedWindowImagePayload.java
│   │   ├── SharedWindowClientHandler.java
│   │   └── SharedWindowServerHandler.java
│   ├── render/                        # Rendering
│   │   ├── WindowDisplay.java
│   │   ├── WindowFramebuffer.java
│   │   ├── SharedWindowDisplay.java
│   │   └── RenderUtils.java
│   └── shared/                        # Sharing system
│       ├── WindowShareManager.java    # Share manager
│       ├── ImageCapture.java          # Image capture (PBO+GPU)
│       ├── RemoteWindowRenderer.java  # Remote renderer (direct mem)
│       ├── FrameRateController.java   # Frame rate control
│       ├── DiffUpdateManager.java     # Diff update
│       └── PermissionManager.java     # Permission management
├── native/src/                        # Rust native library
│   ├── bridge.rs                      # JNI implementation
│   ├── lib.rs                         # Library entry
│   ├── process.rs                     # Process management
│   ├── xdg_spec.rs                    # XDG spec
│   ├── desktop_windows.rs             # Desktop window list
│   └── portal_capture.rs              # Portal capture
├── build.gradle                       # Build config
└── native/Cargo.toml                  # Rust dependencies
```

---

## 📄 License

MIT License

## 🙏 Acknowledgments / 致谢

- [Smithay](https://github.com/Smithay/smithay) — Wayland compositor framework
- [Fabric](https://fabricmc.net/) — Minecraft mod loader
- [Fabric API](https://github.com/FabricMC/fabric) — Minecraft mod API
