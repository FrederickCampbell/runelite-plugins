package com.farminghelper;

import com.farminghelper.queststate.QuestFarmingStateReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogCoverageTest
{
    @Test public void allNinetyNineAdvisorPatchIdentitiesMatchVendoredQuestHelper()
    {
        QuestFarmingStateReader reader = new QuestFarmingStateReader(null, null);
        Set<String> keys = new HashSet<>();
        int physicalCount = 0;
        for (FarmRunPatch group : FarmRunCatalog.patches())
        {
            List<String> names = reader.patchNamesForType(group.getPatchType().name(), group.getLocation());
            assertEquals(group.getDisplayName(), group.getPatchCount(), names.size());
            List<FarmRunPatch> patches = PatchNames.expand(group, names);
            assertEquals(group.getDisplayName(), group.getPatchCount(), patches.size());
            for (FarmRunPatch patch : patches)
            {
                assertTrue("Duplicate identity " + FarmRouteManager.key(patch), keys.add(FarmRouteManager.key(patch)));
                assertFalse(CropCatalog.crops(patch.getPatchType()).isEmpty());
                physicalCount++;
            }
        }
        assertEquals(99, physicalCount);
    }
}
