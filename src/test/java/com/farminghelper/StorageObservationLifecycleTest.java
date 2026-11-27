package com.farminghelper;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class StorageObservationLifecycleTest
{
    @Mock Client client;
    @Mock ConfigManager config;
    @Mock ItemManager itemManager;
    @InjectMocks FarmingHelperPlugin plugin;

    @Test public void incompleteReadsNeverEraseHistoryAndClosingAfterWithdrawalRetainsFinalCounts()
    {
        int seed=ItemID.RANARR_SEED;
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(config.getRSProfileKey()).thenReturn("one");
        when(config.getRSProfileConfiguration("farming-helper","observedBankV1")).thenReturn(seed+":12");
        ItemComposition def=mock(ItemComposition.class);
        when(def.getPlaceholderTemplateId()).thenReturn(-1);
        when(itemManager.getItemComposition(seed)).thenReturn(def);
        Widget bank=mock(Widget.class);
        ItemContainer nativeBank=mock(ItemContainer.class);
        when(client.getWidget(InterfaceID.Bankmain.UNIVERSE)).thenReturn(bank);
        when(client.getItemContainer(InventoryID.BANK)).thenReturn(nativeBank);
        when(nativeBank.getItems()).thenReturn((Item[])null);
        plugin.sampleStorage();
        assertEquals(Map.of(seed,12),plugin.stock().storage);
        verify(config,never()).setRSProfileConfiguration(eq("farming-helper"),eq("observedBankV1At"),anyString());

        when(nativeBank.getItems()).thenReturn(new Item[]{new Item(seed,5)});
        ItemContainerChanged event=mock(ItemContainerChanged.class);
        when(event.getContainerId()).thenReturn(InventoryID.BANK);
        when(event.getItemContainer()).thenReturn(nativeBank);
        plugin.onItemContainerChanged(event);
        when(client.getWidget(InterfaceID.Bankmain.UNIVERSE)).thenReturn(null);
        plugin.sampleStorage();
        assertEquals(Map.of(seed,5),plugin.stock().storage);
        verify(config).setRSProfileConfiguration("farming-helper","observedBankV1",seed+":5");
        verify(config).setRSProfileConfiguration(eq("farming-helper"),eq("observedBankV1At"),anyString());

        when(client.getWidget(InterfaceID.Bankmain.UNIVERSE)).thenReturn(bank);
        when(nativeBank.getItems()).thenReturn(new Item[0]);
        plugin.sampleStorage();
        assertTrue(plugin.stock().storage.isEmpty());
        verify(config).setRSProfileConfiguration("farming-helper","observedBankV1","");
    }

    @Test public void pendingStorageAndCachedSeedBoxAreIsolatedAcrossProfiles()
    {
        int seed=ItemID.RANARR_SEED;
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(config.getRSProfileKey()).thenReturn("one");
        when(config.getRSProfileConfiguration("farming-helper","observedBankV1")).thenReturn(seed+":12");
        when(config.getRSProfileConfiguration("farming-helper","observedSeedBoxV2")).thenReturn(seed+":99");
        ItemComposition def=mock(ItemComposition.class);
        when(def.getPlaceholderTemplateId()).thenReturn(-1);
        when(itemManager.getItemComposition(seed)).thenReturn(def);
        when(itemManager.getItemComposition(ItemID.SEED_BOX)).thenReturn(def);
        ItemContainer bag=mock(ItemContainer.class);
        when(bag.getItems()).thenReturn(new Item[]{new Item(ItemID.SEED_BOX,1)});
        when(client.getItemContainer(InventoryID.INV)).thenReturn(bag);
        assertEquals(Map.of(seed,12),plugin.stock().storage);
        assertTrue(plugin.stock().seedBox.isEmpty());
        Widget bank=mock(Widget.class);when(client.getWidget(InterfaceID.Bankmain.UNIVERSE)).thenReturn(bank);
        ItemContainer nativeBank=mock(ItemContainer.class);when(nativeBank.getItems()).thenReturn(new Item[]{new Item(seed,8)});
        ItemContainerChanged event=mock(ItemContainerChanged.class);
        when(event.getContainerId()).thenReturn(InventoryID.BANK);when(event.getItemContainer()).thenReturn(nativeBank);
        plugin.onItemContainerChanged(event);
        when(config.getRSProfileKey()).thenReturn("two");
        when(config.getRSProfileConfiguration("farming-helper","observedBankV1")).thenReturn(seed+":3");
        when(client.getWidget(InterfaceID.Bankmain.UNIVERSE)).thenReturn(null);
        plugin.sampleStorage();
        assertEquals(Map.of(seed,3),plugin.stock().storage);
        assertTrue(plugin.stock().seedBox.isEmpty());
        verify(config,never()).setRSProfileConfiguration("farming-helper","observedBankV1",seed+":8");
    }
}
