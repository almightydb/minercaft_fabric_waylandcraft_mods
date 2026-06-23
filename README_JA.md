# WaylandCraft 🎮🪟

**Minecraft内でLinuxデスクトップアプリを実行** — WaylandコンポーザをMinecraftに統合するFabric Mod。プレイヤーはゲーム内でLinuxデスクトップウィンドウを表示・操作できます。マルチプレイヤーのウィンドウ共有に対応。

> ⚠️ このプロジェクトは[WaylandCraft](https://github.com/evvie-jpg/waylandcraft)のオリジナルプロジェクトに基づいています。マルチプレイヤー表示機能はAIが実装しました。**機能と安全性は保証されません。** 自己責任でご利用ください。

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-26.1.2-green" />
  <img src="https://img.shields.io/badge/Fabric-0.19.3-blue" />
  <img src="https://img.shields.io/badge/Java-25-orange" />
  <img src="https://img.shields.io/badge/Rust-Native%20Bridge-red" />
</p>

---

## ダウンロード

👉 **[最新リリース](https://github.com/almightydb/minercaft_fabric_waylandcraft_mods/releases/latest)** — `waylandcraft.jar`をダウンロードして`mods/`フォルダに入れてください。

## 既知の問題

1. **デスクトップウィンドウのキャプチャが動作しない** — XDG Portal + PipeWireキャプチャは現在使用不可
2. **Flatpakアプリが表示されない** — Flatpakサンドボックスが`WAYLAND_DISPLAY`を上書きし、アプリがゲーム内ではなくデスクトップに起動する
3. **ウィンドウの移動が制限されている** — ウィンドウは上下左右にのみ移動可能。前後の移動や角度調整は不可

## 実装済み機能

| 機能 | 説明 |
|------|------|
| マルチプレイヤー表示 | 他のプレイヤーにウィンドウを共有し、相手のゲーム世界にレンダリング |
| 権限管理 | 4段階：NONE / VIEW / INTERACT / CONTROL |
| 解像度設定 | 設定可能なスケール (0.1x – 1.0x) |
| ビットレート制御 | Token Bucketアルゴリズム、設定可能な最大ビットレート |
| 適応品質 | 帯域幅に基づいて解像度と品質を自動調整 |
| パフォーマンス最適化 | PBO非同期リードバック、GPUスケーリング、直接メモリテクスチャ書き込み |

> ほとんどの機能は`/wl`コマンドによるコマンドライン駆動です。

## コマンドシステム

### 共有管理

| コマンド | 機能 |
|------|------|
| `/wl share start <handle>` | ウィンドウ共有を開始 |
| `/wl unshare <handle>` | 共有を停止 |
| `/wl share quality <handle> <s> <q> <fps>` | 品質設定（スケール、品質、FPS） |
| `/wl share quality-reset <handle>` | 品質をデフォルトにリセット |
| `/wl share config <handle> <param> <value>` | 個別パラメータ設定 |
| `/wl share preset <handle> <preset>` | プリセット適用 |
| `/wl share info <handle>` | 現在の設定を表示 |
| `/wl share resolution <handle> <w> <h>` | 目標解像度設定 |
| `/wl share stats <handle>` | 共有統計を表示 |

### 権限管理

| コマンド | 機能 |
|------|------|
| `/wl perm list` | 全権限を一覧表示 |
| `/wl perm default <PERM>` | デフォルト権限を設定 |
| `/wl perm allow <player> <PERM>` | ホワイトリストに追加 |
| `/wl perm deny <player>` | ブラックリストに追加 |
| `/wl perm remove <player>` | プレイヤーを削除 |

### ウィンドウ管理

| コマンド | 機能 |
|------|------|
| `/wl list windows` | コンポーザのウィンドウ一覧 |
| `/wl list apps` | 起動可能なアプリ一覧 |
| `/wl give create <name>` | アプリをコンポーザに起動 |
| `/wl remove <handle>` | ウィンドウアイテムを削除 |
| `/wl close <handle>` | ウィンドウを閉じる |
| `/wl resize <handle> <w> <h>` | ウィンドウサイズ変更 |

### 基本操作

| キー | 機能 |
|------|------|
| `B` | ウィンドウマネージャー |
| `V` | アプリランチャー |
| `N` | 共有ウィンドウマネージャー |
| `G` | キーボードキャプチャ/解除 |
| `右クリック長押し` + WindowItem | 世界にウィンドウを表示 |

### 設定パラメータ

| パラメータ | 説明 | 範囲 |
|------|------|------|
| `scale` | 解像度スケール | 0.1 – 1.0 |
| `quality` | JPEG品質 | 0.1 – 1.0 |
| `fps` | 最大フレームレート | 5 – 120 |
| `bitrate` | 最大ビットレート (kbps) | 0 = 無制限 |
| `diffThreshold` | ピクセル変化閾値 | 0.001 – 1.0 |

### プリセット

| プリセット | スケール | 品質 | FPS | ビットレート |
|------|-------|---------|-----|---------|
| `performance` | 0.25 | 0.5 | 60 | 1000kbps |
| `balanced` | 0.5 | 0.7 | 30 | 2000kbps |
| `quality` | 1.0 | 1.0 | 30 | 無制限 |
| `lowlatency` | 0.35 | 0.6 | 60 | 1500kbps |

## ビルド

```bash
# 前提条件：Java 25, Rustツールチェーン, Wayland開発ライブラリ
apt install libwayland-dev libxkbcommon-dev pkg-config libclang-dev

# 1. Rustネイティブライブラリのビルド
source ~/.cargo/env
cd native && cargo build --release
cp target/release/libwaylandcraft.so target/debug/libwaylandcraft.so

# 2. Java Modのビルド
cd /workspace/waylandcraft
./gradlew clean build

# 出力：build/libs/waylandcraft.jar (~2.0MB)
```

## 技術スタック

| レイヤー | 技術 |
|------|------|
| ゲーム | Java 25, Fabric Loader 0.19.3, Fabric API 0.151.0 |
| ネイティブブリッジ | Rust, JNI |
| Wayland | Smithay, wayland-client, wlr-foreign-toplevel-management |
| 画像 | PBOダブルバッファ, glBlitFramebuffer, JPEG, MemoryUtil |
| ネットワーク | Fabric Networking API, カスタムPayloadプロトコル |

## ライセンス

MIT License

## 謝辞

- [WaylandCraft](https://github.com/evvie-jpg/waylandcraft) — オリジナルプロジェクト
- [Smithay](https://github.com/Smithay/smithay) — Waylandコンポーザフレームワーク
- [Fabric](https://fabricmc.net/) — Minecraft mod loader
