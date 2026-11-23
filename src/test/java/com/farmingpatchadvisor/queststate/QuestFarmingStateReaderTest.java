package com.farmingpatchadvisor.queststate;

import net.runelite.client.plugins.timetracking.Tab;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuestFarmingStateReaderTest
{
    private final FarmingWorld world = new FarmingWorld();

    @Test
    public void questHelperContainsExistingHerbTreeAndAllotmentPatches()
    {
        assertNotNull(QuestFarmingStateReader.find(world, Tab.HERB, "Falador", null));
        assertNotNull(QuestFarmingStateReader.find(world, Tab.TREE, "Farming Guild", null));
        assertNotNull(QuestFarmingStateReader.find(world, Tab.ALLOTMENT, "Catherby", "North"));
        assertNotNull(QuestFarmingStateReader.find(world, Tab.ALLOTMENT, "Catherby", "South"));
    }

    @Test
    public void harmonyAliasMatchesQuestHelperWorld()
    {
        assertNotNull(QuestFarmingStateReader.find(world, Tab.HERB, "Harmony Island", null));
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
