package com.farminghelper;

import java.util.*;
import java.util.function.IntFunction;
import net.runelite.api.ItemComposition;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PhysicalStockTest
{
    private static ItemComposition item(String name,int template)
    {
        ItemComposition item=mock(ItemComposition.class);
        when(item.getName()).thenReturn(name);
        when(item.getPlaceholderTemplateId()).thenReturn(template);
        return item;
    }
    @Test public void sameNamePlaceholderCannotFundCalquat()
    {
        int seed=5290,placeholder=900001;
        Map<Integer,ItemComposition> definitions=Map.of(seed,item("Calquat tree seed",-1),placeholder,item("Calquat tree seed",14401));
        Map<Integer,Integer> physical=new HashMap<>(Map.of(placeholder,1));
        PhysicalStock.sanitize(physical,definitions::get);
        assertTrue(physical.isEmpty());
        FarmRunPatch p=new FarmRunPatch("Tai Bwo Wannai","",PatchType.CALQUAT);
        assertTrue(SeedChoices.allocate(List.of(p),99,physical,t->"",q->"",Map.of(),true).isEmpty());
        physical.put(seed,1);
        PhysicalStock.sanitize(physical,definitions::get);
        Map<Integer,Integer> aliases=TreePreparation.aliases(physical,id->definitions.get(id).getName());
        assertEquals(1,SeedChoices.allocate(List.of(p),99,TreePreparation.allocationStock(physical,aliases),t->"",q->"",Map.of(),true).size());
    }
    @Test public void cachedPlaceholdersAndNonpositiveEntriesArePurged()
    {
        Map<Integer,Integer> cached=StockSnapshot.decode("10:3,11:1,12:9,13:0");
        ItemComposition real=item("Yew seed",-1), placeholder=item("Yew seed",14401);
        IntFunction<ItemComposition> definitions=id->id==10?real:id==11?placeholder:null;
        PhysicalStock.sanitize(cached,definitions);
        assertEquals(Map.of(10,3),cached);
        assertFalse(PhysicalStock.available(10,0,definitions));
        assertFalse(PhysicalStock.available(11,100,definitions));
    }
}
