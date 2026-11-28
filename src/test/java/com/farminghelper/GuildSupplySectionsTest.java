package com.farminghelper;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class GuildSupplySectionsTest
{
    @Test public void quantitiesAndCarriedStockArePartitionedOnce()
    {
        var result=GuideView.guildSections(List.of(new GuideView.ItemRow(1,"Maple sapling",3,7,4,2,false)),Map.of(1,2));
        assertEquals(2,result.size());assertEquals(1,result.get(0).section);assertEquals(2,result.get(0).needed);assertEquals(2,result.get(0).owned);
        assertEquals(2,result.get(1).section);assertEquals(5,result.get(1).needed);assertEquals(1,result.get(1).owned);
        assertEquals(4,result.get(0).bank+result.get(1).bank);assertEquals(2,result.get(0).vault+result.get(1).vault);
    }
}