package com.farminghelper.queststate;

import net.runelite.client.plugins.timetracking.Tab;
import org.junit.Test;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HashTable;
import net.runelite.api.Player;
import net.runelite.api.WidgetNode;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;
import static org.mockito.Mockito.*;
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
        var reader = new QuestFarmingStateReader(client, config);
        org.mockito.Mockito.when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);
        org.mockito.Mockito.when(config.getRSProfileKey()).thenReturn("test");
        var patch=QuestFarmingStateReader.lookup("HOPS","Yanille","");
        long now=System.currentTimeMillis()/1000;
        int[][] dead={{139,141},{150,153},{163,167},{178,183},{195,201},{209,211},{220,223},{230,231},{239,241},{250,253}};
        for(int[] range:dead)for(int value=range[0];value<=range[1];value++)
        {
            assertEquals("live "+value,CropState.DEAD,QuestFarmingStateReader.normalizedState(PatchImplementation.HOPS,value,PatchImplementation.HOPS.forVarbitValue(value)));
            org.mockito.Mockito.when(config.getConfiguration("timetracking","test",patch.configKey())).thenReturn(value+":"+(now-86400));
            assertEquals("remote "+value,CropState.DEAD,reader.observeType("HOPS","Yanille","",null).state);
        }
        for(String invalid:new String[]{"","not-a-number:1","3:invalid","3:0","3:"+(now+86400)})
        {
            org.mockito.Mockito.when(config.getConfiguration("timetracking","test",patch.configKey())).thenReturn(invalid);
            assertNull(reader.observeType("HOPS","Yanille","",null).state);
        }
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
        org.mockito.Mockito.when(config.getConfiguration("timetracking","test",patch.configKey()))
            .thenReturn("84:" + (live.observedAtMillis / 1000 + 86400));
        assertEquals(CropState.DEAD, reader.observeType("HOPS","Yanille","",marker).state);
        org.mockito.Mockito.when(config.getRSProfileKey()).thenReturn("second");
        assertNull(reader.observeType("HOPS","Yanille","",marker).state);
        assertTrue(reader.hasWeeds("HOPS",217));assertTrue(reader.hasWeeds("HOPS",126));
        assertFalse(reader.hasWeeds("HOPS",3));assertFalse(reader.hasWeeds("HOPS",220));
    }

    @Test public void seasonalTimingUsesWorldFlagsAndTheCharactersTickOffset()
    {
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        FarmingHandler handler = new FarmingHandler(client, config);
        FarmingPatch oak = QuestFarmingStateReader.lookup("TREE", "Varrock", "");
        FarmingPatch cactus = QuestFarmingStateReader.lookup("CACTUS", "Al Kharid", "");
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.SEASONAL));
        // Four 8-minute growth ticks for oak on Leagues. Ordinary oak takes four 40-minute ticks.
        assertEquals(CropState.UNCHECKED, handler.predict(oak, "p", 8, 4800, 6720));
        when(config.getConfiguration(TimeTrackingConfig.CONFIG_GROUP, "p", TimeTrackingConfig.FARM_TICK_OFFSET_PRECISION, int.class)).thenReturn(40);
        when(config.getConfiguration(TimeTrackingConfig.CONFIG_GROUP, "p", TimeTrackingConfig.FARM_TICK_OFFSET, int.class)).thenReturn(1);
        assertEquals(CropState.GROWING, handler.predict(cactus, "p", 15, 4800, 4979));
        assertEquals(CropState.HARVESTABLE, handler.predict(cactus, "p", 15, 4800, 4980));
        for (var flags : List.of(EnumSet.noneOf(WorldType.class), EnumSet.of(WorldType.SEASONAL, WorldType.DEADMAN)))
        {
            when(client.getWorldType()).thenReturn(flags);
            assertEquals(CropState.GROWING, handler.predict(oak, "p", 8, 4800, 6720));
            assertEquals(CropState.GROWING, handler.predict(cactus, "p", 15, 4800, 4980));
        }
    }

    @Test public void repeatedLiveObservationSupersedesAnInterveningTrackerRecord()
    {
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        Player player = mock(Player.class);
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1000L, 3000L, 4000L);
        var reader = new QuestFarmingStateReader(client, config, clock);
        var marker = new WorldPoint(2572, 3102, 0);
        var patch = QuestFarmingStateReader.lookup("HOPS", "Yanille", "");
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(config.getRSProfileKey()).thenReturn("p");
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldLocation()).thenReturn(marker);
        when(client.getVarbitValue(patch.getVarbit())).thenReturn(220);
        reader.observeType("HOPS", "Yanille", "", marker);
        reader.observeType("HOPS", "Yanille", "", marker);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        when(config.getConfiguration("timetracking", "p", patch.configKey())).thenReturn("3:2", "3:4");
        assertEquals(CropState.DEAD, reader.observeType("HOPS", "Yanille", "", marker).state);
        assertEquals(CropState.EMPTY, reader.observeType("HOPS", "Yanille", "", marker).state);
    }

    @Test @SuppressWarnings("unchecked")
    public void modalDefersLocalPatchEvidenceUntilVarbitsCanUpdate()
    {
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        Player player = mock(Player.class);
        HashTable<WidgetNode> table = mock(HashTable.class);
        WidgetNode modal = mock(WidgetNode.class);
        when(table.iterator()).thenReturn(List.of(modal).iterator(), List.<WidgetNode>of().iterator(), List.of(modal).iterator(), List.of(modal).iterator());
        when(client.getComponentTable()).thenReturn(table);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(config.getRSProfileKey()).thenReturn("p");
        when(client.getLocalPlayer()).thenReturn(player);
        var marker = new WorldPoint(2572, 3102, 0);
        when(player.getWorldLocation()).thenReturn(marker);
        var reader = new QuestFarmingStateReader(client, config);
        assertFalse(reader.observeType("HOPS", "Yanille", "", marker).known());
        verify(client, never()).getVarbitValue(anyInt());
        when(client.getTickCount()).thenReturn(1);
        var fresh = reader.observeType("HOPS", "Yanille", "", marker);
        assertEquals(QuestFarmingStateReader.Source.LIVE, fresh.source);
        when(client.getTickCount()).thenReturn(2);
        var held = reader.observeType("HOPS", "Yanille", "", marker);
        assertTrue(held.localGuidance());
        assertEquals(QuestFarmingStateReader.Source.LOCAL_SNAPSHOT, held.source);
        assertEquals(fresh.observedAtMillis, held.observedAtMillis);
        assertEquals(fresh.state, held.state);
        when(client.getTickCount()).thenReturn(9);
        assertFalse(reader.observeType("HOPS", "Yanille", "", marker).known());
    }
}
