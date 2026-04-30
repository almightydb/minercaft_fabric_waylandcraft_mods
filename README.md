# waylandcraft
Wayland Compositor in Minecraft

## Implemented protocols
- core
	- wl_compositor
	- wl_subcompositor
	- wl_data_device_manager *(drag n' drop, clipboard)*
	- wl_shm
	- wl_seat *(pointer, keyboard)*
	- wl_output
- xdg-shell
- viewporter
- single-pixel-buffer-v1
- linux-dmabuf-v1
- cursor-shape-v1 *(not all cursors)*
- pointer-constraints-unstable-v1 *(only locked pointers)*
- relative-pointer-unstable-v1

## System dependencies
- OS: Linux
- Minecraft 26.1.2
- Fabric mod loader
- xkbcommon library 1.11.0
- xkbcommon tools (xkbcli)

## Disclaimer
This compositor still has lots of issues and bugs. Use it at your own risk or whatever.

The entire project was written **without the usage of any generative AI**.
