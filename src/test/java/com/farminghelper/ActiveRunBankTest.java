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
    private static Map<Integer,Integer> ownedStock(Client client,java.util.function.IntFunction<ItemComposition> definitions)
    {return ownedStock(client,definitions,id->id);}
    private static Map<Integer,Integer> ownedStock(Client client,java.util.function.IntFunction<ItemComposition> definitions,java.util.function.IntUnaryOperator canonical)
    {
        return new PhysicalStock(PhysicalStock.read(client.getItemContainer(InventoryID.INVENTORY),definitions),
            PhysicalStock.read(client.getItemContainer(InventoryID.EQUIPMENT),definitions),
            PhysicalStock.read(client.getItemContainer(InventoryID.BANK),definitions),Map.of(),id->"",canonical).shelf;
    }
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
            ownedStock(client,id->id==999?placeholder:real));
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
        assertEquals(ownedStock(client,id->real),ownedStock(client,id->real));
    }
    @Test public void wornCanonicalizationDoesNotDuplicateOrNormalizeOtherSources()
    {
        Client client=mock(Client.class);
        ItemComposition real=mock(ItemComposition.class);
        when(real.getPlaceholderTemplateId()).thenReturn(-1);
        ItemContainer bank=container(new Item(11850,1)),bag=container(new Item(952,1)),worn=container(new Item(11851,1));
        when(client.getItemContainer(InventoryID.BANK)).thenReturn(bank);
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(bag);
        when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(worn);
        assertEquals(Map.of(11850,2,952,1),ownedStock(client,id->real,id->id==11851?11850:id));
    }
    @Test public void combinedStockKeepsInputsAndAddsQuantities()
    {
        Map<Integer,Integer> a=Map.of(952,1,556,10),b=Map.of(556,20);
        assertEquals(Map.of(952,1,556,30),PhysicalStock.combine(a,b));
        assertEquals(Integer.valueOf(10),a.get(556));
        assertEquals(Integer.valueOf(20),b.get(556));
    }
    @Test public void filtersExactPhysicalItemIds()
    {
        ItemComposition real=mock(ItemComposition.class),placeholder=mock(ItemComposition.class);
        when(real.getPlaceholderTemplateId()).thenReturn(-1);
        when(placeholder.getPlaceholderTemplateId()).thenReturn(14401);
        assertTrue(ActiveRunBank.includes(Set.of(1513,1755),1513,real));
        when(placeholder.getPlaceholderId()).thenReturn(1513);
        assertTrue(ActiveRunBank.includes(Set.of(1513),900001,placeholder));
        assertFalse(ActiveRunBank.includes(Set.of(1515),900001,placeholder));
        Set<Integer> displayed=new java.util.HashSet<>();
        assertEquals(Map.of(1755,1),PhysicalStock.read(container(new Item(900001,0),new Item(1755,1)),
            id->id==900001?placeholder:real,displayed));
        assertEquals(Set.of(1513),displayed);
        assertFalse(ActiveRunBank.includes(Set.of(1513),1515,real));
    }
}
