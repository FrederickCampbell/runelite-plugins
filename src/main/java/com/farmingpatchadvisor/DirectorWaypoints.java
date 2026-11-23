package com.farmingpatchadvisor;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;

/**
 * Coordinates and verified access requirements adapted from Quest Helper's
 * HerbRun and TreeRun. This is the routing seam, not a second patch database.
 */
final class DirectorWaypoints
{
    private static final Map<String, WorldPoint> HERBS = new HashMap<>();
    private static final Map<String, WorldPoint> TREES = new HashMap<>();
    private static final Map<String, WorldPoint> FRUITS = new HashMap<>();
    private static final Map<String, WorldPoint> CALQUATS = new HashMap<>();
    private static final Map<String, WorldPoint> HARDWOODS = new HashMap<>();

    static
    {
        at(HERBS, "Ardougne", 2670, 3374);
        at(HERBS, "Catherby", 2813, 3463);
        at(HERBS, "Falador", 3058, 3311);
        at(HERBS, "Kourend", 1738, 3550);
        at(HERBS, "Farming Guild", 1238, 3726);
        at(HERBS, "Harmony Island", 3789, 2837);
        at(HERBS, "Morytania", 3605, 3529);
        at(HERBS, "Troll Stronghold", 2826, 3694);
        at(HERBS, "Weiss", 2848, 3934);
        at(HERBS, "Civitas illa Fortis", 1582, 3094);

        at(TREES, "Lumbridge", 3193, 3231);
        at(TREES, "Falador", 3004, 3373);
        at(TREES, "Taverley", 2936, 3438);
        at(TREES, "Varrock", 3229, 3459);
        at(TREES, "Gnome Stronghold", 2436, 3415);
        at(TREES, "Farming Guild", 1232, 3736);
        at(TREES, "Auburnvale", 1367, 3322);

        // Exact Quest Helper TreeRun ObjectStep coordinates, not estimated locations.
        at(FRUITS, "Gnome Stronghold", 2476, 3446);
        at(FRUITS, "Tree Gnome Village", 2490, 3180);
        at(FRUITS, "Brimhaven", 2765, 3213);
        at(FRUITS, "Catherby", 2860, 3433);
        at(FRUITS, "Lletya", 2347, 3162);
        at(FRUITS, "Farming Guild", 1242, 3758);
        at(FRUITS, "Kastori", 1350, 3057);
        at(CALQUATS, "Tai Bwo Wannai", 2795, 3102);
        at(CALQUATS, "Kastori", 1366, 3033);
        at(CALQUATS, "Great Conch", 3129, 2406);
        at(HARDWOODS, "Fossil Island~West", 3702, 3837);
        at(HARDWOODS, "Fossil Island~Middle", 3708, 3833);
        at(HARDWOODS, "Fossil Island~East", 3715, 3835);
        at(HARDWOODS, "Avium Savannah", 1687, 2972);
        at(HARDWOODS, "Anglers' Retreat", 2470, 2704);
    }

    private DirectorWaypoints() {}

    private static void at(Map<String, WorldPoint> map, String name, int x, int y)
    {
        map.put(name, new WorldPoint(x, y, 0));
    }

    static WorldPoint point(PatchType type, String location)
    {
        switch (type)
        {
            case TREE: return TREES.get(location);
            case FRUIT_TREE: return FRUITS.get(location);
            case CALQUAT: return CALQUATS.get(location);
            case HARDWOOD_TREE: return HARDWOODS.get(location);
            default: return HERBS.get(location);
        }
    }

    static WorldPoint exactPoint(FarmRunPatch patch)
    {
        if (patch.getPatchType() == PatchType.HARDWOOD_TREE)
        {
            WorldPoint specific = HARDWOODS.get(patch.getLocation() + "~" + patch.getName());
            return specific != null ? specific : HARDWOODS.get(patch.getLocation());
        }
        return point(patch.getPatchType(), patch.getLocation());
    }

    static String accessIssue(Client client, PatchType type, String location,
                              int farmingLevel, boolean acceptUnverified)
    {
        if ("Farming Guild".equals(location))
        {
            int min = (type == PatchType.ALLOTMENT || type == PatchType.FLOWER) ? 45
                : type == PatchType.FRUIT_TREE ? 85
                : type == PatchType.REDWOOD ? 90 : 65;
            return farmingLevel < min ? "Farming Guild " + type.getDisplayName()
                + " requires level " + min + " Farming (unboosted)." : null;
        }
        if ("Morytania".equals(location) &&
            Quest.PRIEST_IN_PERIL.getState(client) != QuestState.FINISHED)
        {
            return "Morytania requires Priest in Peril.";
        }
        if ("Troll Stronghold".equals(location) &&
            Quest.MY_ARMS_BIG_ADVENTURE.getState(client) != QuestState.FINISHED)
        {
            return "Troll Stronghold requires My Arm's Big Adventure.";
        }
        if ("Weiss".equals(location) &&
            Quest.MAKING_FRIENDS_WITH_MY_ARM.getState(client) != QuestState.FINISHED)
        {
            return "Weiss requires Making Friends with My Arm.";
        }
        if (!acceptUnverified &&
            ("Harmony Island".equals(location) || "Civitas illa Fortis".equals(location) ||
                "Auburnvale".equals(location) || "Kastori".equals(location) ||
                "Great Conch".equals(location) || "Avium Savannah".equals(location) ||
                "Anglers' Retreat".equals(location)))
        {
            return location + " access is not verified; enable unverified locations "
                + "in settings if you know you have access.";
        }
        return null;
    }
}
