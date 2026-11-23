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
    }

    private DirectorWaypoints() {}

    private static void at(Map<String, WorldPoint> map, String name, int x, int y)
    {
        map.put(name, new WorldPoint(x, y, 0));
    }

    static WorldPoint point(PatchType type, String location)
    {
        return (type == PatchType.TREE ? TREES : HERBS).get(location);
    }

    static String accessIssue(Client client, PatchType type, String location,
                              int farmingLevel, boolean acceptUnverified)
    {
        if ("Farming Guild".equals(location))
        {
            int min = type == PatchType.ALLOTMENT ? 45 : 65;
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
                "Auburnvale".equals(location)))
        {
            return location + " access is not verified; enable unverified locations "
                + "in settings if you know you have access.";
        }
        return null;
    }
}
