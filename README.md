<p align="center">
  <a href="#english">English</a> | <a href="#中文">中文</a>
</p>

<p align="center">
  <h1 align="center">WaylandCraft 🎮🪟</h1>
  <p align="center"><b>Run Linux desktop apps inside Minecraft</b></p>
  <p align="center">
    <img src="https://img.shields.io/badge/Minecraft-26.1.2-green" />
    <img src="https://img.shields.io/badge/Fabric-0.19.3-blue" />
    <img src="https://img.shields.io/badge/Java-25-orange" />
    <img src="https://img.shields.io/badge/Rust-Native%20Bridge-red" />
  </p>
</p>

---

> ⚠️ **Disclaimer**
> 
> This project is based on the original [WaylandCraft](https://github.com/evvie-jpg/waylandcraft). Multi-player display features were AI-implemented. **Functionality and security are NOT guaranteed.** Use at your own risk.

---

# English

## Download

👉 **[Latest Release](https://github.com/almightydb/minercaft_fabric_waylandcraft_mods/releases/latest)** — Download `waylandcraft.jar` and drop it into your `mods/` folder.

## Known Issues

1. **Desktop window capture does not work** — XDG Portal + PipeWire capture is currently non-functional
2. **Flatpak apps won't display** — Flatpak sandbox overrides `WAYLAND_DISPLAY`, apps launch to desktop instead of in-game
3. **Window movement is limited** — Windows can only move up/down/left/right. No forward/backward movement, no angle adjustment

## Current Features

| Feature | Description |
|---------|-------------|
| Multi-Player Display | Share windows to other players, rendered in their world |
| Permission System | 4-level: NONE / VIEW / INTERACT / CONTROL |
| Resolution Settings | Configurable scale (0.1x – 1.0x) |
| Bitrate Control | Token Bucket algorithm with configurable max bitrate |
| Adaptive Quality | Auto-adjust resolution and quality based on bandwidth |
| Performance Optimization | PBO async readback, GPU scaling, direct memory texture write |

> Most features are command-line driven via `/wl` command.

## Commands

### Share Management

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

### Permission Management

| Command | Function |
|------|------|
| `/wl perm list` | List all permissions |
| `/wl perm default <PERM>` | Set default permission |
| `/wl perm allow <player> <PERM>` | Add to whitelist |
| `/wl perm deny <player>` | Add to blacklist |
| `/wl perm remove <player>` | Remove player |

### Window Management

| Command | Function |
|------|------|
| `/wl list windows` | List windows in compositor |
| `/wl list apps` | List launchable apps |
| `/wl give create <name>` | Launch app to compositor |
| `/wl remove <handle>` | Remove window item |
| `/wl close <handle>` | Close window |
| `/wl resize <handle> <w> <h>` | Resize window |

### Basic Controls

| Key | Function |
|------|------|
| `B` | Window manager |
| `V` | App launcher |
| `N` | Shared window manager |
| `G` | Capture/release keyboard |
| `Right-click hold` + WindowItem | Display window in world |

### Config Parameters

| Param | Description | Range |
|------|------|------|
| `scale` | Resolution scale | 0.1 – 1.0 |
| `quality` | JPEG quality | 0.1 – 1.0 |
| `fps` | Max framerate | 5 – 120 |
| `bitrate` | Max bitrate (kbps) | 0 = unlimited |
| `diffThreshold` | Pixel change threshold | 0.001 – 1.0 |

### Presets

| Preset | Scale | Quality | FPS | Bitrate |
|------|-------|---------|-----|---------|
| `performance` | 0.25 | 0.5 | 60 | 1000kbps |
| `balanced` | 0.5 | 0.7 | 30 | 2000kbps |
| `quality` | 1.0 | 1.0 | 30 | unlimited |
| `lowlatency` | 0.35 | 0.6 | 60 | 1500kbps |

## Build

```bash
# Prerequisites: Java 25, Rust toolchain, Wayland dev libs
apt install libwayland-dev libxkbcommon-dev pkg-config libclang-dev

# 1. Build Rust native library
source ~/.cargo/env
cd native && cargo build --release
cp target/release/libwaylandcraft.so target/debug/libwaylandcraft.so

# 2. Build Java mod
cd /workspace/waylandcraft
./gradlew clean build

# Output: build/libs/waylandcraft.jar (~2.0MB)
```

---

# 中文

## 下载

👉 **[最新 Release](https://github.com/almightydb/minercaft_fabric_waylandcraft_mods/releases/latest)** — 下载 `waylandcraft.jar`，放入 `mods/` 文件夹即可。

## 已知问题

1. **无法捕获桌面窗口** — XDG Portal + PipeWire 捕获目前不可用
2. **Flatpak 类应用无法显示** — Flatpak 沙盒覆盖了 `WAYLAND_DISPLAY`，应用会启动到桌面而非游戏内
3. **窗口移动受限** — 窗口只能上下左右移动，无法前后移动，无法调整角度

## 已实现功能

| 功能 | 说明 |
|------|------|
| 多人窗口共享 | 将窗口共享给其他玩家，在对方游戏世界中渲染 |
| 权限管理 | 四级：NONE / VIEW / INTERACT / CONTROL |
| 分辨率设置 | 可配置缩放比例 (0.1x – 1.0x) |
| 码率控制 | Token Bucket 算法，可配置最大码率 |
| 自适应画质 | 根据带宽自动调整分辨率和质量 |
| 性能优化 | PBO 异步回读、GPU 缩放、直接内存写入纹理 |

> 大部分功能通过命令行执行（`/wl` 命令）。

## 命令系统

### 共享管理

| 命令 | 功能 |
|------|------|
| `/wl share start <handle>` | 开始共享窗口 |
| `/wl unshare <handle>` | 停止共享 |
| `/wl share quality <handle> <s> <q> <fps>` | 设置画质（缩放、质量、帧率） |
| `/wl share quality-reset <handle>` | 重置画质为默认值 |
| `/wl share config <handle> <param> <value>` | 设置单个参数 |
| `/wl share preset <handle> <preset>` | 应用预设 |
| `/wl share info <handle>` | 显示当前配置 |
| `/wl share resolution <handle> <w> <h>` | 设置目标分辨率 |
| `/wl share stats <handle>` | 显示共享统计 |

### 权限管理

| 命令 | 功能 |
|------|------|
| `/wl perm list` | 列出所有权限 |
| `/wl perm default <PERM>` | 设置默认权限 |
| `/wl perm allow <player> <PERM>` | 加入白名单 |
| `/wl perm deny <player>` | 加入黑名单 |
| `/wl perm remove <player>` | 移除玩家 |

### 窗口管理

| 命令 | 功能 |
|------|------|
| `/wl list windows` | 列出 compositor 中的窗口 |
| `/wl list apps` | 列出可启动的应用 |
| `/wl give create <name>` | 启动应用到 compositor |
| `/wl remove <handle>` | 移除窗口物品 |
| `/wl close <handle>` | 关闭窗口 |
| `/wl resize <handle> <w> <h>` | 调整窗口大小 |

### 基本操作

| 按键 | 功能 |
|------|------|
| `B` | 窗口管理器 |
| `V` | 应用启动器 |
| `N` | 共享窗口管理器 |
| `G` | 捕获/释放键盘 |
| `右键长按` + WindowItem | 在世界中显示窗口 |

### 配置参数

| 参数 | 说明 | 范围 |
|------|------|------|
| `scale` | 分辨率缩放 | 0.1 – 1.0 |
| `quality` | JPEG 质量 | 0.1 – 1.0 |
| `fps` | 最大帧率 | 5 – 120 |
| `bitrate` | 最大码率 (kbps) | 0 = 无限 |
| `diffThreshold` | 像素变化阈值 | 0.001 – 1.0 |

### 预设配置

| 预设 | 缩放 | 质量 | 帧率 | 码率 |
|------|-------|---------|-----|---------|
| `performance` | 0.25 | 0.5 | 60 | 1000kbps |
| `balanced` | 0.5 | 0.7 | 30 | 2000kbps |
| `quality` | 1.0 | 1.0 | 30 | 无限 |
| `lowlatency` | 0.35 | 0.6 | 60 | 1500kbps |

## 构建

```bash
# 前置要求：Java 25, Rust 工具链, Wayland 开发库
apt install libwayland-dev libxkbcommon-dev pkg-config libclang-dev

# 1. 编译 Rust 原生库
source ~/.cargo/env
cd native && cargo build --release
cp target/release/libwaylandcraft.so target/debug/libwaylandcraft.so

# 2. 编译 Java mod
cd /workspace/waylandcraft
./gradlew clean build

# 输出：build/libs/waylandcraft.jar (~2.0MB)
```

---

## Tech Stack

| Layer | Technology |
|------|------|
| Game | Java 25, Fabric Loader 0.19.3, Fabric API 0.151.0 |
| Native Bridge | Rust, JNI |
| Wayland | Smithay, wayland-client, wlr-foreign-toplevel-management |
| Image | PBO double-buffer, glBlitFramebuffer, JPEG, MemoryUtil |
| Network | Fabric Networking API, custom Payload protocol |

## License

MIT License

## Acknowledgments

- [WaylandCraft](https://github.com/evvie-jpg/waylandcraft) — Original project
- [Smithay](https://github.com/Smithay/smithay) — Wayland compositor framework
- [Fabric](https://fabricmc.net/) — Minecraft mod loader
