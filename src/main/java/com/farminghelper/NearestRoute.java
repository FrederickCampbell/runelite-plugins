package com.farminghelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.runelite.api.coords.WorldPoint;

/**
 * Optional straight-line proximity heuristic. This is NOT a path-distance
 * estimator: water, obstacles, transport networks and plane changes are not
 * modeled. Advisor's configured route remains the default.
 */
final class NearestRoute
{
    private NearestRoute() {}

    static List<FarmRunPatch> order(List<FarmRunPatch> patches, WorldPoint origin,
                                    Function<FarmRunPatch, WorldPoint> location)
    {
        List<FarmRunPatch> remaining = new ArrayList<>(patches);
        List<FarmRunPatch> result = new ArrayList<>(patches.size());
        WorldPoint current = origin;
        while (!remaining.isEmpty())
        {
            int bestIndex = 0;
            long bestDistance = Long.MAX_VALUE;
            for (int i = 0; i < remaining.size(); ++i)
            {
                WorldPoint candidate = location.apply(remaining.get(i));
                long distance = estimate(current, candidate);
                if (distance < bestDistance)
                {
                    bestDistance = distance;
                    bestIndex = i;
                }
            }
            FarmRunPatch chosen = remaining.remove(bestIndex);
            result.add(chosen);
            WorldPoint next = location.apply(chosen);
            if (next != null) current = next;
        }
        return result;
    }

    static long estimate(WorldPoint from, WorldPoint to)
    {
        if (from == null || to == null) return Long.MAX_VALUE / 2;
        long dx = (long) from.getX() - to.getX();
        long dy = (long) from.getY() - to.getY();
        // Different planes are penalized, not treated as adjoining tiles.
        long planePenalty = from.getPlane() == to.getPlane() ? 0 : 2000000L;
        return dx * dx + dy * dy + planePenalty;
    }
}
