package com.farmingpatchadvisor;

import com.farmingpatchadvisor.queststate.QuestFarmingStateReader;
import net.runelite.api.coords.WorldPoint;

/**
 * Uses exact Quest Helper patch ObjectStep locations, then previously observed
 * Advisor timer coordinates, then an explicitly labelled approximate region.
 * We never advertise an approximate region as an exact patch destination.
 */
final class DirectorTravelTarget
{
    final WorldPoint point;
    final boolean exact;
    final String source;

    private DirectorTravelTarget(WorldPoint point, boolean exact, String source)
    {
        this.point = point;
        this.exact = exact;
        this.source = source;
    }

    static DirectorTravelTarget choose(FarmRunPatch patch,
                                       QuestFarmingStateReader states,
                                       PatchTimerManager timers)
    {
        WorldPoint verified = DirectorWaypoints.exactPoint(patch);
        if (verified != null)
        {
            boolean patchExact = patch.getPatchType() == PatchType.HERB ||
                DirectorStepEngine.isTreeType(patch.getPatchType());
            return new DirectorTravelTarget(verified, patchExact,
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
            return new DirectorTravelTarget(hint, false,
                "Approximate area (multi-patch region)");
        }
        for (PatchTimer timer : timers.getTimers())
        {
            if (timer.getPatchType() == patch.getPatchType() &&
                patch.getLocation().equals(PatchLocationCatalog.name(timer.getPatchLocation())))
            {
                return new DirectorTravelTarget(timer.getPatchLocation(),
                    true, "Observed Advisor patch");
            }
        }
        WorldPoint region = states.regionHint(patch.getPatchType().name(), patch.getLocation());
        return region == null ? new DirectorTravelTarget(null, false, "Unknown")
            : new DirectorTravelTarget(region, false, "Approximate area (Quest Helper region)");
    }
}
