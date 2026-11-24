package com.farminghelper;
import java.util.Set;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemComposition;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class ActiveRunBankTest
{
    @Test public void admitsOnlyIdsAlreadyInSuppliedNativeLayout()
    {
        assertEquals(Set.of(1513,1755),ActiveRunBank.admittedIds(new int[]{-1,1513,1755,-1,1513}));
        assertFalse(ActiveRunBank.admittedIds(new int[]{1513,-1}).contains(1755));
    }
    private static ItemContainer container(Item... contents)
    {
        ItemContainer result=mock(ItemContainer.class);
        when(result.getItems()).thenReturn(contents);
        return result;
    }
    @Test public void ownershipIncludesBankInventoryAndEquipmentWithoutGhostStock()
    {
        Client client=mock(Client.class);
        ItemComposition real=mock(ItemComposition.class),placeholder=mock(ItemComposition.class);
        when(real.getPlaceholderTemplateId()).thenReturn(-1);
        when(placeholder.getPlaceholderTemplateId()).thenReturn(14401);
        ItemContainer bank=container(new Item(952,1),new Item(556,90),new Item(999,1),new Item(555,0));
        ItemContainer inventory=container(new Item(556,10),new Item(7409,1));
        ItemContainer equipment=container(new Item(11850,1));
        when(client.getItemContainer(InventoryID.BANK)).thenReturn(bank);
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);
        when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
        assertEquals(Map.of(952,1,556,100,7409,1,11850,1),
            ActiveRunBank.ownedStock(client,id->id==999?placeholder:real));
    }
    @Test public void transfersAndWearingDoNotChangeOwnership()
    {
        Client client=mock(Client.class);
        ItemComposition real=mock(ItemComposition.class);
        when(real.getPlaceholderTemplateId()).thenReturn(-1);
        ItemContainer initialBank=container(new Item(11850,1),new Item(952,1));
        ItemContainer empty=container();
        ItemContainer inventory=container(new Item(952,1));
        ItemContainer equipment=container(new Item(11850,1));
        when(client.getItemContainer(InventoryID.BANK)).thenReturn(initialBank,empty);
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(empty,inventory);
        when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(empty,equipment);
        assertEquals(ActiveRunBank.ownedStock(client,id->real),ActiveRunBank.ownedStock(client,id->real));
    }
    @Test public void filtersExactPhysicalItemIds()
    {
        ItemComposition real=mock(ItemComposition.class),placeholder=mock(ItemComposition.class);
        when(real.getPlaceholderTemplateId()).thenReturn(-1);
        when(placeholder.getPlaceholderTemplateId()).thenReturn(14401);
        assertTrue(ActiveRunBank.includes(Set.of(1513,1755),1513,real));
        assertFalse(ActiveRunBank.includes(Set.of(1513),1513,placeholder));
        assertFalse(ActiveRunBank.includes(Set.of(1513),1515,real));
    }
}
