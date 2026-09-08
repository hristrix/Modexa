package net.hristrix.modexa.modules.homes;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record HomeRecord(
        String storageKey,
        String slot,
        String name,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public static HomeRecord fromLocation(String storageKey, String slot, String name, Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location has no world");
        }

        return new HomeRecord(
                storageKey,
                slot,
                name,
                world.getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    public HomeRecord withName(String newName) {
        return new HomeRecord(storageKey, slot, newName, worldName, x, y, z, yaw, pitch);
    }

    public HomeRecord withLocation(Location location) {
        return fromLocation(storageKey, slot, name, location);
    }
}
