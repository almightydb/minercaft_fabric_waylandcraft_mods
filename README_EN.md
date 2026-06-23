# WaylandCraft 🎮🪟

**Run Linux desktop apps inside Minecraft** — A Fabric mod that integrates a Wayland compositor into Minecraft, allowing players to view and interact with Linux desktop windows in-game. Supports multi-player window sharing.

> ⚠️ This project is based on the original [WaylandCraft](https://github.com/evvie-jpg/waylandcraft). Multi-player display features were AI-implemented. **Functionality and security are NOT guaranteed.** Use at your own risk.

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-26.1.2-green" />
  <img src="https://img.shields.io/badge/Fabric-0.19.3-blue" />
  <img src="https://img.shields.io/badge/Java-25-orange" />
  <img src="https://img.shields.io/badge/Rust-Native%20Bridge-red" />
</p>

---

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
