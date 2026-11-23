package com.farmingpatchadvisor;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;

/** Explicit player-chosen navigation waypoints; never used to infer crop state. */
final class DirectorWaypointPins
{
    private static final String GROUP = "ironman-farming-director";
    private DirectorWaypointPins() {}

    static WorldPoint decode(String stored)
    {
        if (stored == null) return null;
        String[] fields = stored.split(":", -1);
        if (fields.length != 3) return null;
        try
        {
            int x = Integer.parseInt(fields[0]), y = Integer.parseInt(fields[1]),
                plane = Integer.parseInt(fields[2]);
            if (x < 0 || x > 16383 || y < 0 || y > 16383 || plane < 0 || plane > 3) return null;
            return new WorldPoint(x, y, plane);
        }
        catch (NumberFormatException ignored) { return null; }
    }

    static String encode(WorldPoint point)
    {
        return point.getX() + ":" + point.getY() + ":" + point.getPlane();
    }

    static WorldPoint get(ConfigManager config, FarmRunPatch patch)
    {
        return config.getRSProfileKey() == null ? null : decode(config.getRSProfileConfiguration(
            GROUP, "directorWaypoint_" + FarmRouteManager.key(patch)));
    }

    static boolean save(ConfigManager config, FarmRunPatch patch, WorldPoint point)
    {
        if (config.getRSProfileKey() == null || point == null ||
            !patch.getLocation().equals(PatchLocationCatalog.name(point))) return false;
        config.setRSProfileConfiguration(GROUP, "directorWaypoint_" + FarmRouteManager.key(patch), encode(point));
        return true;
    }

    static void clear(ConfigManager config, FarmRunPatch patch)
    {
        if (config.getRSProfileKey() != null)
            config.unsetRSProfileConfiguration(GROUP, "directorWaypoint_" + FarmRouteManager.key(patch));
    }
}
