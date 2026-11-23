package com.farminghelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable location grouping, not a new pathfinder or a claim of optimal travel. */
final class LocationOrder
{
    static List<FarmRunPatch> group(List<FarmRunPatch> patches)
    {
        Map<String, List<FarmRunPatch>> farms = new LinkedHashMap<>();
        for (FarmRunPatch patch : patches)
            farms.computeIfAbsent(patch.getLocation(), key -> new ArrayList<>()).add(patch);
        List<FarmRunPatch> result = new ArrayList<>();
        farms.values().forEach(result::addAll);
        return result;
    }
}
