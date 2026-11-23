package com.farmingpatchadvisor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Split Advisor's paired allotment stop into Quest Helper's individual varbits. */
final class DirectorAllotments
{
    private DirectorAllotments() {}

    static List<FarmRunPatch> split(FarmRunPatch grouped, List<String> questHelperNames)
    {
        if (grouped.getPatchType() != PatchType.ALLOTMENT ||
            questHelperNames == null || questHelperNames.size() != grouped.getPatchCount())
        {
            return Collections.emptyList();
        }
        List<FarmRunPatch> result = new ArrayList<>();
        for (String name : questHelperNames)
        {
            result.add(new FarmRunPatch(grouped.getLocation(), name,
                PatchType.ALLOTMENT, 1));
        }
        return result;
    }
}
