package com.farminghelper.queststate;

import net.runelite.client.plugins.timetracking.Tab;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuestFarmingStateReaderTest
{
    private final FarmingWorld world = new FarmingWorld();

    @Test
    public void questHelperContainsExistingHerbTreeAndAllotmentPatches()
    {
        assertNotNull(QuestFarmingStateReader.lookup("HERB","Falador",""));
        assertNotNull(QuestFarmingStateReader.lookup("TREE","Farming Guild",""));
        assertNotNull(QuestFarmingStateReader.lookup("ALLOTMENT","Catherby","North"));
        assertNotNull(QuestFarmingStateReader.lookup("ALLOTMENT","Catherby","South"));
    }

    @Test
    public void canonicalAllotmentPatchesAreSeparate()
    {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (FarmingPatch patch : world.getTabs().get(Tab.ALLOTMENT))
        {
            if ("Falador".equals(patch.getRegion().getName()))
            {
                names.add(patch.getName());
            }
        }
        assertEquals(2, names.size());
        assertNotEquals(names.get(0), names.get(1));
    }

    @Test
    public void harmonyAliasMatchesQuestHelperWorld()
    {
        assertNotNull(QuestFarmingStateReader.lookup("HERB","Harmony Island",""));
    }

    @Test
    public void neverTreatUnknownTimeTrackingDataAsEmpty()
    {
        assertEquals(0L, QuestFarmingStateReader.timestampMillis(null));
        assertEquals(0L, QuestFarmingStateReader.timestampMillis(""));
        assertEquals(0L, QuestFarmingStateReader.timestampMillis("3:invalid"));
        assertEquals(0L, QuestFarmingStateReader.timestampMillis("3:0"));
        assertEquals(1000000000000L,
            QuestFarmingStateReader.timestampMillis("99:1000000000"));
    }
}
