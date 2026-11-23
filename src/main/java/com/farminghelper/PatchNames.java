package com.farminghelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Expand Advisor's grouped stops using Quest Helper's individual patch names. */
final class PatchNames
{
    private PatchNames() {}

    static List<FarmRunPatch> expand(FarmRunPatch grouped, List<String> names)
    {
        if (names == null || names.isEmpty())
        {
            return grouped.getPatchCount() > 1 ? Collections.emptyList()
                : Collections.singletonList(grouped);
        }
        if (grouped.getPatchCount() > 1)
        {
            if (names.size() != grouped.getPatchCount())
            {
                return Collections.emptyList(); // Never invent missing varbit identities.
            }
            List<FarmRunPatch> split = new ArrayList<>();
            for (String name : names)
            {
                split.add(new FarmRunPatch(grouped.getLocation(), name,
                    grouped.getPatchType(), 1));
            }
            return split;
        }
        if (names.contains(grouped.getName()))
        {
            return Collections.singletonList(grouped);
        }
        if (names.size() == 1)
        {
            // Advisor might label the same patch "Fruit tree" while Quest
            // Helper calls it "". Preserve both location and crop type.
            return Collections.singletonList(new FarmRunPatch(grouped.getLocation(),
                names.get(0), grouped.getPatchType(), 1));
        }
        return Collections.emptyList();
    }
}
