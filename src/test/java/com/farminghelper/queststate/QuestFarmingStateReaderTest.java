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
    public void underwaterSeaweedUsesItsLiveRegionDespitePlaneZeroMapMarker()
    {
        var client=org.mockito.Mockito.mock(net.runelite.api.Client.class);
        var config=org.mockito.Mockito.mock(net.runelite.client.config.ConfigManager.class);
        var player=org.mockito.Mockito.mock(net.runelite.api.Player.class);
        org.mockito.Mockito.when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);
        org.mockito.Mockito.when(config.getRSProfileKey()).thenReturn("test");
        org.mockito.Mockito.when(client.getLocalPlayer()).thenReturn(player);
        var marker=new net.runelite.api.coords.WorldPoint(3730,10271,0);
        org.mockito.Mockito.when(player.getWorldLocation()).thenReturn(new net.runelite.api.coords.WorldPoint(3730,10271,1));
        var reader=new QuestFarmingStateReader(client,config);
        for(String name:new String[]{"North","South"})
        {
            var patch=lookupForTest(name);
            org.mockito.Mockito.when(client.getVarbitValue(patch.getVarbit())).thenReturn(0);
            assertEquals(QuestFarmingStateReader.Source.LIVE,reader.observeType("SEAWEED","Seaweed",name,marker).source);
        }
        org.mockito.Mockito.when(player.getWorldLocation()).thenReturn(marker);
        assertNotEquals(QuestFarmingStateReader.Source.LIVE,reader.observeType("SEAWEED","Seaweed","North",marker).source);
        org.mockito.Mockito.when(player.getWorldLocation()).thenReturn(new net.runelite.api.coords.WorldPoint(3764,3899,0));
        assertNotEquals(QuestFarmingStateReader.Source.LIVE,reader.observeType("SEAWEED","Seaweed","South",marker).source);
    }
    private static FarmingPatch lookupForTest(String name)
    {return QuestFarmingStateReader.lookup("SEAWEED","Seaweed",name);}

    @Test
    public void historicalCompostCannotSurviveAnObservedCropRemoval()
    {
        var client=org.mockito.Mockito.mock(net.runelite.api.Client.class);
        var config=org.mockito.Mockito.mock(net.runelite.client.config.ConfigManager.class);
        var player=org.mockito.Mockito.mock(net.runelite.api.Player.class);
        org.mockito.Mockito.when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);
        org.mockito.Mockito.when(config.getRSProfileKey()).thenReturn("test");
        org.mockito.Mockito.when(client.getLocalPlayer()).thenReturn(player);
        var marker=new net.runelite.api.coords.WorldPoint(3229,3459,0);
        org.mockito.Mockito.when(player.getWorldLocation()).thenReturn(marker);
        var patch=QuestFarmingStateReader.lookup("TREE","Varrock","");
        var reader=new QuestFarmingStateReader(client,config);
        String key=patch.configKey()+".compost",group=net.runelite.client.plugins.timetracking.TimeTrackingConfig.CONFIG_GROUP;
        org.mockito.Mockito.when(config.getRSProfileConfiguration(group,key)).thenReturn("ULTRACOMPOST");
        org.mockito.Mockito.when(client.getVarbitValue(patch.getVarbit())).thenReturn(12);
        assertEquals(CropState.UNCHECKED,reader.observeType("TREE","Varrock","",marker).state);
        assertEquals(3,reader.compostTier("TREE","Varrock",""));
        org.mockito.Mockito.when(client.getVarbitValue(patch.getVarbit())).thenReturn(3);
        assertEquals(CropState.EMPTY,reader.observeType("TREE","Varrock","",marker).state);
        assertEquals(0,reader.compostTier("TREE","Varrock",""));
        org.mockito.Mockito.when(config.getRSProfileConfiguration(group,key)).thenReturn((String)null);
        assertEquals(0,reader.compostTier("TREE","Varrock",""));
        org.mockito.Mockito.when(config.getRSProfileConfiguration(group,key)).thenReturn("ULTRACOMPOST");
        assertEquals(3,reader.compostTier("TREE","Varrock",""));
        reader.observeType("TREE","Varrock","",marker);
        assertEquals(3,reader.compostTier("TREE","Varrock",""));
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

    @Test public void allDocumentedDeadHopsDecodeAndRemotePredictionDoesNotReviveThem()
    {
        var client=org.mockito.Mockito.mock(net.runelite.api.Client.class);
        var config=org.mockito.Mockito.mock(net.runelite.client.config.ConfigManager.class);
        var handler=new FarmingHandler(client,config);
        var patch=QuestFarmingStateReader.lookup("HOPS","Yanille","");
        long now=System.currentTimeMillis()/1000;
        int[][] dead={{139,141},{150,153},{163,167},{178,183},{195,201},{209,211},{220,223},{230,231},{239,241},{250,253}};
        for(int[] range:dead)for(int value=range[0];value<=range[1];value++)
        {
            assertEquals("live "+value,CropState.DEAD,QuestFarmingStateReader.normalizedState(PatchImplementation.HOPS,value,PatchImplementation.HOPS.forVarbitValue(value)));
            assertEquals("remote "+value,CropState.DEAD,handler.predictObserved(patch,"test",value+":"+(now-86400)));
        }
        for(String invalid:new String[]{"","not-a-number:1","3:invalid","3:0","3:"+(now+86400)})
            assertNull(handler.predictObserved(patch,"test",invalid));
    }

    @Test public void localDeadHopsOverrideHistoryAndUndecodableValuesStayUnknown()
    {
        var client=org.mockito.Mockito.mock(net.runelite.api.Client.class);
        var config=org.mockito.Mockito.mock(net.runelite.client.config.ConfigManager.class);
        var player=org.mockito.Mockito.mock(net.runelite.api.Player.class);
        var marker=new net.runelite.api.coords.WorldPoint(2572,3102,0);
        var patch=QuestFarmingStateReader.lookup("HOPS","Yanille","");
        org.mockito.Mockito.when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);
        org.mockito.Mockito.when(config.getRSProfileKey()).thenReturn("test");
        org.mockito.Mockito.when(client.getLocalPlayer()).thenReturn(player);
        org.mockito.Mockito.when(player.getWorldLocation()).thenReturn(marker);
        org.mockito.Mockito.when(client.getVarbitValue(patch.getVarbit())).thenReturn(220);
        var reader=new QuestFarmingStateReader(client,config);
        var live=reader.observeType("HOPS","Yanille","",marker);
        assertEquals(CropState.DEAD,live.state);assertEquals(QuestFarmingStateReader.Source.LIVE,live.source);
        org.mockito.Mockito.when(config.getConfiguration("timetracking","test",patch.configKey())).thenReturn("84:"+(live.observedAtMillis/1000));
        org.mockito.Mockito.when(client.getVarbitValue(patch.getVarbit())).thenReturn(999);
        assertNull(reader.observeType("HOPS","Yanille","",marker).state);
        org.mockito.Mockito.when(player.getWorldLocation()).thenReturn(new net.runelite.api.coords.WorldPoint(3200,3200,0));
        var remote=reader.observeType("HOPS","Yanille","",marker);
        assertEquals(CropState.DEAD,remote.state);
        assertTrue(reader.hasWeeds("HOPS",217));assertTrue(reader.hasWeeds("HOPS",126));
        assertFalse(reader.hasWeeds("HOPS",3));assertFalse(reader.hasWeeds("HOPS",220));
    }
}
