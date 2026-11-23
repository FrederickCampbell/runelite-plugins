package com.farminghelper;

import com.farminghelper.queststate.QuestFarmingStateReader;
import net.runelite.api.coords.WorldPoint;

/**
 * Uses exact Quest Helper patch ObjectStep locations, then previously observed
 * Advisor timer coordinates, then an explicitly labelled approximate region.
 * We never advertise an approximate region as an exact patch destination.
 */
final class TravelTarget
{
    final WorldPoint point;
    final boolean exact;
    final String source;

    private TravelTarget(WorldPoint point, boolean exact, String source)
    {
        this.point = point;
        this.exact = exact;
        this.source = source;
    }

    static TravelTarget playerWaypoint(WorldPoint point)
    {
        return new TravelTarget(point, false, "Saved player waypoint");
    }

    boolean routable()
    {
        return point != null && (exact || source.startsWith("Nearby herb") || source.equals("RuneLite farm area") ||
            source.equals("Saved player waypoint"));
    }

    /** Navigation must not inherit Advisor timer coordinates. */
    static TravelTarget navigation(FarmRunPatch patch, QuestFarmingStateReader states)
    {
        return choose(patch, states, null);
    }

    static TravelTarget choose(FarmRunPatch patch,
                                       QuestFarmingStateReader states,
                                       PatchTimerManager timers)
    {
        WorldPoint area = Waypoints.farmArea(patch);
        WorldPoint verified = Waypoints.exactPoint(patch);
        // Prefer Quest Helper object points, otherwise use published farm map markers.
        if (area != null && (verified == null || !StepEngine.isTreeType(patch.getPatchType())
            && patch.getPatchType() != PatchType.HERB))
            return new TravelTarget(area, false, "RuneLite farm area");
        if (verified != null)
        {
            boolean patchExact = patch.getPatchType() == PatchType.HERB ||
                StepEngine.isTreeType(patch.getPatchType());
            return new TravelTarget(verified, patchExact,
                patchExact ? "Quest Helper patch" : "Nearby herb-patch area");
        }
        if (patch.getPatchType() == PatchType.ALLOTMENT ||
            patch.getPatchType() == PatchType.SEAWEED ||
            patch.getPatchType() == PatchType.GRAPEVINE ||
            patch.getPatchType() == PatchType.CORAL)
        {
            // Multiple identical patch types in one region cannot be
            // identified by Advisor's region name alone.
            WorldPoint hint = states.regionHint(patch.getPatchType().name(),
                patch.getLocation());
            return new TravelTarget(hint, false,
                "Approximate area (multi-patch region)");
        }
        // Timer coordinates are available only to the independent observation path.
        if (timers != null) for (PatchTimer timer : timers.getTimers())
        {
            if (timer.getPatchType() == patch.getPatchType() &&
                patch.getLocation().equals(PatchLocationCatalog.name(timer.getPatchLocation())))
            {
                return new TravelTarget(timer.getPatchLocation(),
                    true, "Observed Advisor patch");
            }
        }
        WorldPoint region = states.regionHint(patch.getPatchType().name(), patch.getLocation());
        return region == null ? new TravelTarget(null, false, "Unknown")
            : new TravelTarget(region, false, "Approximate area (Quest Helper region)");
    }
}
