package com.farminghelper;

import org.junit.Test;
import static org.junit.Assert.*;

public class WaypointsTest
{
    @Test
    public void farmingGuildRequires65ForBothPatches()
    {
        assertTrue(issue(PatchType.TREE,
            "Farming Guild", 64).contains("65"));
        assertNull(issue(PatchType.TREE,
            "Farming Guild", 65));
        assertNull(issue(PatchType.HERB,
            "Farming Guild", 65));
    }

    @Test
    public void guildWingAccessIsSeparateFromCropLevel()
    {
        assertEquals(45, Waypoints.guildAccessLevel(PatchType.BUSH));
        assertEquals(45, Waypoints.guildAccessLevel(PatchType.CACTUS));
        assertEquals(65, Waypoints.guildAccessLevel(PatchType.ANIMA));
        assertEquals(85, Waypoints.guildAccessLevel(PatchType.SPIRIT_TREE));
        assertEquals(85, Waypoints.guildAccessLevel(PatchType.CELASTRUS));
        assertEquals(85, Waypoints.guildAccessLevel(PatchType.REDWOOD));
    }

    @Test
    public void missingAccountStateIsNotAnUnlock()
    {
        assertEquals("Children of the Sun not verified.",issue(PatchType.TREE,"Auburnvale",99));
        assertEquals("Mourning's End Part I not verified.",issue(PatchType.FRUIT_TREE,"Lletya",99));
        assertEquals("Elite Morytania Diary not verified.",issue(PatchType.HERB,"Harmony Island",99));
    }

    private static String issue(PatchType type,String site,int level)
    {return Waypoints.accessIssue(null,type,site,level,new java.util.EnumMap<>(net.runelite.api.Quest.class));}

    @Test public void repeatedPlotsShareAQuestObservationAndTheNextPollReadsAgain()
    {
        var client=org.mockito.Mockito.mock(net.runelite.api.Client.class);
        org.mockito.Mockito.when(client.getIntStack()).thenReturn(new int[]{2});
        var quests=new java.util.EnumMap<net.runelite.api.Quest,net.runelite.api.QuestState>(net.runelite.api.Quest.class);
        assertNull(Waypoints.accessIssue(client,PatchType.TREE,"Auburnvale",99,quests));
        assertNull(Waypoints.accessIssue(client,PatchType.FLOWER,"Kastori",99,quests));
        org.mockito.Mockito.verify(client).getIntStack();
        quests.clear();
        assertNull(Waypoints.accessIssue(client,PatchType.FLOWER,"Kastori",99,quests));
        org.mockito.Mockito.verify(client,org.mockito.Mockito.times(2)).getIntStack();
    }

    @Test
    public void standardWaypointsHaveCoordinates()
    {
        assertNotNull(Waypoints.destination(new FarmRunPatch("Falador","",PatchType.HERB)));
        assertNotNull(Waypoints.destination(new FarmRunPatch("Auburnvale","",PatchType.TREE)));
        assertNull(Waypoints.destination(new FarmRunPatch("Catherby","",PatchType.TREE)));
    }
}
