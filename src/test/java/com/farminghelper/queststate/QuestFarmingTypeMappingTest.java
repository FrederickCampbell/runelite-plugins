package com.farminghelper.queststate;

import org.junit.Test;
import static org.junit.Assert.*;

public class QuestFarmingTypeMappingTest
{
    @Test
    public void advisorCropCategoriesMapToExactQuestHelperImplementations()
    {
        String[] types = {"HERB", "ALLOTMENT", "FLOWER", "TREE",
            "FRUIT_TREE", "CALQUAT", "HARDWOOD_TREE", "HOPS", "BUSH",
            "CACTUS", "MUSHROOM", "BELLADONNA", "SPIRIT_TREE",
            "SEAWEED", "GRAPEVINE", "CELASTRUS", "REDWOOD",
            "HESPORI", "ANIMA", "CRYSTAL_TREE", "CORAL"};
        for (String type : types)
        {
            assertNotNull(type, QuestFarmingStateReader.implementation(type));
        }
        assertEquals(PatchImplementation.GRAPES,
            QuestFarmingStateReader.implementation("GRAPEVINE"));
    }

    @Test
    public void multiTabLookupsNeverConflateTreeKinds()
    {
        FarmingWorld world = new FarmingWorld();
        assertNotNull(QuestFarmingStateReader.lookup("HARDWOOD_TREE","Fossil Island","East"));
        assertNull(QuestFarmingStateReader.lookup("TREE","Fossil Island","East"));
    }
}
