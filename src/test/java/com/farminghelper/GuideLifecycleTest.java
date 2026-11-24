package com.farminghelper;

import com.farminghelper.queststate.QuestFarmingStateReader;
import java.util.EnumSet;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class GuideLifecycleTest
{
    @Mock Client client;
    @Mock net.runelite.client.plugins.PluginManager plugins;
    @Mock net.runelite.client.plugins.Plugin shortestPath;
    @Mock ConfigManager config;
    @Mock ClientThread thread;
    @Mock ClientToolbar toolbar;
    @Mock OverlayManager overlays;
    @Mock PatchHighlight highlight;
    @Mock EventBus bus;
    @Spy FarmingHelperConfig legacy = new FarmingHelperConfig() {};
    @Mock QuestFarmingStateReader reader;
    @InjectMocks FarmingHelperPlugin plugin;

    @Test public void routesWithoutSeedsHonorsPreferencesAndStopsOnExplicitClose() throws Exception
    {
        Player player = mock(Player.class);
        WorldPoint start = new WorldPoint(3100,3200,0);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldLocation()).thenReturn(start);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getRealSkillLevel(Skill.FARMING)).thenReturn(72);
        when(client.getIntStack()).thenReturn(new int[]{2});
        when(client.getVarbitValue(VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE)).thenReturn(1);
        when(config.getRSProfileKey()).thenReturn("profile");
        when(config.getRSProfileConfiguration("farming-helper","patchEnabled_Falador~HERB~")).thenReturn("false");
        when(reader.observeType(anyString(),anyString(),anyString(),any())).thenReturn(
            new QuestFarmingStateReader.Observation(null,QuestFarmingStateReader.Source.NONE,0,null,""));
        doAnswer(invocation -> {
            ((Runnable)invocation.getArgument(0)).run();
            return null;
        }).when(thread).invokeLater(any(Runnable.class));
        when(shortestPath.getName()).thenReturn("Shortest Path");
        plugin.startUp();
        plugin.start(EnumSet.of(FarmRunType.HERB));
        assertEquals("Ardougne",plugin.activePatch().getLocation());
        verifyNoInteractions(bus);
        QuestFarmingStateReader.Observation growing = new QuestFarmingStateReader.Observation(
            com.farminghelper.queststate.CropState.GROWING,QuestFarmingStateReader.Source.LIVE,0,null,"");
        when(reader.observeType(eq("HERB"),eq("Ardougne"),anyString(),any())).thenReturn(growing);
        plugin.onGameTick(null);
        assertEquals("Catherby",plugin.activePatch().getLocation());
        when(reader.observeType(eq("HERB"),eq("Ardougne"),anyString(),any())).thenReturn(
            new QuestFarmingStateReader.Observation(null,QuestFarmingStateReader.Source.NONE,0,null,""));
        plugin.stop();
        verifyNoInteractions(bus);
        plugin.onPluginChanged(new net.runelite.client.events.PluginChanged(shortestPath,true));
        plugin.start(EnumSet.of(FarmRunType.HERB));
        assertEquals("Ardougne",plugin.activePatch().getLocation());
        verify(bus).post(new PluginMessage("shortestpath","path",Map.of("start",start,"target",new WorldPoint(2670,3374,0))));
        plugin.onGameTick(null);
        verify(bus,atLeastOnce()).post(new PluginMessage("shortestpath","clear"));
        ArgumentCaptor<NavigationButton> navigationButton=ArgumentCaptor.forClass(NavigationButton.class);
        verify(toolbar).addNavigation(navigationButton.capture());
        FarmingHelperPanel sidebar=(FarmingHelperPanel)navigationButton.getValue().getPanel();
        FarmRunPatch beforeHide=plugin.activePatch();
        clearInvocations(bus);
        // Both collapsing the sidebar and selecting another plugin deactivate this panel.
        SwingUtilities.invokeAndWait(sidebar::onDeactivate);
        plugin.onGameTick(null);
        assertSame(beforeHide,plugin.activePatch());
        verify(bus,never()).post(new PluginMessage("shortestpath","clear"));
        SwingUtilities.invokeAndWait(sidebar::onActivate);
        assertSame(beforeHide,plugin.activePatch());
        // Progress and navigation still update when the panel is not visible.
        SwingUtilities.invokeAndWait(sidebar::onDeactivate);
        plugin.skip(plugin.activePatch());
        assertEquals("Catherby",plugin.activePatch().getLocation());
        verify(bus).post(new PluginMessage("shortestpath","path",Map.of("start",start,"target",new WorldPoint(2813,3463,0))));
        SwingUtilities.invokeAndWait(sidebar::onActivate);
        assertEquals("Catherby",plugin.activePatch().getLocation());
        clearInvocations(bus);
        plugin.onPluginChanged(new net.runelite.client.events.PluginChanged(shortestPath,false));
        plugin.onGameTick(null);
        assertEquals("Catherby", plugin.activePatch().getLocation());
        verifyNoInteractions(bus);
        plugin.onPluginChanged(new net.runelite.client.events.PluginChanged(shortestPath,true));
        verify(bus).post(new PluginMessage("shortestpath","path",Map.of("start",start,"target",new WorldPoint(2813,3463,0))));
        SwingUtilities.invokeAndWait(() -> {
            javax.swing.JButton end=findEndButton(sidebar);
            assertNotNull("Explicit end control must remain available",end);
            end.doClick(0);
        });
        assertNull(plugin.activePatch());
        plugin.onGameTick(null);
        verify(bus,atLeastOnce()).post(new PluginMessage("shortestpath","clear"));
        plugin.start(EnumSet.of(FarmRunType.HERB));
        assertEquals("Ardougne",plugin.activePatch().getLocation());
        plugin.stop();
        when(config.getRSProfileConfiguration("farming-helper","patchEnabled_Falador~HERB~")).thenReturn("true");
        plugin.start(EnumSet.of(FarmRunType.HERB));
        int[][] herbTargets = {{3058,3311},{2670,3374},{2813,3463},{3605,3529},{1738,3550},
            {2826,3694},{2848,3934},{1238,3726},{3789,2837},{1582,3094}};
        for (int[] xy : herbTargets)
        {
            verify(bus,atLeastOnce()).post(new PluginMessage("shortestpath","path",
                Map.of("start",start,"target",new WorldPoint(xy[0],xy[1],0))));
            clearInvocations(bus);
            plugin.skip(plugin.activePatch());
        }
        assertNull(plugin.activePatch());
        plugin.shutDown();
    }
    private static javax.swing.JButton findEndButton(java.awt.Container root)
    {
        for(java.awt.Component child:root.getComponents())
        {
            if(child instanceof javax.swing.JButton && "End farming run".equals(((javax.swing.JButton)child).getToolTipText()))
                return (javax.swing.JButton)child;
            if(child instanceof java.awt.Container)
            {
                javax.swing.JButton found=findEndButton((java.awt.Container)child);
                if(found!=null)return found;
            }
        }
        return null;
    }
    @Test public void manualRunFiltersPhysicalPatchesAndKeepsClusterTogether()
    {
        Player player=mock(Player.class);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(2813,3463,0));
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getRealSkillLevel(Skill.FARMING)).thenReturn(72);
        when(client.getIntStack()).thenReturn(new int[]{2});
        when(config.getRSProfileKey()).thenReturn("profile");
        when(config.getRSProfileConfiguration("farming-helper","patchEnabled_Falador~HERB~")).thenReturn("false");
        when(reader.observeType(anyString(),anyString(),anyString(),any())).thenReturn(
            new QuestFarmingStateReader.Observation(null,QuestFarmingStateReader.Source.NONE,0,null,""));
        doAnswer(invocation->{((Runnable)invocation.getArgument(0)).run();return null;}).when(thread).invokeLater(any(Runnable.class));
        plugin.startUp();
        java.util.Set<String> keys=new java.util.HashSet<>();
        for(FarmRunPatch p:plugin.patches())if((p.getLocation().equals("Catherby")&&(p.getPatchType()==PatchType.HERB||p.getPatchType()==PatchType.FLOWER))||
            p.getPatchType()==PatchType.MUSHROOM||(p.getLocation().equals("Falador")&&p.getPatchType()==PatchType.HERB))keys.add(p.key());
        plugin.startManual(keys);
        assertEquals("Catherby",plugin.activePatch().getLocation());
        assertEquals(PatchType.FLOWER,plugin.activePatch().getPatchType());
        plugin.skip(plugin.activePatch());
        assertEquals("Catherby",plugin.activePatch().getLocation());
        assertEquals(PatchType.HERB,plugin.activePatch().getPatchType());
        plugin.skip(plugin.activePatch());
        assertEquals(PatchType.MUSHROOM,plugin.activePatch().getPatchType());
        plugin.skip(plugin.activePatch());
        assertNull(plugin.activePatch());
        verifyNoInteractions(bus);
        plugin.shutDown();
    }
}
