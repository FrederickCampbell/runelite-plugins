package com.farmingpatchadvisor;

import net.runelite.api.coords.WorldPoint;

/**
 * Approximate walk-to-bank entrance targets sourced from common OSRS bank
 * areas. These are bank-area waypoints, not bank-booth interaction tiles.
 * They cannot prove quickest teleport-aware journey.
 */
final class DirectorBankStops
{
    enum Bank
    {
        AUTO("Nearest listed bank", null),
        CATHERBY("Catherby bank", new WorldPoint(2808, 3442, 0)),
        FALADOR_WEST("Falador west bank", new WorldPoint(2946, 3368, 0)),
        VARROCK_WEST("Varrock west bank", new WorldPoint(3185, 3438, 0)),
        ARDOUGNE_NORTH("Ardougne north bank", new WorldPoint(2616, 3332, 0));

        final String label;
        final WorldPoint point;

        Bank(String label, WorldPoint point)
        {
            this.label = label;
            this.point = point;
        }

        @Override
        public String toString() { return label; }
    }

    private DirectorBankStops() {}

    static Bank choose(Bank preference, WorldPoint from)
    {
        if (preference != null && preference != Bank.AUTO)
        {
            return preference;
        }
        Bank result = null;
        long best = Long.MAX_VALUE;
        for (Bank candidate : Bank.values())
        {
            if (candidate.point == null) continue;
            long distance = DirectorNearestRoute.estimate(from, candidate.point);
            if (distance < best)
            {
                best = distance;
                result = candidate;
            }
        }
        return result;
    }
}
