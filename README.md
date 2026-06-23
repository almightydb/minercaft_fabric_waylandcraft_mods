# WaylandCraft 🎮🪟

**在 Minecraft 里运行 Linux 桌面应用** — 一个 Fabric mod，将 Wayland compositor 功能集成到 Minecraft 中，让玩家可以在游戏世界中查看和交互 Linux 桌面窗口。

> ⚠️ **Disclaimer / 免责声明**
> 
> 本项目基于 [WaylandCraft](https://github.com/evvie-jpg/waylandcraft) 原始项目，多人显示功能由 AI 辅助实现。功能和安全性不保证稳定，可能存在未知问题。请自行承担使用风险。
> 
> This project is based on the original [WaylandCraft](https://github.com/evvie-jpg/waylandcraft). Multi-player display features were AI-implemented. Functionality and security are NOT guaranteed. Use at your own risk.

![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-green)
![Fabric](https://img.shields.io/badge/Fabric-0.19.3-blue)
![Java](https://img.shields.io/badge/Java-25-orange)
![Rust](https://img.shields.io/badge/Rust-Native%20Bridge-red)

---

## 🐛 Known Issues / 已知问题

1. **无法捕获桌面窗口** — Desktop window capture (XDG Portal + PipeWire) 目前不可用
2. **Flatpak 类应用无法显示** — Flatpak 沙盒覆盖了 `WAYLAND_DISPLAY`，应用会启动到桌面而非游戏内
3. **窗口移动受限** — 窗口只能上下左右移动，无法前后移动，无法调整角度

---

## ✨ Current Features / 已实现功能

| Feature | Description |
|---------|-------------|
| **Multi-Player Display** | Share windows to other players, rendered in their world / 多人窗口共享显示 |
| **Permission System** | 4-level: NONE / VIEW / INTERACT / CONTROL / 四级权限管理 |
| **Resolution Settings** | Configurable scale (0.1x–1.0x) / 可配置分辨率缩放 |
| **Bitrate Control** | Token Bucket algorithm with configurable max bitrate / 码率控制 |
| **Adaptive Quality** | Auto-adjust resolution and quality based on bandwidth / 自适应画质 |
| **Performance Optimization** | PBO async readback, GPU scaling, direct memory texture write / 性能优化 |

> Most features are executed via command line (`/wl` command). Most features are command-line driven (`/wl`).
> 
> 大部分功能通过命令行执行（`/wl` 命令）。

---

## 🎮 Commands / 命令系统

### Share Management / 共享管理

| Command | Function |
|------|------|
| `/wl share start <handle>` | Start sharing window / 开始共享窗口 |
| `/wl unshare <handle>` | Stop sharing / 停止共享 |
| `/wl share quality <handle> <s> <q> <fps>` | Set quality (scale, quality, fps) / 设置画质 |
| `/wl share quality-reset <handle>` | Reset quality to default / 重置画质 |
| `/wl share config <handle> <param> <value>` | Set single parameter / 设置单个参数 |
| `/wl share preset <handle> <preset>` | Apply preset / 应用预设 |
| `/wl share info <handle>` | Show current config / 显示当前配置 |
| `/wl share resolution <handle> <w> <h>` | Set target resolution / 设置目标分辨率 |
| `/wl share stats <handle>` | Show sharing statistics / 显示共享统计 |

### Permission Management / 权限管理

| Command | Function |
|------|------|
| `/wl perm list` | List all permissions / 列出所有权限 |
| `/wl perm default <PERM>` | Set default permission / 设置默认权限 |
| `/wl perm allow <player> <PERM>` | Add to whitelist / 加入白名单 |
| `/wl perm deny <player>` | Add to blacklist / 加入黑名单 |
| `/wl perm remove <player>` | Remove player / 移除玩家 |

### Window Management / 窗口管理

| Command | Function |
|------|------|
| `/wl list windows` | List windows in compositor |
| `/wl list apps` | List launchable apps |
| `/wl list desktop` | List desktop windows |
| `/wl give create <name>` | Launch app to compositor |
| `/wl remove <handle>` | Remove window item |
| `/wl close <handle>` | Close window |
| `/wl resize <handle> <w> <h>` | Resize window |

### Basic Controls / 基本操作

| Key | Function |
|------|------|
| `B` | Window manager / 窗口管理器 |
| `V` | App launcher / 应用启动器 |
| `N` | Shared window manager / 共享窗口管理器 |
| `G` | Capture/release keyboard / 捕获/释放键盘 |
| `Right-click hold` + WindowItem | Display window in world / 在世界中显示窗口 |

### Configuration Parameters / 配置参数

| Param | Description | Range |
|------|------|------|
| `scale` | Resolution scale / 分辨率缩放 | 0.1 – 1.0 |
| `quality` | JPEG quality / JPEG 质量 | 0.1 – 1.0 |
| `fps` | Max framerate / 最大帧率 | 5 – 120 |
| `bitrate` | Max bitrate (kbps) / 最大码率 | 0 = unlimited |
| `diffThreshold` | Pixel change threshold / 像素变化阈值 | 0.001 – 1.0 |

### Presets / 预设配置

| Preset | Scale | Quality | FPS | Bitrate |
|------|-------|---------|-----|---------|
| `performance` | 0.25 | 0.5 | 60 | 1000kbps |
| `balanced` | 0.5 | 0.7 | 30 | 2000kbps |
| `quality` | 1.0 | 1.0 | 30 | unlimited |
| `lowlatency` | 0.35 | 0.6 | 60 | 1500kbps |

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
| **Native Bridge** | Rust, JNI |
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

---

## 📄 License

MIT License

## 🙏 Acknowledgments / 致谢

- [WaylandCraft](https://github.com/evvie-jpg/waylandcraft) — Original project / 原始项目
- [Smithay](https://github.com/Smithay/smithay) — Wayland compositor framework
- [Fabric](https://fabricmc.net/) — Minecraft mod loader
- [Fabric API](https://github.com/FabricMC/fabric) — Minecraft mod API
- AI-assisted multi-player display implementation / AI 辅助实现多人显示
