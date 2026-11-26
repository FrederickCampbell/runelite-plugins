package com.farminghelper;

import com.farminghelper.queststate.QuestFarmingStateReader;
import java.util.Set;
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

    @Test public void preparationRetargetsAfterStorageObservationsAndReleasesWhenCarried() throws Exception
    {
        int seed=net.runelite.api.gameval.ItemID.RANARR_SEED;
        var start=new WorldPoint(3092,3243,0);
        Player player=mock(Player.class);when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldLocation()).thenReturn(start);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getIntStack()).thenReturn(new int[]{2});
        when(client.getRealSkillLevel(Skill.FARMING)).thenReturn(72);
        when(config.getRSProfileKey()).thenReturn("one");
        when(config.getRSProfileConfiguration("farming-helper","startAtV1")).thenReturn("BANK");
        when(config.getRSProfileConfiguration("farming-helper","observedBankV1")).thenReturn(seed+":1");
        doAnswer(i->{((Runnable)i.getArgument(0)).run();return null;}).when(thread).invokeLater(any(Runnable.class));
        when(reader.observeType(anyString(),anyString(),anyString(),any())).thenReturn(
            new QuestFarmingStateReader.Observation(null,QuestFarmingStateReader.Source.NONE,0,null,""));

        var items=mock(net.runelite.client.game.ItemManager.class);
        var field=FarmingHelperPlugin.class.getDeclaredField("itemManager");field.setAccessible(true);field.set(plugin,items);
        var definition=mock(net.runelite.api.ItemComposition.class);
        when(definition.getNote()).thenReturn(-1);when(definition.getPlaceholderTemplateId()).thenReturn(-1);
        when(definition.getName()).thenReturn("Ranarr seed");
        when(items.getItemComposition(anyInt())).thenReturn(definition);
        when(items.getImage(anyInt())).thenReturn(new net.runelite.client.util.AsyncBufferedImage(thread,1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB));
        plugin.startUp();
        plugin.startManual(Set.of("Catherby~HERB~"));
        assertEquals(Preparation.DRAYNOR,plugin.activeLeg().point);

        var bank=mock(net.runelite.api.ItemContainer.class);
        var vault=mock(net.runelite.api.ItemContainer.class);
        var root=mock(net.runelite.api.widgets.Widget.class);
        when(bank.getItems()).thenReturn(new net.runelite.api.Item[0]);
        when(vault.getItems()).thenReturn(new net.runelite.api.Item[]{new net.runelite.api.Item(seed,1)});
        when(client.getItemContainer(net.runelite.api.InventoryID.BANK)).thenReturn(bank);
        when(client.getItemContainer(net.runelite.api.InventoryID.SEED_VAULT)).thenReturn(vault);
        when(client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.UNIVERSE)).thenReturn(root);
        plugin.onGameTick(null);
        when(client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.UNIVERSE)).thenReturn(null);
        when(client.getWidget(net.runelite.api.gameval.InterfaceID.SeedVault.UNIVERSE)).thenReturn(root);
        plugin.onGameTick(null);
        when(client.getWidget(net.runelite.api.gameval.InterfaceID.SeedVault.UNIVERSE)).thenReturn(null);
        plugin.onGameTick(null);
        assertEquals(Preparation.SEED_VAULT.point,plugin.activeLeg().point);

        var inventory=mock(net.runelite.api.ItemContainer.class);
        when(inventory.getItems()).thenReturn(plugin.requiredItems().stream().map(id->new net.runelite.api.Item(id,1000)).toArray(net.runelite.api.Item[]::new));
        when(client.getItemContainer(net.runelite.api.InventoryID.INVENTORY)).thenReturn(inventory);
        var changed=mock(net.runelite.api.events.ItemContainerChanged.class);
        when(changed.getContainerId()).thenReturn(net.runelite.api.InventoryID.INVENTORY.getId());
        plugin.onItemContainerChanged(changed);plugin.onGameTick(null);
        assertTrue(plugin.missingStorageItems().isEmpty());
        assertNull(plugin.activeLeg().point);
        plugin.shutDown();
    }

    @Test public void aHopRetainsRunPreferencesAndLogoutClearsTheSession() throws Exception
    {
        doAnswer(i->{((Runnable)i.getArgument(0)).run();return null;}).when(thread).invokeLater(any(Runnable.class));
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(config.getRSProfileKey()).thenReturn("one");
        when(client.getIntStack()).thenReturn(new int[]{2});
        when(client.getRealSkillLevel(Skill.FARMING)).thenReturn(90);
        plugin.startUp();
        plugin.startIndividual(PatchType.HERB);
        plugin.setRunPolicy(RunPolicy.NONE,false);
        var hop=new net.runelite.api.events.GameStateChanged();
        hop.setGameState(GameState.HOPPING);plugin.onGameStateChanged(hop);
        plugin.onGameTick(null);
        assertEquals(RunPolicy.NONE,plugin.runPolicy());
        assertFalse(plugin.runPayRemoval());
        hop.setGameState(GameState.LOGIN_SCREEN);plugin.onGameStateChanged(hop);
        plugin.startIndividual(PatchType.HERB);
        assertEquals(RunPolicy.BOTH,plugin.runPolicy());
        assertTrue(plugin.runPayRemoval());
        plugin.shutDown();
    }

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
        plugin.startIndividual(PatchType.HERB);
        assertEquals("Farming Guild",plugin.activePatch().getLocation());
        verifyNoInteractions(bus);
        QuestFarmingStateReader.Observation growing = new QuestFarmingStateReader.Observation(
            com.farminghelper.queststate.CropState.GROWING,QuestFarmingStateReader.Source.LIVE,0,null,"");
        when(reader.observeType(eq("HERB"),eq("Farming Guild"),anyString(),any())).thenReturn(growing);
        plugin.onGameTick(null);
        assertEquals("Morytania",plugin.activePatch().getLocation());
        when(reader.observeType(eq("HERB"),eq("Farming Guild"),anyString(),any())).thenReturn(
            new QuestFarmingStateReader.Observation(null,QuestFarmingStateReader.Source.NONE,0,null,""));
        plugin.stop();
        verifyNoInteractions(bus);
        plugin.onPluginChanged(new net.runelite.client.events.PluginChanged(shortestPath,true));
        plugin.startIndividual(PatchType.HERB);
        assertEquals("Farming Guild",plugin.activePatch().getLocation());
        verify(bus).post(new PluginMessage("shortestpath","path",Map.of("start",start,"target",new WorldPoint(1238,3726,0))));
        plugin.onGameTick(null);
        // Starting a guide must not clear an unrelated Shortest Path route.
        // Explicit guide termination below must still clear our own route.
        verify(bus,never()).post(new PluginMessage("shortestpath","clear"));
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
        assertEquals("Morytania",plugin.activePatch().getLocation());
        verify(bus).post(new PluginMessage("shortestpath","path",Map.of("start",start,"target",new WorldPoint(3605,3529,0))));
        SwingUtilities.invokeAndWait(sidebar::onActivate);
        assertEquals("Morytania",plugin.activePatch().getLocation());
        clearInvocations(bus);
        plugin.onPluginChanged(new net.runelite.client.events.PluginChanged(shortestPath,false));
        plugin.onGameTick(null);
        assertEquals("Morytania", plugin.activePatch().getLocation());
        verifyNoInteractions(bus);
        plugin.onPluginChanged(new net.runelite.client.events.PluginChanged(shortestPath,true));
        verify(bus).post(new PluginMessage("shortestpath","path",Map.of("start",start,"target",new WorldPoint(3605,3529,0))));
        SwingUtilities.invokeAndWait(() -> {
            javax.swing.JButton end=findEndButton(sidebar);
            assertNotNull("Explicit end control must remain available",end);
            end.doClick(0);
        });
        assertNull(plugin.activePatch());
        plugin.onGameTick(null);
        verify(bus,atLeastOnce()).post(new PluginMessage("shortestpath","clear"));
        plugin.startIndividual(PatchType.HERB);
        assertEquals("Farming Guild",plugin.activePatch().getLocation());
        plugin.stop();
        when(config.getRSProfileConfiguration("farming-helper","patchEnabled_Falador~HERB~")).thenReturn("true");
        plugin.startIndividual(PatchType.HERB);
        int[][] herbTargets = {{1238,3726},{3058,3311},{3605,3529},{2670,3374},{2813,3463},{1738,3550},
            {1582,3094},{3789,2837},{2826,3694},{2848,3934}};
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
    @Test public void globalPreferencesWinAndRunOverridesExpireAcrossRuns() throws Exception
    {
        Player player=mock(Player.class);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(2813,3463,0));
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getRealSkillLevel(Skill.FARMING)).thenReturn(72);
        when(client.getIntStack()).thenReturn(new int[]{2});
        when(config.getRSProfileKey()).thenReturn("profile");
        when(config.getRSProfileConfiguration("farming-helper","directorCompostChoice")).thenReturn("ULTRA");
        when(config.getRSProfileConfiguration("farming-helper","directorProtectTrees")).thenReturn("false");
        when(config.getRSProfileConfiguration("farming-helper","directorPayRemoval")).thenReturn("true");
        when(reader.observeType(anyString(),anyString(),anyString(),any())).thenReturn(new QuestFarmingStateReader.Observation(null,QuestFarmingStateReader.Source.NONE,0,null,""));
        doAnswer(invocation->{((Runnable)invocation.getArgument(0)).run();return null;}).when(thread).invokeLater(any(Runnable.class));
        plugin.startUp();
        plugin.startIndividual(PatchType.HERB);
        assertEquals(RunPolicy.COMPOST,plugin.runPolicy());assertTrue(plugin.runPayRemoval());
        plugin.setRunPolicy(RunPolicy.NONE,false);plugin.setSeedlessPolicy(SeedlessPolicy.SKIP);
        assertEquals(RunPolicy.NONE,plugin.runPolicy());assertFalse(plugin.runPayRemoval());assertTrue(plugin.hideSeedless());assertFalse(plugin.clearSeedless());
        plugin.setSeedlessPolicy(SeedlessPolicy.GUIDE);
        FarmRunPatch before=plugin.activePatch();assertNotNull(before);
        net.runelite.api.events.GameStateChanged hop=mock(net.runelite.api.events.GameStateChanged.class);
        when(hop.getGameState()).thenReturn(GameState.HOPPING);plugin.onGameStateChanged(hop);
        assertSame(before,plugin.activePatch());
        plugin.onGameTick(null);assertSame(before,plugin.activePatch());assertEquals(RunPolicy.NONE,plugin.runPolicy());
        plugin.stop();plugin.startIndividual(PatchType.HERB);
        assertEquals(RunPolicy.COMPOST,plugin.runPolicy());assertTrue(plugin.runPayRemoval());assertFalse(plugin.hideSeedless());
        verify(config,never()).getRSProfileConfiguration(eq("farming-helper"),startsWith("runPolicy_"));
        verify(config,never()).getRSProfileConfiguration(eq("farming-helper"),startsWith("runPayRemoval_"));
        plugin.stop();plugin.shutDown();SwingUtilities.invokeAndWait(()->{});
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

    private static QuestFarmingStateReader.Observation liveHop(com.farminghelper.queststate.CropState state,int raw)
    {return new QuestFarmingStateReader.Observation(state,QuestFarmingStateReader.Source.LIVE,1,null,"",raw);}

    @Test public void deadHopsFollowSeedlessPolicyAndReopenAfterVerifiedLoss()
    {
        var state=com.farminghelper.queststate.CropState.DEAD;
        Player player=mock(Player.class);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(2572,3102,0));
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getRealSkillLevel(Skill.FARMING)).thenReturn(99);
        when(client.getIntStack()).thenReturn(new int[]{2});
        when(config.getRSProfileKey()).thenReturn("profile");
        var observation=new java.util.concurrent.atomic.AtomicReference<>(liveHop(state,220));
        when(reader.observeType(eq("HOPS"),eq("Yanille"),anyString(),any())).thenAnswer(i->observation.get());
        doAnswer(i->{((Runnable)i.getArgument(0)).run();return null;}).when(thread).invokeLater(any(Runnable.class));
        plugin.startUp();
        try
        {
            plugin.startManual(Set.of("Yanille~HOPS~"));
            plugin.setRunPolicy(RunPolicy.NONE,false);plugin.setRunSupplies(false,false);
            assertNotNull(plugin.activePatch());assertEquals(StepEngine.Action.CLEAR_DEAD,plugin.activeAction());
            plugin.setSeedlessPolicy(SeedlessPolicy.SKIP);
            assertNull(plugin.activePatch());
            plugin.setSeedlessPolicy(SeedlessPolicy.CLEAR);
            assertNotNull(plugin.activePatch());assertEquals(StepEngine.Action.CLEAR_DEAD,plugin.activeAction());
            observation.set(liveHop(com.farminghelper.queststate.CropState.EMPTY,3));plugin.onGameTick(null);
            assertNull("Clear-only completes when verified empty",plugin.activePatch());
            plugin.setSeedlessPolicy(SeedlessPolicy.GUIDE);
            observation.set(liveHop(state,221));plugin.onGameTick(null);
            assertNotNull(plugin.activePatch());assertEquals(StepEngine.Action.CLEAR_DEAD,plugin.activeAction());
            observation.set(liveHop(com.farminghelper.queststate.CropState.EMPTY,3));plugin.onGameTick(null);
            assertEquals(StepEngine.Action.PLANT,plugin.activeAction());
            observation.set(liveHop(com.farminghelper.queststate.CropState.GROWING,84));plugin.onGameTick(null);
            assertNull("Confirmed planting completes the plot",plugin.activePatch());
            observation.set(liveHop(state,222));plugin.onGameTick(null);
            assertEquals(StepEngine.Action.CLEAR_DEAD,plugin.activeAction());
            assertNotNull(plugin.activePatch());
            plugin.skip(plugin.activePatch());plugin.onGameTick(null);
            assertNull("Explicit skip remains authoritative",plugin.activePatch());
        }
        finally {plugin.shutDown();}
    }

    @Test public void deadHopsDuringWateringDetourReturnToNormalClearing()
    {
        Player player=mock(Player.class);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(2572,3102,0));
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getRealSkillLevel(Skill.FARMING)).thenReturn(99);
        when(client.getIntStack()).thenReturn(new int[]{2});
        when(config.getRSProfileKey()).thenReturn("profile");
        var observation=new java.util.concurrent.atomic.AtomicReference<>(
            liveHop(com.farminghelper.queststate.CropState.GROWING,84));
        when(reader.observeType(eq("HOPS"),eq("Yanille"),anyString(),any())).thenAnswer(i->observation.get());
        when(reader.inPatchRegion(eq("HOPS"),eq("Yanille"),anyString(),any())).thenReturn(true);
        when(highlight.hasLocalPatch(argThat(p->p!=null&&p.getPatchType()==PatchType.HOPS&&p.getLocation().equals("Yanille")))).thenReturn(true);
        doAnswer(i->{((Runnable)i.getArgument(0)).run();return null;}).when(thread).invokeLater(any(Runnable.class));
        plugin.startUp();
        try
        {
            plugin.startManual(Set.of("Yanille~HOPS~"));
            assertNotNull(plugin.activePatch());assertEquals(StepEngine.Action.WATER,plugin.activeAction());
            observation.set(liveHop(com.farminghelper.queststate.CropState.DEAD,220));
            plugin.onGameTick(null);
            assertNotNull(plugin.activePatch());assertEquals(StepEngine.Action.CLEAR_DEAD,plugin.activeAction());
        }
        finally {plugin.shutDown();}
    }
}
