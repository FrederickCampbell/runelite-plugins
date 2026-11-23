package com.farminghelper;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.coords.WorldPoint;

/**
 * Coordinates and verified access requirements adapted from Quest Helper's
 * HerbRun and TreeRun. This is the routing seam, not a second patch database.
 */
final class Waypoints
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

    private Waypoints() {}

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
            case HERB:
            case ALLOTMENT:
            case FLOWER:
                return HERBS.get(location); // Same farming area, not the precise subpatch tile.
            default:
                return null; // Never send a bush/mushroom/specialty run to an unrelated herb patch.

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

    enum AccessState { AVAILABLE, LOCKED, UNKNOWN }

    static final class Access
    {
        final AccessState state;
        final String detail;
        Access(AccessState state, String detail)
        {
            this.state = state;
            this.detail = detail;
        }
        String issue(boolean acceptUnverified)
        {
            return state == AccessState.LOCKED ||
                state == AccessState.UNKNOWN && !acceptUnverified ? detail : null;
        }
    }

    static int guildAccessLevel(PatchType type)
    {
        switch (type)
        {
            case ALLOTMENT: case FLOWER: case BUSH: case CACTUS: return 45;
            case FRUIT_TREE: case CELASTRUS: case SPIRIT_TREE: case REDWOOD: return 85;
            default: return 65;
        }
    }

    private static Access available(String detail) { return new Access(AccessState.AVAILABLE, detail); }
    private static Access locked(String detail) { return new Access(AccessState.LOCKED, detail); }
    private static Access unknown(String detail) { return new Access(AccessState.UNKNOWN, detail); }

    private static Access quest(Client client, Quest quest, String description, boolean partial)
    {
        if (client == null) return unknown(description + " not verified.");
        QuestState state = quest.getState(client);
        if (state == QuestState.FINISHED) return available(description + " completed.");
        if (partial && state == QuestState.IN_PROGRESS)
            return unknown(description + " started; exact access progress is unverified.");
        return locked(description + " required.");
    }

    /**
     * Known conditions from the upstream Quest Helper HerbRun / TreeRun
     * and Farming Guild tier access. Partial quest progress and unmapped
     * gates are not silently reported as unlocked.
     */
    static Access access(Client client, PatchType type, String location, int farmingLevel)
    {
        switch (location)
        {
            case "Farming Guild":
                int min = guildAccessLevel(type);
                return farmingLevel < min
                    ? locked("Farming Guild " + type.getDisplayName() + " requires level " + min + " Farming (unboosted).")
                    : available("Farming Guild tier: " + min + " Farming.");
            case "Morytania":
                return quest(client, Quest.PRIEST_IN_PERIL, "Priest in Peril", false);
            case "Troll Stronghold":
                return quest(client, Quest.MY_ARMS_BIG_ADVENTURE, "My Arm's Big Adventure", false);
            case "Weiss":
                return quest(client, Quest.MAKING_FRIENDS_WITH_MY_ARM, "Making Friends with My Arm", false);
            case "Harmony Island":
                if (type == PatchType.HERB)
                {
                    if (client == null) return unknown("Elite Morytania Diary not verified.");
                    return client.getVarbitValue(VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE) > 0
                        ? available("Elite Morytania Diary completed.")
                        : locked("Elite Morytania Diary required.");
                }
                return quest(client, Quest.THE_GREAT_BRAIN_ROBBERY, "The Great Brain Robbery", true);
            case "Civitas illa Fortis": case "Auburnvale": case "Kastori": case "Aldarin":
                return quest(client, Quest.CHILDREN_OF_THE_SUN, "Children of the Sun", false);
            case "Avium Savannah":
                return quest(client, Quest.THE_RIBBITING_TALE_OF_A_LILY_PAD_LABOUR_DISPUTE,
                    "The Ribbiting Tale of a Lily Pad Labour Dispute", false);
            case "Great Conch":
                return quest(client, Quest.TROUBLED_TORTUGANS, "Troubled Tortugans", false);
            case "Anglers' Retreat":
                if (client == null) return unknown("51 Sailing requirement unverified.");
                return client.getRealSkillLevel(Skill.SAILING) >= 51
                    ? available("51 Sailing requirement met.") : locked("51 Sailing required.");
            case "Fossil Island": case "Seaweed":
                return quest(client, Quest.BONE_VOYAGE, "Bone Voyage", false);
            case "Prifddinas":
                return quest(client, Quest.SONG_OF_THE_ELVES, "Song of the Elves", false);
            case "Lletya":
                return quest(client, Quest.MOURNINGS_END_PART_I, "Mourning's End Part I", true);
            case "Etceteria":
                return quest(client, Quest.THE_FREMENNIK_TRIALS, "The Fremennik Trials", false);
            default:
                return available("No special access gate in the catalog; transport still unverified.");
        }
    }

    static String accessIssue(Client client, PatchType type, String location,
                              int farmingLevel, boolean acceptUnverified)
    {
        return access(client, type, location, farmingLevel).issue(acceptUnverified);
    }
}
