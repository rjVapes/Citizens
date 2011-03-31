package com.fullwall.Citizens.Utils;

import org.bukkit.Location;

import com.fullwall.Citizens.Citizens;

public class NPCLocation {

	private int x;
	private int y;
	private int z;
	private float yaw;
	private float pitch;
	private String world;
	private Citizens plugin;
	private int UID;

	public NPCLocation(Citizens plugin, Location loc, int UID) {
		this.plugin = plugin;
		this.x = loc.getBlockX();
		this.y = loc.getBlockY();
		this.z = loc.getBlockZ();
		this.pitch = loc.getPitch();
		this.yaw = loc.getYaw();
		this.world = loc.getWorld().getName();
		this.UID = UID;
	}

	public int getZ() {
		return this.z;
	}

	public int getX() {
		return this.x;
	}

	public int getUID() {
		return this.UID;
	}

	public Location getLocation() {
		return new Location(plugin.getServer().getWorld(this.world), x, y, z,
				pitch, yaw);
	}
}
