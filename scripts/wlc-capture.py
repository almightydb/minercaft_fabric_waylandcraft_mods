#!/usr/bin/env python3
"""WaylandCraft screen capture helper.
Uses xdg-desktop-portal ScreenCast to capture desktop windows.

Usage:
  wlc-capture.py list              - List available windows (JSON)
  wlc-capture.py capture <node_id> - Capture window by PipeWire node ID, output JPEG frames to stdout
  wlc-capture.py screenshot <path> - Take a screenshot of the whole screen
"""

import sys
import os
import json
import struct
import subprocess
import tempfile
import signal

# D-Bus session bus
DBUS_ADDR = os.environ.get("DBUS_SESSION_BUS_ADDRESS", f"unix:path=/run/user/{os.getuid()}/bus")

def get_bus():
    import dbus
    return dbus.SessionBus()

def list_windows():
    """List windows using wmctrl (X11) or gnome-shell eval."""
    windows = []
    
    # Try wmctrl first (works via Xwayland)
    try:
        out = subprocess.check_output(["wmctrl", "-l", "-x"], text=True, timeout=5)
        for line in out.strip().split("\n"):
            parts = line.split(None, 4)
            if len(parts) >= 4:
                wid = parts[0]
                wm_class = parts[2]
                title = parts[3] if len(parts) == 4 else parts[4] if len(parts) == 5 else ""
                # Skip desktop and panel
                if "Desktop" in title or "panel" in wm_class.lower():
                    continue
                windows.append({
                    "id": wid,
                    "title": title,
                    "wm_class": wm_class,
                    "source": "wmctrl"
                })
        if windows:
            return windows
    except Exception:
        pass
    
    # Try xprop on root window
    try:
        out = subprocess.check_output(
            ["xprop", "-root", "_NET_CLIENT_LIST_STACKING"],
            text=True, timeout=5
        )
        # Parse window IDs
        if "window id" in out:
            wid_str = out.split("=")[1].strip()
            for wid in wid_str.split(","):
                wid = wid.strip()
                if not wid or wid == "0x0":
                    continue
                try:
                    title_out = subprocess.check_output(
                        ["xprop", "-id", wid, "WM_NAME"],
                        text=True, timeout=2
                    )
                    title = title_out.split("=")[1].strip().strip('"')
                    class_out = subprocess.check_output(
                        ["xprop", "-id", wid, "WM_CLASS"],
                        text=True, timeout=2
                    )
                    wm_class = class_out.split("=")[1].strip().strip('"').split('", "')[0].strip('"')
                    if title:
                        windows.append({
                            "id": wid,
                            "title": title,
                            "wm_class": wm_class,
                            "source": "xprop"
                        })
                except Exception:
                    continue
    except Exception:
        pass
    
    return windows

def capture_screen_screenshot(output_path):
    """Take a screenshot using gnome-screenshot or import."""
    try:
        subprocess.run(["gnome-screenshot", "-f", output_path], timeout=10, check=True)
        return True
    except Exception:
        pass
    
    try:
        subprocess.run(["import", "-window", "root", output_path], timeout=10, check=True)
        return True
    except Exception:
        pass
    
    # Try grim (Wayland)
    try:
        subprocess.run(["grim", output_path], timeout=10, check=True)
        return True
    except Exception:
        pass
    
    return False

def capture_window_ffmpeg(output_dir, fps=5):
    """Capture screen using ffmpeg pipewire/grab."""
    # Try pipewire-based capture
    cmd = [
        "ffmpeg",
        "-f", "pipewire",
        "-i", "default",
        "-vf", f"fps={fps}",
        "-f", "image2",
        "-q:v", "5",
        os.path.join(output_dir, "frame_%04d.jpg")
    ]
    try:
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        return proc
    except Exception:
        pass
    
    # Try x11grab via Xwayland
    cmd = [
        "ffmpeg",
        "-f", "x11grab",
        "-video_size", "1920x1080",
        "-framerate", str(fps),
        "-i", ":0",
        "-vf", f"fps={fps}",
        "-f", "image2",
        "-q:v", "5",
        os.path.join(output_dir, "frame_%04d.jpg")
    ]
    try:
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        return proc
    except Exception:
        pass
    
    return None

def main():
    if len(sys.argv) < 2:
        print("Usage: wlc-capture.py <list|capture|screenshot> [args...]", file=sys.stderr)
        sys.exit(1)
    
    cmd = sys.argv[1]
    
    if cmd == "list":
        windows = list_windows()
        print(json.dumps(windows, ensure_ascii=False))
    
    elif cmd == "screenshot":
        if len(sys.argv) < 3:
            print("Usage: wlc-capture.py screenshot <output_path>", file=sys.stderr)
            sys.exit(1)
        output_path = sys.argv[2]
        if capture_screen_screenshot(output_path):
            print(json.dumps({"ok": True, "path": output_path}))
        else:
            print(json.dumps({"ok": False, "error": "No screenshot tool available"}))
    
    elif cmd == "capture":
        fps = int(sys.argv[2]) if len(sys.argv) > 2 else 5
        output_dir = tempfile.mkdtemp(prefix="wlc-capture-")
        print(json.dumps({"ok": True, "dir": output_dir, "fps": fps}))
        
        proc = capture_window_ffmpeg(output_dir, fps)
        if proc is None:
            print(json.dumps({"ok": False, "error": "No capture tool available"}))
            sys.exit(1)
        
        # Handle SIGTERM gracefully
        def cleanup(sig, frame):
            proc.terminate()
            proc.wait()
            sys.exit(0)
        signal.signal(signal.SIGTERM, cleanup)
        
        proc.wait()
    
    else:
        print(f"Unknown command: {cmd}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
