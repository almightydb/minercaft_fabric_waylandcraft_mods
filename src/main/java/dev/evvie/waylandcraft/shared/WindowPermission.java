package dev.evvie.waylandcraft.shared;

public enum WindowPermission {
	NONE(0),
	VIEW(1),
	INTERACT(2),
	CONTROL(3);

	private final int level;

	WindowPermission(int level) {
		this.level = level;
	}

	public int getLevel() {
		return level;
	}

	public boolean hasPermission(WindowPermission required) {
		return this.level >= required.level;
	}
}
